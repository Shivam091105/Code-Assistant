package com.example.codeassistant.indexing;

import com.example.codeassistant.embedding.EmbeddingService;
import com.example.codeassistant.github.GitHubService;
import com.example.codeassistant.github.GitHubTreeDto;
import com.example.codeassistant.indexing.ChunkPersistenceService.ChunkToInsert;
import com.example.codeassistant.indexing.ChunkPersistenceService.FileShaUpdate;
import com.example.codeassistant.repository.IndexingStatus;
import com.example.codeassistant.repository.RepositoryEntity;
import com.example.codeassistant.repository.RepositoryJpaRepository;
import com.example.codeassistant.vector.PgVectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the indexing pipeline for one repository:
 *   GitHub tree -> diff against last-indexed blob SHAs -> read contents of
 *   changed/new files only -> chunk -> embed -> save chunks -> mark COMPLETED
 *
 * Indexing is incremental: GitHub's tree API returns a blob SHA (a content
 * hash) for every file. We keep the SHA we last indexed for each file in
 * the `indexed_files` table (see IndexedFile). On every run we compare:
 *   - same path, same SHA        -> file unchanged, skip entirely
 *   - same path, different SHA   -> file changed, re-fetch/chunk/embed it
 *   - new path                   -> new file, fetch/chunk/embed it
 *   - previously-tracked path
 *     no longer in the tree      -> file deleted, drop its chunks
 * Only the "changed/new" bucket costs a GitHub content fetch and an
 * embedding call, so repeat indexing runs on a repo with a handful of
 * changed files are dramatically cheaper than the old full re-embed.
 *
 * A full re-index can still be forced (see IndexingController's ?full=true)
 * by wiping indexed_files/code_chunks first, which makes every file look
 * "new" to the diff below.
 *
 * startIndexing() flips the status to INDEXING and hands off to
 * IndexingWorker, which does the actual work on a background thread pool
 * (see AsyncConfig) so the triggering HTTP request can return immediately.
 * The GitHub access token is captured by the caller and passed in
 * explicitly, since the security context does not propagate to @Async
 * worker threads.
 *
 * (The status update and the background job are deliberately in two
 * separate Spring beans: calling an @Async method via "this." within the
 * same bean would silently run it synchronously, since Spring's AOP proxy
 * only intercepts calls that arrive from outside the bean.)
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final ChunkPersistenceService chunkPersistenceService;
    private final IndexedFileJpaRepository indexedFileJpaRepository;
    private final GitHubService gitHubService;
    private final FileFilter fileFilter;
    private final CodeChunker codeChunker;
    private final EmbeddingService embeddingService;
    private final IndexingWorker indexingWorker;

    public IndexingService(RepositoryJpaRepository repositoryJpaRepository,
                            ChunkPersistenceService chunkPersistenceService,
                            IndexedFileJpaRepository indexedFileJpaRepository,
                            GitHubService gitHubService,
                            FileFilter fileFilter,
                            CodeChunker codeChunker,
                            EmbeddingService embeddingService,
                            IndexingWorker indexingWorker) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.chunkPersistenceService = chunkPersistenceService;
        this.indexedFileJpaRepository = indexedFileJpaRepository;
        this.gitHubService = gitHubService;
        this.fileFilter = fileFilter;
        this.codeChunker = codeChunker;
        this.embeddingService = embeddingService;
        this.indexingWorker = indexingWorker;
    }

    /**
     * Flips status to INDEXING (visible to the caller immediately) and hands
     * off the heavy work to the async worker.
     *
     * @param fullReindex if true, all previously-indexed chunks and blob-SHA
     *                    tracking for this repository are wiped first, so
     *                    every file is treated as new and re-embedded. Use
     *                    this after changing chunking/embedding settings, or
     *                    if the index is ever suspected to be inconsistent.
     */
    @Transactional
    public void startIndexing(Long repositoryId, String accessToken, boolean fullReindex) {
        RepositoryEntity repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalStateException("Repository disappeared before indexing could start"));
        repository.setIndexingStatus(IndexingStatus.INDEXING);
        repository.setIndexingError(null);
        repositoryJpaRepository.save(repository);

        indexingWorker.runIndexingJob(repositoryId, accessToken, fullReindex, this);
    }

    void runIndexingJob(Long repositoryId, String accessToken, boolean fullReindex) {
        log.info("Repository indexing started: repositoryId={}, fullReindex={}", repositoryId, fullReindex);
        try {
            RepositoryEntity repository = repositoryJpaRepository.findById(repositoryId)
                    .orElseThrow(() -> new IllegalStateException("Repository not found: " + repositoryId));

            if (fullReindex) {
                chunkPersistenceService.clearRepository(repository.getId());
            }

            indexIncrementally(repository, accessToken);

            repository.setIndexingStatus(IndexingStatus.COMPLETED);
            repositoryJpaRepository.save(repository);
            log.info("Repository indexing completed: repositoryId={}", repositoryId);
        } catch (Exception e) {
            log.error("Repository indexing failed: repositoryId={}", repositoryId, e);
            repositoryJpaRepository.findById(repositoryId).ifPresent(repository -> {
                repository.setIndexingStatus(IndexingStatus.FAILED);
                repository.setIndexingError(truncate(e.getMessage(), 2000));
                repositoryJpaRepository.save(repository);
            });
        }
    }

    /**
     * Diffs the current GitHub tree against what's already indexed, fetches
     * and embeds only the files that are new or changed, and reconciles
     * deletions - all OUTSIDE of a single long-lived transaction (these are
     * slow network calls), then persists everything in one batch-friendly
     * transaction at the end. This avoids holding a DB connection/transaction
     * open for the whole indexing job.
     */
    private void indexIncrementally(RepositoryEntity repository, String accessToken) {
        GitHubTreeDto tree = gitHubService.getRepositoryTree(
                accessToken, repository.getOwner(), repository.getName(), repository.getDefaultBranch());

        List<GitHubTreeDto.Entry> filesToIndex = tree.tree().stream()
                .filter(entry -> "blob".equals(entry.type()))
                .filter(entry -> fileFilter.shouldIndex(entry.path(), entry.size()))
                .toList();

        Map<String, String> currentShaByPath = new HashMap<>();
        for (GitHubTreeDto.Entry entry : filesToIndex) {
            currentShaByPath.put(entry.path(), entry.sha());
        }

        Map<String, String> previousShaByPath = new HashMap<>();
        for (IndexedFile indexedFile : indexedFileJpaRepository.findByRepositoryId(repository.getId())) {
            previousShaByPath.put(indexedFile.getFilePath(), indexedFile.getBlobSha());
        }

        List<GitHubTreeDto.Entry> changedOrNewFiles = filesToIndex.stream()
                .filter(entry -> !entry.sha().equals(previousShaByPath.get(entry.path())))
                .toList();

        List<String> deletedFiles = previousShaByPath.keySet().stream()
                .filter(path -> !currentShaByPath.containsKey(path))
                .toList();

        int unchangedCount = filesToIndex.size() - changedOrNewFiles.size();
        log.info("Incremental diff: repositoryId={}, changedOrNew={}, deleted={}, unchanged={}",
                repository.getId(), changedOrNewFiles.size(), deletedFiles.size(), unchangedCount);

        if (changedOrNewFiles.isEmpty() && deletedFiles.isEmpty()) {
            log.info("No file changes detected, nothing to re-embed: repositoryId={}", repository.getId());
            return;
        }

        List<PendingChunk> pending = new ArrayList<>();
        List<FileShaUpdate> shaUpdates = new ArrayList<>();
        // Only files we actually managed to fetch content for get their old
        // chunks replaced - if a fetch fails we leave the existing chunks
        // and tracking row alone so the file is simply retried next run.
        List<String> filePathsWithStaleChunks = new ArrayList<>();

        for (GitHubTreeDto.Entry entry : changedOrNewFiles) {
            String content = gitHubService.getFileContent(
                    accessToken, repository.getOwner(), repository.getName(), entry.path(), repository.getDefaultBranch());
            if (content == null || content.isBlank()) {
                log.warn("Skipping unreadable/empty file, will retry next run: repositoryId={}, path={}",
                        repository.getId(), entry.path());
                continue;
            }
            for (CodeChunker.Chunk chunk : codeChunker.chunk(content)) {
                pending.add(new PendingChunk(entry.path(), chunk));
            }
            shaUpdates.add(new FileShaUpdate(entry.path(), entry.sha()));
            filePathsWithStaleChunks.add(entry.path());
        }
        filePathsWithStaleChunks.addAll(deletedFiles);

        log.info("Files fetched for (re)indexing: repositoryId={}, filesFetched={}, chunksCreated={}",
                repository.getId(), shaUpdates.size(), pending.size());

        List<List<Float>> embeddings = embeddingService.embedBatch(
                pending.stream().map(p -> p.chunk().content()).toList());

        List<ChunkToInsert> toInsert = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            PendingChunk p = pending.get(i);
            String embeddingLiteral = PgVectorUtils.toPgVectorLiteral(embeddings.get(i));
            toInsert.add(new ChunkToInsert(p.filePath(), p.chunk().content(), p.chunk().startLine(), p.chunk().endLine(), embeddingLiteral));
        }

        chunkPersistenceService.applyIncrementalChanges(
                repository.getId(), filePathsWithStaleChunks, toInsert, deletedFiles, shaUpdates);
    }

    record PendingChunk(String filePath, CodeChunker.Chunk chunk) {
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return "Unknown error during indexing";
        }
        return message.length() <= max ? message : message.substring(0, max);
    }
}
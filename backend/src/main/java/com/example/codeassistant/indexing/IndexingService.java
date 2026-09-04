package com.example.codeassistant.indexing;

import com.example.codeassistant.embedding.EmbeddingService;
import com.example.codeassistant.github.GitHubService;
import com.example.codeassistant.github.GitHubTreeDto;
import com.example.codeassistant.indexing.ChunkPersistenceService.ChunkToInsert;
import com.example.codeassistant.repository.IndexingStatus;
import com.example.codeassistant.repository.RepositoryEntity;
import com.example.codeassistant.repository.RepositoryJpaRepository;
import com.example.codeassistant.vector.PgVectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the indexing pipeline for one repository:
 *   GitHub files -> filter -> read contents -> chunk -> embed -> save chunks -> mark COMPLETED
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
    private final GitHubService gitHubService;
    private final FileFilter fileFilter;
    private final CodeChunker codeChunker;
    private final EmbeddingService embeddingService;
    private final IndexingWorker indexingWorker;

    public IndexingService(RepositoryJpaRepository repositoryJpaRepository,
                            ChunkPersistenceService chunkPersistenceService,
                            GitHubService gitHubService,
                            FileFilter fileFilter,
                            CodeChunker codeChunker,
                            EmbeddingService embeddingService,
                            IndexingWorker indexingWorker) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.chunkPersistenceService = chunkPersistenceService;
        this.gitHubService = gitHubService;
        this.fileFilter = fileFilter;
        this.codeChunker = codeChunker;
        this.embeddingService = embeddingService;
        this.indexingWorker = indexingWorker;
    }

    /**
     * Flips status to INDEXING (visible to the caller immediately) and hands
     * off the heavy work to the async worker.
     */
    @Transactional
    public void startIndexing(Long repositoryId, String accessToken) {
        RepositoryEntity repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalStateException("Repository disappeared before indexing could start"));
        repository.setIndexingStatus(IndexingStatus.INDEXING);
        repository.setIndexingError(null);
        repositoryJpaRepository.save(repository);

        indexingWorker.runIndexingJob(repositoryId, accessToken, this);
    }

    void runIndexingJob(Long repositoryId, String accessToken) {
        log.info("Repository indexing started: repositoryId={}", repositoryId);
        try {
            RepositoryEntity repository = repositoryJpaRepository.findById(repositoryId)
                    .orElseThrow(() -> new IllegalStateException("Repository not found: " + repositoryId));

            replaceChunks(repository, accessToken);

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
     * Fetches the tree + file contents from GitHub and generates embeddings
     * OUTSIDE of a single long-lived transaction (these are slow network
     * calls), then persists everything in one batch-friendly transaction at
     * the end. This avoids holding a DB connection/transaction open for the
     * whole indexing job.
     */
    private void replaceChunks(RepositoryEntity repository, String accessToken) {
        GitHubTreeDto tree = gitHubService.getRepositoryTree(
                accessToken, repository.getOwner(), repository.getName(), repository.getDefaultBranch());

        List<GitHubTreeDto.Entry> filesToIndex = tree.tree().stream()
                .filter(entry -> "blob".equals(entry.type()))
                .filter(entry -> fileFilter.shouldIndex(entry.path(), entry.size()))
                .toList();

        log.info("Files selected for indexing: repositoryId={}, count={}", repository.getId(), filesToIndex.size());

        List<PendingChunk> pending = new ArrayList<>();
        int filesProcessed = 0;

        for (GitHubTreeDto.Entry entry : filesToIndex) {
            String content = gitHubService.getFileContent(
                    accessToken, repository.getOwner(), repository.getName(), entry.path(), repository.getDefaultBranch());
            if (content == null || content.isBlank()) {
                continue;
            }
            for (CodeChunker.Chunk chunk : codeChunker.chunk(content)) {
                pending.add(new PendingChunk(entry.path(), chunk));
            }
            filesProcessed++;
        }

        log.info("Files processed: repositoryId={}, filesProcessed={}, chunksCreated={}",
                repository.getId(), filesProcessed, pending.size());

        List<List<Float>> embeddings = embeddingService.embedBatch(
                pending.stream().map(p -> p.chunk().content()).toList());

        List<ChunkToInsert> toInsert = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            PendingChunk p = pending.get(i);
            String embeddingLiteral = PgVectorUtils.toPgVectorLiteral(embeddings.get(i));
            toInsert.add(new ChunkToInsert(p.filePath(), p.chunk().content(), p.chunk().startLine(), p.chunk().endLine(), embeddingLiteral));
        }

        chunkPersistenceService.replaceChunks(repository.getId(), toInsert);
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
package com.example.codeassistant.indexing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes code chunks (including their pgvector embedding) using a native,
 * parameterized INSERT rather than JPA's entity save/persist.
 *
 * Why: Hibernate's automatic JDBC binding for a String field annotated
 * @JdbcTypeCode(SqlTypes.OTHER) mis-resolves the binder for pgvector's
 * `vector` column type (it tries to bind as VARBINARY and throws
 * "Could not convert 'java.lang.String' to '[B'"). Casting an ordinary text
 * parameter to `vector` on the database side, the same way
 * VectorSearchService already does for reads, sidesteps that entirely.
 *
 * This is a separate bean (not a method on IndexingService) so that
 * @Transactional here is scoped ONLY to the fast DB work - not the slow
 * GitHub/embedding network calls that happen earlier in the pipeline - and
 * so the annotation is guaranteed to actually apply (it's invoked from
 * IndexingService, i.e. from outside this bean, so Spring's transactional
 * proxy is not bypassed by self-invocation).
 */
@Service
public class ChunkPersistenceService {

    @PersistenceContext
    private EntityManager entityManager;

    public record ChunkToInsert(String filePath, String content, int startLine, int endLine, String embeddingLiteral) {
    }

    /** A file's freshly-observed GitHub blob SHA, to be recorded as "now indexed". */
    public record FileShaUpdate(String filePath, String blobSha) {
    }

    /**
     * Applies one incremental indexing pass in a single transaction:
     *  1. Delete existing chunks for any file whose content changed (so its
     *     old chunks don't linger alongside the new ones) or was removed
     *     from the repository.
     *  2. Insert the freshly-chunked-and-embedded content for changed/new
     *     files.
     *  3. Drop the blob-SHA tracking row for files that were removed.
     *  4. Upsert the blob-SHA tracking row for files that were (re)indexed,
     *     so the next run can tell they're unchanged.
     *
     * Unchanged files are touched nowhere in this method - their existing
     * chunks and tracking rows are simply left alone.
     */
    @Transactional
    public void applyIncrementalChanges(Long repositoryId,
                                         List<String> filePathsWithStaleChunks,
                                         List<ChunkToInsert> chunksToInsert,
                                         List<String> filePathsToStopTracking,
                                         List<FileShaUpdate> fileShaUpdates) {
        if (!filePathsWithStaleChunks.isEmpty()) {
            entityManager.createNativeQuery(
                            "DELETE FROM code_chunks WHERE repository_id = :repositoryId AND file_path IN (:paths)")
                    .setParameter("repositoryId", repositoryId)
                    .setParameter("paths", filePathsWithStaleChunks)
                    .executeUpdate();
        }

        for (ChunkToInsert chunk : chunksToInsert) {
            entityManager.createNativeQuery("""
                            INSERT INTO code_chunks
                                (repository_id, file_path, content, start_line, end_line, embedding, created_at)
                            VALUES
                                (:repositoryId, :filePath, :content, :startLine, :endLine, CAST(:embedding AS vector), now())
                            """)
                    .setParameter("repositoryId", repositoryId)
                    .setParameter("filePath", chunk.filePath())
                    .setParameter("content", chunk.content())
                    .setParameter("startLine", chunk.startLine())
                    .setParameter("endLine", chunk.endLine())
                    .setParameter("embedding", chunk.embeddingLiteral())
                    .executeUpdate();
        }

        if (!filePathsToStopTracking.isEmpty()) {
            entityManager.createNativeQuery(
                            "DELETE FROM indexed_files WHERE repository_id = :repositoryId AND file_path IN (:paths)")
                    .setParameter("repositoryId", repositoryId)
                    .setParameter("paths", filePathsToStopTracking)
                    .executeUpdate();
        }

        for (FileShaUpdate update : fileShaUpdates) {
            entityManager.createNativeQuery("""
                            INSERT INTO indexed_files (repository_id, file_path, blob_sha, updated_at)
                            VALUES (:repositoryId, :filePath, :blobSha, now())
                            ON CONFLICT (repository_id, file_path)
                            DO UPDATE SET blob_sha = EXCLUDED.blob_sha, updated_at = now()
                            """)
                    .setParameter("repositoryId", repositoryId)
                    .setParameter("filePath", update.filePath())
                    .setParameter("blobSha", update.blobSha())
                    .executeUpdate();
        }
    }

    /**
     * Wipes every chunk and every blob-SHA tracking row for a repository.
     * Used for a full re-index (e.g. triggered with ?full=true), after
     * which the normal incremental pass will treat every file as new and
     * re-embed everything from scratch.
     */
    @Transactional
    public void clearRepository(Long repositoryId) {
        entityManager.createNativeQuery("DELETE FROM code_chunks WHERE repository_id = :repositoryId")
                .setParameter("repositoryId", repositoryId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM indexed_files WHERE repository_id = :repositoryId")
                .setParameter("repositoryId", repositoryId)
                .executeUpdate();
    }
}
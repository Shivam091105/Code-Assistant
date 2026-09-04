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
 * VectorSearchService already does for reads, sidesteps that entirely and
 * keeps this class's job easy to explain: "delete old chunks, insert new
 * ones, both in one transaction."
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

    @Transactional
    public void replaceChunks(Long repositoryId, List<ChunkToInsert> chunks) {
        entityManager.createNativeQuery("DELETE FROM code_chunks WHERE repository_id = :repositoryId")
                .setParameter("repositoryId", repositoryId)
                .executeUpdate();

        for (ChunkToInsert chunk : chunks) {
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
    }
}
package com.example.codeassistant.vector;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the actual pgvector similarity query. Deliberately a plain native
 * query rather than a framework abstraction, so the retrieval step in the
 * RAG pipeline stays easy to read and explain: "find the chunks whose
 * embedding is closest (cosine distance) to the question's embedding,
 * scoped to one repository".
 */
@Service
public class VectorSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    public record SimilarChunk(Long id, String filePath, String content, int startLine, int endLine, double distance) {
    }

    @Transactional(readOnly = true)
    public List<SimilarChunk> findMostSimilar(Long repositoryId, String queryEmbeddingLiteral, int topK) {
        List<Tuple> rows = entityManager.createNativeQuery("""
                        SELECT id, file_path, content, start_line, end_line,
                               (embedding <=> CAST(:queryEmbedding AS vector)) AS distance
                        FROM code_chunks
                        WHERE repository_id = :repositoryId
                        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
                        LIMIT :topK
                        """, Tuple.class)
                .setParameter("repositoryId", repositoryId)
                .setParameter("queryEmbedding", queryEmbeddingLiteral)
                .setParameter("topK", topK)
                .getResultList();

        return rows.stream()
                .map(row -> new SimilarChunk(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("file_path"),
                        (String) row.get("content"),
                        ((Number) row.get("start_line")).intValue(),
                        ((Number) row.get("end_line")).intValue(),
                        ((Number) row.get("distance")).doubleValue()
                ))
                .toList();
    }
}

package com.example.codeassistant.rag;

import com.example.codeassistant.chat.LLMService;
import com.example.codeassistant.embedding.EmbeddingService;
import com.example.codeassistant.vector.PgVectorUtils;
import com.example.codeassistant.vector.VectorSearchService;
import com.example.codeassistant.vector.VectorSearchService.SimilarChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * The retrieval-augmented-generation pipeline:
 *   question -> question embedding -> pgvector search -> top-K chunks
 *            -> prompt -> LLM (streamed)
 *
 * Deliberately knows nothing about GitHub, users, or how chunks got into
 * the database - only question, retrieval, context, prompt, and the LLM.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final PromptBuilder promptBuilder;
    private final LLMService llmService;
    private final int topK;

    public RagService(EmbeddingService embeddingService,
                       VectorSearchService vectorSearchService,
                       PromptBuilder promptBuilder,
                       LLMService llmService,
                       @Value("${app.rag.top-k:5}") int topK) {
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
        this.topK = topK;
    }

    public record RagAnswer(Flux<String> answerStream, List<SimilarChunk> sources) {
    }

    public RagAnswer answerStreaming(Long repositoryId, String question, List<String> recentHistory) {
        List<SimilarChunk> sources = retrieve(repositoryId, question);
        String prompt = promptBuilder.build(question, sources, recentHistory);
        log.info("RAG query executed: repositoryId={}, retrievedChunks={}", repositoryId, sources.size());
        return new RagAnswer(llmService.stream(prompt), sources);
    }

    private List<SimilarChunk> retrieve(Long repositoryId, String question) {
        List<Float> questionEmbedding = embeddingService.embed(question);
        String literal = PgVectorUtils.toPgVectorLiteral(questionEmbedding);
        return vectorSearchService.findMostSimilar(repositoryId, literal, topK);
    }
}

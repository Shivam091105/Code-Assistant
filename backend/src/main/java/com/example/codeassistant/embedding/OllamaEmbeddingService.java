package com.example.codeassistant.embedding;

import com.example.codeassistant.common.UpstreamServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Calls a locally running Ollama instance's /api/embeddings endpoint.
 * Ollama has no batch embeddings endpoint, so embedBatch simply calls the
 * single-text endpoint in a loop - fine at this project's scale (a few
 * hundred chunks per repository).
 *
 * Active by default (app.ai.provider=ollama or unset).
 */
@Service
@ConditionalOnProperty(name = "app.ai.embedding-provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingService implements EmbeddingService {

    private final WebClient webClient;
    private final String model;
    private final int dimension;

    public OllamaEmbeddingService(WebClient.Builder webClientBuilder,
                                   @Value("${app.ai.ollama.base-url}") String baseUrl,
                                   @Value("${app.ai.embedding-model}") String model,
                                   @Value("${app.ai.embedding-dimension}") int dimension) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public List<Float> embed(String text) {
        try {
            EmbeddingResponse response = webClient.post()
                    .uri("/api/embeddings")
                    .bodyValue(new EmbeddingRequest(model, text))
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .block();
            if (response == null || response.embedding() == null) {
                throw new UpstreamServiceException("Ollama returned an empty embedding");
            }
            return response.embedding();
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to generate embedding via Ollama", e);
        }
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private record EmbeddingRequest(String model, String prompt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<Float> embedding) {
    }
}
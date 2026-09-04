package com.example.codeassistant.embedding;

import com.example.codeassistant.common.UpstreamServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Calls the Hugging Face Inference API's feature-extraction pipeline as an
 * alternative to Ollama when the user prefers a hosted free-tier option.
 * Only active when app.ai.provider=huggingface.
 */
@Service
@ConditionalOnProperty(name = "app.ai.embedding-provider", havingValue = "huggingface")
public class HuggingFaceEmbeddingService implements EmbeddingService {

    private final WebClient webClient;
    private final String model;
    private final int dimension;

    public HuggingFaceEmbeddingService(WebClient.Builder webClientBuilder,
                                        @Value("${app.ai.huggingface.api-url}") String apiUrl,
                                        @Value("${app.ai.huggingface.api-key}") String apiKey,
                                        @Value("${app.ai.embedding-model}") String model,
                                        @Value("${app.ai.embedding-dimension}") int dimension) {
        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public List<Float> embed(String text) {
        try {
            List<Float> result = webClient.post()
                    .uri("/pipeline/feature-extraction/{model}", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new Request(text))
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Float>>() {
                    })
                    .block();
            if (result == null) {
                throw new UpstreamServiceException("Hugging Face returned an empty embedding");
            }
            return result;
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to generate embedding via Hugging Face", e);
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

    private record Request(String inputs) {
    }
}
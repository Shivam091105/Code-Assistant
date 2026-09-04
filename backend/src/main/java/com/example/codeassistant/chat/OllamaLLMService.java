package com.example.codeassistant.chat;

import com.example.codeassistant.common.UpstreamServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Talks to a locally running Ollama server. Ollama's /api/generate endpoint
 * streams newline-delimited JSON objects by default, which is exactly what
 * we want for real token-by-token SSE - no artificial splitting needed.
 *
 * Active by default (app.ai.provider=ollama or unset).
 */
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLLMService implements LLMService {

    private final WebClient webClient;
    private final String model;

    public OllamaLLMService(WebClient.Builder webClientBuilder,
                             @Value("${app.ai.ollama.base-url}") String baseUrl,
                             @Value("${app.ai.llm-model}") String model) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.model = model;
    }

    @Override
    public String generate(String prompt) {
        var chunks = stream(prompt).collectList().block();
        return chunks == null ? "" : String.join("", chunks);
    }

    @Override
    public Flux<String> stream(String prompt) {
        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new GenerateRequest(model, prompt, true))
                .retrieve()
                .bodyToFlux(GenerateChunk.class)
                .map(chunk -> chunk.response() == null ? "" : chunk.response())
                .onErrorMap(e -> new UpstreamServiceException("Failed to generate response via Ollama", e));
    }

    private record GenerateRequest(String model, String prompt, boolean stream) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateChunk(String response, boolean done) {
    }
}

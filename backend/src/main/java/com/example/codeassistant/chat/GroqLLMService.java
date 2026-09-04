package com.example.codeassistant.chat;

import com.example.codeassistant.common.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Calls Groq's hosted inference API, which is OpenAI-compatible
 * (POST /chat/completions with "stream": true) and returns real Server-Sent
 * Events - so, like Ollama, this gives genuine token-level streaming, just
 * from much faster (LPU-backed) hardware than a local CPU can offer. Free
 * tier, rate-limited rather than token-metered - see console.groq.com.
 *
 * Active when app.ai.llm-provider=groq. Embeddings are not offered by Groq,
 * so EMBEDDING_PROVIDER stays on Ollama (or Hugging Face) independently of
 * this setting - see EmbeddingService implementations.
 */
@Service
@ConditionalOnProperty(name = "app.ai.llm-provider", havingValue = "groq")
public class GroqLLMService implements LLMService {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public GroqLLMService(WebClient.Builder webClientBuilder,
                           @Value("${app.ai.groq.api-key}") String apiKey,
                           @Value("${app.ai.groq.api-url}") String apiUrl,
                           @Value("${app.ai.llm-model}") String model,
                           ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY is required when LLM_PROVIDER=groq. Get a free key at https://console.groq.com/keys");
        }
        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String prompt) {
        List<String> chunks = stream(prompt).collectList().block();
        return chunks == null ? "" : String.join("", chunks);
    }

    @Override
    public Flux<String> stream(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "stream", true,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .mapNotNull(ServerSentEvent::data)
                .filter(data -> data != null && !"[DONE]".equals(data))
                .map(this::extractDeltaContent)
                .onErrorMap(e -> new UpstreamServiceException("Failed to generate response via Groq", e));
    }

    /** Pulls choices[0].delta.content out of one OpenAI-format streaming chunk. */
    private String extractDeltaContent(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() ? "" : content.asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
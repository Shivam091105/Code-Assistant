package com.example.codeassistant.chat;

import com.example.codeassistant.common.UpstreamServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Calls the Hugging Face Inference API for text generation. The free-tier
 * inference API does not support token streaming, so we generate the full
 * answer and then emit it as a Flux of word-sized chunks. This still drives
 * real SSE end-to-end (the frontend receives genuine incremental events);
 * only the LLM call itself is not natively streamed. Prefer Ollama for true
 * token-level streaming.
 */
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "huggingface")
public class HuggingFaceLLMService implements LLMService {

    private final WebClient webClient;
    private final String model;

    public HuggingFaceLLMService(WebClient.Builder webClientBuilder,
                                  @Value("${app.ai.huggingface.api-url}") String apiUrl,
                                  @Value("${app.ai.huggingface.api-key}") String apiKey,
                                  @Value("${app.ai.llm-model}") String model) {
        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    @Override
    public String generate(String prompt) {
        try {
            List<Response> result = webClient.post()
                    .uri("/models/{model}", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new Request(prompt, new Params(512, 0.2)))
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Response>>() {
                    })
                    .block();
            if (result == null || result.isEmpty()) {
                throw new UpstreamServiceException("Hugging Face returned an empty response");
            }
            return result.get(0).generated_text();
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to generate response via Hugging Face", e);
        }
    }

    @Override
    public Flux<String> stream(String prompt) {
        String fullText = generate(prompt);
        String[] words = fullText.split("(?<=\\s)"); // keep trailing whitespace attached to each token
        return Flux.fromArray(words);
    }

    private record Request(String inputs, Params parameters) {
    }

    private record Params(int max_new_tokens, double temperature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(String generated_text) {
    }
}

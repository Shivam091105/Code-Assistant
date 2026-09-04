package com.example.codeassistant.chat;

import reactor.core.publisher.Flux;

/**
 * Generates text from a prompt. Two shapes are exposed deliberately:
 * generate() for callers that just want the final string, and stream() for
 * the SSE chat endpoint that needs tokens as they arrive.
 */
public interface LLMService {

    String generate(String prompt);

    Flux<String> stream(String prompt);
}

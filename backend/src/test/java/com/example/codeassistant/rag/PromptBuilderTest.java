package com.example.codeassistant.rag;

import com.example.codeassistant.vector.VectorSearchService.SimilarChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void includesFilePathAndLineRangeForEachChunk() {
        List<SimilarChunk> chunks = List.of(
                new SimilarChunk(1L, "src/main/java/SecurityConfig.java", "public class SecurityConfig {}", 20, 65, 0.1)
        );

        String prompt = promptBuilder.build("Where is auth configured?", chunks, List.of());

        assertThat(prompt).contains("src/main/java/SecurityConfig.java");
        assertThat(prompt).contains("lines: 20-65");
        assertThat(prompt).contains("Where is auth configured?");
    }

    @Test
    void notesWhenNoContextWasFound() {
        String prompt = promptBuilder.build("What does this do?", List.of(), List.of());

        assertThat(prompt).contains("No relevant code was found");
    }

    @Test
    void includesRecentHistoryWhenPresent() {
        String prompt = promptBuilder.build("And then?", List.of(), List.of("USER: What is this repo?", "ASSISTANT: It's a RAG demo."));

        assertThat(prompt).contains("Recent Conversation");
        assertThat(prompt).contains("USER: What is this repo?");
    }

    @Test
    void instructsModelNotToInventBehavior() {
        String prompt = promptBuilder.build("Explain X", List.of(), List.of());

        assertThat(prompt).contains("Do not invent files, classes, methods, or behavior.");
    }
}

package com.example.codeassistant.rag;

import com.example.codeassistant.vector.VectorSearchService.SimilarChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the final prompt sent to the LLM from retrieved chunks + the
 * user's question (+ a little recent chat history). Kept as one small,
 * dedicated, testable class so the exact prompt text is easy to inspect
 * and tune without hunting through RagService.
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are an AI software engineering assistant.

            Answer the user's question using the supplied repository context.

            Do not invent files, classes, methods, or behavior.

            If the provided context is insufficient, clearly say so.

            When possible, mention the relevant file path and line range.

            Explain the answer clearly and concisely.
            """;

    public String build(String question, List<SimilarChunk> contextChunks, List<String> recentHistory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_INSTRUCTIONS).append("\n\n");

        prompt.append("Repository Context:\n\n");
        if (contextChunks.isEmpty()) {
            prompt.append("(No relevant code was found for this question.)\n\n");
        } else {
            for (SimilarChunk chunk : contextChunks) {
                prompt.append("[file: ").append(chunk.filePath()).append("]\n");
                prompt.append("lines: ").append(chunk.startLine()).append("-").append(chunk.endLine()).append("\n");
                prompt.append(chunk.content()).append("\n\n");
            }
        }

        if (!recentHistory.isEmpty()) {
            prompt.append("Recent Conversation:\n\n");
            for (String line : recentHistory) {
                prompt.append(line).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("User Question:\n\n").append(question).append("\n");
        return prompt.toString();
    }
}

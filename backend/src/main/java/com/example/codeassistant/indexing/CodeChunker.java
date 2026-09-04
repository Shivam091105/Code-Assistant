package com.example.codeassistant.indexing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits file content into overlapping, line-based chunks. No AST parsing -
 * just fixed-size windows over the lines, which is simple to reason about,
 * language-agnostic, and independently testable.
 */
@Component
public class CodeChunker {

    public record Chunk(String content, int startLine, int endLine) {
    }

    private final int chunkSizeLines;
    private final int overlapLines;

    public CodeChunker(@Value("${app.indexing.chunk-size-lines:100}") int chunkSizeLines,
                        @Value("${app.indexing.chunk-overlap-lines:20}") int overlapLines) {
        if (overlapLines >= chunkSizeLines) {
            throw new IllegalArgumentException("chunk-overlap-lines must be smaller than chunk-size-lines");
        }
        this.chunkSizeLines = chunkSizeLines;
        this.overlapLines = overlapLines;
    }

    public List<Chunk> chunk(String fileContent) {
        List<Chunk> chunks = new ArrayList<>();
        if (fileContent == null || fileContent.isEmpty()) {
            return chunks;
        }

        String[] lines = fileContent.split("\n", -1);
        int totalLines = lines.length;
        int step = chunkSizeLines - overlapLines;

        int start = 0;
        while (start < totalLines) {
            int end = Math.min(start + chunkSizeLines, totalLines);
            String content = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
            if (!content.isBlank()) {
                // stored as 1-indexed, inclusive line numbers, matching how editors display them
                chunks.add(new Chunk(content, start + 1, end));
            }
            if (end == totalLines) {
                break;
            }
            start += step;
        }
        return chunks;
    }
}

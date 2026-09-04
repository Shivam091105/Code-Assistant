package com.example.codeassistant.indexing;

import com.example.codeassistant.indexing.CodeChunker.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker(100, 20);

    @Test
    void returnsNoChunksForEmptyContent() {
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void producesOneChunkWhenFileIsShorterThanChunkSize() {
        String content = fileWithLines(50);
        List<Chunk> chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(0).endLine()).isEqualTo(50);
    }

    @Test
    void splitsLongFilesIntoOverlappingChunks() {
        String content = fileWithLines(260);
        List<Chunk> chunks = chunker.chunk(content);

        assertThat(chunks).extracting(Chunk::startLine).containsExactly(1, 81, 161);
        assertThat(chunks).extracting(Chunk::endLine).containsExactly(100, 180, 260);
    }

    @Test
    void lastChunkNeverExceedsFileLength() {
        String content = fileWithLines(105);
        List<Chunk> chunks = chunker.chunk(content);

        assertThat(chunks.get(chunks.size() - 1).endLine()).isEqualTo(105);
    }

    @Test
    void rejectsOverlapGreaterThanOrEqualToChunkSize() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CodeChunker(50, 50));
    }

    private String fileWithLines(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.joining("\n"));
    }
}

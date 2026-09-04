package com.example.codeassistant.embedding;

import java.util.List;

/**
 * Turns text into a fixed-length embedding vector. The same implementation
 * (and therefore the same vector space) must be used for both code chunks
 * at indexing time and questions at query time.
 */
public interface EmbeddingService {

    List<Float> embed(String text);

    /** Batched form - implementations may call the provider more efficiently than one-by-one. */
    List<List<Float>> embedBatch(List<String> texts);

    int dimension();
}

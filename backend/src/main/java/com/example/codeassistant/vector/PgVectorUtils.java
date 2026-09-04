package com.example.codeassistant.vector;

import java.util.List;
import java.util.stream.Collectors;

/** Converts between our List<Float> embeddings and pgvector's text literal format, e.g. "[0.1,0.2,0.3]". */
public final class PgVectorUtils {

    private PgVectorUtils() {
    }

    public static String toPgVectorLiteral(List<Float> vector) {
        return vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}

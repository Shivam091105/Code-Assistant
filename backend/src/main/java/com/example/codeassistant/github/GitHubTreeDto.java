package com.example.codeassistant.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Raw shape returned by GitHub's git trees API (recursive=1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubTreeDto(
        @JsonProperty("tree") List<Entry> tree,
        @JsonProperty("truncated") boolean truncated
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            @JsonProperty("path") String path,
            @JsonProperty("type") String type, // "blob" or "tree"
            @JsonProperty("size") Long size,
            @JsonProperty("sha") String sha
    ) {
    }
}

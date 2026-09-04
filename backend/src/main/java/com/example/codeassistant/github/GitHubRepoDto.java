package com.example.codeassistant.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw shape returned by GitHub's /user/repos and /repos/{owner}/{repo}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepoDto(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("description") String description,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("language") String language,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("owner") Owner owner
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(@JsonProperty("login") String login) {
    }
}

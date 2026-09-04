package com.example.codeassistant.repository;

/** A repository as listed straight from GitHub, before the user chooses to track/index it. */
public record GitHubRepoSummaryDto(
        Long githubRepositoryId,
        String name,
        String fullName,
        String owner,
        String description,
        String language,
        String defaultBranch,
        String url,
        boolean tracked,
        IndexingStatus indexingStatus
) {
}

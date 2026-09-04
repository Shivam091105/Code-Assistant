package com.example.codeassistant.repository;

import java.time.Instant;

public record RepositoryDto(
        Long id,
        String name,
        String fullName,
        String owner,
        String defaultBranch,
        String description,
        String language,
        String url,
        IndexingStatus indexingStatus,
        String indexingError,
        Instant updatedAt
) {
    public static RepositoryDto from(RepositoryEntity entity) {
        return new RepositoryDto(
                entity.getId(),
                entity.getName(),
                entity.getFullName(),
                entity.getOwner(),
                entity.getDefaultBranch(),
                entity.getDescription(),
                entity.getLanguage(),
                entity.getUrl(),
                entity.getIndexingStatus(),
                entity.getIndexingError(),
                entity.getUpdatedAt()
        );
    }
}

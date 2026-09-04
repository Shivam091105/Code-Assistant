package com.example.codeassistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, Long> {
    List<RepositoryEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<RepositoryEntity> findByIdAndUserId(Long id, Long userId);

    Optional<RepositoryEntity> findByUserIdAndGithubRepositoryId(Long userId, Long githubRepositoryId);
}

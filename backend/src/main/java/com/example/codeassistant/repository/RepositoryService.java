package com.example.codeassistant.repository;

import com.example.codeassistant.common.NotFoundException;
import com.example.codeassistant.github.GitHubRepoDto;
import com.example.codeassistant.github.GitHubService;
import com.example.codeassistant.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RepositoryService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final GitHubService gitHubService;

    public RepositoryService(RepositoryJpaRepository repositoryJpaRepository, GitHubService gitHubService) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.gitHubService = gitHubService;
    }

    /**
     * Lists the user's GitHub repositories, merged with our local tracking
     * state (so the dashboard can show "Not indexed" / "Indexing" / "Indexed"
     * without a second round trip).
     */
    public List<GitHubRepoSummaryDto> listForUser(Authentication authentication, User user) {
        List<GitHubRepoDto> githubRepos = gitHubService.listRepositories(authentication);

        Map<Long, RepositoryEntity> tracked = repositoryJpaRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream()
                .collect(Collectors.toMap(RepositoryEntity::getGithubRepositoryId, r -> r));

        return githubRepos.stream()
                .map(gh -> {
                    RepositoryEntity existing = tracked.get(gh.id());
                    return new GitHubRepoSummaryDto(
                            gh.id(),
                            gh.name(),
                            gh.fullName(),
                            gh.owner().login(),
                            gh.description(),
                            gh.language(),
                            gh.defaultBranch() == null ? "main" : gh.defaultBranch(),
                            gh.htmlUrl(),
                            existing != null,
                            existing != null ? existing.getIndexingStatus() : IndexingStatus.NOT_INDEXED
                    );
                })
                .toList();
    }

    @Transactional
    public RepositoryEntity findOwnedOrCreateFromGitHub(Authentication authentication, User user, Long githubRepositoryId) {
        return repositoryJpaRepository.findByUserIdAndGithubRepositoryId(user.getId(), githubRepositoryId)
                .orElseGet(() -> {
                    // Not tracked yet locally - look it up on GitHub to create the tracking row.
                    List<GitHubRepoDto> repos = gitHubService.listRepositories(authentication);
                    GitHubRepoDto match = repos.stream()
                            .filter(r -> r.id().equals(githubRepositoryId))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("Repository not found on GitHub for this user"));

                    RepositoryEntity entity = new RepositoryEntity(
                            user,
                            match.id(),
                            match.name(),
                            match.fullName(),
                            match.owner().login(),
                            match.defaultBranch() == null ? "main" : match.defaultBranch(),
                            match.description(),
                            match.language(),
                            match.htmlUrl()
                    );
                    return repositoryJpaRepository.save(entity);
                });
    }

    @Transactional(readOnly = true)
    public RepositoryEntity getOwned(Long repositoryId, User user) {
        return repositoryJpaRepository.findByIdAndUserId(repositoryId, user.getId())
                .orElseThrow(() -> ownershipError(repositoryId));
    }

    /**
     * Central authorization check used everywhere a repositoryId comes from
     * the client. Returns 404 rather than 403 for a repo owned by someone
     * else, so we don't leak which repository ids exist.
     */
    private NotFoundException ownershipError(Long repositoryId) {
        return new NotFoundException("Repository not found: " + repositoryId);
    }

    @Transactional
    public RepositoryDto toDto(RepositoryEntity entity) {
        return RepositoryDto.from(entity);
    }
}

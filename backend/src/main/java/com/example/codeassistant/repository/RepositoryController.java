package com.example.codeassistant.repository;

import com.example.codeassistant.github.GitHubService;
import com.example.codeassistant.indexing.IndexingService;
import com.example.codeassistant.security.CurrentUser;
import com.example.codeassistant.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final IndexingService indexingService;
    private final GitHubService gitHubService;
    private final CurrentUser currentUser;

    public RepositoryController(RepositoryService repositoryService, IndexingService indexingService,
                                 GitHubService gitHubService, CurrentUser currentUser) {
        this.repositoryService = repositoryService;
        this.indexingService = indexingService;
        this.gitHubService = gitHubService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<GitHubRepoSummaryDto> list(Authentication authentication) {
        User user = currentUser.resolve(authentication);
        return repositoryService.listForUser(authentication, user);
    }

    /**
     * repositoryId here is the GitHub repository id. If we haven't tracked
     * it locally yet, it gets created on first access - ownership is
     * established by the fact that it appeared in the caller's own GitHub
     * repo list.
     */
    @GetMapping("/{githubRepositoryId}")
    public RepositoryDto get(@PathVariable Long githubRepositoryId, Authentication authentication) {
        User user = currentUser.resolve(authentication);
        RepositoryEntity entity = repositoryService.findOwnedOrCreateFromGitHub(authentication, user, githubRepositoryId);
        return RepositoryDto.from(entity);
    }

    @PostMapping("/{githubRepositoryId}/index")
    public RepositoryDto index(@PathVariable Long githubRepositoryId, Authentication authentication) {
        User user = currentUser.resolve(authentication);
        RepositoryEntity entity = repositoryService.findOwnedOrCreateFromGitHub(authentication, user, githubRepositoryId);
        String accessToken = gitHubService.resolveAccessToken(authentication);
        indexingService.startIndexing(entity.getId(), accessToken);
        // re-fetch to reflect the just-applied INDEXING status
        RepositoryEntity refreshed = repositoryService.getOwned(entity.getId(), user);
        return RepositoryDto.from(refreshed);
    }

    @GetMapping("/{githubRepositoryId}/index-status")
    public Map<String, Object> indexStatus(@PathVariable Long githubRepositoryId, Authentication authentication) {
        User user = currentUser.resolve(authentication);
        RepositoryEntity entity = repositoryService.findOwnedOrCreateFromGitHub(authentication, user, githubRepositoryId);
        return Map.of(
                "status", entity.getIndexingStatus(),
                "error", entity.getIndexingError() == null ? "" : entity.getIndexingError()
        );
    }
}

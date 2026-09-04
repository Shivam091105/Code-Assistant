package com.example.codeassistant.github;

import com.example.codeassistant.common.UpstreamServiceException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Domain-facing GitHub operations. Resolves the caller's access token via
 * Spring's OAuth2AuthorizedClientService (never persisted by us, never sent
 * to the frontend) and delegates the actual HTTP work to GitHubClient.
 */
@Service
public class GitHubService {

    private final GitHubClient gitHubClient;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public GitHubService(GitHubClient gitHubClient, OAuth2AuthorizedClientService authorizedClientService) {
        this.gitHubClient = gitHubClient;
        this.authorizedClientService = authorizedClientService;
    }

    public List<GitHubRepoDto> listRepositories(Authentication authentication) {
        return gitHubClient.listAuthenticatedUserRepos(resolveAccessToken(authentication));
    }

    public GitHubRepoDto getRepository(String accessToken, String owner, String repo) {
        return gitHubClient.getRepo(accessToken, owner, repo);
    }

    public GitHubTreeDto getRepositoryTree(String accessToken, String owner, String repo, String branch) {
        return gitHubClient.getRepoTree(accessToken, owner, repo, branch);
    }

    public String getFileContent(String accessToken, String owner, String repo, String path, String branch) {
        return gitHubClient.getFileContent(accessToken, owner, repo, path, branch);
    }

    /**
     * Resolves the current user's GitHub access token so it can be captured
     * before handing off to an @Async background job (the security context
     * is not propagated to worker threads automatically).
     */
    public String resolveAccessToken(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new UpstreamServiceException("No GitHub OAuth2 session for current user");
        }
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(), token.getName());
        if (client == null || client.getAccessToken() == null) {
            throw new UpstreamServiceException("GitHub access token unavailable; please sign in again");
        }
        return client.getAccessToken().getTokenValue();
    }
}

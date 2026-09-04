package com.example.codeassistant.github;

import com.example.codeassistant.common.UpstreamServiceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Thin wrapper around GitHub's REST API. Knows nothing about our domain
 * model - only how to make authenticated HTTP calls and deserialize
 * responses. GitHubService builds on top of this.
 */
@Component
public class GitHubClient {

    private static final String BASE_URL = "https://api.github.com";

    private final WebClient webClient;

    public GitHubClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
    }

    public List<GitHubRepoDto> listAuthenticatedUserRepos(String accessToken) {
        try {
            return webClient.get()
                    .uri("/user/repos?per_page=100&sort=updated")
                    .headers(h -> authHeaders(h, accessToken))
                    .retrieve()
                    .bodyToFlux(GitHubRepoDto.class)
                    .collectList()
                    .block();
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to list GitHub repositories", e);
        }
    }

    public GitHubRepoDto getRepo(String accessToken, String owner, String repo) {
        try {
            return webClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .headers(h -> authHeaders(h, accessToken))
                    .retrieve()
                    .bodyToMono(GitHubRepoDto.class)
                    .block();
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to fetch repository " + owner + "/" + repo, e);
        }
    }

    public GitHubTreeDto getRepoTree(String accessToken, String owner, String repo, String branch) {
        try {
            return webClient.get()
                    .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch)
                    .headers(h -> authHeaders(h, accessToken))
                    .retrieve()
                    .bodyToMono(GitHubTreeDto.class)
                    .block();
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to fetch file tree for " + owner + "/" + repo, e);
        }
    }

    /**
     * Fetches raw file content via the GitHub "contents" API (base64-encoded)
     * and decodes it to a UTF-8 string. Returns null if the file cannot be
     * decoded as text (e.g. it is actually binary despite the extension).
     */
    public String getFileContent(String accessToken, String owner, String repo, String path, String branch) {
        try {
            RawContentResponse response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}", owner, repo, path, branch)
                    .headers(h -> authHeaders(h, accessToken))
                    .retrieve()
                    .bodyToMono(RawContentResponse.class)
                    .block();

            if (response == null || response.content() == null) {
                return null;
            }
            String cleaned = response.content().replace("\n", "");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // skip unreadable files rather than failing the whole indexing job
        }
    }

    private void authHeaders(HttpHeaders headers, String accessToken) {
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
    }

    private record RawContentResponse(String content, String encoding) {
    }
}

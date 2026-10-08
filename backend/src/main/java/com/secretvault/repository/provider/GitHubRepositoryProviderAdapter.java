package com.secretvault.repository.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.repository.engine.GitCloneUrlValidator;
import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.model.RepositoryVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-ready GitHub provider adapter supporting token validation,
 * repository discovery, metadata inspection, and secure authenticated clone URL generation.
 */
@Component
public class GitHubRepositoryProviderAdapter implements RepositoryProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryProviderAdapter.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GitCloneUrlValidator urlValidator;

    public GitHubRepositoryProviderAdapter(ObjectMapper objectMapper, GitCloneUrlValidator urlValidator) {
        this.objectMapper = objectMapper;
        this.urlValidator = urlValidator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public RepositoryProvider getProvider() {
        return RepositoryProvider.GITHUB;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean validateConnection(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + "/user"))
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-RepoSecurity/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("GitHub connection validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<RemoteRepositoryDto> listRepositories(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }

        List<RemoteRepositoryDto> results = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + "/user/repos?per_page=100&sort=updated"))
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-RepoSecurity/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode arrayNode = objectMapper.readTree(response.body());
                if (arrayNode.isArray()) {
                    for (JsonNode repoNode : arrayNode) {
                        String id = repoNode.path("id").asText();
                        String name = repoNode.path("name").asText();
                        String owner = repoNode.path("owner").path("login").asText();
                        String defaultBranch = repoNode.path("default_branch").asText("main");
                        String cloneUrl = repoNode.path("clone_url").asText();
                        boolean isPrivate = repoNode.path("private").asBoolean(true);
                        String description = repoNode.path("description").asText("");

                        results.add(new RemoteRepositoryDto(
                                id,
                                owner,
                                name,
                                defaultBranch,
                                cloneUrl,
                                isPrivate ? RepositoryVisibility.PRIVATE : RepositoryVisibility.PUBLIC,
                                description,
                                isPrivate
                        ));
                    }
                }
            } else {
                log.warn("Failed to list GitHub repositories: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error discovering repositories from GitHub: {}", e.getMessage());
        }

        return results;
    }

    @Override
    public RemoteRepositoryDto getRepository(String owner, String name, String token) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + "/repos/" + owner + "/" + name))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-RepoSecurity/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(10));

            if (token != null && !token.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                String id = node.path("id").asText();
                String repoName = node.path("name").asText();
                String repoOwner = node.path("owner").path("login").asText();
                String defaultBranch = node.path("default_branch").asText("main");
                String cloneUrl = node.path("clone_url").asText();
                boolean isPrivate = node.path("private").asBoolean(true);
                String description = node.path("description").asText("");

                return new RemoteRepositoryDto(
                        id,
                        repoOwner,
                        repoName,
                        defaultBranch,
                        cloneUrl,
                        isPrivate ? RepositoryVisibility.PRIVATE : RepositoryVisibility.PUBLIC,
                        description,
                        isPrivate
                );
            }
        } catch (Exception e) {
            log.warn("Could not fetch GitHub repository {}/{}: {}", owner, name, e.getMessage());
        }

        return null;
    }

    @Override
    public String buildAuthenticatedCloneUrl(String cloneUrl, String token) {
        urlValidator.validateCloneUrl(cloneUrl);

        if (token == null || token.isBlank()) {
            return cloneUrl;
        }

        // Convert https://github.com/owner/repo.git to https://x-access-token:<token>@github.com/owner/repo.git
        if (cloneUrl.startsWith("https://github.com/")) {
            return "https://x-access-token:" + token.trim() + "@github.com/" + cloneUrl.substring("https://github.com/".length());
        }

        return cloneUrl;
    }
}

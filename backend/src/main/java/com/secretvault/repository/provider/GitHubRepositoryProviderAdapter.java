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
        String effectiveToken = getEffectiveToken(token);
        if (effectiveToken == null || effectiveToken.isBlank()) {
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + "/user"))
                    .header("Authorization", "Bearer " + effectiveToken)
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
    public UserProfileDto getUserProfile(String token) {
        String effectiveToken = getEffectiveToken(token);
        if (effectiveToken == null || effectiveToken.isBlank()) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + "/user"))
                    .header("Authorization", "Bearer " + effectiveToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-RepoSecurity/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String login = root.path("login").asText("");
                String name = root.path("name").asText(login);
                String avatarUrl = root.path("avatar_url").asText("");
                String email = root.path("email").asText("");
                int publicRepos = root.path("public_repos").asInt(0);
                int totalPrivateRepos = root.path("total_private_repos").asInt(0);

                List<String> scopes = new ArrayList<>();
                response.headers().firstValue("X-OAuth-Scopes").ifPresent(header -> {
                    for (String s : header.split(",")) {
                        if (!s.isBlank()) scopes.add(s.trim());
                    }
                });

                return new UserProfileDto(login, name, avatarUrl, email, scopes, publicRepos, totalPrivateRepos);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve GitHub user profile: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public List<RemoteRepositoryDto> listRepositories(String token) {
        return listRepositories(token, null);
    }

    @Override
    public List<RemoteRepositoryDto> listRepositories(String token, String username) {
        String effectiveToken = getEffectiveToken(token);

        String url;
        String authUserLogin = null;
        if (effectiveToken != null && !effectiveToken.isBlank() && (username == null || username.isBlank())) {
            // Affiliation includes user-owned, collaborator, and organization repos
            url = GITHUB_API_BASE + "/user/repos?affiliation=owner,collaborator,organization_member&sort=updated&per_page=100";
            UserProfileDto profile = getUserProfile(effectiveToken);
            if (profile != null) {
                authUserLogin = profile.login();
            }
        } else if (username != null && !username.isBlank()) {
            String cleanUser = username.trim();
            if (cleanUser.contains("@")) {
                cleanUser = cleanUser.substring(0, cleanUser.indexOf("@"));
            }
            String encodedUser = java.net.URLEncoder.encode(cleanUser, java.nio.charset.StandardCharsets.UTF_8);
            url = GITHUB_API_BASE + "/users/" + encodedUser + "/repos?per_page=100&sort=updated";
        } else if (effectiveToken != null && !effectiveToken.isBlank()) {
            url = GITHUB_API_BASE + "/user/repos?affiliation=owner,collaborator,organization_member&sort=updated&per_page=100";
        } else {
            return List.of();
        }

        List<RemoteRepositoryDto> results = new ArrayList<>();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-RepoSecurity/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(15));

            if (effectiveToken != null && !effectiveToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + effectiveToken);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode arrayNode = objectMapper.readTree(response.body());
                if (arrayNode.isArray()) {
                    for (JsonNode repoNode : arrayNode) {
                        String id = repoNode.path("id").asText();
                        String name = repoNode.path("name").asText();
                        JsonNode ownerNode = repoNode.path("owner");
                        String owner = ownerNode.path("login").asText();
                        String ownerType = ownerNode.path("type").asText("User");
                        String defaultBranch = repoNode.path("default_branch").asText("main");
                        String cloneUrl = repoNode.path("clone_url").asText();
                        boolean isPrivate = repoNode.path("private").asBoolean(false);
                        String description = repoNode.path("description").asText("");

                        // Inspect GitHub fine-grained / OAuth permissions
                        JsonNode permissionsNode = repoNode.path("permissions");
                        boolean canRead = permissionsNode.path("pull").asBoolean(true);
                        boolean canAdminHooks = permissionsNode.path("admin").asBoolean(false);

                        // Determine contributor / affiliation role
                        String role = "COLLABORATOR";
                        if (authUserLogin != null && authUserLogin.equalsIgnoreCase(owner)) {
                            role = "OWNER";
                        } else if ("Organization".equalsIgnoreCase(ownerType)) {
                            role = "ORGANIZATION_MEMBER";
                        }

                        String permissionWarning = null;
                        if (!canAdminHooks) {
                            permissionWarning = "Continuous webhook configuration requires repository admin privileges; manual and scheduled scans supported.";
                        }

                        results.add(new RemoteRepositoryDto(
                                id,
                                owner,
                                name,
                                defaultBranch,
                                cloneUrl,
                                isPrivate ? RepositoryVisibility.PRIVATE : RepositoryVisibility.PUBLIC,
                                description,
                                isPrivate,
                                canRead,
                                canAdminHooks,
                                false,
                                null,
                                null,
                                permissionWarning,
                                role
                        ));
                    }
                }
            } else {
                log.warn("Failed to list GitHub repositories from {}: HTTP {}", url, response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error discovering repositories from GitHub URL {}: {}", url, e.getMessage());
        }

        return results;
    }

    @Override
    public RemoteRepositoryDto getRepository(String owner, String name, String token) {
        String effectiveToken = getEffectiveToken(token);
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + "/repos/" + owner + "/" + name))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-RepoSecurity/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(10));

            if (effectiveToken != null && !effectiveToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + effectiveToken);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                String id = node.path("id").asText();
                String repoName = node.path("name").asText();
                String repoOwner = node.path("owner").path("login").asText();
                String defaultBranch = node.path("default_branch").asText("main");
                String cloneUrl = node.path("clone_url").asText();
                boolean isPrivate = node.path("private").asBoolean(false);
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

        String effectiveToken = getEffectiveToken(token);
        if (effectiveToken == null || effectiveToken.isBlank()) {
            return cloneUrl;
        }

        // Convert https://github.com/owner/repo.git to https://x-access-token:<token>@github.com/owner/repo.git
        if (cloneUrl.startsWith("https://github.com/")) {
            return "https://x-access-token:" + effectiveToken + "@github.com/" + cloneUrl.substring("https://github.com/".length());
        }

        return cloneUrl;
    }

    private String getEffectiveToken(String token) {
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken.trim();
        }
        return System.getProperty("GITHUB_TOKEN");
    }
}

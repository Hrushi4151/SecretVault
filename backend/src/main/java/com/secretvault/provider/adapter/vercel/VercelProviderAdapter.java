package com.secretvault.provider.adapter.vercel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderDiscoveredEnvironment;
import com.secretvault.provider.model.ProviderDiscoveredResource;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderSecretMetadata;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.model.ProviderValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dedicated Vercel platform integration adapter.
 * Communicates with Vercel REST APIs for project discovery, environment mapping, and environment variable management.
 */
@Component
public class VercelProviderAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(VercelProviderAdapter.class);
    private static final Set<ProviderCapability> CAPABILITIES = Set.of(
            ProviderCapability.VALIDATE_CONNECTION,
            ProviderCapability.DISCOVER_PROJECTS,
            ProviderCapability.DISCOVER_ENVIRONMENTS,
            ProviderCapability.READ_SECRET_METADATA,
            ProviderCapability.WRITE_SECRETS,
            ProviderCapability.DELETE_SECRETS
    );

    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VercelProviderAdapter(
            @Value("${secretvault.provider.vercel.base-url:https://api.vercel.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this(baseUrl, objectMapper, createDefaultRestClient(baseUrl));
    }

    public VercelProviderAdapter(
            String baseUrl,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.restClient = Objects.requireNonNull(restClient, "RestClient must not be null");
    }

    private static RestClient createDefaultRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.VERCEL;
    }

    @Override
    public Set<ProviderCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProviderValidationResult validateConnection(Map<String, Object> configuration, String credential) {
        try {
            String teamId = extractTeamId(configuration);
            String uri = teamId != null ? "/v2/teams/" + teamId : "/v2/user";

            String responseBody = executeGet(uri, credential, teamId);
            JsonNode root = objectMapper.readTree(responseBody);

            if (teamId != null && root.has("id")) {
                String teamName = root.path("name").asText(root.path("slug").asText("Vercel Team"));
                return ProviderValidationResult.success(teamName, teamId, CAPABILITIES);
            } else if (root.has("user")) {
                JsonNode user = root.get("user");
                String username = user.path("username").asText(user.path("email").asText("Vercel User"));
                String userId = user.path("id").asText();
                return ProviderValidationResult.success(username, userId, CAPABILITIES);
            }

            return ProviderValidationResult.success("Vercel Account", "vercel-auth", CAPABILITIES);
        } catch (HttpClientErrorException.Unauthorized e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Invalid or expired Vercel API token");
        } catch (HttpClientErrorException.Forbidden e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_AUTHORIZATION_FAILED, "Access forbidden by Vercel permissions or team scope");
        } catch (HttpClientErrorException.TooManyRequests e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_RATE_LIMITED, "Vercel API rate limit exceeded");
        } catch (ResourceAccessException e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_TIMEOUT, "Connection to Vercel API timed out");
        } catch (Exception e) {
            log.warn("Vercel connection validation failed: {}", e.getMessage());
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_UNAVAILABLE, "Unable to reach Vercel API: " + e.getMessage());
        }
    }

    @Override
    public List<ProviderDiscoveredResource> discoverResources(Map<String, Object> configuration, String credential) {
        try {
            String teamId = extractTeamId(configuration);
            String uri = "/v9/projects?limit=100";
            if (teamId != null) {
                uri += "&teamId=" + teamId;
            }

            String responseBody = executeGet(uri, credential, teamId);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode projects = root.path("projects");

            List<ProviderDiscoveredResource> resources = new ArrayList<>();
            if (projects.isArray()) {
                for (JsonNode p : projects) {
                    String id = p.path("id").asText();
                    String name = p.path("name").asText();
                    String framework = p.path("framework").asText("unknown");
                    resources.add(new ProviderDiscoveredResource(
                            id,
                            name,
                            ProviderResourceType.PROJECT,
                            "ACTIVE",
                            "global",
                            Map.of("framework", framework, "accountId", p.path("accountId").asText(""))
                    ));
                }
            }
            return resources;
        } catch (Exception e) {
            log.warn("Vercel resource discovery failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<ProviderDiscoveredEnvironment> discoverEnvironments(Map<String, Object> configuration, String credential, String providerResourceId) {
        // Vercel standard environment targets
        return List.of(
                new ProviderDiscoveredEnvironment("production", "Production", "production", Map.of("description", "Vercel Production Deployment Environment")),
                new ProviderDiscoveredEnvironment("preview", "Preview", "preview", Map.of("description", "Vercel Preview / Pull Request Deployment Environment")),
                new ProviderDiscoveredEnvironment("development", "Development", "development", Map.of("description", "Vercel Local CLI Development Environment"))
        );
    }

    @Override
    public List<ProviderSecretMetadata> listSecrets(Map<String, Object> configuration, String credential, ProviderResourceMapping mapping) {
        try {
            String teamId = extractTeamId(configuration);
            String projectId = mapping.getProviderResourceId();
            String targetEnv = mapping.getProviderEnvironment().toLowerCase();

            String uri = "/v10/projects/" + projectId + "/env";
            if (teamId != null) {
                uri += "?teamId=" + teamId;
            }

            String responseBody = executeGet(uri, credential, teamId);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode envs = root.path("envs");

            List<ProviderSecretMetadata> results = new ArrayList<>();
            if (envs.isArray()) {
                for (JsonNode env : envs) {
                    String envId = env.path("id").asText();
                    String key = env.path("key").asText();
                    JsonNode targets = env.path("target");

                    boolean matchesTarget = false;
                    if (targets.isArray()) {
                        for (JsonNode t : targets) {
                            if (t.asText().equalsIgnoreCase(targetEnv)) {
                                matchesTarget = true;
                                break;
                            }
                        }
                    }

                    if (matchesTarget) {
                        long updatedAtEpoch = env.path("updatedAt").asLong(0);
                        Instant updatedAt = updatedAtEpoch > 0 ? Instant.ofEpochMilli(updatedAtEpoch) : Instant.now();
                        results.add(new ProviderSecretMetadata(key, targetEnv, updatedAt, envId));
                    }
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to list Vercel env vars for project [{}]: {}", mapping.getProviderResourceId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public ProviderSecretOperationResult pushSecret(
            Map<String, Object> configuration,
            String credential,
            ProviderResourceMapping mapping,
            String secretName,
            String secretValue
    ) {
        try {
            String teamId = extractTeamId(configuration);
            String projectId = mapping.getProviderResourceId();
            String targetEnv = mapping.getProviderEnvironment().toLowerCase();

            // 1. Check if variable already exists
            List<ProviderSecretMetadata> existing = listSecrets(configuration, credential, mapping);
            String existingEnvId = existing.stream()
                    .filter(m -> m.key().equalsIgnoreCase(secretName))
                    .map(ProviderSecretMetadata::providerSecretId)
                    .findFirst()
                    .orElse(null);

            if (existingEnvId != null) {
                // Update existing env var
                String updateUri = "/v10/projects/" + projectId + "/env/" + existingEnvId;
                if (teamId != null) {
                    updateUri += "?teamId=" + teamId;
                }

                Map<String, Object> updatePayload = Map.of(
                        "value", secretValue,
                        "type", "encrypted",
                        "target", List.of(targetEnv)
                );

                executeRequest(HttpMethod.PATCH, updateUri, credential, teamId, updatePayload);
                return ProviderSecretOperationResult.success("UPDATE", secretName, existingEnvId);
            } else {
                // Create new env var
                String createUri = "/v10/projects/" + projectId + "/env";
                if (teamId != null) {
                    createUri += "?teamId=" + teamId;
                }

                Map<String, Object> createPayload = Map.of(
                        "key", secretName,
                        "value", secretValue,
                        "type", "encrypted",
                        "target", List.of(targetEnv)
                );

                String responseBody = executeRequest(HttpMethod.POST, createUri, credential, teamId, createPayload);
                JsonNode root = objectMapper.readTree(responseBody);
                String newId = root.path("id").asText(root.path("created").path("id").asText(secretName));

                return ProviderSecretOperationResult.success("CREATE", secretName, newId);
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Vercel authorization token expired");
        } catch (HttpClientErrorException.Forbidden e) {
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_AUTHORIZATION_FAILED, "Insufficient permissions to write Vercel project environment variables");
        } catch (HttpClientErrorException.TooManyRequests e) {
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_RATE_LIMITED, "Vercel API rate limit exceeded");
        } catch (Exception e) {
            log.warn("Failed to push secret [{}] to Vercel: {}", secretName, e.getMessage());
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_UNAVAILABLE, "Vercel API error: " + e.getMessage());
        }
    }

    @Override
    public ProviderSecretOperationResult deleteSecret(
            Map<String, Object> configuration,
            String credential,
            ProviderResourceMapping mapping,
            String secretName
    ) {
        try {
            String teamId = extractTeamId(configuration);
            String projectId = mapping.getProviderResourceId();

            List<ProviderSecretMetadata> existing = listSecrets(configuration, credential, mapping);
            String existingEnvId = existing.stream()
                    .filter(m -> m.key().equalsIgnoreCase(secretName))
                    .map(ProviderSecretMetadata::providerSecretId)
                    .findFirst()
                    .orElse(null);

            if (existingEnvId == null) {
                return ProviderSecretOperationResult.success("DELETE", secretName, "not_found");
            }

            String deleteUri = "/v10/projects/" + projectId + "/env/" + existingEnvId;
            if (teamId != null) {
                deleteUri += "?teamId=" + teamId;
            }

            executeRequest(HttpMethod.DELETE, deleteUri, credential, teamId, null);
            return ProviderSecretOperationResult.success("DELETE", secretName, existingEnvId);
        } catch (Exception e) {
            log.warn("Failed to delete secret [{}] from Vercel: {}", secretName, e.getMessage());
            return ProviderSecretOperationResult.failure("DELETE", secretName, ProviderErrorCode.PROVIDER_UNAVAILABLE, "Vercel API error: " + e.getMessage());
        }
    }

    private String executeGet(String uri, String credential, String teamId) {
        return executeRequest(HttpMethod.GET, uri, credential, teamId, null);
    }

    private String executeRequest(HttpMethod method, String uri, String credential, String teamId, Object body) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .accept(MediaType.APPLICATION_JSON);

        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return spec.retrieve().body(String.class);
    }

    private String extractTeamId(Map<String, Object> configuration) {
        if (configuration == null) return null;
        Object teamId = configuration.get("teamId");
        if (teamId instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }
}

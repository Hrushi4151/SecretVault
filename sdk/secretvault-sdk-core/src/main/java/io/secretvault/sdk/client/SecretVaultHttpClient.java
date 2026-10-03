package io.secretvault.sdk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.secretvault.sdk.auth.CredentialsProvider;
import io.secretvault.sdk.exception.AuthenticationException;
import io.secretvault.sdk.exception.AuthorizationException;
import io.secretvault.sdk.exception.ErrorCode;
import io.secretvault.sdk.exception.RateLimitException;
import io.secretvault.sdk.exception.SecretNotFoundException;
import io.secretvault.sdk.exception.SecretVaultException;
import io.secretvault.sdk.exception.SecretVaultUnavailableException;
import io.secretvault.sdk.exception.SecretVersionNotFoundException;
import io.secretvault.sdk.exception.TimeoutException;
import io.secretvault.sdk.model.SecretMetadata;
import io.secretvault.sdk.model.SecretStatus;
import io.secretvault.sdk.model.SecretValue;
import io.secretvault.sdk.observability.RedactionUtil;
import io.secretvault.sdk.observability.SdkMetrics;
import io.secretvault.sdk.resilience.CircuitBreaker;
import io.secretvault.sdk.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Robust HTTP client implementing resilient communication with the SecretVault backend API.
 */
public class SecretVaultHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SecretVaultHttpClient.class);

    private final SdkConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;
    private final SdkMetrics metrics;

    // Cache for resolved slug-to-UUID mappings
    private final Map<String, UUID> idCache = new ConcurrentHashMap<>();

    public SecretVaultHttpClient(SdkConfig config, CircuitBreaker circuitBreaker, SdkMetrics metrics) {
        this(config, circuitBreaker, metrics, HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public SecretVaultHttpClient(SdkConfig config, CircuitBreaker circuitBreaker, SdkMetrics metrics, HttpClient httpClient) {
        this.config = config;
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new CircuitBreaker();
        this.retryPolicy = new RetryPolicy(config.getRetryAttempts(), Duration.ofMillis(100), Duration.ofSeconds(2), 2.0);
        this.metrics = metrics;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public SecretValue revealSecret(UUID workspaceId, UUID projectId, UUID envId, UUID secretId, String secretName, Integer version) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s/reveal%s",
                workspaceId, projectId, envId, secretId,
                version != null ? "?version=" + version : "");

        JsonNode data = executeWithRetry("POST", path, null, workspaceId, true);

        String name = data.path("name").asText(secretName);
        String value = data.path("value").asText();
        int versionNum = data.path("versionNumber").asInt(version != null ? version : 1);
        String envSlug = data.path("environmentSlug").asText("default");

        return new SecretValue(name, value, versionNum, envSlug, Instant.now(), Instant.now(), null, Collections.emptyMap());
    }

    public List<SecretMetadata> listSecrets(UUID workspaceId, UUID projectId, UUID envId, String search, SecretStatus status) {
        StringBuilder path = new StringBuilder(String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets",
                workspaceId, projectId, envId));
        List<String> queryParams = new ArrayList<>();
        if (search != null && !search.isEmpty()) queryParams.add("search=" + search);
        if (status != null) queryParams.add("status=" + status.name());
        if (!queryParams.isEmpty()) path.append("?").append(String.join("&", queryParams));

        JsonNode data = executeWithRetry("GET", path.toString(), null, workspaceId, false);

        List<SecretMetadata> list = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                list.add(new SecretMetadata(
                        UUID.fromString(node.path("id").asText()),
                        node.path("name").asText(),
                        node.path("currentVersion").asInt(1),
                        SecretStatus.valueOf(node.path("status").asText(SecretStatus.ACTIVE.name())),
                        node.path("description").asText(null),
                        UUID.fromString(node.path("environmentId").asText(envId.toString())),
                        Instant.now(),
                        Instant.now(),
                        null
                ));
            }
        }
        return list;
    }

    public SecretMetadata getSecretMetadata(UUID workspaceId, UUID projectId, UUID envId, UUID secretId) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s",
                workspaceId, projectId, envId, secretId);

        JsonNode node = executeWithRetry("GET", path, null, workspaceId, false);
        return new SecretMetadata(
                UUID.fromString(node.path("id").asText(secretId.toString())),
                node.path("name").asText(),
                node.path("currentVersion").asInt(1),
                SecretStatus.valueOf(node.path("status").asText(SecretStatus.ACTIVE.name())),
                node.path("description").asText(null),
                UUID.fromString(node.path("environmentId").asText(envId.toString())),
                Instant.now(),
                Instant.now(),
                null
        );
    }

    public boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(config.getEndpoint().resolve("/api/v1/auth/whoami"))
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", getUserAgent())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    public UUID resolveWorkspaceId(String slugOrId) {
        if (isUuid(slugOrId)) return UUID.fromString(slugOrId);
        return idCache.computeIfAbsent("ws::" + slugOrId, k -> {
            JsonNode data = executeWithRetry("GET", "/api/v1/workspaces", null, null, false);
            if (data.isArray()) {
                for (JsonNode ws : data) {
                    if (slugOrId.equalsIgnoreCase(ws.path("slug").asText()) ||
                            slugOrId.equalsIgnoreCase(ws.path("name").asText()) ||
                            slugOrId.equalsIgnoreCase(ws.path("id").asText())) {
                        return UUID.fromString(ws.path("id").asText());
                    }
                }
            }
            throw new SecretNotFoundException("Workspace '" + slugOrId + "' not found");
        });
    }

    public UUID resolveProjectId(UUID workspaceId, String slugOrId) {
        if (isUuid(slugOrId)) return UUID.fromString(slugOrId);
        return idCache.computeIfAbsent("proj::" + workspaceId + "::" + slugOrId, k -> {
            JsonNode data = executeWithRetry("GET", "/api/v1/workspaces/" + workspaceId + "/projects", null, workspaceId, false);
            if (data.isArray()) {
                for (JsonNode p : data) {
                    if (slugOrId.equalsIgnoreCase(p.path("slug").asText()) ||
                            slugOrId.equalsIgnoreCase(p.path("name").asText()) ||
                            slugOrId.equalsIgnoreCase(p.path("id").asText())) {
                        return UUID.fromString(p.path("id").asText());
                    }
                }
            }
            throw new SecretNotFoundException("Project '" + slugOrId + "' not found in workspace");
        });
    }

    public UUID resolveEnvironmentId(UUID workspaceId, UUID projectId, String slugOrId) {
        if (isUuid(slugOrId)) return UUID.fromString(slugOrId);
        return idCache.computeIfAbsent("env::" + workspaceId + "::" + projectId + "::" + slugOrId, k -> {
            JsonNode data = executeWithRetry("GET", "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments", null, workspaceId, false);
            if (data.isArray()) {
                for (JsonNode env : data) {
                    if (slugOrId.equalsIgnoreCase(env.path("slug").asText()) ||
                            slugOrId.equalsIgnoreCase(env.path("name").asText()) ||
                            slugOrId.equalsIgnoreCase(env.path("id").asText()) ||
                            slugOrId.equalsIgnoreCase(env.path("environmentType").asText())) {
                        return UUID.fromString(env.path("id").asText());
                    }
                }
            }
            throw new SecretNotFoundException("Environment '" + slugOrId + "' not found in project");
        });
    }

    public UUID resolveSecretId(UUID workspaceId, UUID projectId, UUID envId, String nameOrId) {
        if (isUuid(nameOrId)) return UUID.fromString(nameOrId);
        List<SecretMetadata> list = listSecrets(workspaceId, projectId, envId, nameOrId, null);
        for (SecretMetadata m : list) {
            if (nameOrId.equalsIgnoreCase(m.name()) || nameOrId.equalsIgnoreCase(m.id().toString())) {
                return m.id();
            }
        }
        throw new SecretNotFoundException(nameOrId);
    }

    private JsonNode executeWithRetry(String method, String path, String bodyJson, UUID workspaceId, boolean isReveal) {
        if (config.isCircuitBreakerEnabled()) {
            circuitBreaker.checkPermission();
        }

        int maxAttempts = isReveal ? 2 : retryPolicy.getMaxAttempts();
        CredentialsProvider creds = config.getCredentialsProvider();
        boolean refreshedAuth = false;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startTime = System.currentTimeMillis();
            String requestId = UUID.randomUUID().toString();
            try {
                String token = creds.getBearerToken();
                URI uri = config.getEndpoint().resolve(path);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(config.getReadTimeout())
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .header("User-Agent", getUserAgent())
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", requestId);

                if (workspaceId != null) {
                    reqBuilder.header("X-Workspace-ID", workspaceId.toString());
                }

                if ("POST".equalsIgnoreCase(method)) {
                    reqBuilder.header("Content-Type", "application/json");
                    reqBuilder.POST(bodyJson != null ? HttpRequest.BodyPublishers.ofString(bodyJson) : HttpRequest.BodyPublishers.noBody());
                } else if ("GET".equalsIgnoreCase(method)) {
                    reqBuilder.GET();
                }

                HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                long duration = System.currentTimeMillis() - startTime;
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    circuitBreaker.recordSuccess();
                    metrics.recordRequest(method + " " + path, true, duration);
                    JsonNode root = objectMapper.readTree(response.body());
                    return root.path("data");
                }

                // Handle 401 with one-time re-authentication
                if (status == 401 && !refreshedAuth && creds.supportsRefresh()) {
                    log.warn("Received 401 Unauthorized. Attempting on-demand token refresh.");
                    refreshedAuth = true;
                    creds.refresh();
                    metrics.recordAuthRefresh(true);
                    continue;
                }

                // Rate limiting (429)
                if (status == 429) {
                    Duration retryAfter = parseRetryAfter(response.headers().firstValue("Retry-After").orElse(null));
                    if (attempt < maxAttempts) {
                        Thread.sleep(retryPolicy.computeBackoff(attempt, retryAfter).toMillis());
                        continue;
                    }
                    circuitBreaker.recordFailure();
                    metrics.recordRequest(method + " " + path, false, duration);
                    throw new RateLimitException("Rate limit exceeded from SecretVault API", retryAfter, requestId);
                }

                // 403 Forbidden
                if (status == 403) {
                    metrics.recordRequest(method + " " + path, false, duration);
                    throw new AuthorizationException("Access denied by SecretVault authorization policy", requestId);
                }

                // 404 Not Found
                if (status == 404) {
                    metrics.recordRequest(method + " " + path, false, duration);
                    throw new SecretNotFoundException(path, requestId);
                }

                // 401 Invalid Auth
                if (status == 401) {
                    metrics.recordRequest(method + " " + path, false, duration);
                    throw new AuthenticationException("Authentication token invalid or revoked", ErrorCode.SV_AUTH_INVALID, 401, requestId);
                }

                // 5xx Server Errors -> retryable
                if (status >= 500 && attempt < maxAttempts) {
                    log.warn("Server error HTTP {} from {}. Retrying attempt {}/{}", status, path, attempt, maxAttempts);
                    Thread.sleep(retryPolicy.computeBackoff(attempt, null).toMillis());
                    continue;
                }

                circuitBreaker.recordFailure();
                metrics.recordRequest(method + " " + path, false, duration);
                throw new SecretVaultException("SecretVault API returned HTTP " + status, ErrorCode.SV_INTERNAL_ERROR, status, requestId);

            } catch (SecretVaultException e) {
                throw e;
            } catch (HttpTimeoutException e) {
                circuitBreaker.recordFailure();
                metrics.recordRequest(method + " " + path, false, System.currentTimeMillis() - startTime);
                if (attempt < maxAttempts) {
                    try { Thread.sleep(retryPolicy.computeBackoff(attempt, null).toMillis()); } catch (InterruptedException ignored) {}
                    continue;
                }
                throw new TimeoutException("Network timeout contacting SecretVault endpoint", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SecretVaultException("Request was interrupted", ErrorCode.SV_INTERNAL_ERROR, e);
            } catch (IOException e) {
                circuitBreaker.recordFailure();
                metrics.recordRequest(method + " " + path, false, System.currentTimeMillis() - startTime);
                if (attempt < maxAttempts) {
                    try { Thread.sleep(retryPolicy.computeBackoff(attempt, null).toMillis()); } catch (InterruptedException ignored) {}
                    continue;
                }
                throw new SecretVaultUnavailableException("Failed to connect to SecretVault at " + config.getEndpoint(), e);
            }
        }

        throw new SecretVaultUnavailableException("SecretVault unavailable after " + maxAttempts + " attempts");
    }

    private String getUserAgent() {
        return "SecretVault-Java-SDK/1.0.0 (Java 21; app=" + config.getApplicationName() + "/" + config.getApplicationVersion() + ")";
    }

    private Duration parseRetryAfter(String headerVal) {
        if (headerVal == null || headerVal.trim().isEmpty()) return Duration.ofSeconds(1);
        try {
            long seconds = Long.parseLong(headerVal.trim());
            return Duration.ofSeconds(Math.max(1, seconds));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(1);
        }
    }

    private boolean isUuid(String str) {
        if (str == null || str.length() != 36) return false;
        try {
            UUID.fromString(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

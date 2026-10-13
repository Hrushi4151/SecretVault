package io.secretvault.sdk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.secretvault.sdk.model.PromotionPreviewResult;
import io.secretvault.sdk.model.PromotionResult;
import io.secretvault.sdk.model.SecretMetadata;
import io.secretvault.sdk.model.SecretStatus;
import io.secretvault.sdk.model.SecretValue;
import io.secretvault.sdk.model.SecretVersion;
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

    public SecretMetadata createSecret(UUID workspaceId, UUID projectId, UUID envId, String name, String value, String description) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets",
                workspaceId, projectId, envId);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.put("value", value);
        if (description != null) {
            body.put("description", description);
        }
        JsonNode node = executeWithRetry("POST", path, body.toString(), workspaceId, false);
        return parseSecretMetadata(node, envId);
    }

    public SecretMetadata updateSecret(UUID workspaceId, UUID projectId, UUID envId, UUID secretId, String value, String description, String reason) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s",
                workspaceId, projectId, envId, secretId);
        ObjectNode body = objectMapper.createObjectNode();
        if (value != null) {
            body.put("value", value);
        }
        if (description != null) {
            body.put("description", description);
        }
        if (reason != null) {
            body.put("reason", reason);
        }
        JsonNode node = executeWithRetry("PATCH", path, body.toString(), workspaceId, false);
        return parseSecretMetadata(node, envId);
    }

    public void deleteSecret(UUID workspaceId, UUID projectId, UUID envId, UUID secretId) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s",
                workspaceId, projectId, envId, secretId);
        executeWithRetry("DELETE", path, null, workspaceId, false);
    }

    public List<SecretVersion> listVersions(UUID workspaceId, UUID projectId, UUID envId, UUID secretId) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s/versions",
                workspaceId, projectId, envId, secretId);
        JsonNode data = executeWithRetry("GET", path, null, workspaceId, false);
        List<SecretVersion> versions = new ArrayList<>();
        JsonNode array = data.has("content") ? data.path("content") : data;
        if (array.isArray()) {
            for (JsonNode n : array) {
                versions.add(parseSecretVersion(n));
            }
        }
        return versions;
    }

    public SecretVersion rollbackSecret(UUID workspaceId, UUID projectId, UUID envId, UUID secretId, int targetVersion, String reason) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s/rollback",
                workspaceId, projectId, envId, secretId);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("targetVersion", targetVersion);
        if (reason != null) {
            body.put("reason", reason);
        }
        JsonNode node = executeWithRetry("POST", path, body.toString(), workspaceId, false);
        return parseSecretVersion(node);
    }

    public List<String> listTags(UUID workspaceId, UUID projectId, UUID envId, UUID secretId, int versionNumber) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s/versions/%d/tags",
                workspaceId, projectId, envId, secretId, versionNumber);
        JsonNode data = executeWithRetry("GET", path, null, workspaceId, false);
        List<String> tags = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode n : data) {
                tags.add(n.path("name").asText());
            }
        }
        return tags;
    }

    public void addTag(UUID workspaceId, UUID projectId, UUID envId, UUID secretId, int versionNumber, String tag) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s/versions/%d/tags",
                workspaceId, projectId, envId, secretId, versionNumber);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", tag);
        executeWithRetry("POST", path, body.toString(), workspaceId, false);
    }

    public void removeTag(UUID workspaceId, UUID projectId, UUID envId, UUID secretId, int versionNumber, String tag) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s/versions/%d/tags/%s",
                workspaceId, projectId, envId, secretId, versionNumber, tag);
        executeWithRetry("DELETE", path, null, workspaceId, false);
    }

    public PromotionPreviewResult previewPromotion(UUID workspaceId, UUID projectId, UUID sourceEnvId, UUID targetEnvId, List<String> secretNames) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/promote/preview",
                workspaceId, projectId, sourceEnvId);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("destinationEnvironmentId", targetEnvId.toString());
        if (secretNames != null && !secretNames.isEmpty()) {
            ArrayNode arr = body.putArray("secretNames");
            for (String s : secretNames) arr.add(s);
        }
        JsonNode node = executeWithRetry("POST", path, body.toString(), workspaceId, false);
        List<PromotionPreviewResult.CandidateItem> items = new ArrayList<>();
        if (node.path("items").isArray()) {
            for (JsonNode itemNode : node.path("items")) {
                items.add(new PromotionPreviewResult.CandidateItem(
                        itemNode.path("secretName").asText(),
                        itemNode.path("status").asText(),
                        itemNode.hasNonNull("sourceVersion") ? itemNode.path("sourceVersion").asInt() : null,
                        itemNode.hasNonNull("destinationVersion") ? itemNode.path("destinationVersion").asInt() : null,
                        itemNode.path("message").asText(null)
                ));
            }
        }
        return new PromotionPreviewResult(
                UUID.fromString(node.path("sourceEnvironmentId").asText(sourceEnvId.toString())),
                node.path("sourceEnvironmentName").asText(null),
                UUID.fromString(node.path("destinationEnvironmentId").asText(targetEnvId.toString())),
                node.path("destinationEnvironmentName").asText(null),
                node.path("isDestinationProtected").asBoolean(false),
                node.path("totalCandidates").asInt(),
                node.path("addedCount").asInt(),
                node.path("modifiedCount").asInt(),
                node.path("unchangedCount").asInt(),
                node.path("conflictCount").asInt(),
                items
        );
    }

    public PromotionResult executePromotion(UUID workspaceId, UUID projectId, UUID sourceEnvId, UUID targetEnvId, List<String> secretNames, String reason) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/promote",
                workspaceId, projectId, sourceEnvId);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("destinationEnvironmentId", targetEnvId.toString());
        if (secretNames != null && !secretNames.isEmpty()) {
            ArrayNode arr = body.putArray("secretNames");
            for (String s : secretNames) arr.add(s);
        }
        if (reason != null) {
            body.put("reason", reason);
        }
        JsonNode node = executeWithRetry("POST", path, body.toString(), workspaceId, false);
        List<PromotionResult.PromotedItem> items = new ArrayList<>();
        if (node.path("promotedSecrets").isArray()) {
            for (JsonNode itemNode : node.path("promotedSecrets")) {
                items.add(new PromotionResult.PromotedItem(
                        itemNode.path("secretName").asText(),
                        itemNode.hasNonNull("destinationSecretId") ? UUID.fromString(itemNode.path("destinationSecretId").asText()) : null,
                        itemNode.hasNonNull("newVersionNumber") ? itemNode.path("newVersionNumber").asInt() : null,
                        itemNode.path("action").asText(),
                        itemNode.path("status").asText()
                ));
            }
        }
        return new PromotionResult(
                UUID.fromString(node.path("sourceEnvironmentId").asText(sourceEnvId.toString())),
                UUID.fromString(node.path("destinationEnvironmentId").asText(targetEnvId.toString())),
                node.path("totalRequested").asInt(),
                node.path("promotedCount").asInt(),
                node.path("skippedUnchangedCount").asInt(),
                node.path("failedCount").asInt(),
                items,
                parseInstant(node.path("completedAt"))
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

    public JsonNode executeApi(String method, String path, String bodyJson, UUID workspaceId) {
        return executeWithRetry(method, path, bodyJson, workspaceId, false);
    }

    private JsonNode executeWithRetry(String method, String path, String bodyJson, UUID workspaceId, boolean isReveal) {
        if (config.isCircuitBreakerEnabled()) {
            circuitBreaker.checkPermission();
        }

        boolean isIdempotent = "GET".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
        int maxAttempts = isReveal ? 2 : (isIdempotent ? retryPolicy.getMaxAttempts() : 1);
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
                } else if ("PUT".equalsIgnoreCase(method)) {
                    reqBuilder.header("Content-Type", "application/json");
                    reqBuilder.PUT(bodyJson != null ? HttpRequest.BodyPublishers.ofString(bodyJson) : HttpRequest.BodyPublishers.noBody());
                } else if ("PATCH".equalsIgnoreCase(method)) {
                    reqBuilder.header("Content-Type", "application/json");
                    reqBuilder.method("PATCH", bodyJson != null ? HttpRequest.BodyPublishers.ofString(bodyJson) : HttpRequest.BodyPublishers.noBody());
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    reqBuilder.DELETE();
                } else if ("GET".equalsIgnoreCase(method)) {
                    reqBuilder.GET();
                } else {
                    reqBuilder.method(method.toUpperCase(), bodyJson != null ? HttpRequest.BodyPublishers.ofString(bodyJson) : HttpRequest.BodyPublishers.noBody());
                }

                HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                long duration = System.currentTimeMillis() - startTime;
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    circuitBreaker.recordSuccess();
                    metrics.recordRequest(method + " " + path, true, duration);
                    if (response.body() == null || response.body().trim().isEmpty()) {
                        return objectMapper.createObjectNode();
                    }
                    JsonNode root = objectMapper.readTree(response.body());
                    return root.has("data") ? root.path("data") : root;
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

                // 400 Bad Request / Validation failure
                if (status == 400) {
                    metrics.recordRequest(method + " " + path, false, duration);
                    String errorMsg = extractErrorMessage(response.body(), "Invalid request parameters or payload validation failure");
                    throw new SecretVaultException(errorMsg, ErrorCode.SV_INVALID_REQUEST, 400, requestId);
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

                // 409 Conflict
                if (status == 409) {
                    metrics.recordRequest(method + " " + path, false, duration);
                    String errorMsg = extractErrorMessage(response.body(), "Resource conflict or concurrent modification");
                    throw new SecretVaultException(errorMsg, ErrorCode.SV_CONFLICT, 409, requestId);
                }

                // 401 Invalid Auth
                if (status == 401) {
                    metrics.recordRequest(method + " " + path, false, duration);
                    throw new AuthenticationException("Authentication token invalid or revoked", ErrorCode.SV_AUTH_INVALID, 401, requestId);
                }

                // 5xx Server Errors -> retryable ONLY if idempotent
                if (status >= 500 && attempt < maxAttempts && isIdempotent) {
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
                if (attempt < maxAttempts && isIdempotent) {
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
                if (attempt < maxAttempts && isIdempotent) {
                    try { Thread.sleep(retryPolicy.computeBackoff(attempt, null).toMillis()); } catch (InterruptedException ignored) {}
                    continue;
                }
                throw new SecretVaultUnavailableException("Failed to connect to SecretVault at " + config.getEndpoint(), e);
            }
        }

        throw new SecretVaultUnavailableException("SecretVault unavailable after " + maxAttempts + " attempts");
    }

    private SecretMetadata parseSecretMetadata(JsonNode node, UUID envId) {
        return new SecretMetadata(
                UUID.fromString(node.path("id").asText()),
                node.path("name").asText(),
                node.path("currentVersion").asInt(1),
                SecretStatus.valueOf(node.path("status").asText(SecretStatus.ACTIVE.name())),
                node.hasNonNull("description") ? node.path("description").asText() : null,
                UUID.fromString(node.path("environmentId").asText(envId != null ? envId.toString() : UUID.randomUUID().toString())),
                parseInstant(node.path("createdAt")),
                parseInstant(node.path("updatedAt")),
                parseInstant(node.path("expiresAt"))
        );
    }

    private SecretVersion parseSecretVersion(JsonNode node) {
        List<String> tags = new ArrayList<>();
        if (node.path("tags").isArray()) {
            for (JsonNode t : node.path("tags")) {
                tags.add(t.asText());
            }
        }
        return new SecretVersion(
                UUID.fromString(node.path("id").asText()),
                UUID.fromString(node.path("secretId").asText()),
                node.path("versionNumber").asInt(1),
                SecretStatus.ACTIVE,
                parseInstant(node.path("createdAt")),
                parseInstant(node.path("createdAt")),
                null,
                node.hasNonNull("reason") ? node.path("reason").asText() : null,
                tags
        );
    }

    private Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isEmpty()) return null;
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String extractErrorMessage(String responseBody, String defaultMsg) {
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("message") && !root.get("message").asText().isEmpty()) {
                    return root.get("message").asText();
                }
                if (root.has("error") && !root.get("error").asText().isEmpty()) {
                    return root.get("error").asText();
                }
            } catch (Exception ignored) {}
        }
        return defaultMsg;
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

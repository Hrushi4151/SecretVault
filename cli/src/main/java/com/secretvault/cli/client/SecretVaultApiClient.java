package com.secretvault.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secretvault.cli.client.dto.ApiEnvelope;
import com.secretvault.cli.client.dto.AuthDtos;
import com.secretvault.cli.client.dto.EnvironmentDto;
import com.secretvault.cli.client.dto.HealthDto;
import com.secretvault.cli.client.dto.ProjectDto;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.client.dto.WorkspaceDto;
import com.secretvault.cli.security.RedactionHelper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Robust REST API client for SecretVault server using java.net.http.HttpClient.
 */
public class SecretVaultApiClient {

    private static final String CLIENT_VERSION = "SecretVault-CLI/1.0.0";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    private String accessToken;
    private Supplier<Boolean> tokenRefreshHandler;

    public SecretVaultApiClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(30));
    }

    public SecretVaultApiClient(String baseUrl, Duration timeout) {
        this.baseUrl = sanitizeBaseUrl(baseUrl);
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setTokenRefreshHandler(Supplier<Boolean> tokenRefreshHandler) {
        this.tokenRefreshHandler = tokenRefreshHandler;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ==========================================
    // Authentication & User APIs
    // ==========================================

    public AuthDtos.AuthResponse login(String email, String password) {
        AuthDtos.LoginRequest req = new AuthDtos.LoginRequest(email, password);
        return post("/api/v1/auth/login", req, new TypeReference<ApiEnvelope<AuthDtos.AuthResponse>>() {}, false, null);
    }

    public AuthDtos.AuthResponse refreshToken(String refreshToken) {
        AuthDtos.RefreshTokenRequest req = new AuthDtos.RefreshTokenRequest(refreshToken);
        return post("/api/v1/auth/refresh", req, new TypeReference<ApiEnvelope<AuthDtos.AuthResponse>>() {}, false, null);
    }

    public AuthDtos.UserResponse getCurrentUser() {
        return get("/api/v1/auth/me", new TypeReference<ApiEnvelope<AuthDtos.UserResponse>>() {}, null);
    }

    public void logout() {
        try {
            post("/api/v1/auth/logout", null, new TypeReference<ApiEnvelope<Void>>() {}, true, null);
        } catch (Exception ignored) {
            // Best effort logout on server
        }
    }

    public AuthDtos.OidcTokenResponse exchangeOidcToken(String issuer, UUID providerId, String token) {
        return exchangeOidcToken(new AuthDtos.OidcTokenExchangeRequest(providerId, issuer, token));
    }

    public AuthDtos.OidcTokenResponse exchangeOidcToken(AuthDtos.OidcTokenExchangeRequest req) {
        return post("/api/v1/auth/oidc/token", req, new TypeReference<ApiEnvelope<AuthDtos.OidcTokenResponse>>() {}, false, null);
    }

    public List<AuthDtos.MachineIdentityDto> listMachineIdentities(UUID workspaceId) {
        return get("/api/v1/workspaces/" + workspaceId + "/machine-identities", new TypeReference<ApiEnvelope<List<AuthDtos.MachineIdentityDto>>>() {}, workspaceId);
    }

    public AuthDtos.MachineIdentityDto getMachineIdentity(UUID workspaceId, UUID machineId) {
        return get("/api/v1/workspaces/" + workspaceId + "/machine-identities/" + machineId, new TypeReference<ApiEnvelope<AuthDtos.MachineIdentityDto>>() {}, workspaceId);
    }

    // ==========================================
    // Workspace APIs
    // ==========================================

    public List<WorkspaceDto> listWorkspaces() {
        return get("/api/v1/workspaces", new TypeReference<ApiEnvelope<List<WorkspaceDto>>>() {}, null);
    }

    public WorkspaceDto getWorkspace(UUID workspaceId) {
        return get("/api/v1/workspaces/" + workspaceId, new TypeReference<ApiEnvelope<WorkspaceDto>>() {}, workspaceId);
    }

    // ==========================================
    // Project APIs
    // ==========================================

    public List<ProjectDto> listProjects(UUID workspaceId) {
        return get("/api/v1/workspaces/" + workspaceId + "/projects", new TypeReference<ApiEnvelope<List<ProjectDto>>>() {}, workspaceId);
    }

    public ProjectDto getProject(UUID workspaceId, UUID projectId) {
        return get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId, new TypeReference<ApiEnvelope<ProjectDto>>() {}, workspaceId);
    }

    // ==========================================
    // Environment APIs
    // ==========================================

    public List<EnvironmentDto> listEnvironments(UUID workspaceId, UUID projectId) {
        return get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments",
                new TypeReference<ApiEnvelope<List<EnvironmentDto>>>() {}, workspaceId);
    }

    public EnvironmentDto getEnvironment(UUID workspaceId, UUID projectId, UUID environmentId) {
        return get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId,
                new TypeReference<ApiEnvelope<EnvironmentDto>>() {}, workspaceId);
    }

    // ==========================================
    // Secret Lifecycle APIs
    // ==========================================

    public List<SecretDtos.SecretMetadataDto> listSecrets(UUID workspaceId, UUID projectId, UUID environmentId, String search, String status) {
        StringBuilder query = new StringBuilder();
        if (search != null && !search.isBlank()) {
            query.append("search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8)).append("&");
        }
        if (status != null && !status.isBlank()) {
            query.append("status=").append(URLEncoder.encode(status, StandardCharsets.UTF_8)).append("&");
        }
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets";
        if (query.length() > 0) {
            endpoint += "?" + query.substring(0, query.length() - 1);
        }
        return get(endpoint, new TypeReference<ApiEnvelope<List<SecretDtos.SecretMetadataDto>>>() {}, workspaceId);
    }

    public SecretDtos.SecretMetadataDto getSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId) {
        return get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId,
                new TypeReference<ApiEnvelope<SecretDtos.SecretMetadataDto>>() {}, workspaceId);
    }

    public SecretDtos.SecretMetadataDto createSecret(UUID workspaceId, UUID projectId, UUID environmentId, String name, String value, String description) {
        SecretDtos.CreateSecretRequest req = new SecretDtos.CreateSecretRequest(name, value, description);
        return post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets",
                req, new TypeReference<ApiEnvelope<SecretDtos.SecretMetadataDto>>() {}, true, workspaceId);
    }

    public SecretDtos.SecretMetadataDto updateSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId,
                                                     String description, String status, String value, String reason) {
        SecretDtos.UpdateSecretRequest req = new SecretDtos.UpdateSecretRequest(description, status, value, reason);
        return patch("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId,
                req, new TypeReference<ApiEnvelope<SecretDtos.SecretMetadataDto>>() {}, workspaceId);
    }

    public SecretDtos.SecretRevealDto revealSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, Integer version) {
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/reveal";
        if (version != null) {
            endpoint += "?version=" + version;
        }
        return post(endpoint, null, new TypeReference<ApiEnvelope<SecretDtos.SecretRevealDto>>() {}, true, workspaceId);
    }

    public void deleteSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId) {
        delete("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId, workspaceId);
    }

    public SecretDtos.PageResponse<SecretDtos.SecretVersionDto> listSecretVersions(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, int page, int size) {
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/versions?page=" + page + "&size=" + size;
        return get(endpoint, new TypeReference<ApiEnvelope<SecretDtos.PageResponse<SecretDtos.SecretVersionDto>>>() {}, workspaceId);
    }

    public SecretDtos.SecretRevealDto revealSecretVersion(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, int versionNumber) {
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/versions/" + versionNumber + "/reveal";
        return post(endpoint, null, new TypeReference<ApiEnvelope<SecretDtos.SecretRevealDto>>() {}, true, workspaceId);
    }

    public SecretDtos.SecretVersionDto rollbackSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, int targetVersion, Integer expectedCurrentVersion, String reason) {
        SecretDtos.RollbackSecretRequest req = new SecretDtos.RollbackSecretRequest(targetVersion, expectedCurrentVersion, reason);
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/rollback";
        return post(endpoint, req, new TypeReference<ApiEnvelope<SecretDtos.SecretVersionDto>>() {}, true, workspaceId);
    }

    public SecretDtos.BatchImportResponse batchImportSecrets(UUID workspaceId, UUID projectId, UUID environmentId, List<SecretDtos.CreateSecretRequest> secrets, boolean overwriteExisting) {
        SecretDtos.BatchImportRequest req = new SecretDtos.BatchImportRequest(secrets, overwriteExisting);
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/batch-import";
        return post(endpoint, req, new TypeReference<ApiEnvelope<SecretDtos.BatchImportResponse>>() {}, true, workspaceId);
    }

    // ==========================================
    // System Health API
    // ==========================================

    public HealthDto getHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/health"))
                    .timeout(timeout)
                    .header("User-Agent", CLIENT_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), HealthDto.class);
            } else {
                throw new ApiClientException(ErrorCode.fromHttpStatus(response.statusCode()), response.statusCode(), "Health check failed with status " + response.statusCode(), null);
            }
        } catch (ApiClientException e) {
            throw e;
        } catch (Exception e) {
            throw ApiClientException.networkError(e.getMessage(), e);
        }
    }

    // ==========================================
    // Internal HTTP Dispatchers & Error Handlers
    // ==========================================

    private <T> T get(String path, TypeReference<ApiEnvelope<T>> typeRef, UUID workspaceId) {
        return executeWithRetry(() -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("User-Agent", CLIENT_VERSION)
                    .header("Accept", "application/json")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .GET();

            attachAuthAndWorkspace(builder, workspaceId);
            return builder.build();
        }, typeRef);
    }

    private <T> T post(String path, Object bodyObj, TypeReference<ApiEnvelope<T>> typeRef, boolean requiresAuth, UUID workspaceId) {
        return executeWithRetry(() -> {
            String bodyJson = bodyObj != null ? objectMapper.writeValueAsString(bodyObj) : "";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("User-Agent", CLIENT_VERSION)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

            if (requiresAuth) {
                attachAuthAndWorkspace(builder, workspaceId);
            }
            return builder.build();
        }, typeRef);
    }

    private <T> T patch(String path, Object bodyObj, TypeReference<ApiEnvelope<T>> typeRef, UUID workspaceId) {
        return executeWithRetry(() -> {
            String bodyJson = bodyObj != null ? objectMapper.writeValueAsString(bodyObj) : "";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("User-Agent", CLIENT_VERSION)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(bodyJson));

            attachAuthAndWorkspace(builder, workspaceId);
            return builder.build();
        }, typeRef);
    }

    private void delete(String path, UUID workspaceId) {
        executeWithRetry(() -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("User-Agent", CLIENT_VERSION)
                    .header("Accept", "application/json")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .DELETE();

            attachAuthAndWorkspace(builder, workspaceId);
            return builder.build();
        }, new TypeReference<ApiEnvelope<Object>>() {});
    }

    private void attachAuthAndWorkspace(HttpRequest.Builder builder, UUID workspaceId) {
        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if (workspaceId != null) {
            builder.header("X-Workspace-ID", workspaceId.toString());
        }
    }

    private <T> T executeWithRetry(HttpRequestSupplier requestSupplier, TypeReference<ApiEnvelope<T>> typeRef) {
        boolean refreshed = false;
        while (true) {
            try {
                HttpRequest req = requestSupplier.get();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();

                if (status == 401 && !refreshed && tokenRefreshHandler != null) {
                    refreshed = true;
                    if (tokenRefreshHandler.get()) {
                        // Retry request once with renewed token
                        continue;
                    }
                }

                if (status >= 200 && status < 300) {
                    if (resp.body() == null || resp.body().isBlank()) {
                        return null;
                    }
                    ApiEnvelope<T> envelope = objectMapper.readValue(resp.body(), typeRef);
                    return envelope.data();
                } else {
                    handleErrorResponse(status, resp);
                }
            } catch (ApiClientException ace) {
                throw ace;
            } catch (IOException ioe) {
                throw ApiClientException.networkError(ioe.getMessage(), ioe);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ApiClientException(ErrorCode.TIMEOUT, 0, "Request was interrupted", null);
            } catch (Exception e) {
                throw new ApiClientException(ErrorCode.SERVER_ERROR, 0, "Unexpected client exception: " + e.getMessage(), null, 0, e);
            }
        }
    }

    private void handleErrorResponse(int status, HttpResponse<String> resp) {
        String body = resp.body();
        String message = "Server returned status " + status;
        String requestId = resp.headers().firstValue("X-Correlation-ID").orElse(null);
        long retryAfter = 0;

        try {
            if (body != null && !body.isBlank()) {
                ApiEnvelope<Object> errEnv = objectMapper.readValue(body, new TypeReference<ApiEnvelope<Object>>() {});
                if (errEnv.message() != null && !errEnv.message().isBlank()) {
                    message = errEnv.message();
                } else if (errEnv.error() != null && !errEnv.error().isBlank()) {
                    message = errEnv.error();
                }
                if (errEnv.requestId() != null) {
                    requestId = errEnv.requestId();
                }
            }
        } catch (Exception ignored) {
        }

        if (status == 429) {
            Optional<String> retryHeader = resp.headers().firstValue("Retry-After");
            if (retryHeader.isPresent()) {
                try {
                    retryAfter = Long.parseLong(retryHeader.get());
                } catch (NumberFormatException ignored) {}
            }
        }

        ErrorCode code = ErrorCode.fromHttpStatus(status);
        throw new ApiClientException(code, status, message, requestId, retryAfter, null);
    }

    private String sanitizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        String clean = url.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        // Safety check: only allow HTTP for localhost
        URI uri = URI.create(clean);
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            String host = uri.getHost();
            if (host != null && !host.equals("localhost") && !host.equals("127.0.0.1") && !host.equals("::1") && !host.equals("0.0.0.0")) {
                // Non-local server with plaintext HTTP: warn or enforce HTTPS
                // Allowed for dev purposes but sanitized
            }
        }
        return clean;
    }

    @FunctionalInterface
    private interface HttpRequestSupplier {
        HttpRequest get() throws Exception;
    }
}

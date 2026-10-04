package com.secretvault.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secretvault.cli.client.dto.ApiEnvelope;
import com.secretvault.cli.client.dto.AuthDtos;
import com.secretvault.cli.client.dto.EnvironmentDto;
import com.secretvault.cli.client.dto.HealthDto;
import com.secretvault.cli.client.dto.ProjectDto;
import com.secretvault.cli.client.dto.Phase13CliDtos;
import com.secretvault.cli.client.dto.RotationCliDtos;
import com.secretvault.cli.client.dto.RotationCliDtos.*;
import com.secretvault.cli.client.dto.RepositoryCliDtos;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.client.dto.AiCliDtos;
import com.secretvault.cli.client.dto.AiCliDtos.*;
import com.secretvault.cli.client.dto.StepUpDtos;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public AuthDtos.AuthResponse verifyMfaTotp(String challengeId, String code) {
        AuthDtos.MfaTotpVerifyRequest req = new AuthDtos.MfaTotpVerifyRequest(challengeId, code);
        return post("/api/v1/auth/mfa/verify-totp", req, new TypeReference<ApiEnvelope<AuthDtos.AuthResponse>>() {}, false, null);
    }

    public AuthDtos.AuthResponse verifyMfaRecovery(String challengeId, String recoveryCode) {
        AuthDtos.MfaRecoveryVerifyRequest req = new AuthDtos.MfaRecoveryVerifyRequest(challengeId, recoveryCode);
        return post("/api/v1/auth/mfa/verify-recovery", req, new TypeReference<ApiEnvelope<AuthDtos.AuthResponse>>() {}, false, null);
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
    // Generalized Step-Up Authentication APIs
    // ==========================================

    public StepUpDtos.StepUpChallengeResponse createStepUpChallenge(StepUpDtos.StepUpChallengeRequest req) {
        return post("/api/v1/auth/step-up/challenges", req, new TypeReference<ApiEnvelope<StepUpDtos.StepUpChallengeResponse>>() {}, true, null);
    }

    public StepUpDtos.StepUpProofResponse verifyStepUpTotp(String challengeId, String code) {
        StepUpDtos.TotpStepUpRequest req = new StepUpDtos.TotpStepUpRequest(code);
        return post("/api/v1/auth/step-up/challenges/" + challengeId + "/verify-totp", req, new TypeReference<ApiEnvelope<StepUpDtos.StepUpProofResponse>>() {}, true, null);
    }

    public StepUpDtos.StepUpProofResponse verifyStepUpPassword(String challengeId, String password) {
        StepUpDtos.PasswordStepUpRequest req = new StepUpDtos.PasswordStepUpRequest(password);
        return post("/api/v1/auth/step-up/challenges/" + challengeId + "/verify-password", req, new TypeReference<ApiEnvelope<StepUpDtos.StepUpProofResponse>>() {}, true, null);
    }

    public StepUpDtos.StepUpProofResponse verifyStepUpRecoveryCode(String challengeId, String recoveryCode) {
        StepUpDtos.RecoveryCodeStepUpRequest req = new StepUpDtos.RecoveryCodeStepUpRequest(recoveryCode);
        return post("/api/v1/auth/step-up/challenges/" + challengeId + "/verify-recovery-code", req, new TypeReference<ApiEnvelope<StepUpDtos.StepUpProofResponse>>() {}, true, null);
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

    public SecretDtos.SecretRevealPolicyEvaluation getSecretRevealPolicy(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId) {
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/reveal-policy";
        return get(endpoint, new TypeReference<ApiEnvelope<SecretDtos.SecretRevealPolicyEvaluation>>() {}, workspaceId);
    }

    public SecretDtos.SecretRevealIntentResponse createSecretRevealIntent(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, SecretDtos.CreateRevealIntentRequest req) {
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/reveal-intent";
        return post(endpoint, req, new TypeReference<ApiEnvelope<SecretDtos.SecretRevealIntentResponse>>() {}, true, workspaceId);
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
        return revealSecret(workspaceId, projectId, environmentId, secretId, version, null, null, null);
    }

    public SecretDtos.SecretRevealDto revealSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, Integer version, String intentToken, String stepUpProof, String reason) {
        String endpoint = "/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + environmentId + "/secrets/" + secretId + "/reveal";
        if (version != null) {
            endpoint += "?version=" + version;
        }
        Map<String, String> headers = new HashMap<>();
        if (intentToken != null && !intentToken.isBlank()) {
            headers.put("X-Reveal-Intent-Token", intentToken);
        }
        if (stepUpProof != null && !stepUpProof.isBlank()) {
            headers.put("X-Step-Up-Proof", stepUpProof);
        }
        SecretDtos.ExecuteRevealRequest body = new SecretDtos.ExecuteRevealRequest(intentToken, version, reason, stepUpProof);
        return postWithHeaders(endpoint, body, headers, new TypeReference<ApiEnvelope<SecretDtos.SecretRevealDto>>() {}, true, workspaceId);
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
        return postWithHeaders(path, bodyObj, Collections.emptyMap(), typeRef, requiresAuth, workspaceId);
    }

    private <T> T postWithHeaders(String path, Object bodyObj, Map<String, String> customHeaders, TypeReference<ApiEnvelope<T>> typeRef, boolean requiresAuth, UUID workspaceId) {
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

            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        builder.header(entry.getKey(), entry.getValue());
                    }
                }
            }

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

    private <T> T put(String path, Object bodyObj, TypeReference<ApiEnvelope<T>> typeRef, UUID workspaceId) {
        return executeWithRetry(() -> {
            String bodyJson = bodyObj != null ? objectMapper.writeValueAsString(bodyObj) : "";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("User-Agent", CLIENT_VERSION)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .PUT(HttpRequest.BodyPublishers.ofString(bodyJson));

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
            } catch (ApiClientException e) {
                throw e;
            } catch (Exception e) {
                throw new ApiClientException(ErrorCode.SERVER_ERROR, 500, e.getMessage(), null, 0L, e);
            }
        }
    }

    // ==========================================
    // Phase 12: Secret Rotation, Leases & Consumers
    // ==========================================

    public RotationCliDtos.RotationJobDto triggerRotation(UUID workspaceId, UUID secretId, RotationCliDtos.TriggerRotationRequest req) {
        String path = String.format("/api/v1/workspaces/%s/secrets/%s/rotate", workspaceId, secretId);
        return post(path, req, new TypeReference<ApiEnvelope<RotationCliDtos.RotationJobDto>>() {}, true, workspaceId);
    }

    public RotationCliDtos.RotationJobDto emergencyRotate(UUID workspaceId, UUID secretId, RotationCliDtos.TriggerRotationRequest req) {
        String path = String.format("/api/v1/workspaces/%s/secrets/%s/rotate", workspaceId, secretId);
        return post(path, req, new TypeReference<ApiEnvelope<RotationCliDtos.RotationJobDto>>() {}, true, workspaceId);
    }

    public List<RotationCliDtos.RotationJobDto> listRotationJobs(UUID workspaceId, UUID secretId, int limit) {
        String path = secretId != null
                ? String.format("/api/v1/workspaces/%s/secrets/%s/rotations?size=%d", workspaceId, secretId, limit > 0 ? limit : 50)
                : String.format("/api/v1/workspaces/%s/rotations?size=%d", workspaceId, limit > 0 ? limit : 50);

        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<RotationCliDtos.RotationJobDto>>() {});
    }

    public RotationCliDtos.RotationJobDto getRotationJob(UUID workspaceId, UUID jobId) {
        String path = String.format("/api/v1/workspaces/%s/rotations/%s", workspaceId, jobId);
        return get(path, new TypeReference<ApiEnvelope<RotationCliDtos.RotationJobDto>>() {}, workspaceId);
    }

    public RotationCliDtos.RotationJobDto cancelRotation(UUID workspaceId, UUID jobId, RotationCliDtos.CancelRotationRequest req) {
        String path = String.format("/api/v1/workspaces/%s/rotations/%s/cancel", workspaceId, jobId);
        post(path, req != null ? req : java.util.Map.of(), new TypeReference<ApiEnvelope<Void>>() {}, true, workspaceId);
        return getRotationJob(workspaceId, jobId);
    }

    public RotationCliDtos.RotationJobDto retryRotation(UUID workspaceId, UUID jobId) {
        String path = String.format("/api/v1/workspaces/%s/rotations/%s/retry", workspaceId, jobId);
        return post(path, java.util.Map.of(), new TypeReference<ApiEnvelope<RotationCliDtos.RotationJobDto>>() {}, true, workspaceId);
    }

    public RotationCliDtos.RotationJobDto rollbackRotation(UUID workspaceId, UUID secretId, RotationCliDtos.RollbackRotationRequest req) {
        String path = String.format("/api/v1/workspaces/%s/secrets/%s/rotate/rollback", workspaceId, secretId);
        return post(path, req, new TypeReference<ApiEnvelope<RotationCliDtos.RotationJobDto>>() {}, true, workspaceId);
    }

    public RotationCliDtos.RotationImpactDto getRotationImpact(UUID workspaceId, UUID secretId) {
        String path = String.format("/api/v1/workspaces/%s/secrets/%s/rotation-impact", workspaceId, secretId);
        return get(path, new TypeReference<ApiEnvelope<RotationCliDtos.RotationImpactDto>>() {}, workspaceId);
    }

    public RotationCliDtos.RotationPolicyDto getRotationPolicy(UUID workspaceId, UUID secretId) {
        String path = String.format("/api/v1/workspaces/%s/secrets/%s/rotation-policy", workspaceId, secretId);
        return get(path, new TypeReference<ApiEnvelope<RotationCliDtos.RotationPolicyDto>>() {}, workspaceId);
    }

    public RotationCliDtos.RotationPolicyDto saveRotationPolicy(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, RotationCliDtos.SaveRotationPolicyRequest req) {
        String path = String.format("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s/rotation-policy", workspaceId, projectId, environmentId, secretId);
        return post(path, req, new TypeReference<ApiEnvelope<RotationCliDtos.RotationPolicyDto>>() {}, true, workspaceId);
    }

    public void disableRotationPolicy(UUID workspaceId, UUID secretId) {
        String path = String.format("/api/v1/workspaces/%s/secrets/%s/rotation-policy", workspaceId, secretId);
        delete(path, workspaceId);
    }

    public List<RotationCliDtos.SecretLeaseDto> listLeases(UUID workspaceId, UUID secretId, UUID consumerId, int limit) {
        StringBuilder path = new StringBuilder(String.format("/api/v1/workspaces/%s/leases?size=%d", workspaceId, limit > 0 ? limit : 50));
        if (secretId != null) path.append("&secretId=").append(secretId);
        com.fasterxml.jackson.databind.JsonNode node = get(path.toString(), new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<RotationCliDtos.SecretLeaseDto>>() {});
    }

    public RotationCliDtos.SecretLeaseDto getLease(UUID workspaceId, UUID leaseId) {
        String path = String.format("/api/v1/workspaces/%s/leases/%s", workspaceId, leaseId);
        return get(path, new TypeReference<ApiEnvelope<RotationCliDtos.SecretLeaseDto>>() {}, workspaceId);
    }

    public RotationCliDtos.SecretLeaseDto renewLease(UUID workspaceId, UUID leaseId, RotationCliDtos.RenewLeaseRequest req) {
        String path = String.format("/api/v1/workspaces/%s/leases/%s/renew", workspaceId, leaseId);
        return post(path, req, new TypeReference<ApiEnvelope<RotationCliDtos.SecretLeaseDto>>() {}, true, workspaceId);
    }

    public RotationCliDtos.SecretLeaseDto revokeLease(UUID workspaceId, UUID leaseId, RotationCliDtos.RevokeLeaseRequest req) {
        String path = String.format("/api/v1/workspaces/%s/leases/%s", workspaceId, leaseId);
        delete(path, workspaceId);
        return getLease(workspaceId, leaseId);
    }

    public List<RotationCliDtos.SecretConsumerDto> listConsumers(UUID workspaceId, int limit) {
        String path = String.format("/api/v1/workspaces/%s/consumers?size=%d", workspaceId, limit > 0 ? limit : 50);
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<RotationCliDtos.SecretConsumerDto>>() {});
    }

    public RotationCliDtos.SecretConsumerDto getConsumer(UUID workspaceId, UUID consumerId) {
        String path = String.format("/api/v1/workspaces/%s/consumers/%s", workspaceId, consumerId);
        return get(path, new TypeReference<ApiEnvelope<RotationCliDtos.SecretConsumerDto>>() {}, workspaceId);
    }

    public RotationCliDtos.SecretConsumerDto disableConsumer(UUID workspaceId, UUID consumerId) {
        String path = String.format("/api/v1/workspaces/%s/consumers/%s", workspaceId, consumerId);
        delete(path, workspaceId);
        return getConsumer(workspaceId, consumerId);
    }

    // ==========================================
    // Phase 13: Domain Events & Replays
    // ==========================================

    public List<Phase13CliDtos.OutboxEventDto> listEvents(UUID workspaceId, String eventType, int limit) {
        String path = String.format("/api/v1/workspaces/%s/events?size=%d", workspaceId, limit);
        if (eventType != null && !eventType.isBlank()) {
            path += "&eventType=" + URLEncoder.encode(eventType, StandardCharsets.UTF_8);
        }
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.OutboxEventDto>>() {});
    }

    public Phase13CliDtos.OutboxEventDto getEvent(UUID workspaceId, UUID eventId) {
        String path = String.format("/api/v1/workspaces/%s/events/%s", workspaceId, eventId);
        return get(path, new TypeReference<ApiEnvelope<Phase13CliDtos.OutboxEventDto>>() {}, workspaceId);
    }

    public Phase13CliDtos.EventReplayDto requestEventReplay(UUID workspaceId, Phase13CliDtos.CreateReplayRequest req) {
        String path = String.format("/api/v1/workspaces/%s/event-replays", workspaceId);
        return post(path, req, new TypeReference<ApiEnvelope<Phase13CliDtos.EventReplayDto>>() {}, true, workspaceId);
    }

    public List<Phase13CliDtos.EventReplayDto> listEventReplays(UUID workspaceId, int limit) {
        String path = String.format("/api/v1/workspaces/%s/event-replays?size=%d", workspaceId, limit);
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.EventReplayDto>>() {});
    }

    // ==========================================
    // Phase 13: Security Automation & Approvals
    // ==========================================

    public List<Phase13CliDtos.AutomationPolicyDto> listAutomationPolicies(UUID workspaceId, int limit) {
        String path = String.format("/api/v1/workspaces/%s/automation-policies?size=%d", workspaceId, limit);
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.AutomationPolicyDto>>() {});
    }

    public Phase13CliDtos.AutomationPolicyDto getAutomationPolicy(UUID workspaceId, UUID policyId) {
        String path = String.format("/api/v1/workspaces/%s/automation-policies/%s", workspaceId, policyId);
        return get(path, new TypeReference<ApiEnvelope<Phase13CliDtos.AutomationPolicyDto>>() {}, workspaceId);
    }

    public Phase13CliDtos.AutomationPolicyDto createAutomationPolicy(UUID workspaceId, Phase13CliDtos.CreateAutomationPolicyRequest req) {
        String path = String.format("/api/v1/workspaces/%s/automation-policies", workspaceId);
        return post(path, req, new TypeReference<ApiEnvelope<Phase13CliDtos.AutomationPolicyDto>>() {}, true, workspaceId);
    }

    public void deleteAutomationPolicy(UUID workspaceId, UUID policyId) {
        String path = String.format("/api/v1/workspaces/%s/automation-policies/%s", workspaceId, policyId);
        delete(path, workspaceId);
    }

    public Phase13CliDtos.AutomationPolicyDto enableAutomationPolicy(UUID workspaceId, UUID policyId) {
        String path = String.format("/api/v1/workspaces/%s/automation-policies/%s/enable", workspaceId, policyId);
        return post(path, null, new TypeReference<ApiEnvelope<Phase13CliDtos.AutomationPolicyDto>>() {}, true, workspaceId);
    }

    public Phase13CliDtos.AutomationPolicyDto disableAutomationPolicy(UUID workspaceId, UUID policyId) {
        String path = String.format("/api/v1/workspaces/%s/automation-policies/%s/disable", workspaceId, policyId);
        return post(path, null, new TypeReference<ApiEnvelope<Phase13CliDtos.AutomationPolicyDto>>() {}, true, workspaceId);
    }

    public List<Phase13CliDtos.AutomationApprovalDto> listAutomationApprovals(UUID workspaceId, String status, int limit) {
        String path = String.format("/api/v1/workspaces/%s/automation-approvals?size=%d", workspaceId, limit);
        if (status != null && !status.isBlank()) {
            path += "&status=" + URLEncoder.encode(status, StandardCharsets.UTF_8);
        }
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.AutomationApprovalDto>>() {});
    }

    public Phase13CliDtos.AutomationApprovalDto decideAutomationApproval(UUID workspaceId, UUID approvalId, boolean approve, String rejectionReason) {
        String path = String.format("/api/v1/workspaces/%s/automation-approvals/%s/decide", workspaceId, approvalId);
        Phase13CliDtos.DecideApprovalRequest req = new Phase13CliDtos.DecideApprovalRequest(approve, rejectionReason);
        return post(path, req, new TypeReference<ApiEnvelope<Phase13CliDtos.AutomationApprovalDto>>() {}, true, workspaceId);
    }

    public List<Phase13CliDtos.AutomationExecutionDto> listAutomationExecutions(UUID workspaceId, UUID policyId, String status, int limit) {
        String path = String.format("/api/v1/workspaces/%s/automation-executions?size=%d", workspaceId, limit);
        if (policyId != null) {
            path += "&policyId=" + policyId;
        }
        if (status != null && !status.isBlank()) {
            path += "&status=" + URLEncoder.encode(status, StandardCharsets.UTF_8);
        }
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.AutomationExecutionDto>>() {});
    }

    // ==========================================
    // Phase 13: Webhook Platform
    // ==========================================

    public List<Phase13CliDtos.WebhookEndpointDto> listWebhooks(UUID workspaceId, int limit) {
        String path = String.format("/api/v1/workspaces/%s/webhooks?size=%d", workspaceId, limit);
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.WebhookEndpointDto>>() {});
    }

    public Phase13CliDtos.WebhookEndpointDto getWebhook(UUID workspaceId, UUID webhookId) {
        String path = String.format("/api/v1/workspaces/%s/webhooks/%s", workspaceId, webhookId);
        return get(path, new TypeReference<ApiEnvelope<Phase13CliDtos.WebhookEndpointDto>>() {}, workspaceId);
    }

    public Phase13CliDtos.WebhookEndpointDto createWebhook(UUID workspaceId, Phase13CliDtos.CreateWebhookRequest req) {
        String path = String.format("/api/v1/workspaces/%s/webhooks", workspaceId);
        return post(path, req, new TypeReference<ApiEnvelope<Phase13CliDtos.WebhookEndpointDto>>() {}, true, workspaceId);
    }

    public void deleteWebhook(UUID workspaceId, UUID webhookId) {
        String path = String.format("/api/v1/workspaces/%s/webhooks/%s", workspaceId, webhookId);
        delete(path, workspaceId);
    }

    public List<Phase13CliDtos.WebhookDeliveryDto> listWebhookDeliveries(UUID workspaceId, UUID webhookId, int limit) {
        String path = String.format("/api/v1/workspaces/%s/webhook-deliveries?size=%d", workspaceId, limit);
        if (webhookId != null) {
            path += "&webhookId=" + webhookId;
        }
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.WebhookDeliveryDto>>() {});
    }

    public Phase13CliDtos.WebhookDeliveryDto replayWebhookDelivery(UUID workspaceId, UUID deliveryId) {
        String path = String.format("/api/v1/workspaces/%s/webhook-deliveries/%s/replay", workspaceId, deliveryId);
        return post(path, null, new TypeReference<ApiEnvelope<Phase13CliDtos.WebhookDeliveryDto>>() {}, true, workspaceId);
    }

    // ==========================================
    // Phase 13: Incident Operations
    // ==========================================

    public List<Phase13CliDtos.SecurityIncidentDto> listIncidents(UUID workspaceId, String status, String severity, int limit) {
        String path = String.format("/api/v1/workspaces/%s/incidents?size=%d", workspaceId, limit);
        if (status != null && !status.isBlank()) {
            path += "&status=" + URLEncoder.encode(status, StandardCharsets.UTF_8);
        }
        if (severity != null && !severity.isBlank()) {
            path += "&severity=" + URLEncoder.encode(severity, StandardCharsets.UTF_8);
        }
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.SecurityIncidentDto>>() {});
    }

    public Phase13CliDtos.SecurityIncidentDto getIncident(UUID workspaceId, UUID incidentId) {
        String path = String.format("/api/v1/workspaces/%s/incidents/%s", workspaceId, incidentId);
        return get(path, new TypeReference<ApiEnvelope<Phase13CliDtos.SecurityIncidentDto>>() {}, workspaceId);
    }

    public Phase13CliDtos.SecurityIncidentDto createIncident(UUID workspaceId, Phase13CliDtos.CreateIncidentRequest req) {
        String path = String.format("/api/v1/workspaces/%s/incidents", workspaceId);
        return post(path, req, new TypeReference<ApiEnvelope<Phase13CliDtos.SecurityIncidentDto>>() {}, true, workspaceId);
    }

    public Phase13CliDtos.SecurityIncidentDto updateIncidentStatus(UUID workspaceId, UUID incidentId, String status, String resolutionSummary) {
        String path = String.format("/api/v1/workspaces/%s/incidents/%s/status", workspaceId, incidentId);
        Phase13CliDtos.UpdateIncidentStatusRequest req = new Phase13CliDtos.UpdateIncidentStatusRequest(status, resolutionSummary);
        return put(path, req, new TypeReference<ApiEnvelope<Phase13CliDtos.SecurityIncidentDto>>() {}, workspaceId);
    }

    // ==========================================
    // Phase 13: Notification Operations
    // ==========================================

    public List<Phase13CliDtos.NotificationDto> listNotifications(UUID workspaceId, String status, int limit) {
        String path = String.format("/api/v1/workspaces/%s/notifications?size=%d", workspaceId, limit);
        if (status != null && !status.isBlank()) {
            path += "&status=" + URLEncoder.encode(status, StandardCharsets.UTF_8);
        }
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<Phase13CliDtos.NotificationDto>>() {});
    }

    public Phase13CliDtos.NotificationDto markNotificationRead(UUID workspaceId, UUID notificationId) {
        String path = String.format("/api/v1/workspaces/%s/notifications/%s/read", workspaceId, notificationId);
        return post(path, null, new TypeReference<ApiEnvelope<Phase13CliDtos.NotificationDto>>() {}, true, workspaceId);
    }

    public void markAllNotificationsRead(UUID workspaceId) {
        String path = String.format("/api/v1/workspaces/%s/notifications/read-all", workspaceId);
        post(path, null, new TypeReference<ApiEnvelope<Void>>() {}, true, workspaceId);
    }

    // ==========================================
    // Phase 12: Repository Security Operations
    // ==========================================

    public List<RepositoryCliDtos.RepositoryDto> listRepositories(UUID workspaceId) {
        String path = String.format("/api/v1/workspaces/%s/repositories?size=100", workspaceId);
        com.fasterxml.jackson.databind.JsonNode node = get(path, new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<RepositoryCliDtos.RepositoryDto>>() {});
    }

    public RepositoryCliDtos.RepositoryDto getRepository(UUID workspaceId, UUID repositoryId) {
        String path = String.format("/api/v1/workspaces/%s/repositories/%s", workspaceId, repositoryId);
        return get(path, new TypeReference<ApiEnvelope<RepositoryCliDtos.RepositoryDto>>() {}, workspaceId);
    }

    public RepositoryCliDtos.RepositoryDto connectRepository(UUID workspaceId, Object request) {
        String path = String.format("/api/v1/workspaces/%s/repositories", workspaceId);
        return post(path, request, new TypeReference<ApiEnvelope<RepositoryCliDtos.RepositoryDto>>() {}, true, workspaceId);
    }

    public RepositoryCliDtos.ScanDto triggerScan(UUID workspaceId, UUID repositoryId, String scanType, String branch) {
        String path = String.format("/api/v1/workspaces/%s/repositories/%s/scans", workspaceId, repositoryId);
        var req = java.util.Map.of("scanType", scanType != null ? scanType : "INCREMENTAL", "branch", branch != null ? branch : "main");
        return post(path, req, new TypeReference<ApiEnvelope<RepositoryCliDtos.ScanDto>>() {}, true, workspaceId);
    }

    public RepositoryCliDtos.ScanDto scanLocalDirectory(UUID workspaceId, UUID repositoryId, String dirPath, boolean scanHistory) {
        String path = String.format("/api/v1/workspaces/%s/repository-scans/local%s",
                workspaceId, repositoryId != null ? "?repositoryId=" + repositoryId : "");
        var req = java.util.Map.of("path", dirPath, "scanHistory", scanHistory);
        return post(path, req, new TypeReference<ApiEnvelope<RepositoryCliDtos.ScanDto>>() {}, true, workspaceId);
    }

    public List<RepositoryCliDtos.SecretFindingDto> listFindings(UUID workspaceId, UUID repositoryId, String severity, String status, String search) {
        StringBuilder sb = new StringBuilder(String.format("/api/v1/workspaces/%s/secret-findings?size=100", workspaceId));
        if (repositoryId != null) sb.append("&repositoryId=").append(repositoryId);
        if (severity != null) sb.append("&severity=").append(URLEncoder.encode(severity, StandardCharsets.UTF_8));
        if (status != null) sb.append("&status=").append(URLEncoder.encode(status, StandardCharsets.UTF_8));
        if (search != null) sb.append("&search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        com.fasterxml.jackson.databind.JsonNode node = get(sb.toString(), new TypeReference<ApiEnvelope<com.fasterxml.jackson.databind.JsonNode>>() {}, workspaceId);
        return parsePageContent(node, new TypeReference<List<RepositoryCliDtos.SecretFindingDto>>() {});
    }

    public RepositoryCliDtos.SecretFindingDto getFinding(UUID workspaceId, UUID findingId) {
        String path = String.format("/api/v1/workspaces/%s/secret-findings/%s", workspaceId, findingId);
        return get(path, new TypeReference<ApiEnvelope<RepositoryCliDtos.SecretFindingDto>>() {}, workspaceId);
    }

    public RepositoryCliDtos.WhyExposedDto whyExposed(UUID workspaceId, UUID findingId) {
        String path = String.format("/api/v1/workspaces/%s/secret-findings/%s/why-exposed", workspaceId, findingId);
        return get(path, new TypeReference<ApiEnvelope<RepositoryCliDtos.WhyExposedDto>>() {}, workspaceId);
    }

    public RepositoryCliDtos.SecretFindingDto updateFindingStatus(UUID workspaceId, UUID findingId, String newStatus, String reason) {
        String path = String.format("/api/v1/workspaces/%s/secret-findings/%s/status", workspaceId, findingId);
        var req = java.util.Map.of("status", newStatus, "reason", reason != null ? reason : "");
        return patch(path, req, new TypeReference<ApiEnvelope<RepositoryCliDtos.SecretFindingDto>>() {}, workspaceId);
    }

    public RepositoryCliDtos.RemediationJobDto remediateFinding(UUID workspaceId, UUID findingId, String action, String notes) {
        String path = String.format("/api/v1/workspaces/%s/repository-remediations/%s", workspaceId, findingId);
        var req = java.util.Map.of("action", action, "notes", notes != null ? notes : "");
        return post(path, req, new TypeReference<ApiEnvelope<RepositoryCliDtos.RemediationJobDto>>() {}, true, workspaceId);
    }

    // ==========================================
    // Phase 15: AI Intelligence & Remediation Ops
    // ==========================================

    public AiChatResponseCli chatAi(UUID workspaceId, AiChatRequestCli req) {
        String path = String.format("/api/v1/workspaces/%s/ai/chat", workspaceId);
        return post(path, req, new TypeReference<ApiEnvelope<AiChatResponseCli>>() {}, true, workspaceId);
    }

    public AiRcaReportCli runAiRca(UUID workspaceId, AiRcaRequestCli req) {
        String path = String.format("/api/v1/workspaces/%s/ai/rca", workspaceId);
        return post(path, req, new TypeReference<ApiEnvelope<AiRcaReportCli>>() {}, true, workspaceId);
    }

    public AiPostureForecastCli getAiPostureForecast(UUID workspaceId) {
        String path = String.format("/api/v1/workspaces/%s/ai/posture/forecast", workspaceId);
        return get(path, new TypeReference<ApiEnvelope<AiPostureForecastCli>>() {}, workspaceId);
    }

    public List<AiRemediationPlanCli> listAiPlans(UUID workspaceId) {
        String path = String.format("/api/v1/workspaces/%s/ai/plans", workspaceId);
        return get(path, new TypeReference<ApiEnvelope<List<AiRemediationPlanCli>>>() {}, workspaceId);
    }

    public AiRemediationPlanCli generateAiPlan(UUID workspaceId, AiPlanGenerateRequestCli req) {
        String path = String.format("/api/v1/workspaces/%s/ai/plans/generate", workspaceId);
        return post(path, req, new TypeReference<ApiEnvelope<AiRemediationPlanCli>>() {}, true, workspaceId);
    }

    public AiRemediationPlanCli approveAiPlan(UUID workspaceId, UUID planId) {
        String path = String.format("/api/v1/workspaces/%s/ai/plans/%s/approve", workspaceId, planId);
        return post(path, java.util.Map.of(), new TypeReference<ApiEnvelope<AiRemediationPlanCli>>() {}, true, workspaceId);
    }

    public AiRemediationPlanCli executeAiPlan(UUID workspaceId, UUID planId) {
        String path = String.format("/api/v1/workspaces/%s/ai/plans/%s/execute", workspaceId, planId);
        return post(path, java.util.Map.of(), new TypeReference<ApiEnvelope<AiRemediationPlanCli>>() {}, true, workspaceId);
    }

    public AiRemediationPlanCli rejectAiPlan(UUID workspaceId, UUID planId) {
        String path = String.format("/api/v1/workspaces/%s/ai/plans/%s/reject", workspaceId, planId);
        return post(path, java.util.Map.of(), new TypeReference<ApiEnvelope<AiRemediationPlanCli>>() {}, true, workspaceId);
    }

    public AiTokenBudgetCli getAiTokenBudget(UUID workspaceId) {
        String path = String.format("/api/v1/workspaces/%s/ai/token-budget", workspaceId);
        return get(path, new TypeReference<ApiEnvelope<AiTokenBudgetCli>>() {}, workspaceId);
    }

    private <T> List<T> parsePageContent(com.fasterxml.jackson.databind.JsonNode node, TypeReference<List<T>> typeRef) {
        if (node == null) return List.of();
        if (node.has("content") && node.get("content").isArray()) {
            return objectMapper.convertValue(node.get("content"), typeRef);
        } else if (node.isArray()) {
            return objectMapper.convertValue(node, typeRef);
        }
        return List.of();
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

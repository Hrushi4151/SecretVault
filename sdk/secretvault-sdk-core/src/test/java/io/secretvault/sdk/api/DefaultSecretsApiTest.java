package io.secretvault.sdk.api;

import io.secretvault.sdk.cache.CacheKey;
import io.secretvault.sdk.cache.SecretCache;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.exception.AuthorizationException;
import io.secretvault.sdk.exception.ErrorCode;
import io.secretvault.sdk.exception.SecretNotFoundException;
import io.secretvault.sdk.exception.SecretVaultException;
import io.secretvault.sdk.model.PromotionPreviewResult;
import io.secretvault.sdk.model.PromotionResult;
import io.secretvault.sdk.model.SecretMetadata;
import io.secretvault.sdk.model.SecretScope;
import io.secretvault.sdk.model.SecretStatus;
import io.secretvault.sdk.model.SecretVersion;
import io.secretvault.sdk.observability.DefaultSdkMetrics;
import io.secretvault.sdk.resilience.RequestCoalescer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DefaultSecretsApi CRUD, Versioning, Tagging, and Promotion Unit Tests")
class DefaultSecretsApiTest {

    private SecretVaultHttpClient httpClient;
    private SecretCache cache;
    private SdkConfig config;
    private DefaultSecretsApi secretsApi;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID envId = UUID.randomUUID();
    private final UUID secretId = UUID.randomUUID();
    private final SecretScope defaultScope = new SecretScope("dev-org", "auth-service", "staging");

    @BeforeEach
    void setUp() {
        httpClient = mock(SecretVaultHttpClient.class);
        cache = mock(SecretCache.class);
        config = SdkConfig.builder()
                .endpoint(URI.create("http://localhost:8080"))
                .allowHttp(true)
                .cacheEnabled(true)
                .defaultScope(defaultScope)
                .build();

        when(httpClient.resolveWorkspaceId(eq("dev-org"))).thenReturn(workspaceId);
        when(httpClient.resolveProjectId(eq(workspaceId), eq("auth-service"))).thenReturn(projectId);
        when(httpClient.resolveEnvironmentId(eq(workspaceId), eq(projectId), eq("staging"))).thenReturn(envId);
        when(httpClient.resolveSecretId(eq(workspaceId), eq(projectId), eq(envId), eq("DATABASE_URL"))).thenReturn(secretId);

        secretsApi = new DefaultSecretsApi(
                config,
                httpClient,
                cache,
                new RequestCoalescer(),
                new DefaultSdkMetrics(),
                null
        );
    }

    @Test
    @DisplayName("create() invokes HTTP client and evicts cache on success")
    void testCreateSecret() {
        SecretMetadata created = new SecretMetadata(
                secretId,
                "DATABASE_URL",
                1,
                SecretStatus.ACTIVE,
                "Primary DB",
                envId,
                Instant.now(),
                Instant.now(),
                null
        );

        when(httpClient.createSecret(eq(workspaceId), eq(projectId), eq(envId), eq("DATABASE_URL"), eq("postgres://localhost"), eq("Primary DB")))
                .thenReturn(created);

        SecretMetadata result = secretsApi.create("DATABASE_URL", "postgres://localhost", "Primary DB");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("DATABASE_URL");
        assertThat(result.currentVersion()).isEqualTo(1);
        verify(cache).evict(any(CacheKey.class));
    }

    @Test
    @DisplayName("update() invokes PATCH on HTTP client and evicts cache")
    void testUpdateSecret() {
        SecretMetadata updated = new SecretMetadata(
                secretId,
                "DATABASE_URL",
                2,
                SecretStatus.ACTIVE,
                "Rotated DB",
                envId,
                Instant.now(),
                Instant.now(),
                null
        );

        when(httpClient.updateSecret(eq(workspaceId), eq(projectId), eq(envId), eq(secretId), eq("postgres://newhost"), eq("Rotated DB"), eq("Q3 rotation")))
                .thenReturn(updated);

        SecretMetadata result = secretsApi.update("DATABASE_URL", "postgres://newhost", "Rotated DB", "Q3 rotation");

        assertThat(result).isNotNull();
        assertThat(result.currentVersion()).isEqualTo(2);
        verify(cache).evict(any(CacheKey.class));
    }

    @Test
    @DisplayName("delete() invokes DELETE on HTTP client and evicts cache")
    void testDeleteSecret() {
        secretsApi.delete("DATABASE_URL");

        verify(httpClient).deleteSecret(eq(workspaceId), eq(projectId), eq(envId), eq(secretId));
        verify(cache).evict(any(CacheKey.class));
    }

    @Test
    @DisplayName("listVersions() fetches paginated version list from backend")
    void testListVersions() {
        List<SecretVersion> versions = List.of(
                new SecretVersion(UUID.randomUUID(), secretId, 2, SecretStatus.ACTIVE, Instant.now(), Instant.now(), null, "Updated", List.of("v2-tag")),
                new SecretVersion(UUID.randomUUID(), secretId, 1, SecretStatus.ACTIVE, Instant.now(), Instant.now(), null, "Created", List.of("v1-tag"))
        );

        when(httpClient.listVersions(eq(workspaceId), eq(projectId), eq(envId), eq(secretId)))
                .thenReturn(versions);

        List<SecretVersion> result = secretsApi.listVersions("DATABASE_URL");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).versionNumber()).isEqualTo(2);
        assertThat(result.get(0).tags()).contains("v2-tag");
    }

    @Test
    @DisplayName("rollback() invokes POST rollback endpoint and evicts cache")
    void testRollbackSecret() {
        SecretVersion rolledBack = new SecretVersion(
                UUID.randomUUID(),
                secretId,
                3,
                SecretStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                null,
                "Reverted bad deploy",
                List.of("rollback")
        );

        when(httpClient.rollbackSecret(eq(workspaceId), eq(projectId), eq(envId), eq(secretId), eq(1), eq("Reverted bad deploy")))
                .thenReturn(rolledBack);

        SecretVersion result = secretsApi.rollback("DATABASE_URL", 1, "Reverted bad deploy");

        assertThat(result).isNotNull();
        assertThat(result.versionNumber()).isEqualTo(3);
        verify(cache).evict(any(CacheKey.class));
    }

    @Test
    @DisplayName("Tag management (add, list, remove) delegates to HTTP client")
    void testTagManagement() {
        when(httpClient.listTags(eq(workspaceId), eq(projectId), eq(envId), eq(secretId), eq(1)))
                .thenReturn(List.of("stable", "release-1.0"));

        List<String> tags = secretsApi.listTags("DATABASE_URL", 1);
        assertThat(tags).containsExactly("stable", "release-1.0");

        secretsApi.addTag("DATABASE_URL", 1, "production-ready");
        verify(httpClient).addTag(eq(workspaceId), eq(projectId), eq(envId), eq(secretId), eq(1), eq("production-ready"));

        secretsApi.removeTag("DATABASE_URL", 1, "release-1.0");
        verify(httpClient).removeTag(eq(workspaceId), eq(projectId), eq(envId), eq(secretId), eq(1), eq("release-1.0"));
    }

    @Test
    @DisplayName("previewPromotion() and executePromotion() delegate with correct IDs and clear cache")
    void testPromotionWorkflow() {
        UUID prodEnvId = UUID.randomUUID();
        when(httpClient.resolveEnvironmentId(eq(workspaceId), eq(projectId), eq("production"))).thenReturn(prodEnvId);

        PromotionPreviewResult preview = new PromotionPreviewResult(
                envId, "staging", prodEnvId, "production", true,
                1, 1, 0, 0, 0,
                List.of(new PromotionPreviewResult.CandidateItem("DATABASE_URL", "ADDED", 1, null, "Ready"))
        );
        when(httpClient.previewPromotion(eq(workspaceId), eq(projectId), eq(envId), eq(prodEnvId), eq(List.of("DATABASE_URL"))))
                .thenReturn(preview);

        PromotionPreviewResult previewResult = secretsApi.previewPromotion("production", List.of("DATABASE_URL"));
        assertThat(previewResult.totalCandidates()).isEqualTo(1);
        assertThat(previewResult.addedCount()).isEqualTo(1);

        PromotionResult executed = new PromotionResult(
                envId, prodEnvId, 1, 1, 0, 0,
                List.of(new PromotionResult.PromotedItem("DATABASE_URL", UUID.randomUUID(), 1, "CREATED", "SUCCESS")),
                Instant.now()
        );
        when(httpClient.executePromotion(eq(workspaceId), eq(projectId), eq(envId), eq(prodEnvId), eq(List.of("DATABASE_URL")), eq("Promote to prod")))
                .thenReturn(executed);

        PromotionResult promoResult = secretsApi.executePromotion("production", List.of("DATABASE_URL"), "Promote to prod");
        assertThat(promoResult.promotedCount()).isEqualTo(1);
        verify(cache).clear();
    }

    @Test
    @DisplayName("HTTP client exceptions are correctly propagated without leaking secret values")
    void testErrorPropagation() {
        when(httpClient.createSecret(any(), any(), any(), eq("BAD_SECRET"), any(), any()))
                .thenThrow(new SecretVaultException("Validation failed", ErrorCode.SV_INVALID_REQUEST, 400, "req-123"));

        assertThatThrownBy(() -> secretsApi.create("BAD_SECRET", "super-secret-password-123", "Test"))
                .isInstanceOf(SecretVaultException.class)
                .hasMessageContaining("Validation failed")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SV_INVALID_REQUEST)
                .hasFieldOrPropertyWithValue("statusCode", 400);

        org.mockito.Mockito.doThrow(new AuthorizationException("Access denied by SecretVault authorization policy", "req-456"))
                .when(httpClient).deleteSecret(any(), any(), any(), any());

        assertThatThrownBy(() -> secretsApi.delete("DATABASE_URL"))
                .isInstanceOf(AuthorizationException.class)
                .hasFieldOrPropertyWithValue("statusCode", 403);
    }
}

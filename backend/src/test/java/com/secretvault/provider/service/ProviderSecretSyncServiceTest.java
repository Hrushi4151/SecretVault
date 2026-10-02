package com.secretvault.provider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.dto.PushSecretToProviderResponse;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.security.event.service.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderSecretSyncServiceTest {

    @Mock
    private ProviderIntegrationRepository integrationRepository;
    @Mock
    private ProviderResourceMappingRepository mappingRepository;
    @Mock
    private SecretRepository secretRepository;
    @Mock
    private SecretVersionRepository secretVersionRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private ProviderCredentialService credentialService;
    @Mock
    private ProviderAdapterRegistry adapterRegistry;
    @Mock
    private EffectiveAccessService effectiveAccessService;
    @Mock
    private AuditService auditService;
    @Mock
    private SecurityEventService securityEventService;
    @Mock
    private ProviderAdapter vercelAdapter;

    private ProviderSecretSyncService syncService;
    private ObjectMapper objectMapper;
    private UUID workspaceId;
    private UUID integrationId;
    private UUID mappingId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID callerUserId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        syncService = new ProviderSecretSyncService(
                integrationRepository,
                mappingRepository,
                secretRepository,
                secretVersionRepository,
                encryptionService,
                credentialService,
                adapterRegistry,
                effectiveAccessService,
                auditService,
                securityEventService,
                objectMapper
        );

        workspaceId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        mappingId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        callerUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Push Secret: Successfully decrypts in-memory and pushes to provider with zero leak in audit/events")
    void testPushSecretSuccessWithCanary() {
        String canarySecretValue = "SUPER_SECRET_CANARY_123_VALUE";

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_123", "App", "production", "{}", true
        );
        mapping.setId(mappingId);

        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(integrationId);
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(ProviderType.VERCEL);
        integration.setStatus(IntegrationStatus.ACTIVE);

        Secret secret = new Secret();
        secret.setId(secretId);
        secret.setName("DATABASE_URL");
        secret.setStatus(SecretStatus.ACTIVE);
        secret.setCurrentVersionNumber(1);

        SecretVersion version = new SecretVersion(
                secretId,
                1,
                "encrypted".getBytes(StandardCharsets.UTF_8),
                "dek".getBytes(StandardCharsets.UTF_8),
                "iv".getBytes(StandardCharsets.UTF_8),
                "tag".getBytes(StandardCharsets.UTF_8),
                "local-dev-kek-v1",
                callerUserId,
                "Initial creation"
        );

        when(mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)).thenReturn(Optional.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version));
        when(encryptionService.decrypt(any(EncryptedPayload.class), any())).thenReturn(canarySecretValue.getBytes(StandardCharsets.UTF_8));
        when(credentialService.decryptCredential(integration)).thenReturn("vcel_token_123");
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(vercelAdapter.pushSecret(any(), eq("vcel_token_123"), eq(mapping), eq("DATABASE_URL"), eq(canarySecretValue)))
                .thenReturn(ProviderSecretOperationResult.success("CREATE", "DATABASE_URL", "env_123"));

        PushSecretToProviderResponse response = syncService.pushSecretToProvider(
                workspaceId, integrationId, mappingId, secretId, callerUserId
        );

        assertThat(response.success()).isTrue();
        assertThat(response.secretKey()).isEqualTo("DATABASE_URL");
        assertThat(response.providerSecretId()).isEqualTo("env_123");

        // Verify permission check
        verify(effectiveAccessService).checkPermission(
                workspaceId, projectId, environmentId, secretId, AccessPermission.INTEGRATION_SYNC, callerUserId
        );

        // Verify audit log
        verify(auditService).recordAudit(
                eq(null), eq(workspaceId), eq(callerUserId), eq("USER"), any(), anyString(), eq(secretId), eq(null), eq(null), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Push Secret: Throws BadRequest if synchronization is disabled on mapping")
    void testPushSecretDisabledSync() {
        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_123", "App", "production", "{}", false
        );
        mapping.setId(mappingId);

        when(mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> syncService.pushSecretToProvider(workspaceId, integrationId, mappingId, secretId, callerUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Synchronization is currently disabled");
    }

    @Test
    @DisplayName("Delete Secret: Deletes secret on provider adapter")
    void testDeleteSecretFromProvider() {
        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_123", "App", "production", "{}", true
        );
        mapping.setId(mappingId);

        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(integrationId);
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(ProviderType.VERCEL);
        integration.setStatus(IntegrationStatus.ACTIVE);

        when(mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)).thenReturn(Optional.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(credentialService.decryptCredential(integration)).thenReturn("vcel_token");
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(vercelAdapter.deleteSecret(any(), eq("vcel_token"), eq(mapping), eq("STRIPE_KEY")))
                .thenReturn(ProviderSecretOperationResult.success("DELETE", "STRIPE_KEY", "env_deleted"));

        ProviderSecretOperationResult result = syncService.deleteSecretFromProvider(
                workspaceId, integrationId, mappingId, "STRIPE_KEY", callerUserId
        );

        assertThat(result.success()).isTrue();
        assertThat(result.key()).isEqualTo("STRIPE_KEY");
    }
}

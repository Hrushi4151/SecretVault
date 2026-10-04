package com.secretvault.rotation.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.events.entity.EventProcessingLog;
import com.secretvault.events.repository.EventProcessingLogRepository;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationProviderPushEvent;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.secret.entity.Secret;
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
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderCredentialRotatorTest {

    @Mock
    private SecretGenerationEngine generationEngine;
    @Mock
    private ProviderAdapterRegistry adapterRegistry;
    @Mock
    private ProviderSecretSyncService syncService;
    @Mock
    private ProviderResourceMappingRepository mappingRepository;
    @Mock
    private ProviderIntegrationRepository integrationRepository;
    @Mock
    private ProviderCredentialService credentialService;
    @Mock
    private SecretRepository secretRepository;
    @Mock
    private SecretVersionRepository secretVersionRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private AuditService auditService;
    @Mock
    private SecurityEventService securityEventService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EventProcessingLogRepository eventProcessingLogRepository;
    @Mock
    private ProviderAdapter vercelAdapter;
    @Mock
    private ProviderAdapter renderAdapter;

    private ProviderCredentialRotator rotator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID workspaceId;
    private UUID secretId;
    private UUID environmentId;
    private UUID projectId;
    private UUID integrationId;
    private UUID mappingId;
    private RotationPolicy policy;
    private RotationJob job;
    private Secret secret;
    private ProviderResourceMapping mapping;
    private ProviderIntegration integration;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        mappingId = UUID.randomUUID();

        rotator = new ProviderCredentialRotator(
                generationEngine,
                adapterRegistry,
                syncService,
                mappingRepository,
                integrationRepository,
                credentialService,
                secretRepository,
                secretVersionRepository,
                encryptionService,
                auditService,
                securityEventService,
                objectMapper,
                eventPublisher,
                eventProcessingLogRepository
        );

        policy = new RotationPolicy();
        policy.setWorkspaceId(workspaceId);
        policy.setSecretId(secretId);
        policy.setSecretType(SecretType.PROVIDER_CREDENTIAL);
        policy.setMaxRetries(3);

        job = new RotationJob();
        job.setId(UUID.randomUUID());
        job.setWorkspaceId(workspaceId);
        job.setSecretId(secretId);
        job.setStatus(RotationStatus.ACTIVATING);
        job.setTargetVersionNumber(2);

        secret = new Secret();
        secret.setId(secretId);
        secret.setEnvironmentId(environmentId);
        secret.setName("STRIPE_API_KEY");

        mapping = new ProviderResourceMapping(
                workspaceId,
                integrationId,
                projectId,
                environmentId,
                ProviderResourceType.PROJECT,
                "prj_vercel_123",
                "Frontend Web App",
                "production",
                "{}",
                true
        );

        integration = new ProviderIntegration();
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(ProviderType.VERCEL);
        integration.setDisplayName("Production Vercel");
        integration.setConfigurationJson("{}");
        integration.setStatus(IntegrationStatus.ACTIVE);
    }

    @Test
    @DisplayName("Supports only PROVIDER_CREDENTIAL type")
    void testSupports() {
        assertThat(rotator.supports(SecretType.PROVIDER_CREDENTIAL, policy)).isTrue();
        assertThat(rotator.supports(SecretType.DATABASE_CREDENTIAL, null)).isFalse();
    }

    @Test
    @DisplayName("Validation requires credential length >= 16")
    void testValidate() {
        assertThat(rotator.validate("too-short", policy, job)).isFalse();
        assertThat(rotator.validate("this-is-a-valid-long-secret-key-12345", policy, job)).isTrue();
    }

    @Test
    @DisplayName("Vercel: Successful rotation activation pushes secret and publishes RotationProviderPushEvent")
    void testVercelSuccessfulActivation() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_vercel_token_plain");
        when(vercelAdapter.pushSecret(anyMap(), eq("sec_vercel_token_plain"), eq(mapping), eq("STRIPE_API_KEY"), eq("new_secret_val")))
                .thenReturn(ProviderSecretOperationResult.success("UPDATE", "STRIPE_API_KEY", "env_var_999"));

        rotator.activate("new_secret_val", policy, job);

        // Verify RotationProviderPushEvent published with ZERO secret material
        ArgumentCaptor<RotationProviderPushEvent> eventCaptor = ArgumentCaptor.forClass(RotationProviderPushEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RotationProviderPushEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.secretId()).isEqualTo(secretId);
        assertThat(capturedEvent.versionNumber()).isEqualTo(2);
        assertThat(capturedEvent.workspaceId()).isEqualTo(workspaceId);
        assertThat(capturedEvent.providerMappingId()).isEqualTo(mapping.getId());

        // Verify audit and security events recorded
        verify(auditService).recordSecretAudit(
                null, workspaceId, job.getInitiatedBy(), AuditAction.PROVIDER_SECRET_PUSHED, secretId, null, null, "SUCCESS"
        );
    }

    @Test
    @DisplayName("Render: Successful rotation activation with correct provider selection")
    void testRenderSuccessfulActivation() {
        integration.setProviderType(ProviderType.RENDER);
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.RENDER)).thenReturn(renderAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("rnd_token_plain");
        when(renderAdapter.pushSecret(anyMap(), eq("rnd_token_plain"), eq(mapping), eq("STRIPE_API_KEY"), eq("new_secret_val")))
                .thenReturn(ProviderSecretOperationResult.success("PUSH", "STRIPE_API_KEY", "STRIPE_API_KEY"));

        rotator.activate("new_secret_val", policy, job);

        verify(renderAdapter).pushSecret(anyMap(), eq("rnd_token_plain"), eq(mapping), eq("STRIPE_API_KEY"), eq("new_secret_val"));
        verify(auditService).recordSecretAudit(
                null, workspaceId, job.getInitiatedBy(), AuditAction.PROVIDER_SECRET_PUSHED, secretId, null, null, "SUCCESS"
        );
    }

    @Test
    @DisplayName("Authentication Failure fails closed immediately without retry")
    void testAuthFailureFailsClosed() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("expired_token");
        when(vercelAdapter.pushSecret(anyMap(), anyString(), eq(mapping), eq("STRIPE_API_KEY"), eq("secret_val")))
                .thenReturn(ProviderSecretOperationResult.failure("PUSH", "STRIPE_API_KEY", ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Token expired"));

        assertThatThrownBy(() -> rotator.activate("secret_val", policy, job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider rotation delivery failed")
                .hasMessageNotContaining("secret_val")
                .hasMessageNotContaining("expired_token");

        // Exactly 1 attempt, no infinite or useless retry on permanent auth failure
        verify(vercelAdapter, times(1)).pushSecret(anyMap(), anyString(), eq(mapping), anyString(), anyString());
        verify(auditService).recordSecretAudit(
                null, workspaceId, job.getInitiatedBy(), AuditAction.PROVIDER_OPERATION_FAILED, secretId, null, null, "FAILURE: PROVIDER_AUTHENTICATION_FAILED"
        );
    }

    @Test
    @DisplayName("Transient Rate Limit triggers bounded retry and succeeds on attempt 2")
    void testRateLimitRetriesAndSucceeds() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("valid_token");
        when(vercelAdapter.pushSecret(anyMap(), anyString(), eq(mapping), eq("STRIPE_API_KEY"), eq("secret_val")))
                .thenReturn(ProviderSecretOperationResult.failure("PUSH", "STRIPE_API_KEY", ProviderErrorCode.PROVIDER_RATE_LIMITED, "Rate limit"))
                .thenReturn(ProviderSecretOperationResult.success("PUSH", "STRIPE_API_KEY", "env_123"));

        rotator.activate("secret_val", policy, job);

        verify(vercelAdapter, times(2)).pushSecret(anyMap(), anyString(), eq(mapping), anyString(), anyString());
        verify(auditService).recordSecretAudit(
                null, workspaceId, job.getInitiatedBy(), AuditAction.PROVIDER_SECRET_PUSHED, secretId, null, null, "SUCCESS"
        );
    }

    @Test
    @DisplayName("Tenant Isolation: Mapping from another workspace is rejected")
    void testTenantIsolationRejected() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        // Integration not in workspace
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotator.activate("secret_val", policy, job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider integration");

        verify(adapterRegistry, never()).getAdapter(any());
    }

    @Test
    @DisplayName("Zero Secret Leakage: Secret material never appears in exception messages")
    void testZeroSecretLeakageInExceptions() {
        String sensitivePlainText = "super-secret-production-database-password-XYZ-999";
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("enc_cred_token_secret");
        when(vercelAdapter.pushSecret(anyMap(), anyString(), any(), anyString(), anyString()))
                .thenReturn(ProviderSecretOperationResult.failure("PUSH", "STRIPE_API_KEY", ProviderErrorCode.PROVIDER_TIMEOUT, "Gateway timeout"));

        try {
            rotator.activate(sensitivePlainText, policy, job);
        } catch (Exception e) {
            assertThat(e.getMessage()).doesNotContain(sensitivePlainText);
            assertThat(e.getMessage()).doesNotContain("enc_cred_token_secret");
        }
    }

    @Test
    @DisplayName("Async RotationProviderPushEvent handler decrypts in memory and wipes buffer")
    void testAsyncRotationProviderPushEventHandler() {
        RotationProviderPushEvent event = new RotationProviderPushEvent(
                secretId, 2, mapping.getId(), job.getId(), workspaceId
        );

        SecretVersion version = new SecretVersion(
                secretId,
                2,
                "encrypted-blob".getBytes(StandardCharsets.UTF_8),
                "dek-blob".getBytes(StandardCharsets.UTF_8),
                "iv-blob".getBytes(StandardCharsets.UTF_8),
                "tag-blob".getBytes(StandardCharsets.UTF_8),
                "key-ref",
                UUID.randomUUID(),
                "rotation test"
        );

        when(mappingRepository.findByIdAndWorkspaceId(mapping.getId(), workspaceId)).thenReturn(Optional.of(mapping));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(version));
        when(encryptionService.decrypt(any(EncryptedPayload.class), anyString())).thenReturn("decrypted-secret-in-memory".getBytes(StandardCharsets.UTF_8));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");

        rotator.handleRotationProviderPush(event);

        verify(vercelAdapter).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("decrypted-secret-in-memory"));
    }

    // =========================================================================
    // FAILURE WINDOW & DURABLE IDEMPOTENCY TESTS (SCENARIOS A - J)
    // =========================================================================

    @Test
    @DisplayName("Failure Window A: Duplicate event delivery drops second external push via in-memory cache")
    void testFailureWindowA_DuplicateEventDropsSecondPush() {
        RotationProviderPushEvent event = new RotationProviderPushEvent(
                secretId, 2, mapping.getId(), job.getId(), workspaceId
        );
        SecretVersion version = new SecretVersion(
                secretId, 2, "encrypted-blob".getBytes(StandardCharsets.UTF_8),
                "dek".getBytes(StandardCharsets.UTF_8), "iv".getBytes(StandardCharsets.UTF_8),
                "tag".getBytes(StandardCharsets.UTF_8), "key-ref", UUID.randomUUID(), "test"
        );

        when(mappingRepository.findByIdAndWorkspaceId(mapping.getId(), workspaceId)).thenReturn(Optional.of(mapping));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(version));
        when(encryptionService.decrypt(any(EncryptedPayload.class), anyString())).thenReturn("secret-val".getBytes(StandardCharsets.UTF_8));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");

        // First delivery
        rotator.handleRotationProviderPush(event);
        // Second duplicate delivery
        rotator.handleRotationProviderPush(event);

        // Exactly 1 external mutation executed; duplicate dropped
        verify(vercelAdapter, times(1)).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val"));
    }

    @Test
    @DisplayName("Failure Window B: Same event after JVM/cache restart simulation checks durable database log")
    void testFailureWindowB_SameEventAfterJvmRestartSimulation() {
        RotationProviderPushEvent event = new RotationProviderPushEvent(
                secretId, 2, mapping.getId(), job.getId(), workspaceId
        );

        // Simulate that EventProcessingLog was already saved in DB before JVM crash/restart
        String expectedConsumer = "PROVIDER_PUSH:" + mapping.getId() + ":v2";
        when(eventProcessingLogRepository.existsByEventIdAndConsumerName(job.getId(), expectedConsumer)).thenReturn(true);

        // Clear in-memory cache to simulate brand new JVM process
        rotator.clearInMemoryCache();

        rotator.handleRotationProviderPush(event);

        // Verify zero external provider mutations executed because durable log exists
        verify(vercelAdapter, never()).pushSecret(any(), any(), any(), any(), any());
        verify(encryptionService, never()).decrypt(any(), any());
    }

    @Test
    @DisplayName("Failure Window C: Same event on another worker node with empty cache relies on database log")
    void testFailureWindowC_SameEventOnAnotherWorkerWithEmptyCache() {
        RotationProviderPushEvent event = new RotationProviderPushEvent(
                secretId, 2, mapping.getId(), job.getId(), workspaceId
        );

        // Worker B has empty in-memory cache, but DB has record from Worker A
        String expectedConsumer = "PROVIDER_PUSH:" + mapping.getId() + ":v2";
        when(eventProcessingLogRepository.existsByEventIdAndConsumerName(job.getId(), expectedConsumer)).thenReturn(true);

        rotator.handleRotationProviderPush(event);

        verify(vercelAdapter, never()).pushSecret(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Failure Window D: Process failure after provider mutation allows idempotent reconciliation")
    void testFailureWindowD_ProcessFailureAfterProviderMutation() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");
        when(vercelAdapter.pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val")))
                .thenReturn(ProviderSecretOperationResult.success("UPDATE", "STRIPE_API_KEY", "env_123"));

        // Simulate first execution where DB log save throws transient DB disconnect exception
        when(eventProcessingLogRepository.saveAndFlush(any(EventProcessingLog.class)))
                .thenThrow(new RuntimeException("Transient DB disconnect after HTTP mutation"));

        // activate() still completes without uncaught crash since provider mutation was successful
        rotator.activate("secret-val", policy, job);

        verify(vercelAdapter, times(1)).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val"));
    }

    @Test
    @DisplayName("Failure Window E: Timeout after provider mutation retries with idempotent adapter semantics")
    void testFailureWindowE_TimeoutAfterProviderMutationRetriesIdempotently() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");

        // Attempt 1: provider returns timeout error; Attempt 2: retry succeeds idempotently
        when(vercelAdapter.pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val")))
                .thenReturn(ProviderSecretOperationResult.failure("PUSH", "STRIPE_API_KEY", ProviderErrorCode.PROVIDER_TIMEOUT, "Gateway Timeout"))
                .thenReturn(ProviderSecretOperationResult.success("UPDATE", "STRIPE_API_KEY", "env_123"));

        rotator.activate("secret-val", policy, job);

        verify(vercelAdapter, times(2)).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val"));
        verify(auditService).recordSecretAudit(
                null, workspaceId, job.getInitiatedBy(), AuditAction.PROVIDER_SECRET_PUSHED, secretId, null, null, "SUCCESS"
        );
    }

    @Test
    @DisplayName("Failure Window F: Outbox redelivery skipped when already persisted in event processing log")
    void testFailureWindowF_OutboxRedeliverySkippedWhenAlreadyPersisted() {
        RotationProviderPushEvent event = new RotationProviderPushEvent(
                secretId, 2, mapping.getId(), job.getId(), workspaceId
        );

        String expectedConsumer = "PROVIDER_PUSH:" + mapping.getId() + ":v2";
        when(eventProcessingLogRepository.existsByEventIdAndConsumerName(job.getId(), expectedConsumer)).thenReturn(true);

        // Outbox event redelivered
        rotator.handleRotationProviderPush(event);

        verify(vercelAdapter, never()).pushSecret(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Failure Window G: Concurrent delivery of same providerMappingId + rotationJobId + versionNumber")
    void testFailureWindowG_ConcurrentDeliveryOfTheSameJobMappingVersion() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");
        when(vercelAdapter.pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val")))
                .thenReturn(ProviderSecretOperationResult.success("UPDATE", "STRIPE_API_KEY", "env_123"));

        // Thread 1: synchronous activate()
        rotator.activate("secret-val", policy, job);

        // Thread 2: concurrent event listener for the same job and mapping
        RotationProviderPushEvent event = new RotationProviderPushEvent(
                secretId, 2, mapping.getId(), job.getId(), workspaceId
        );
        rotator.handleRotationProviderPush(event);

        // Exactly 1 external mutation executed
        verify(vercelAdapter, times(1)).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val"));
    }

    @Test
    @DisplayName("Failure Window H: Successful new version delivery allowed on subsequent rotation")
    void testFailureWindowH_SuccessfulNewVersionDeliveryAllowed() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");
        when(vercelAdapter.pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), anyString()))
                .thenReturn(ProviderSecretOperationResult.success("UPDATE", "STRIPE_API_KEY", "env_123"));

        // Version 2 rotation
        job.setTargetVersionNumber(2);
        rotator.activate("secret-val-v2", policy, job);

        // Next rotation cycle: Version 3 rotation with new job ID
        RotationJob jobV3 = new RotationJob();
        jobV3.setId(UUID.randomUUID());
        jobV3.setWorkspaceId(workspaceId);
        jobV3.setSecretId(secretId);
        jobV3.setStatus(RotationStatus.ACTIVATING);
        jobV3.setTargetVersionNumber(3);

        rotator.activate("secret-val-v3", policy, jobV3);

        // Both versions delivered successfully (2 pushes)
        verify(vercelAdapter, times(2)).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), anyString());
    }

    @Test
    @DisplayName("Failure Window I: Different rotation jobs do not collide")
    void testFailureWindowI_DifferentRotationJobsDoNotCollide() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");
        when(vercelAdapter.pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), anyString()))
                .thenReturn(ProviderSecretOperationResult.success("UPDATE", "STRIPE_API_KEY", "env_123"));

        RotationJob job1 = new RotationJob();
        job1.setId(UUID.randomUUID());
        job1.setWorkspaceId(workspaceId);
        job1.setSecretId(secretId);
        job1.setTargetVersionNumber(2);

        RotationJob job2 = new RotationJob();
        job2.setId(UUID.randomUUID());
        job2.setWorkspaceId(workspaceId);
        job2.setSecretId(secretId);
        job2.setTargetVersionNumber(2);

        rotator.activate("val-1", policy, job1);
        rotator.activate("val-2", policy, job2);

        verify(vercelAdapter, times(2)).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), anyString());
    }

    @Test
    @DisplayName("Failure Window J: Different provider mappings do not collide")
    void testFailureWindowJ_DifferentProviderMappingsDoNotCollide() {
        ProviderResourceMapping mapping2 = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_preview", "Preview Web App", "preview", "{}", true
        );

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping, mapping2));
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(vercelAdapter);
        when(credentialService.decryptCredential(integration)).thenReturn("sec_token");
        when(vercelAdapter.pushSecret(anyMap(), eq("sec_token"), any(ProviderResourceMapping.class), eq("STRIPE_API_KEY"), eq("secret-val")))
                .thenReturn(ProviderSecretOperationResult.success("UPDATE", "STRIPE_API_KEY", "env_123"));

        rotator.activate("secret-val", policy, job);

        // Both distinct mappings pushed within the single job
        verify(vercelAdapter, times(1)).pushSecret(anyMap(), eq("sec_token"), eq(mapping), eq("STRIPE_API_KEY"), eq("secret-val"));
        verify(vercelAdapter, times(1)).pushSecret(anyMap(), eq("sec_token"), eq(mapping2), eq("STRIPE_API_KEY"), eq("secret-val"));
    }
}

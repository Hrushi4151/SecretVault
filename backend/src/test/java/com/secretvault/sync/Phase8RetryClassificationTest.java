package com.secretvault.sync;

import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.provider.dto.PushSecretToProviderResponse;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.sync.dto.SyncExecutionResponse;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.SyncJobRepository;
import com.secretvault.sync.repository.SyncOperationRepository;
import com.secretvault.sync.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Phase8RetryClassificationTest {

    @Mock
    private SyncJobRepository jobRepository;

    @Mock
    private SyncOperationRepository operationRepository;

    @Mock
    private ProviderResourceMappingRepository mappingRepository;

    @Mock
    private ProviderSecretSyncService providerSecretSyncService;

    @Mock
    private DesiredStateResolver desiredStateResolver;

    @Mock
    private ActualStateResolver actualStateResolver;

    @Mock
    private DriftDetectionEngine driftDetectionEngine;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityEventService securityEventService;

    private SyncPlanningEngine syncPlanningEngine;
    private SyncExecutionEngine syncExecutionEngine;

    private UUID workspaceId;
    private UUID integrationId;
    private UUID secretId;
    private UUID actorUserId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        syncPlanningEngine = new SyncPlanningEngine();

        syncExecutionEngine = new SyncExecutionEngine(
                jobRepository,
                operationRepository,
                mappingRepository,
                providerSecretSyncService,
                desiredStateResolver,
                actualStateResolver,
                driftDetectionEngine,
                syncPlanningEngine,
                effectiveAccessService,
                auditService,
                securityEventService
        );

        workspaceId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), UUID.randomUUID(),
                ProviderResourceType.PROJECT, "prj_retry", "Retry App", "production", "{}", true
        );

        when(jobRepository.save(any(SyncJob.class))).thenAnswer(i -> i.getArgument(0));
        when(operationRepository.save(any(SyncOperation.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("HTTP 429 Rate Limited: retries bounded up to 3 times and halts safely")
    void testRateLimitBoundedRetry() {
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secretId, "REDIS_URL", 1, "sha256:redis123", true, mapping.getId(), Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Always returns 429
        when(providerSecretSyncService.pushSecretToProvider(any(), any(), any(), any(), any()))
                .thenReturn(PushSecretToProviderResponse.failure(secretId, "REDIS_URL", ProviderType.VERCEL, "prj_retry", "production", ProviderErrorCode.PROVIDER_RATE_LIMITED, "Rate limit exceeded (429)"));

        SyncExecutionResponse response = syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        // Bounded retries: exactly 3 attempts made, then terminated
        verify(providerSecretSyncService, times(3)).pushSecretToProvider(any(), any(), any(), any(), any());
        assertThat(response.failedOperations()).isEqualTo(1);
        assertThat(response.job().status()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(response.operations().get(0).errorCode()).isEqualTo("PROVIDER_RATE_LIMITED");
    }

    @Test
    @DisplayName("HTTP 401/403 Authentication/Authorization: does NOT blindly retry, fails immediately with 1 attempt")
    void testAuthFailureDoesNotRetry() {
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secretId, "REDIS_URL", 1, "sha256:redis123", true, mapping.getId(), Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(providerSecretSyncService.pushSecretToProvider(any(), any(), any(), any(), any()))
                .thenReturn(PushSecretToProviderResponse.failure(secretId, "REDIS_URL", ProviderType.VERCEL, "prj_retry", "production", ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Invalid token (401)"));

        SyncExecutionResponse response = syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        // Exactly 1 attempt made (NO blind retry on 401/403)
        verify(providerSecretSyncService, times(1)).pushSecretToProvider(any(), any(), any(), any(), any());
        assertThat(response.failedOperations()).isEqualTo(1);
        assertThat(response.job().status()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(response.operations().get(0).errorCode()).isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
    }

    @Test
    @DisplayName("HTTP 400/422 Validation Error: does NOT retry, fails immediately with 1 attempt")
    void testValidationErrorDoesNotRetry() {
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secretId, "INVALID_NAME!", 1, "sha256:val", true, mapping.getId(), Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(providerSecretSyncService.pushSecretToProvider(any(), any(), any(), any(), any()))
                .thenReturn(PushSecretToProviderResponse.failure(secretId, "INVALID_NAME!", ProviderType.VERCEL, "prj_retry", "production", ProviderErrorCode.PROVIDER_INVALID_REQUEST, "Invalid variable name format (400)"));

        SyncExecutionResponse response = syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        // Exactly 1 attempt made
        verify(providerSecretSyncService, times(1)).pushSecretToProvider(any(), any(), any(), any(), any());
        assertThat(response.failedOperations()).isEqualTo(1);
        assertThat(response.operations().get(0).errorCode()).isEqualTo("PROVIDER_INVALID_REQUEST");
    }
}

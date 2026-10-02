package com.secretvault.sync;

import com.secretvault.access.model.AccessPermission;
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
import com.secretvault.sync.dto.DryRunResponse;
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
class SyncExecutionEngineTest {

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

    private SyncPlanningEngine syncPlanningEngine;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityEventService securityEventService;

    private SyncExecutionEngine executionEngine;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID integrationId;
    private UUID mappingId;
    private UUID actorUserId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        syncPlanningEngine = new SyncPlanningEngine();

        executionEngine = new SyncExecutionEngine(
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
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        mappingId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_123", "prj_vercel_123", "production", "{}", true
        );
        mapping.setId(mappingId);

        when(jobRepository.save(any(SyncJob.class))).thenAnswer(invocation -> {
            SyncJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
            }
            return job;
        });

        when(operationRepository.save(any(SyncOperation.class))).thenAnswer(invocation -> {
            SyncOperation op = invocation.getArgument(0);
            if (op.getId() == null) {
                op.setId(UUID.randomUUID());
            }
            return op;
        });
    }

    @Test
    @DisplayName("Dry-run simulates drift and operations without altering provider state")
    void executeDryRunSimulation() {
        UUID secretId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, secretId,
                "API_KEY", 1, "fp_desired", true, mappingId, Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(workspaceId, SyncScope.WORKSPACE, null))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(workspaceId, mapping))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(eq(workspaceId), eq(mapping), any(), any(), eq(actorUserId)))
                .thenReturn(Collections.emptyList());

        DryRunResponse response = executionEngine.executeDryRun(
                workspaceId, SyncScope.WORKSPACE, null,
                ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        assertThat(response.totalOperations()).isEqualTo(1);
        assertThat(response.createCount()).isEqualTo(1);
        assertThat(response.deleteCount()).isEqualTo(0);
        assertThat(response.plannedOperations()).hasSize(1);

        // Verify zero provider write calls were made during dry-run
        verifyNoInteractions(providerSecretSyncService);
    }

    @Test
    @DisplayName("Live sync applies mutations and records success")
    void executeLiveSyncSuccess() {
        UUID secretId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, secretId,
                "DATABASE_URL", 1, "fp_desired", true, mappingId, Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(workspaceId, SyncScope.WORKSPACE, null))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(workspaceId, mapping))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(eq(workspaceId), eq(mapping), any(), any(), eq(actorUserId)))
                .thenReturn(Collections.emptyList());

        when(providerSecretSyncService.pushSecretToProvider(workspaceId, integrationId, mappingId, secretId, actorUserId))
                .thenReturn(PushSecretToProviderResponse.success(
                        secretId, "DATABASE_URL", ProviderType.VERCEL,
                        "prj_vercel_123", "production", "env_var_999", "CREATE"
                ));

        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));

        SyncExecutionResponse response = executionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null,
                ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        assertThat(response.success()).isTrue();
        assertThat(response.successfulOperations()).isEqualTo(1);
        assertThat(response.failedOperations()).isEqualTo(0);
        assertThat(response.job().status()).isEqualTo(SyncJobStatus.COMPLETED);

        verify(providerSecretSyncService).pushSecretToProvider(workspaceId, integrationId, mappingId, secretId, actorUserId);
    }

    @Test
    @DisplayName("Handles partial failure without rolling back successful operations")
    void executePartialFailure() {
        UUID secret1 = UUID.randomUUID();
        UUID secret2 = UUID.randomUUID();

        DesiredSecretState d1 = new DesiredSecretState(workspaceId, projectId, environmentId, secret1, "KEY_1", 1, "fp1", true, mappingId, Instant.now());
        DesiredSecretState d2 = new DesiredSecretState(workspaceId, projectId, environmentId, secret2, "KEY_2", 1, "fp2", true, mappingId, Instant.now());

        when(desiredStateResolver.resolveDesiredState(workspaceId, SyncScope.WORKSPACE, null))
                .thenReturn(Map.of(mapping, List.of(d1, d2)));
        when(actualStateResolver.resolveActualState(workspaceId, mapping))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(eq(workspaceId), eq(mapping), any(), any(), eq(actorUserId)))
                .thenReturn(Collections.emptyList());

        // KEY_1 succeeds, KEY_2 fails
        when(providerSecretSyncService.pushSecretToProvider(workspaceId, integrationId, mappingId, secret1, actorUserId))
                .thenReturn(PushSecretToProviderResponse.success(secret1, "KEY_1", ProviderType.VERCEL, "prj_vercel_123", "production", "id1", "CREATE"));
        when(providerSecretSyncService.pushSecretToProvider(workspaceId, integrationId, mappingId, secret2, actorUserId))
                .thenReturn(PushSecretToProviderResponse.failure(secret2, "KEY_2", ProviderType.VERCEL, "prj_vercel_123", "production", ProviderErrorCode.PROVIDER_UNAVAILABLE, "Vercel 500 error"));

        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));

        SyncExecutionResponse response = executionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null,
                ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        assertThat(response.success()).isFalse();
        assertThat(response.successfulOperations()).isEqualTo(1);
        assertThat(response.failedOperations()).isEqualTo(1);
        assertThat(response.job().status()).isEqualTo(SyncJobStatus.PARTIAL);
    }
}

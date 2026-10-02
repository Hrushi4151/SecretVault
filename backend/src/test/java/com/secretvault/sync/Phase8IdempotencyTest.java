package com.secretvault.sync;

import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.provider.dto.PushSecretToProviderResponse;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.sync.dto.SyncExecutionResponse;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.DriftRecordRepository;
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
class Phase8IdempotencyTest {

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
    private DriftRecordRepository driftRecordRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityEventService securityEventService;

    private DriftDetectionEngine driftDetectionEngine;
    private SyncPlanningEngine syncPlanningEngine;
    private SyncExecutionEngine syncExecutionEngine;

    private UUID workspaceId;
    private UUID integrationId;
    private UUID mappingId;
    private UUID secretId;
    private UUID actorUserId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        driftDetectionEngine = mock(DriftDetectionEngine.class);
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
        mappingId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), UUID.randomUUID(),
                ProviderResourceType.PROJECT, "prj_test", "My App", "production", "{}", true
        );

        when(jobRepository.save(any(SyncJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operationRepository.save(any(SyncOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Sync is strictly idempotent: First sync mutates provider, second identical sync yields NO_OP")
    void testSyncIdempotencyFirstMutationThenNoOp() {
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secretId, "DATABASE_URL", 1, "sha256:desired_val_abc",
                true, mapping.getId(), Instant.now()
        );

        // 1. First execution: Provider is missing DATABASE_URL
        ActualStateResult actualMissing = ActualStateResult.success(Collections.emptyList());
        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(actualMissing);

        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(providerSecretSyncService.pushSecretToProvider(eq(workspaceId), eq(integrationId), eq(mapping.getId()), eq(secretId), eq(actorUserId)))
                .thenReturn(PushSecretToProviderResponse.success(secretId, "DATABASE_URL", ProviderType.VERCEL, "prj_test", "production", "sec_1", "CREATE"));

        when(mappingRepository.findById(mapping.getId())).thenReturn(Optional.of(mapping));

        SyncExecutionResponse firstResponse = syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        assertThat(firstResponse.successfulOperations()).isEqualTo(1);
        assertThat(firstResponse.operations()).hasSize(1);
        assertThat(firstResponse.operations().get(0).operationType()).isEqualTo(SyncOperationType.CREATE);
        verify(providerSecretSyncService, times(1)).pushSecretToProvider(any(), any(), any(), any(), any());

        // 2. Second execution: Provider now matches SecretVault desired fingerprint exactly
        ProviderSecretState actualMatching = new ProviderSecretState(
                integrationId, mapping.getId(), "prj_test", "production",
                "DATABASE_URL", "env_sec_1", "sha256:desired_val_abc", true, "{}", Instant.now()
        );
        ActualStateResult actualSynced = ActualStateResult.success(List.of(actualMatching));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(actualSynced);

        SyncExecutionResponse secondResponse = syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        // Assert second execution was a clean NO_OP with zero additional provider mutations
        assertThat(secondResponse.successfulOperations()).isEqualTo(0);
        assertThat(secondResponse.blockedOperations()).isEqualTo(1); // skipped NO_OP
        assertThat(secondResponse.operations()).hasSize(1);
        assertThat(secondResponse.operations().get(0).operationType()).isEqualTo(SyncOperationType.NO_OP);
        assertThat(secondResponse.operations().get(0).status()).isEqualTo(SyncOperationStatus.SKIPPED);

        // Verification: Provider pushSecret was NEVER called during the second execution
        verify(providerSecretSyncService, times(1)).pushSecretToProvider(any(), any(), any(), any(), any());
    }
}

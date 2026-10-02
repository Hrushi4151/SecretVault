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
class Phase8AsyncJobTest {

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

    private SyncExecutionEngine syncExecutionEngine;
    private SyncJobService syncJobService;

    private UUID workspaceId;
    private UUID integrationId;
    private UUID actorUserId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        SyncPlanningEngine syncPlanningEngine = new SyncPlanningEngine();

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

        syncJobService = new SyncJobService(
                jobRepository,
                operationRepository,
                syncExecutionEngine,
                effectiveAccessService
        );

        workspaceId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), UUID.randomUUID(),
                ProviderResourceType.PROJECT, "prj_async", "Async App", "production", "{}", true
        );

        when(jobRepository.save(any(SyncJob.class))).thenAnswer(i -> {
            SyncJob j = i.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        when(operationRepository.save(any(SyncOperation.class))).thenAnswer(i -> {
            SyncOperation op = i.getArgument(0);
            if (op.getId() == null) op.setId(UUID.randomUUID());
            return op;
        });
    }

    @Test
    @DisplayName("Sync Job Lifecycle: Partial failure tracks success and failure counters accurately")
    void testSyncJobPartialFailureTracking() {
        UUID sec1 = UUID.randomUUID();
        UUID sec2 = UUID.randomUUID();

        DesiredSecretState desired1 = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                sec1, "SEC_ONE", 1, "sha256:val1", true, mapping.getId(), Instant.now()
        );
        DesiredSecretState desired2 = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                sec2, "SEC_TWO", 1, "sha256:val2", true, mapping.getId(), Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(desired1, desired2)));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Secret 1 succeeds, Secret 2 fails
        when(providerSecretSyncService.pushSecretToProvider(eq(workspaceId), eq(integrationId), eq(mapping.getId()), eq(sec1), eq(actorUserId)))
                .thenReturn(PushSecretToProviderResponse.success(sec1, "SEC_ONE", ProviderType.VERCEL, "prj_async", "production", "sec_1", "CREATE"));
        when(providerSecretSyncService.pushSecretToProvider(eq(workspaceId), eq(integrationId), eq(mapping.getId()), eq(sec2), eq(actorUserId)))
                .thenReturn(PushSecretToProviderResponse.failure(sec2, "SEC_TWO", ProviderType.VERCEL, "prj_async", "production", ProviderErrorCode.PROVIDER_UNAVAILABLE, "Server error (500)"));

        when(mappingRepository.findById(mapping.getId())).thenReturn(Optional.of(mapping));

        SyncExecutionResponse response = syncJobService.triggerSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        assertThat(response.job().status()).isEqualTo(SyncJobStatus.PARTIAL);
        assertThat(response.successfulOperations()).isEqualTo(1);
        assertThat(response.failedOperations()).isEqualTo(1);
        assertThat(response.operations()).hasSize(2);
    }
}

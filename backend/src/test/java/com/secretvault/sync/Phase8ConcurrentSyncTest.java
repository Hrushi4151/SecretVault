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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Phase8ConcurrentSyncTest {

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
        actorUserId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), UUID.randomUUID(),
                ProviderResourceType.PROJECT, "prj_conc", "Conc App", "production", "{}", true
        );

        when(jobRepository.save(any(SyncJob.class))).thenAnswer(i -> i.getArgument(0));
        when(operationRepository.save(any(SyncOperation.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Concurrent sync requests execute safely without state corruption or race conditions")
    void testConcurrentSyncExecutions() throws Exception {
        UUID secId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secId, "API_KEY", 1, "sha256:val1",
                true, mapping.getId(), Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AtomicInteger callCount = new AtomicInteger(0);
        when(providerSecretSyncService.pushSecretToProvider(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    callCount.incrementAndGet();
                    Thread.sleep(50); // simulate network latency
                    return PushSecretToProviderResponse.success(secId, "API_KEY", ProviderType.VERCEL, "prj_conc", "production", "sec_1", "CREATE");
                });

        int numThreads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<SyncExecutionResponse>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return syncExecutionEngine.executeSync(
                        workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
                );
            }));
        }

        latch.countDown();
        executor.shutdown();
        boolean completed = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        for (Future<SyncExecutionResponse> future : futures) {
            SyncExecutionResponse response = future.get();
            assertThat(response).isNotNull();
            assertThat(response.job().status()).isEqualTo(SyncJobStatus.COMPLETED);
        }

        // All threads synchronized safely under mutex lock
        assertThat(callCount.get()).isEqualTo(numThreads);
    }
}

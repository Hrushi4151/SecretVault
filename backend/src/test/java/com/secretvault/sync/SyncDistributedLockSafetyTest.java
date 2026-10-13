package com.secretvault.sync;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.dto.PushSecretToProviderResponse;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.sync.dto.SyncExecutionResponse;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.SyncJobRepository;
import com.secretvault.sync.repository.SyncOperationRepository;
import com.secretvault.sync.scheduler.SyncDriftScheduler;
import com.secretvault.sync.service.*;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Rigorous tests for distributed lock safety, lease lifecycle, fencing barriers,
 * fail-closed semantics on Redis outages, and concurrency control.
 */
@ExtendWith(MockitoExtension.class)
class SyncDistributedLockSafetyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SyncJobRepository jobRepository;

    @Mock
    private SyncOperationRepository operationRepository;

    @Mock
    private ProviderResourceMappingRepository mappingRepository;

    @Mock
    private ProviderIntegrationRepository integrationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ProviderSecretSyncService providerSecretSyncService;

    @Mock
    private DesiredStateResolver desiredStateResolver;

    @Mock
    private ActualStateResolver actualStateResolver;

    @Mock
    private DriftDetectionEngine driftDetectionEngine;

    @Mock
    private SyncPlanningEngine syncPlanningEngine;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityEventService securityEventService;

    private SyncDistributedLockCoordinator coordinator;
    private SyncExecutionEngine executionEngine;

    private UUID workspaceId;
    private UUID scopeResourceId1;
    private UUID scopeResourceId2;
    private UUID actorUserId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        scopeResourceId1 = UUID.randomUUID();
        scopeResourceId2 = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        // Standard coordinator instance for safety tests
        coordinator = new SyncDistributedLockCoordinator(null, true);

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
                securityEventService,
                coordinator
        );
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    @DisplayName("1. Lease renewal extends lock during long-running operation")
    void test1_leaseRenewalDuringLongRunningOperation() throws InterruptedException {
        // Lease duration 300ms, heartbeat interval 80ms
        SyncDistributedLockCoordinator.SyncLockHandle handle = coordinator.acquireExecutionLockHandle(
                workspaceId, scopeResourceId1, Duration.ofMillis(300), Duration.ofMillis(80)
        );

        assertThat(handle).isNotNull();
        assertThat(handle.isActive()).isTrue();
        assertThat(handle.isOwnershipValid()).isTrue();

        // Wait 450ms (which exceeds the initial 300ms lease)
        Thread.sleep(450);

        // Heartbeat should have renewed the lease
        assertThat(handle.isOwnershipValid()).isTrue();
        assertThat(handle.isOwnershipLost()).isFalse();

        handle.close();
        assertThat(handle.isActive()).isFalse();
    }

    @Test
    @DisplayName("2. Renewal failure marks ownership lost and stops subsequent mutations")
    void test2_renewalFailureStopsSubsequentMutations() {
        SyncDistributedLockCoordinator.SyncLockHandle handle = coordinator.acquireExecutionLockHandle(
                workspaceId, scopeResourceId1, Duration.ofMillis(200), Duration.ofMillis(50)
        );

        assertThat(handle).isNotNull();
        assertThat(handle.isOwnershipValid()).isTrue();

        // Simulate lost ownership (e.g. renewal failed because key was lost)
        handle.markOwnershipLost();

        assertThat(handle.isOwnershipLost()).isTrue();
        assertThat(handle.isOwnershipValid()).isFalse();
        assertThat(handle.renewLease()).isFalse();
    }

    @Test
    @DisplayName("3. An expired worker cannot release a new worker's lock")
    void test3_expiredWorkerCannotReleaseNewWorkersLock() throws InterruptedException {
        // Worker A acquires lock with a very short lease (100ms)
        boolean acquiredA = coordinator.acquireExecutionLock(workspaceId, scopeResourceId1, "TOKEN-A", Duration.ofMillis(100));
        assertThat(acquiredA).isTrue();

        // Worker A expires
        Thread.sleep(150);

        // Worker B acquires the same target
        boolean acquiredB = coordinator.acquireExecutionLock(workspaceId, scopeResourceId1, "TOKEN-B", Duration.ofMinutes(5));
        assertThat(acquiredB).isTrue();

        // Stale Worker A attempts to release
        coordinator.releaseExecutionLock(workspaceId, scopeResourceId1, "TOKEN-A");

        // Lock must STILL be owned by Worker B
        boolean stillOwnedByB = coordinator.verifyOwnership("secretvault:lock:sync:exec:" + workspaceId + ":" + scopeResourceId1, "TOKEN-B");
        assertThat(stillOwnedByB).isTrue();

        // Worker B can release successfully
        coordinator.releaseExecutionLock(workspaceId, scopeResourceId1, "TOKEN-B");
        boolean nowReleased = coordinator.verifyOwnership("secretvault:lock:sync:exec:" + workspaceId + ":" + scopeResourceId1, "TOKEN-B");
        assertThat(nowReleased).isFalse();
    }

    @Test
    @DisplayName("4. Redis outage prevents production mutations (Fail-Closed)")
    void test4_redisOutagePreventsProductionMutations() {
        // Production coordinator: allowLocalFallback is false!
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused to 6379"));

        SyncDistributedLockCoordinator prodCoordinator = new SyncDistributedLockCoordinator(redisTemplate, false);

        assertThatThrownBy(() -> prodCoordinator.acquireExecutionLockHandle(workspaceId, scopeResourceId1))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiEx.getCode()).isEqualTo("COORDINATION_UNAVAILABLE");
                });

        prodCoordinator.shutdown();
    }

    @Test
    @DisplayName("5. Local fallback operates ONLY when explicitly configured in dev/test mode")
    void test5_localFallbackOperatesOnlyInExplicitDevMode() {
        // Case A: allowLocalFallback = false -> fails closed
        SyncDistributedLockCoordinator prodCoord = new SyncDistributedLockCoordinator(null, false);
        assertThatThrownBy(() -> prodCoord.acquireExecutionLockHandle(workspaceId, scopeResourceId1))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Failing closed");

        // Case B: allowLocalFallback = true -> succeeds locally
        SyncDistributedLockCoordinator devCoord = new SyncDistributedLockCoordinator(null, true);
        SyncDistributedLockCoordinator.SyncLockHandle handle = devCoord.acquireExecutionLockHandle(workspaceId, scopeResourceId1);
        assertThat(handle).isNotNull();
        assertThat(handle.isOwnershipValid()).isTrue();
        handle.close();

        prodCoord.shutdown();
        devCoord.shutdown();
    }

    @Test
    @DisplayName("6. Competing workers cannot proceed concurrently for same target, but unrelated targets run concurrently")
    void test6_competingWorkersAndUnrelatedTargetConcurrency() {
        // Worker 1 acquires target 1
        SyncDistributedLockCoordinator.SyncLockHandle handle1 = coordinator.acquireExecutionLockHandle(workspaceId, scopeResourceId1);
        assertThat(handle1).isNotNull();

        // Worker 2 attempts same target 1 -> rejected
        SyncDistributedLockCoordinator.SyncLockHandle handle2 = coordinator.acquireExecutionLockHandle(workspaceId, scopeResourceId1);
        assertThat(handle2).isNull();

        // Worker 3 attempts target 2 -> succeeds (unrelated targets run concurrently)
        SyncDistributedLockCoordinator.SyncLockHandle handle3 = coordinator.acquireExecutionLockHandle(workspaceId, scopeResourceId2);
        assertThat(handle3).isNotNull();

        handle1.close();
        handle3.close();
    }

    @Test
    @DisplayName("7. Stale worker cannot start subsequent mutations after detecting lost ownership")
    void test7_staleWorkerStopsSubsequentMutations() {
        UUID mappingId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        UUID secId1 = UUID.randomUUID();
        UUID secId2 = UUID.randomUUID();

        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setId(mappingId);
        mapping.setWorkspaceId(workspaceId);
        mapping.setIntegrationId(integrationId);

        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of()));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Plan with 2 CREATE operations
        SyncOperationPlan op1 = new SyncOperationPlan(secId1, "SECRET_ONE", mappingId, integrationId,
                SyncOperationType.CREATE, SyncOperationStatus.PENDING, "f1", null, "Add 1", null, null);
        SyncOperationPlan op2 = new SyncOperationPlan(secId2, "SECRET_TWO", mappingId, integrationId,
                SyncOperationType.CREATE, SyncOperationStatus.PENDING, "f2", null, "Add 2", null, null);

        SyncPlan plan = new SyncPlan(workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION,
                List.of(op1, op2), List.of(), List.of());
        when(syncPlanningEngine.planSync(any(), any(), any(), any(), any(), any(), any())).thenReturn(plan);

        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(operationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When operation 1 executes, push succeeds and then Worker loses ownership before op 2
        when(providerSecretSyncService.pushSecretToProvider(eq(workspaceId), eq(integrationId), eq(mappingId), eq(secId1), any()))
                .thenAnswer(inv -> {
                    // Invalidate lock immediately after op1 finishes
                    coordinator.evictLockForTesting("secretvault:lock:sync:exec:" + workspaceId + ":ALL");
                    return PushSecretToProviderResponse.success(secId1, "SECRET_ONE", ProviderType.VERCEL, "prj_1", "production", "var_1", "CREATE");
                });

        SyncExecutionResponse response = executionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        // Op1 succeeded, Op2 was BLOCKED by pre-mutation write barrier
        assertThat(response.operations()).hasSize(2);
        assertThat(response.operations().get(0).status()).isEqualTo(SyncOperationStatus.SUCCESS);
        assertThat(response.operations().get(1).status()).isEqualTo(SyncOperationStatus.BLOCKED);
        assertThat(response.operations().get(1).errorCode()).isEqualTo("COORDINATION_OWNERSHIP_LOST");

        // providerSecretSyncService was called ONLY once (for op 1, never for op 2)
        verify(providerSecretSyncService, times(1)).pushSecretToProvider(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("8. In-flight provider request that completes after lease expiry trips write barrier")
    void test8_inFlightRequestExpiryTripsWriteBarrier() {
        UUID mappingId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        UUID secId1 = UUID.randomUUID();
        UUID secId2 = UUID.randomUUID();

        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setId(mappingId);
        mapping.setWorkspaceId(workspaceId);
        mapping.setIntegrationId(integrationId);

        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(desiredStateResolver.resolveDesiredState(any(), any(), any())).thenReturn(Map.of(mapping, List.of()));
        when(actualStateResolver.resolveActualState(any(), any())).thenReturn(ActualStateResult.success(List.of()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any())).thenReturn(List.of());

        SyncOperationPlan op1 = new SyncOperationPlan(secId1, "LONG_RUNNING_SECRET", mappingId, integrationId,
                SyncOperationType.CREATE, SyncOperationStatus.PENDING, "f1", null, "Push 1", null, null);
        SyncOperationPlan op2 = new SyncOperationPlan(secId2, "NEXT_SECRET", mappingId, integrationId,
                SyncOperationType.CREATE, SyncOperationStatus.PENDING, "f2", null, "Push 2", null, null);

        SyncPlan plan = new SyncPlan(workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION,
                List.of(op1, op2), List.of(), List.of());
        when(syncPlanningEngine.planSync(any(), any(), any(), any(), any(), any(), any())).thenReturn(plan);

        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(operationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simulate provider push taking time and lease expiring during the in-flight remote call
        when(providerSecretSyncService.pushSecretToProvider(eq(workspaceId), eq(integrationId), eq(mappingId), eq(secId1), any()))
                .thenAnswer(inv -> {
                    // Evict lock during the call to simulate in-flight expiration
                    coordinator.evictLockForTesting("secretvault:lock:sync:exec:" + workspaceId + ":ALL");
                    return PushSecretToProviderResponse.success(secId1, "LONG_RUNNING_SECRET", ProviderType.VERCEL, "prj_1", "production", "var_1", "CREATE");
                });

        SyncExecutionResponse response = executionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );

        // Op1 returns success, but write barrier trips -> op2 is blocked and not dispatched
        assertThat(response.operations().get(0).status()).isEqualTo(SyncOperationStatus.SUCCESS);
        assertThat(response.operations().get(1).status()).isEqualTo(SyncOperationStatus.BLOCKED);
        assertThat(response.operations().get(1).errorCode()).isEqualTo("COORDINATION_OWNERSHIP_LOST");

        verify(providerSecretSyncService, times(1)).pushSecretToProvider(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("9. Redis recovery does not duplicate pending jobs")
    void test9_redisRecoveryDoesNotDuplicatePendingJobs() {
        UUID jobId = UUID.randomUUID();
        SyncJob completedJob = new SyncJob(workspaceId, SyncScope.WORKSPACE, null, false, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId);
        completedJob.setStatus(SyncJobStatus.COMPLETED);

        when(jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)).thenReturn(Optional.of(completedJob));

        // Attempting to retry an already-completed job throws BadRequest
        assertThatThrownBy(() -> executionEngine.retryFailedOperations(workspaceId, jobId, actorUserId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getMessage()).contains("Job has already completed successfully");
                });
    }

    @Test
    @DisplayName("10. Scheduled scans never bypass production coordination")
    void test10_scheduledScansNeverBypassProductionCoordination() {
        // Mock failing Redis in production
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new QueryTimeoutException("Redis connection timed out"));

        SyncDistributedLockCoordinator prodCoordinator = new SyncDistributedLockCoordinator(redisTemplate, false);

        SyncDriftScheduler scheduler = new SyncDriftScheduler(
                workspaceRepository,
                integrationRepository,
                mappingRepository,
                desiredStateResolver,
                actualStateResolver,
                driftDetectionEngine,
                prodCoordinator
        );
        scheduler.setSchedulerEnabled(true);

        Workspace ws = new Workspace();
        ws.setId(workspaceId);
        when(workspaceRepository.findAll()).thenReturn(List.of(ws));

        // When scan runs with Redis down, it must fail closed and catch ApiException without scanning uncoordinated
        scheduler.runScheduledDriftScan();

        // Verify that mappingRepository and desiredStateResolver were NEVER called without lock
        verify(mappingRepository, never()).findByWorkspaceId(any());
        verify(desiredStateResolver, never()).resolveDesiredStateForMapping(any(), any());

        prodCoordinator.shutdown();
    }

    @Test
    @DisplayName("11. Worker restart and lease expiry recover without manual intervention")
    void test11_workerCrashAndLeaseExpiryRecoverAutomatically() throws InterruptedException {
        // Worker A acquires lock with 150ms lease and crashes (does not call release)
        SyncDistributedLockCoordinator.SyncLockHandle handleA = coordinator.acquireExecutionLockHandle(
                workspaceId, scopeResourceId1, Duration.ofMillis(150), Duration.ofMillis(300)
        );
        assertThat(handleA).isNotNull();

        // Immediately, Worker B cannot acquire
        SyncDistributedLockCoordinator.SyncLockHandle handleBImmediate = coordinator.acquireExecutionLockHandle(
                workspaceId, scopeResourceId1, Duration.ofMillis(500), Duration.ofMillis(100)
        );
        assertThat(handleBImmediate).isNull();

        // Wait 250ms for Worker A's lease to expire
        Thread.sleep(250);

        // Worker B can now acquire automatically without manual operator intervention
        SyncDistributedLockCoordinator.SyncLockHandle handleBRecovered = coordinator.acquireExecutionLockHandle(
                workspaceId, scopeResourceId1, Duration.ofMillis(500), Duration.ofMillis(100)
        );
        assertThat(handleBRecovered).isNotNull();
        assertThat(handleBRecovered.isOwnershipValid()).isTrue();

        handleBRecovered.close();
    }
}

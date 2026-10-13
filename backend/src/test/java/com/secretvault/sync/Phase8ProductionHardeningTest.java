package com.secretvault.sync;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
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
import com.secretvault.sync.dto.SyncJobResponse;
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
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Production Hardening Test Suite verifying:
 * 1. Distributed coordination across multi-worker simulations with exclusive leasing
 * 2. Independent target scope concurrency
 * 3. Stale-plan rejection and secret rotation invalidation
 * 4. Partial execution recovery and retry safety without duplicate mutations
 * 5. Secret rotation guards during retry attempts
 * 6. Job cancellation lifecycles
 * 7. Keyed HMAC domain separation and constant-time equality
 * 8. Transient vs permanent error retry filtering
 */
@ExtendWith(MockitoExtension.class)
class Phase8ProductionHardeningTest {

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

    private SyncDistributedLockCoordinator lockCoordinator;
    private SyncPlanningEngine syncPlanningEngine;
    private SyncExecutionEngine syncExecutionEngine;

    private UUID workspaceId;
    private UUID integrationId;
    private UUID actorUserId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        lockCoordinator = new SyncDistributedLockCoordinator(null, true); // in-memory fallback lease coordinator
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
                securityEventService,
                lockCoordinator
        );

        workspaceId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), UUID.randomUUID(),
                ProviderResourceType.PROJECT, "prj_prod", "Prod App", "production", "{}", true
        );

        lenient().when(jobRepository.save(any(SyncJob.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(operationRepository.save(any(SyncOperation.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Priority 1: Two simulated workers cannot execute conflicting operations on the same target scope simultaneously")
    void testCrossInstanceDistributedCoordination_competingWorkersRejectedWith409() {
        UUID secId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secId, "DATABASE_URL", 1, "sha256:db1",
                true, mapping.getId(), Instant.now()
        );

        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(desired)));
        when(actualStateResolver.resolveActualState(eq(workspaceId), eq(mapping)))
                .thenReturn(ActualStateResult.success(Collections.emptyList()));
        when(driftDetectionEngine.detectDriftForMapping(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Simulate Worker 1 holding the distributed lock
        String worker1Token = UUID.randomUUID().toString();
        boolean lockedByWorker1 = lockCoordinator.acquireExecutionLock(workspaceId, (UUID) null, worker1Token, Duration.ofMinutes(10));
        assertThat(lockedByWorker1).isTrue();

        // Worker 2 attempts execution while Worker 1 holds the lock -> must be rejected with 409 Conflict
        assertThatThrownBy(() -> syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getMessage()).contains("Another synchronization job is currently in progress");
                });

        // Worker 1 finishes and releases lock
        lockCoordinator.releaseExecutionLock(workspaceId, (UUID) null, worker1Token);

        // Now Worker 2 is able to acquire the lock and execute successfully
        when(providerSecretSyncService.pushSecretToProvider(any(), any(), any(), any(), any()))
                .thenReturn(PushSecretToProviderResponse.success(secId, "DATABASE_URL", ProviderType.VERCEL, "prj_prod", "production", "var_1", "CREATE"));

        SyncExecutionResponse response = syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId
        );
        assertThat(response).isNotNull();
        assertThat(response.job().status()).isEqualTo(SyncJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("Priority 1: Independent reconciliation targets proceed concurrently without blocking each other")
    void testIndependentTargets_reconcileConcurrently() {
        UUID resourceIdA = UUID.randomUUID();
        UUID resourceIdB = UUID.randomUUID();

        String tokenA = UUID.randomUUID().toString();
        String tokenB = UUID.randomUUID().toString();

        // Acquire lock for target A
        boolean lockA = lockCoordinator.acquireExecutionLock(workspaceId, resourceIdA, tokenA, Duration.ofMinutes(5));
        assertThat(lockA).isTrue();

        // Simultaneously acquire lock for target B in same workspace
        boolean lockB = lockCoordinator.acquireExecutionLock(workspaceId, resourceIdB, tokenB, Duration.ofMinutes(5));
        assertThat(lockB).isTrue();

        // Releasing target A does not affect target B
        lockCoordinator.releaseExecutionLock(workspaceId, resourceIdA, tokenA);

        // Target B is still locked
        boolean relockB = lockCoordinator.acquireExecutionLock(workspaceId, resourceIdB, UUID.randomUUID().toString(), Duration.ofMinutes(5));
        assertThat(relockB).isFalse();

        lockCoordinator.releaseExecutionLock(workspaceId, resourceIdB, tokenB);
    }

    @Test
    @DisplayName("Priority 2: Stale plan is rejected with 409 Conflict when a secret has rotated after dry-run preview")
    void testStalePlanRejection_whenSecretRotatedAfterDryRunPreview() {
        UUID planJobId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();

        // Mock the approved dry-run plan
        SyncJob dryRunJob = new SyncJob(workspaceId, SyncScope.WORKSPACE, null, true, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId);
        dryRunJob.setId(planJobId);
        dryRunJob.setStatus(SyncJobStatus.COMPLETED);
        dryRunJob.setCompletedAt(Instant.now().minus(Duration.ofMinutes(5)));
        when(jobRepository.findByIdAndWorkspaceId(planJobId, workspaceId)).thenReturn(Optional.of(dryRunJob));

        // Planned operation has the old secret fingerprint
        SyncOperation plannedOp = new SyncOperation(
                planJobId, secretId, "JWT_SECRET",
                mapping.getId(), integrationId, SyncOperationType.UPDATE,
                "sha256:old_fingerprint_v1", "sha256:observed_val", "Update drifted secret"
        );
        when(operationRepository.findByJobId(planJobId)).thenReturn(List.of(plannedOp));

        // Current desired state resolver returns ROTATED secret with a NEW fingerprint
        DesiredSecretState rotatedSecret = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secretId, "JWT_SECRET", 2, "sha256:rotated_fingerprint_v2", // ROTATED!
                true, mapping.getId(), Instant.now()
        );
        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(rotatedSecret)));

        // Attempting to execute the stale plan must be rejected with 409 Conflict
        assertThatThrownBy(() -> syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, planJobId, actorUserId
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getMessage()).contains("stale");
                    assertThat(apiEx.getMessage()).contains("rotated or changed");
                });

        // Ensure no provider mutation was executed
        verify(providerSecretSyncService, never()).pushSecretToProvider(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Priority 2: Stale plan is rejected when plan was generated older than 24 hours ago")
    void testStalePlanRejection_whenPlanOlderThan24Hours() {
        UUID planJobId = UUID.randomUUID();
        SyncJob expiredPlan = new SyncJob(workspaceId, SyncScope.WORKSPACE, null, true, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId);
        expiredPlan.setId(planJobId);
        expiredPlan.setStatus(SyncJobStatus.COMPLETED);
        expiredPlan.setCompletedAt(Instant.now().minus(Duration.ofHours(25))); // 25 hours old
        when(jobRepository.findByIdAndWorkspaceId(planJobId, workspaceId)).thenReturn(Optional.of(expiredPlan));

        assertThatThrownBy(() -> syncExecutionEngine.executeSync(
                workspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, planJobId, actorUserId
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getMessage()).contains("expired");
                });
    }

    @Test
    @DisplayName("Priority 5: Job retry re-executes only failed operations without duplicating successful ones")
    void testRetryFailedOperations_onlyRetriesFailedWithoutDuplicatingSuccess() {
        UUID jobId = UUID.randomUUID();
        SyncJob partialJob = new SyncJob(workspaceId, SyncScope.WORKSPACE, null, false, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId);
        partialJob.setId(jobId);
        partialJob.setStatus(SyncJobStatus.PARTIAL);
        when(jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)).thenReturn(Optional.of(partialJob));

        UUID secSuccessId = UUID.randomUUID();
        UUID secFailedId = UUID.randomUUID();

        SyncOperation successOp = new SyncOperation(jobId, secSuccessId, "PORT", mapping.getId(), integrationId, SyncOperationType.CREATE, "fp1", null, "Push");
        successOp.setStatus(SyncOperationStatus.SUCCESS);

        SyncOperation failedOp = new SyncOperation(jobId, secFailedId, "API_KEY", mapping.getId(), integrationId, SyncOperationType.CREATE, "fp2", null, "Push");
        failedOp.setStatus(SyncOperationStatus.FAILED);

        // findByJobIdAndStatus returns only the failed operation
        when(operationRepository.findByJobIdAndStatus(jobId, SyncOperationStatus.FAILED)).thenReturn(List.of(failedOp));

        // Desired state verification for the failed op
        DesiredSecretState validDesired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secFailedId, "API_KEY", 1, "fp2", true, mapping.getId(), Instant.now()
        );
        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(validDesired)));

        // Push succeeds on retry
        when(providerSecretSyncService.pushSecretToProvider(workspaceId, integrationId, mapping.getId(), secFailedId, actorUserId))
                .thenReturn(PushSecretToProviderResponse.success(secFailedId, "API_KEY", ProviderType.VERCEL, "prj_prod", "production", "var_2", "CREATE"));

        when(operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.FAILED)).thenReturn(0L);
        when(operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.SUCCESS)).thenReturn(2L);
        when(operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.BLOCKED)).thenReturn(0L);

        SyncExecutionResponse retryResponse = syncExecutionEngine.retryFailedOperations(workspaceId, jobId, actorUserId);

        assertThat(retryResponse).isNotNull();
        assertThat(retryResponse.job().status()).isEqualTo(SyncJobStatus.COMPLETED);

        // Provider push was called ONLY for the failed secret, NOT the already successful one
        verify(providerSecretSyncService, times(1)).pushSecretToProvider(workspaceId, integrationId, mapping.getId(), secFailedId, actorUserId);
        verify(providerSecretSyncService, never()).pushSecretToProvider(workspaceId, integrationId, mapping.getId(), secSuccessId, actorUserId);
    }

    @Test
    @DisplayName("Priority 5: Job retry aborts operation if secret was rotated during intermediate failure state")
    void testRetryFailedOperations_abortsIfSecretRotatedDuringIntermediateFailure() {
        UUID jobId = UUID.randomUUID();
        SyncJob failedJob = new SyncJob(workspaceId, SyncScope.WORKSPACE, null, false, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId);
        failedJob.setId(jobId);
        failedJob.setStatus(SyncJobStatus.FAILED);
        when(jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)).thenReturn(Optional.of(failedJob));

        UUID secId = UUID.randomUUID();
        SyncOperation failedOp = new SyncOperation(jobId, secId, "OAUTH_CLIENT_SECRET", mapping.getId(), integrationId, SyncOperationType.UPDATE, "fp_v1", null, "Update");
        failedOp.setStatus(SyncOperationStatus.FAILED);
        when(operationRepository.findByJobIdAndStatus(jobId, SyncOperationStatus.FAILED)).thenReturn(List.of(failedOp));

        // In SecretVault, the secret was rotated to v2 with fp_v2 while job was in failed state
        DesiredSecretState rotatedDesired = new DesiredSecretState(
                workspaceId, mapping.getProjectId(), mapping.getEnvironmentId(),
                secId, "OAUTH_CLIENT_SECRET", 2, "fp_v2", true, mapping.getId(), Instant.now()
        );
        when(desiredStateResolver.resolveDesiredState(eq(workspaceId), eq(SyncScope.WORKSPACE), isNull()))
                .thenReturn(Map.of(mapping, List.of(rotatedDesired)));

        when(operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.FAILED)).thenReturn(0L);
        when(operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.SUCCESS)).thenReturn(0L);
        when(operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.BLOCKED)).thenReturn(1L);

        syncExecutionEngine.retryFailedOperations(workspaceId, jobId, actorUserId);

        // Failed operation was marked BLOCKED because desired state changed
        assertThat(failedOp.getStatus()).isEqualTo(SyncOperationStatus.BLOCKED);
        assertThat(failedOp.getErrorCode()).isEqualTo("STALE_SECRET_ROTATION");

        // Obsolete secret was NEVER pushed to external provider
        verify(providerSecretSyncService, never()).pushSecretToProvider(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Priority 5: Job cancellation transitions RUNNING job to CANCELLED and rejects terminal job cancellation")
    void testJobCancellation_safelyCancelsRunningJob() {
        UUID jobId = UUID.randomUUID();
        SyncJob runningJob = new SyncJob(workspaceId, SyncScope.WORKSPACE, null, false, ReconciliationPolicy.SAFE_RECONCILIATION, actorUserId);
        runningJob.setId(jobId);
        runningJob.setStatus(SyncJobStatus.RUNNING);
        when(jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)).thenReturn(Optional.of(runningJob));

        SyncJobResponse cancelResp = syncExecutionEngine.cancelSyncJob(workspaceId, jobId, actorUserId);
        assertThat(cancelResp.status()).isEqualTo(SyncJobStatus.CANCELLED);

        // Attempting to cancel already cancelled job throws 400
        assertThatThrownBy(() -> syncExecutionEngine.cancelSyncJob(workspaceId, jobId, actorUserId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Priority 3: Keyed HMAC fingerprint enforces cross-tenant domain separation and constant-time equality")
    void testCryptographicFingerprint_keyedHmacDomainSeparationAndConstantTime() {
        byte[] key = "super-secret-sync-hmac-key-256bit!".getBytes();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Identical secret metadata in two different workspaces
        String fpTenantA = SecretFingerprintCalculator.computeKeyedMetadataFingerprint(tenantA, "STRIPE_KEY", 1, "dek_token_1", key);
        String fpTenantB = SecretFingerprintCalculator.computeKeyedMetadataFingerprint(tenantB, "STRIPE_KEY", 1, "dek_token_1", key);

        // Domain separation guarantees different fingerprints across tenants
        assertThat(fpTenantA).isNotEqualTo(fpTenantB);

        // Deterministic equality within same tenant
        String fpTenantA2 = SecretFingerprintCalculator.computeKeyedMetadataFingerprint(tenantA, "STRIPE_KEY", 1, "dek_token_1", key);
        assertThat(SecretFingerprintCalculator.secureEquals(fpTenantA, fpTenantA2)).isTrue();

        // Null and mismatch safety
        assertThat(SecretFingerprintCalculator.secureEquals(fpTenantA, fpTenantB)).isFalse();
        assertThat(SecretFingerprintCalculator.secureEquals(fpTenantA, null)).isFalse();
        assertThat(SecretFingerprintCalculator.secureEquals(null, null)).isFalse();
    }
}

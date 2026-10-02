package com.secretvault.sync.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.dto.PushSecretToProviderResponse;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.sync.dto.*;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.SyncJobRepository;
import com.secretvault.sync.repository.SyncOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Robust execution engine for live synchronization and dry-run drift simulation.
 * Protects against race conditions via concurrency locks, safely handles partial failures,
 * enforces bounded retries on rate-limits, and eliminates secret plaintext at all layers.
 */
@Service
public class SyncExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(SyncExecutionEngine.class);

    private final SyncJobRepository jobRepository;
    private final SyncOperationRepository operationRepository;
    private final ProviderResourceMappingRepository mappingRepository;
    private final ProviderSecretSyncService providerSecretSyncService;
    private final DesiredStateResolver desiredStateResolver;
    private final ActualStateResolver actualStateResolver;
    private final DriftDetectionEngine driftDetectionEngine;
    private final SyncPlanningEngine syncPlanningEngine;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;

    // Per-mapping concurrency locks to prevent overlapping modifications
    private final ConcurrentHashMap<String, Object> executionLocks = new ConcurrentHashMap<>();

    public SyncExecutionEngine(
            SyncJobRepository jobRepository,
            SyncOperationRepository operationRepository,
            ProviderResourceMappingRepository mappingRepository,
            ProviderSecretSyncService providerSecretSyncService,
            DesiredStateResolver desiredStateResolver,
            ActualStateResolver actualStateResolver,
            DriftDetectionEngine driftDetectionEngine,
            SyncPlanningEngine syncPlanningEngine,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            SecurityEventService securityEventService
    ) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        this.operationRepository = Objects.requireNonNull(operationRepository, "operationRepository must not be null");
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.providerSecretSyncService = Objects.requireNonNull(providerSecretSyncService, "providerSecretSyncService must not be null");
        this.desiredStateResolver = Objects.requireNonNull(desiredStateResolver, "desiredStateResolver must not be null");
        this.actualStateResolver = Objects.requireNonNull(actualStateResolver, "actualStateResolver must not be null");
        this.driftDetectionEngine = Objects.requireNonNull(driftDetectionEngine, "driftDetectionEngine must not be null");
        this.syncPlanningEngine = Objects.requireNonNull(syncPlanningEngine, "syncPlanningEngine must not be null");
        this.effectiveAccessService = Objects.requireNonNull(effectiveAccessService, "effectiveAccessService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.securityEventService = Objects.requireNonNull(securityEventService, "securityEventService must not be null");
    }

    /**
     * Executes dry-run simulation: resolves desired & actual states, detects drift,
     * builds a safe sync plan, and persists audit trail without altering external provider state.
     */
    @Transactional
    public DryRunResponse executeDryRun(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy policy,
            UUID actorUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SYNC_DRY_RUN, actorUserId
        );

        SyncScope effectiveScope = scope != null ? scope : SyncScope.WORKSPACE;
        ReconciliationPolicy effectivePolicy = policy != null ? policy : ReconciliationPolicy.SAFE_RECONCILIATION;

        // 1. Resolve Desired State
        Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap =
                desiredStateResolver.resolveDesiredState(workspaceId, effectiveScope, scopeResourceId);

        // 2. Resolve Actual State & Detect Drift
        Map<UUID, ActualStateResult> actualMap = new HashMap<>();
        List<DriftRecord> allDrifts = new ArrayList<>();

        for (Map.Entry<ProviderResourceMapping, List<DesiredSecretState>> entry : desiredMap.entrySet()) {
            ProviderResourceMapping mapping = entry.getKey();
            List<DesiredSecretState> desiredStates = entry.getValue();

            ActualStateResult actualResult = actualStateResolver.resolveActualState(workspaceId, mapping);
            actualMap.put(mapping.getId(), actualResult);

            List<DriftRecord> mappingDrifts = driftDetectionEngine.detectDriftForMapping(
                    workspaceId, mapping, desiredStates, actualResult, actorUserId
            );
            allDrifts.addAll(mappingDrifts);
        }

        // 3. Build Plan
        SyncPlan plan = syncPlanningEngine.planSync(
                workspaceId, effectiveScope, scopeResourceId, effectivePolicy,
                desiredMap, actualMap, allDrifts
        );

        // 4. Record Dry-Run Job
        Instant now = Instant.now();
        SyncJob job = new SyncJob(
                workspaceId, effectiveScope, scopeResourceId, true,
                effectivePolicy, actorUserId
        );
        job.setStatus(SyncJobStatus.COMPLETED);
        job.setStartedAt(now);
        job.setCompletedAt(now);
        job.setTotalOperations(plan.totalOperations());
        job.setBlockedOperations(plan.countByType(SyncOperationType.BLOCKED) + plan.countByType(SyncOperationType.NO_OP));
        job.setDriftCount(allDrifts.size());
        SyncJob savedJob = jobRepository.save(job);

        // 5. Save Planned Operations
        List<SyncOperationResponse> opResponses = new ArrayList<>();
        for (SyncOperationPlan opPlan : plan.operations()) {
            SyncOperation op = new SyncOperation(
                    savedJob.getId(), opPlan.secretId(), opPlan.secretName(),
                    opPlan.mappingId(), opPlan.integrationId(),
                    opPlan.operationType(), opPlan.desiredFingerprint(),
                    opPlan.observedFingerprint(), opPlan.reason()
            );
            op.setStatus(opPlan.initialStatus());
            op.setErrorCode(opPlan.errorCode());
            op.setErrorMessage(opPlan.errorMessage());
            SyncOperation savedOp = operationRepository.save(op);
            opResponses.add(SyncOperationResponse.fromEntity(savedOp));
        }

        // 6. Audit
        auditService.recordAudit(
                null, workspaceId, actorUserId, "USER",
                AuditAction.SYNC_DRY_RUN_EXECUTED, "SYNC_JOB",
                savedJob.getId(), null, null, "SUCCESS"
        );

        List<DriftRecordResponse> driftResponses = allDrifts.stream()
                .map(DriftRecordResponse::fromEntity)
                .toList();

        return new DryRunResponse(
                savedJob.getId(),
                workspaceId,
                effectiveScope,
                scopeResourceId,
                effectivePolicy,
                plan.totalOperations(),
                plan.countByType(SyncOperationType.CREATE),
                plan.countByType(SyncOperationType.UPDATE),
                plan.countByType(SyncOperationType.DELETE),
                plan.countByType(SyncOperationType.NO_OP),
                plan.countByType(SyncOperationType.BLOCKED) + plan.countByType(SyncOperationType.ERROR),
                allDrifts.size(),
                opResponses,
                driftResponses,
                plan.warnings()
        );
    }

    /**
     * Executes live synchronization: acquires mapping lock, applies mutations to external provider,
     * updates job lifecycle, and triggers post-sync targeted drift verification.
     */
    public SyncExecutionResponse executeSync(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy policy,
            UUID actorUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SYNC_EXECUTE, actorUserId
        );

        SyncScope effectiveScope = scope != null ? scope : SyncScope.WORKSPACE;
        ReconciliationPolicy effectivePolicy = policy != null ? policy : ReconciliationPolicy.SAFE_RECONCILIATION;

        if (effectivePolicy == ReconciliationPolicy.DETECT_ONLY) {
            throw ApiException.badRequest("Cannot execute live sync with DETECT_ONLY policy. Use dry-run or SAFE_RECONCILIATION.");
        }

        String lockKey = workspaceId.toString() + ":" + (scopeResourceId != null ? scopeResourceId.toString() : "ALL");
        Object lock = executionLocks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            try {
                // 1. Resolve Desired State
                Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap =
                        desiredStateResolver.resolveDesiredState(workspaceId, effectiveScope, scopeResourceId);

                // 2. Resolve Actual State & Drift
                Map<UUID, ActualStateResult> actualMap = new HashMap<>();
                List<DriftRecord> initialDrifts = new ArrayList<>();

                for (Map.Entry<ProviderResourceMapping, List<DesiredSecretState>> entry : desiredMap.entrySet()) {
                    ProviderResourceMapping mapping = entry.getKey();
                    List<DesiredSecretState> desiredStates = entry.getValue();

                    ActualStateResult actualResult = actualStateResolver.resolveActualState(workspaceId, mapping);
                    actualMap.put(mapping.getId(), actualResult);

                    List<DriftRecord> mappingDrifts = driftDetectionEngine.detectDriftForMapping(
                            workspaceId, mapping, desiredStates, actualResult, actorUserId
                    );
                    initialDrifts.addAll(mappingDrifts);
                }

                // 3. Build Plan
                SyncPlan plan = syncPlanningEngine.planSync(
                        workspaceId, effectiveScope, scopeResourceId, effectivePolicy,
                        desiredMap, actualMap, initialDrifts
                );

                // 4. Create SyncJob
                Instant startTime = Instant.now();
                SyncJob job = new SyncJob(
                        workspaceId, effectiveScope, scopeResourceId, false,
                        effectivePolicy, actorUserId
                );
                job.setStatus(SyncJobStatus.RUNNING);
                job.setStartedAt(startTime);
                job.setTotalOperations(plan.totalOperations());
                job.setDriftCount(initialDrifts.size());
                SyncJob savedJob = jobRepository.save(job);

                // Telemetry for Sync Started
                securityEventService.recordEvent(
                        workspaceId, null, null,
                        actorUserId, SecurityEventType.SYNC_STARTED,
                        SecurityEventSeverity.INFO, SecurityEventOutcome.SUCCESS,
                        "SYNC_ENGINE", null, null, null,
                        Map.of(
                                "jobId", savedJob.getId().toString(),
                                "scope", effectiveScope.name(),
                                "totalOperations", String.valueOf(plan.totalOperations())
                        )
                );

                auditService.recordAudit(
                        null, workspaceId, actorUserId, "USER",
                        AuditAction.SYNC_REQUESTED, "SYNC_JOB",
                        savedJob.getId(), null, null, "SUCCESS"
                );

                // 5. Execute Operations with rate limit backoff and safe failure handling
                int successCount = 0;
                int failureCount = 0;
                int blockedCount = 0;
                List<SyncOperationResponse> opResponses = new ArrayList<>();
                Set<ProviderResourceMapping> affectedMappings = new HashSet<>();

                for (SyncOperationPlan opPlan : plan.operations()) {
                    SyncOperation op = new SyncOperation(
                            savedJob.getId(), opPlan.secretId(), opPlan.secretName(),
                            opPlan.mappingId(), opPlan.integrationId(),
                            opPlan.operationType(), opPlan.desiredFingerprint(),
                            opPlan.observedFingerprint(), opPlan.reason()
                    );

                    if (opPlan.operationType() == SyncOperationType.NO_OP) {
                        op.setStatus(SyncOperationStatus.SKIPPED);
                        blockedCount++;
                    } else if (opPlan.operationType() == SyncOperationType.BLOCKED || opPlan.operationType() == SyncOperationType.ERROR) {
                        op.setStatus(SyncOperationStatus.BLOCKED);
                        op.setErrorCode(opPlan.errorCode());
                        op.setErrorMessage(opPlan.errorMessage());
                        blockedCount++;
                    } else if (opPlan.operationType() == SyncOperationType.CREATE || opPlan.operationType() == SyncOperationType.UPDATE) {
                        // Execute push to provider
                        boolean opSuccess = executePushWithRetry(
                                workspaceId, opPlan.integrationId(), opPlan.mappingId(),
                                opPlan.secretId(), actorUserId, op
                        );
                        if (opSuccess) {
                            successCount++;
                            mappingRepository.findById(opPlan.mappingId()).ifPresent(affectedMappings::add);
                        } else {
                            failureCount++;
                        }
                    }

                    SyncOperation savedOp = operationRepository.save(op);
                    opResponses.add(SyncOperationResponse.fromEntity(savedOp));
                }

                // 6. Post-sync Targeted Drift Verification
                for (ProviderResourceMapping mapping : affectedMappings) {
                    try {
                        List<DesiredSecretState> desiredStates = desiredStateResolver.resolveDesiredStateForMapping(workspaceId, mapping);
                        ActualStateResult actualResult = actualStateResolver.resolveActualState(workspaceId, mapping);
                        driftDetectionEngine.detectDriftForMapping(workspaceId, mapping, desiredStates, actualResult, actorUserId);
                    } catch (Exception e) {
                        log.warn("Post-sync drift verification failed for mapping [{}]: {}", mapping.getId(), e.getMessage());
                    }
                }

                // 7. Finalize Job Status
                Instant completionTime = Instant.now();
                savedJob.setCompletedAt(completionTime);
                savedJob.setSuccessfulOperations(successCount);
                savedJob.setFailedOperations(failureCount);
                savedJob.setBlockedOperations(blockedCount);

                if (failureCount == 0) {
                    savedJob.setStatus(SyncJobStatus.COMPLETED);
                } else if (successCount > 0) {
                    savedJob.setStatus(SyncJobStatus.PARTIAL);
                    savedJob.setErrorSummary(failureCount + " operations failed during synchronization");
                } else {
                    savedJob.setStatus(SyncJobStatus.FAILED);
                    savedJob.setErrorSummary("All attempted operations failed during synchronization");
                }

                SyncJob finalizedJob = jobRepository.save(savedJob);

                // Telemetry & Audit
                SecurityEventType eventType = finalizedJob.getStatus() == SyncJobStatus.FAILED ?
                        SecurityEventType.SYNC_FAILED : SecurityEventType.SYNC_COMPLETED;
                SecurityEventOutcome outcome = finalizedJob.getStatus() == SyncJobStatus.FAILED ?
                        SecurityEventOutcome.FAILURE : SecurityEventOutcome.SUCCESS;
                SecurityEventSeverity eventSeverity = finalizedJob.getStatus() == SyncJobStatus.FAILED ?
                        SecurityEventSeverity.HIGH : SecurityEventSeverity.INFO;

                securityEventService.recordEvent(
                        workspaceId, null, null,
                        actorUserId, eventType,
                        eventSeverity, outcome,
                        "SYNC_ENGINE", null, null, null,
                        Map.of(
                                "jobId", finalizedJob.getId().toString(),
                                "status", finalizedJob.getStatus().name(),
                                "successfulOperations", String.valueOf(successCount),
                                "failedOperations", String.valueOf(failureCount)
                        )
                );

                auditService.recordAudit(
                        null, workspaceId, actorUserId, "USER",
                        finalizedJob.getStatus() == SyncJobStatus.FAILED ? AuditAction.SYNC_FAILED : AuditAction.SYNC_COMPLETED,
                        "SYNC_JOB", finalizedJob.getId(), null, null,
                        finalizedJob.getStatus() == SyncJobStatus.FAILED ? "FAILURE" : "SUCCESS"
                );

                return SyncExecutionResponse.of(
                        finalizedJob,
                        opResponses,
                        "Synchronization executed: " + successCount + " succeeded, " + failureCount + " failed, " + blockedCount + " skipped/blocked."
                );
            } finally {
                executionLocks.remove(lockKey);
            }
        }
    }

    private boolean executePushWithRetry(
            UUID workspaceId,
            UUID integrationId,
            UUID mappingId,
            UUID secretId,
            UUID actorUserId,
            SyncOperation op
    ) {
        int maxRetries = 3;
        long backoffMs = 500;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                PushSecretToProviderResponse response = providerSecretSyncService.pushSecretToProvider(
                        workspaceId, integrationId, mappingId, secretId, actorUserId
                );

                if (response.success()) {
                    op.setStatus(SyncOperationStatus.SUCCESS);
                    op.setExecutedAt(Instant.now());
                    return true;
                }

                // If rate limited, perform bounded backoff
                if (response.errorCode() == ProviderErrorCode.PROVIDER_RATE_LIMITED && attempt < maxRetries) {
                    try {
                        Thread.sleep(backoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                op.setStatus(SyncOperationStatus.FAILED);
                op.setErrorCode(response.errorCode() != null ? response.errorCode().name() : "PROVIDER_ERROR");
                op.setErrorMessage(response.errorMessage());
                op.setExecutedAt(Instant.now());
                return false;
            } catch (Exception e) {
                log.warn("Push secret failed for secret [{}] on attempt [{}]: {}", secretId, attempt, e.getMessage());
                if (attempt == maxRetries) {
                    op.setStatus(SyncOperationStatus.FAILED);
                    op.setErrorCode("EXECUTION_EXCEPTION");
                    op.setErrorMessage(e.getMessage());
                    op.setExecutedAt(Instant.now());
                    return false;
                }
            }
        }
        return false;
    }
}

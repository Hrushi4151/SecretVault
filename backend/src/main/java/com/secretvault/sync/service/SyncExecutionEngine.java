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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Robust execution engine for live synchronization, dry-run drift simulation,
 * and recoverable retry orchestration.
 * Protects against race conditions via distributed leases, verifies plan freshness
 * against secret rotation, safely handles partial failures, enforces bounded retries
 * on transient provider errors, and eliminates secret plaintext at all layers.
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
    private final SyncDistributedLockCoordinator lockCoordinator;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.secretvault.events.publisher.EventPublisher eventPublisher;

    public void setEventPublisher(com.secretvault.events.publisher.EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @org.springframework.beans.factory.annotation.Autowired
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
            SecurityEventService securityEventService,
            SyncDistributedLockCoordinator lockCoordinator
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
        this.lockCoordinator = Objects.requireNonNull(lockCoordinator, "lockCoordinator must not be null");
    }

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
        this(jobRepository, operationRepository, mappingRepository, providerSecretSyncService,
                desiredStateResolver, actualStateResolver, driftDetectionEngine, syncPlanningEngine,
                effectiveAccessService, auditService, securityEventService,
                new SyncDistributedLockCoordinator(null, true));
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
     * Executes live synchronization without plan binding.
     */
    public SyncExecutionResponse executeSync(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy policy,
            UUID actorUserId
    ) {
        return executeSync(workspaceId, scope, scopeResourceId, policy, null, actorUserId);
    }

    /**
     * Executes live synchronization: acquires cross-instance distributed lock, validates plan freshness,
     * protects against secret rotation, applies mutations to external provider, and verifies post-sync state.
     */
    public SyncExecutionResponse executeSync(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy policy,
            UUID planJobId,
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

        // Validate plan metadata and expiration if planJobId is provided
        if (planJobId != null) {
            SyncJob planJob = jobRepository.findByIdAndWorkspaceId(planJobId, workspaceId)
                    .orElseThrow(() -> ApiException.badRequest("Specified dry-run plan [" + planJobId + "] does not exist in workspace."));
            if (!planJob.isDryRun()) {
                throw ApiException.badRequest("Job [" + planJobId + "] is not a dry-run plan.");
            }
            if (planJob.getCompletedAt() == null || planJob.getCompletedAt().isBefore(Instant.now().minus(Duration.ofHours(24)))) {
                throw ApiException.conflict("The approved plan has expired (older than 24 hours). Please generate a fresh plan.");
            }
            if (planJob.getScope() != effectiveScope || !Objects.equals(planJob.getScopeResourceId(), scopeResourceId)) {
                throw ApiException.badRequest("Dry-run plan scope [" + planJob.getScope() + "] does not match execution scope [" + effectiveScope + "].");
            }
        }

        String scopeKey = scopeResourceId != null ? scopeResourceId.toString() : "ALL";
        SyncDistributedLockCoordinator.SyncLockHandle lockHandle =
                lockCoordinator.acquireExecutionLockHandle(workspaceId, scopeResourceId);
        if (lockHandle == null) {
            throw ApiException.conflict("Another synchronization job is currently in progress for workspace [" + workspaceId + "] and target [" + scopeKey + "].");
        }

        try {
            // 1. Resolve Desired State
            Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap =
                    desiredStateResolver.resolveDesiredState(workspaceId, effectiveScope, scopeResourceId);

            // 2. Validate against stale plan / secret rotation if planJobId was specified
            if (planJobId != null) {
                Map<UUID, DesiredSecretState> currentDesiredBySecretId = desiredMap.values().stream()
                        .flatMap(List::stream)
                        .filter(s -> s.secretId() != null)
                        .collect(Collectors.toMap(DesiredSecretState::secretId, s -> s, (s1, s2) -> s1));

                List<SyncOperation> plannedOps = operationRepository.findByJobId(planJobId);
                for (SyncOperation plannedOp : plannedOps) {
                    if (plannedOp.getSecretId() != null &&
                            (plannedOp.getOperationType() == SyncOperationType.CREATE || plannedOp.getOperationType() == SyncOperationType.UPDATE)) {
                        DesiredSecretState currentState = currentDesiredBySecretId.get(plannedOp.getSecretId());
                        if (currentState == null) {
                            throw ApiException.conflict("Reconciliation plan [" + planJobId + "] is stale: secret [" + plannedOp.getSecretName() + "] no longer exists in desired state. Please generate a fresh plan.");
                        }
                        if (!SecretFingerprintCalculator.secureEquals(plannedOp.getDesiredFingerprint(), currentState.desiredFingerprint())) {
                            throw ApiException.conflict("Reconciliation plan [" + planJobId + "] is stale: secret [" + plannedOp.getSecretName() + "] has rotated or changed since dry-run preview. Please regenerate dry-run preview before executing.");
                        }
                    }
                }
            }

            // 3. Resolve Actual State & Drift
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

            // 4. Build Plan
            SyncPlan plan = syncPlanningEngine.planSync(
                    workspaceId, effectiveScope, scopeResourceId, effectivePolicy,
                    desiredMap, actualMap, initialDrifts
            );

            // 5. Create SyncJob
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

            // 6. Execute Operations with rate limit backoff and safe failure handling
            int successCount = 0;
            int failureCount = 0;
            int blockedCount = 0;
            List<SyncOperationResponse> opResponses = new ArrayList<>();
            Set<ProviderResourceMapping> affectedMappings = new HashSet<>();
            boolean ownershipLostAbort = false;

            for (SyncOperationPlan opPlan : plan.operations()) {
                SyncOperation op = new SyncOperation(
                        savedJob.getId(), opPlan.secretId(), opPlan.secretName(),
                        opPlan.mappingId(), opPlan.integrationId(),
                        opPlan.operationType(), opPlan.desiredFingerprint(),
                        opPlan.observedFingerprint(), opPlan.reason()
                );

                if (ownershipLostAbort) {
                    op.setStatus(SyncOperationStatus.BLOCKED);
                    op.setErrorCode("COORDINATION_OWNERSHIP_LOST");
                    op.setErrorMessage("Distributed lock lease expired or was lost to another worker. Provider mutation aborted before dispatch.");
                    blockedCount++;
                    SyncOperation savedOp = operationRepository.save(op);
                    opResponses.add(SyncOperationResponse.fromEntity(savedOp));
                    continue;
                }

                if (opPlan.operationType() == SyncOperationType.NO_OP) {
                    op.setStatus(SyncOperationStatus.SKIPPED);
                    blockedCount++;
                } else if (opPlan.operationType() == SyncOperationType.BLOCKED || opPlan.operationType() == SyncOperationType.ERROR) {
                    op.setStatus(SyncOperationStatus.BLOCKED);
                    op.setErrorCode(opPlan.errorCode());
                    op.setErrorMessage(opPlan.errorMessage());
                    blockedCount++;
                } else if (opPlan.operationType() == SyncOperationType.CREATE || opPlan.operationType() == SyncOperationType.UPDATE) {
                    // Pre-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Distributed lock ownership lost before push for secret [{}] in workspace [{}]. Halting further mutations.",
                                opPlan.secretName(), workspaceId);
                        ownershipLostAbort = true;
                        op.setStatus(SyncOperationStatus.BLOCKED);
                        op.setErrorCode("COORDINATION_OWNERSHIP_LOST");
                        op.setErrorMessage("Distributed lock lease expired or was lost to another worker. Provider mutation aborted before dispatch.");
                        blockedCount++;
                        SyncOperation savedOp = operationRepository.save(op);
                        opResponses.add(SyncOperationResponse.fromEntity(savedOp));
                        continue;
                    }

                    boolean opSuccess = executePushWithRetry(
                            workspaceId, opPlan.integrationId(), opPlan.mappingId(),
                            opPlan.secretId(), actorUserId, op
                    );

                    // Post-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Distributed lock lease expired while remote push for secret [{}] was in flight. Tripping write barrier.", opPlan.secretName());
                        ownershipLostAbort = true;
                    }

                    if (opSuccess) {
                        successCount++;
                        mappingRepository.findById(opPlan.mappingId()).ifPresent(affectedMappings::add);
                    } else {
                        failureCount++;
                    }
                } else if (opPlan.operationType() == SyncOperationType.DELETE) {
                    // Pre-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Distributed lock ownership lost before delete for secret [{}] in workspace [{}]. Halting further mutations.",
                                opPlan.secretName(), workspaceId);
                        ownershipLostAbort = true;
                        op.setStatus(SyncOperationStatus.BLOCKED);
                        op.setErrorCode("COORDINATION_OWNERSHIP_LOST");
                        op.setErrorMessage("Distributed lock lease expired or was lost to another worker. Provider mutation aborted before dispatch.");
                        blockedCount++;
                        SyncOperation savedOp = operationRepository.save(op);
                        opResponses.add(SyncOperationResponse.fromEntity(savedOp));
                        continue;
                    }

                    boolean opSuccess = executeDeleteWithRetry(
                            workspaceId, opPlan.integrationId(), opPlan.mappingId(),
                            opPlan.secretName(), actorUserId, op
                    );

                    // Post-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Distributed lock lease expired while remote delete for secret [{}] was in flight. Tripping write barrier.", opPlan.secretName());
                        ownershipLostAbort = true;
                    }

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

            // 7. Post-sync Targeted Drift Verification
            for (ProviderResourceMapping mapping : affectedMappings) {
                try {
                    List<DesiredSecretState> desiredStates = desiredStateResolver.resolveDesiredStateForMapping(workspaceId, mapping);
                    ActualStateResult actualResult = actualStateResolver.resolveActualState(workspaceId, mapping);
                    driftDetectionEngine.detectDriftForMapping(workspaceId, mapping, desiredStates, actualResult, actorUserId);
                } catch (Exception e) {
                    log.warn("Post-sync drift verification failed for mapping [{}]: {}", mapping.getId(), e.getMessage());
                }
            }

            // 8. Finalize Job Status
            Instant completionTime = Instant.now();
            savedJob.setCompletedAt(completionTime);
            savedJob.setSuccessfulOperations(successCount);
            savedJob.setFailedOperations(failureCount);
            savedJob.setBlockedOperations(blockedCount);

            if (failureCount == 0 && !ownershipLostAbort) {
                savedJob.setStatus(SyncJobStatus.COMPLETED);
            } else if (successCount > 0) {
                savedJob.setStatus(SyncJobStatus.PARTIAL);
                savedJob.setErrorSummary(ownershipLostAbort ?
                        "Lock ownership expired or lost during synchronization; remaining operations blocked." :
                        failureCount + " operations failed during synchronization");
            } else {
                savedJob.setStatus(SyncJobStatus.FAILED);
                savedJob.setErrorSummary(ownershipLostAbort ?
                        "Lock ownership lost before any mutations could be committed." :
                        "All attempted operations failed during synchronization");
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

            if (eventPublisher != null) {
                try {
                    eventPublisher.publish(
                            com.secretvault.events.model.BaseDomainEvent.builder()
                                    .eventType(finalizedJob.getStatus() == SyncJobStatus.FAILED
                                            ? com.secretvault.events.model.EventType.PROVIDER_SYNC_FAILED
                                            : com.secretvault.events.model.EventType.PROVIDER_SYNC_COMPLETED)
                                    .workspaceId(workspaceId)
                                    .actorType("USER")
                                    .actorId(actorUserId != null ? actorUserId.toString() : null)
                                    .aggregateType("SYNC_JOB")
                                    .aggregateId(finalizedJob.getId().toString())
                                    .addMetadata("jobId", finalizedJob.getId().toString())
                                    .addMetadata("status", finalizedJob.getStatus().name())
                                    .addMetadata("successfulOperations", successCount)
                                    .addMetadata("failedOperations", failureCount)
                                    .build()
                    );
                } catch (Exception ex) {
                    log.warn("Failed to publish sync completion event: {}", ex.getMessage());
                }
            }

            return SyncExecutionResponse.of(
                    finalizedJob,
                    opResponses,
                    "Synchronization executed: " + successCount + " succeeded, " + failureCount + " failed, " + blockedCount + " skipped/blocked."
            );
        } finally {
            lockHandle.close();
        }
    }

    /**
     * Retries failed operations for a partially failed or failed synchronization job.
     * Prevents duplicate execution of already successful operations and aborts mutations
     * if secrets have rotated since the initial sync execution.
     */
    @Transactional
    public SyncExecutionResponse retryFailedOperations(
            UUID workspaceId,
            UUID jobId,
            UUID actorUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SYNC_EXECUTE, actorUserId
        );

        SyncJob job = jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Sync job not found in this workspace."));

        if (job.isDryRun()) {
            throw ApiException.badRequest("Cannot retry a dry-run job.");
        }

        if (job.getStatus() == SyncJobStatus.COMPLETED) {
            throw ApiException.badRequest("Job has already completed successfully.");
        }

        if (job.getStatus() == SyncJobStatus.RUNNING) {
            throw ApiException.conflict("Job is currently running.");
        }

        String scopeKey = job.getScopeResourceId() != null ? job.getScopeResourceId().toString() : "ALL";
        SyncDistributedLockCoordinator.SyncLockHandle lockHandle =
                lockCoordinator.acquireExecutionLockHandle(workspaceId, job.getScopeResourceId());
        if (lockHandle == null) {
            throw ApiException.conflict("Another synchronization operation is currently in progress for this scope.");
        }

        try {
            List<SyncOperation> failedOps = operationRepository.findByJobIdAndStatus(jobId, SyncOperationStatus.FAILED);
            if (failedOps.isEmpty()) {
                return SyncExecutionResponse.of(job, List.of(), "No failed operations to retry for job [" + jobId + "].");
            }

            job.setStatus(SyncJobStatus.RUNNING);
            jobRepository.save(job);

            // Fetch current desired state to prevent applying stale or rotated secrets
            Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap =
                    desiredStateResolver.resolveDesiredState(workspaceId, job.getScope(), job.getScopeResourceId());
            Map<UUID, DesiredSecretState> currentDesiredBySecretId = desiredMap.values().stream()
                    .flatMap(List::stream)
                    .filter(s -> s.secretId() != null)
                    .collect(Collectors.toMap(DesiredSecretState::secretId, s -> s, (s1, s2) -> s1));

            Set<ProviderResourceMapping> affectedMappings = new HashSet<>();
            List<SyncOperationResponse> opResponses = new ArrayList<>();
            boolean retryOwnershipLost = false;

            for (SyncOperation op : failedOps) {
                if (retryOwnershipLost) {
                    op.setStatus(SyncOperationStatus.BLOCKED);
                    op.setErrorCode("COORDINATION_OWNERSHIP_LOST");
                    op.setErrorMessage("Distributed lock lease expired or was lost to another worker. Aborted remaining retries.");
                    operationRepository.save(op);
                    opResponses.add(SyncOperationResponse.fromEntity(op));
                    continue;
                }

                // Stale check for push operations
                if (op.getSecretId() != null && (op.getOperationType() == SyncOperationType.CREATE || op.getOperationType() == SyncOperationType.UPDATE)) {
                    // Pre-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Lock ownership lost before retrying push for secret [{}]. Aborting remaining retries.", op.getSecretName());
                        retryOwnershipLost = true;
                        op.setStatus(SyncOperationStatus.BLOCKED);
                        op.setErrorCode("COORDINATION_OWNERSHIP_LOST");
                        op.setErrorMessage("Distributed lock lease expired or was lost to another worker. Aborted retry.");
                        operationRepository.save(op);
                        opResponses.add(SyncOperationResponse.fromEntity(op));
                        continue;
                    }

                    DesiredSecretState currentSecret = currentDesiredBySecretId.get(op.getSecretId());
                    if (currentSecret == null || !SecretFingerprintCalculator.secureEquals(op.getDesiredFingerprint(), currentSecret.desiredFingerprint())) {
                        op.setStatus(SyncOperationStatus.BLOCKED);
                        op.setErrorCode("STALE_SECRET_ROTATION");
                        op.setErrorMessage("Secret desired state changed or rotated since initial sync. Aborted retry.");
                        operationRepository.save(op);
                        opResponses.add(SyncOperationResponse.fromEntity(op));
                        continue;
                    }

                    boolean success = executePushWithRetry(workspaceId, op.getIntegrationId(), op.getMappingId(), op.getSecretId(), actorUserId, op);

                    // Post-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Lock lease expired while retrying push for secret [{}] was in flight.", op.getSecretName());
                        retryOwnershipLost = true;
                    }

                    if (success) {
                        mappingRepository.findById(op.getMappingId()).ifPresent(affectedMappings::add);
                    }
                } else if (op.getOperationType() == SyncOperationType.DELETE) {
                    // Pre-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Lock ownership lost before retrying delete for secret [{}]. Aborting remaining retries.", op.getSecretName());
                        retryOwnershipLost = true;
                        op.setStatus(SyncOperationStatus.BLOCKED);
                        op.setErrorCode("COORDINATION_OWNERSHIP_LOST");
                        op.setErrorMessage("Distributed lock lease expired or was lost to another worker. Aborted retry.");
                        operationRepository.save(op);
                        opResponses.add(SyncOperationResponse.fromEntity(op));
                        continue;
                    }

                    boolean success = executeDeleteWithRetry(workspaceId, op.getIntegrationId(), op.getMappingId(), op.getSecretName(), actorUserId, op);

                    // Post-mutation write barrier
                    if (!lockHandle.isOwnershipValid()) {
                        log.warn("Lock lease expired while retrying delete for secret [{}] was in flight.", op.getSecretName());
                        retryOwnershipLost = true;
                    }

                    if (success) {
                        mappingRepository.findById(op.getMappingId()).ifPresent(affectedMappings::add);
                    }
                }
                operationRepository.save(op);
                opResponses.add(SyncOperationResponse.fromEntity(op));
            }

            // Post-sync drift verification on affected mappings
            for (ProviderResourceMapping mapping : affectedMappings) {
                try {
                    List<DesiredSecretState> desiredStates = desiredStateResolver.resolveDesiredStateForMapping(workspaceId, mapping);
                    ActualStateResult actualResult = actualStateResolver.resolveActualState(workspaceId, mapping);
                    driftDetectionEngine.detectDriftForMapping(workspaceId, mapping, desiredStates, actualResult, actorUserId);
                } catch (Exception e) {
                    log.warn("Post-retry drift verification failed for mapping [{}]: {}", mapping.getId(), e.getMessage());
                }
            }

            // Recalculate totals
            long newFailed = operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.FAILED);
            long newSuccess = operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.SUCCESS);
            long newBlocked = operationRepository.countByJobIdAndStatus(jobId, SyncOperationStatus.BLOCKED);

            job.setSuccessfulOperations((int) newSuccess);
            job.setFailedOperations((int) newFailed);
            job.setBlockedOperations((int) newBlocked);
            job.setCompletedAt(Instant.now());

            if (newFailed == 0 && !retryOwnershipLost) {
                job.setStatus(SyncJobStatus.COMPLETED);
                job.setErrorSummary(null);
            } else if (newSuccess > 0) {
                job.setStatus(SyncJobStatus.PARTIAL);
                job.setErrorSummary(retryOwnershipLost ?
                        "Lock lease expired or lost during retry execution; remaining retries blocked." :
                        newFailed + " operations still failed after retry.");
            } else {
                job.setStatus(SyncJobStatus.FAILED);
                job.setErrorSummary(retryOwnershipLost ?
                        "Lock ownership lost before any retry mutations completed." :
                        "All retried operations failed.");
            }

            SyncJob finalizedJob = jobRepository.save(job);

            auditService.recordAudit(
                    null, workspaceId, actorUserId, "USER",
                    finalizedJob.getStatus() == SyncJobStatus.FAILED ? AuditAction.SYNC_FAILED : AuditAction.SYNC_COMPLETED,
                    "SYNC_JOB", finalizedJob.getId(), null, null,
                    finalizedJob.getStatus() == SyncJobStatus.FAILED ? "FAILURE" : "SUCCESS"
            );

            return SyncExecutionResponse.of(
                    finalizedJob,
                    opResponses,
                    "Retried failed operations for job [" + jobId + "]. Status: " + finalizedJob.getStatus()
            );
        } finally {
            lockHandle.close();
        }
    }

    /**
     * Cancels an executing or queued synchronization job.
     */
    @Transactional
    public SyncJobResponse cancelSyncJob(
            UUID workspaceId,
            UUID jobId,
            UUID actorUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SYNC_EXECUTE, actorUserId
        );

        SyncJob job = jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Sync job not found in this workspace."));

        if (job.getStatus() == SyncJobStatus.COMPLETED || job.getStatus() == SyncJobStatus.FAILED || job.getStatus() == SyncJobStatus.CANCELLED) {
            throw ApiException.badRequest("Cannot cancel job in terminal state [" + job.getStatus() + "].");
        }

        job.setStatus(SyncJobStatus.CANCELLED);
        job.setCompletedAt(Instant.now());
        job.setErrorSummary("Job was cancelled by user.");
        SyncJob saved = jobRepository.save(job);

        auditService.recordAudit(
                null, workspaceId, actorUserId, "USER",
                AuditAction.SYNC_FAILED, "SYNC_JOB",
                saved.getId(), null, null, "CANCELLED"
        );

        return SyncJobResponse.fromEntity(saved);
    }

    private boolean isTransientError(ProviderErrorCode code) {
        return code == ProviderErrorCode.PROVIDER_RATE_LIMITED
                || code == ProviderErrorCode.PROVIDER_TIMEOUT
                || code == ProviderErrorCode.PROVIDER_UNAVAILABLE;
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

                // Retry only transient errors (rate-limit, timeouts, unavailable)
                if (isTransientError(response.errorCode()) && attempt < maxRetries) {
                    try {
                        long jitter = ThreadLocalRandom.current().nextLong(50, 150);
                        long delay = (long) (backoffMs * Math.pow(2, attempt - 1)) + jitter;
                        Thread.sleep(delay);
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

    private boolean executeDeleteWithRetry(
            UUID workspaceId,
            UUID integrationId,
            UUID mappingId,
            String secretKey,
            UUID actorUserId,
            SyncOperation op
    ) {
        int maxRetries = 3;
        long backoffMs = 500;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                com.secretvault.provider.model.ProviderSecretOperationResult response = providerSecretSyncService.deleteSecretFromProvider(
                        workspaceId, integrationId, mappingId, secretKey, actorUserId
                );

                if (response.success()) {
                    op.setStatus(SyncOperationStatus.SUCCESS);
                    op.setExecutedAt(Instant.now());
                    return true;
                }

                // Retry only transient errors
                if (isTransientError(response.errorCode()) && attempt < maxRetries) {
                    try {
                        long jitter = ThreadLocalRandom.current().nextLong(50, 150);
                        long delay = (long) (backoffMs * Math.pow(2, attempt - 1)) + jitter;
                        Thread.sleep(delay);
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
                log.warn("Delete secret failed for secret [{}] on attempt [{}]: {}", secretKey, attempt, e.getMessage());
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

package com.secretvault.sync.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.sync.dto.*;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.ReconciliationPolicy;
import com.secretvault.sync.model.SyncJobStatus;
import com.secretvault.sync.model.SyncScope;
import com.secretvault.sync.repository.SyncJobRepository;
import com.secretvault.sync.repository.SyncOperationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service managing synchronization job lifecycle, queries, and orchestration.
 */
@Service
public class SyncJobService {

    private static final Set<String> ALLOWED_JOB_SORT_PROPERTIES = Set.of(
            "createdAt", "startedAt", "completedAt", "status", "totalOperations", "successfulOperations", "failedOperations"
    );

    private static final Set<String> ALLOWED_OP_SORT_PROPERTIES = Set.of(
            "createdAt", "executedAt", "status", "operationType", "secretName"
    );

    private final SyncJobRepository syncJobRepository;
    private final SyncOperationRepository syncOperationRepository;
    private final SyncExecutionEngine syncExecutionEngine;
    private final EffectiveAccessService effectiveAccessService;

    public SyncJobService(
            SyncJobRepository syncJobRepository,
            SyncOperationRepository syncOperationRepository,
            SyncExecutionEngine syncExecutionEngine,
            EffectiveAccessService effectiveAccessService
    ) {
        this.syncJobRepository = Objects.requireNonNull(syncJobRepository, "syncJobRepository must not be null");
        this.syncOperationRepository = Objects.requireNonNull(syncOperationRepository, "syncOperationRepository must not be null");
        this.syncExecutionEngine = Objects.requireNonNull(syncExecutionEngine, "syncExecutionEngine must not be null");
        this.effectiveAccessService = Objects.requireNonNull(effectiveAccessService, "effectiveAccessService must not be null");
    }

    /**
     * Lists sync jobs for a workspace with filtering and allowlisted sorting.
     */
    @Transactional(readOnly = true)
    public Page<SyncJobResponse> getSyncJobs(
            UUID workspaceId,
            SyncJobStatus status,
            Boolean dryRun,
            Pageable pageable,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SYNC_VIEW, callerUserId
        );

        Pageable safePageable = sanitizeJobPageable(pageable);
        Page<SyncJob> jobs = syncJobRepository.findWithFilters(workspaceId, status, dryRun, safePageable);
        return jobs.map(SyncJobResponse::fromEntity);
    }

    /**
     * Retrieves a single sync job by ID within workspace boundaries.
     */
    @Transactional(readOnly = true)
    public SyncJobResponse getSyncJobById(UUID workspaceId, UUID jobId, UUID callerUserId) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SYNC_VIEW, callerUserId
        );

        SyncJob job = syncJobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Sync job not found in this workspace"));

        return SyncJobResponse.fromEntity(job);
    }

    /**
     * Retrieves paginated operations associated with a sync job.
     */
    @Transactional(readOnly = true)
    public Page<SyncOperationResponse> getSyncJobOperations(
            UUID workspaceId,
            UUID jobId,
            Pageable pageable,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SYNC_VIEW, callerUserId
        );

        syncJobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Sync job not found in this workspace"));

        Pageable safePageable = sanitizeOpPageable(pageable);
        Page<SyncOperation> ops = syncOperationRepository.findByJobId(jobId, safePageable);
        return ops.map(SyncOperationResponse::fromEntity);
    }

    /**
     * Initiates dry-run simulation.
     */
    public DryRunResponse triggerDryRun(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy policy,
            UUID callerUserId
    ) {
        return syncExecutionEngine.executeDryRun(workspaceId, scope, scopeResourceId, policy, callerUserId);
    }

    /**
     * Initiates live synchronization execution.
     */
    public SyncExecutionResponse triggerSync(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            ReconciliationPolicy policy,
            UUID callerUserId
    ) {
        return syncExecutionEngine.executeSync(workspaceId, scope, scopeResourceId, policy, callerUserId);
    }

    private Pageable sanitizeJobPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(100, Math.max(1, pageable.getPageSize()));

        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (ALLOWED_JOB_SORT_PROPERTIES.contains(order.getProperty())) {
                safeOrders.add(new Sort.Order(order.getDirection(), order.getProperty()));
            }
        }

        if (safeOrders.isEmpty()) {
            safeOrders.add(new Sort.Order(Sort.Direction.DESC, "createdAt"));
        }

        return PageRequest.of(pageNumber, pageSize, Sort.by(safeOrders));
    }

    private Pageable sanitizeOpPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"));
        }

        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(100, Math.max(1, pageable.getPageSize()));

        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (ALLOWED_OP_SORT_PROPERTIES.contains(order.getProperty())) {
                safeOrders.add(new Sort.Order(order.getDirection(), order.getProperty()));
            }
        }

        if (safeOrders.isEmpty()) {
            safeOrders.add(new Sort.Order(Sort.Direction.ASC, "createdAt"));
        }

        return PageRequest.of(pageNumber, pageSize, Sort.by(safeOrders));
    }
}

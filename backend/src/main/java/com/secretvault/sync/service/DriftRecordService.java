package com.secretvault.sync.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.sync.dto.DriftRecordResponse;
import com.secretvault.sync.dto.UpdateDriftStatusRequest;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.DriftSeverity;
import com.secretvault.sync.model.DriftStatus;
import com.secretvault.sync.model.DriftType;
import com.secretvault.sync.repository.DriftRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service managing drift record queries, allowlisted pagination, and triage lifecycle.
 */
@Service
public class DriftRecordService {

    private static final Logger log = LoggerFactory.getLogger(DriftRecordService.class);

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "createdAt", "firstDetectedAt", "lastDetectedAt",
            "severity", "status", "secretName", "updatedAt", "occurrenceCount"
    );

    private final DriftRecordRepository driftRecordRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;

    public DriftRecordService(
            DriftRecordRepository driftRecordRepository,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            SecurityEventService securityEventService
    ) {
        this.driftRecordRepository = Objects.requireNonNull(driftRecordRepository, "driftRecordRepository must not be null");
        this.effectiveAccessService = Objects.requireNonNull(effectiveAccessService, "effectiveAccessService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.securityEventService = Objects.requireNonNull(securityEventService, "securityEventService must not be null");
    }

    /**
     * Lists drift records for a workspace with rich filtering, allowlisted sorting, and pagination.
     */
    @Transactional(readOnly = true)
    public Page<DriftRecordResponse> getDriftRecords(
            UUID workspaceId,
            DriftStatus status,
            DriftType driftType,
            DriftSeverity severity,
            UUID projectId,
            UUID environmentId,
            UUID integrationId,
            UUID mappingId,
            Pageable pageable,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, projectId, environmentId, null,
                AccessPermission.DRIFT_VIEW, callerUserId
        );

        Pageable safePageable = sanitizePageable(pageable);
        Page<DriftRecord> records = driftRecordRepository.findWithFilters(
                workspaceId, status, driftType, severity,
                projectId, environmentId, integrationId, mappingId,
                safePageable
        );

        return records.map(DriftRecordResponse::fromEntity);
    }

    /**
     * Retrieves a single drift record by ID within workspace boundary.
     */
    @Transactional(readOnly = true)
    public DriftRecordResponse getDriftRecordById(UUID workspaceId, UUID driftId, UUID callerUserId) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.DRIFT_VIEW, callerUserId
        );

        DriftRecord record = driftRecordRepository.findByIdAndWorkspaceId(driftId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Drift record not found in this workspace"));

        return DriftRecordResponse.fromEntity(record);
    }

    /**
     * Updates the triage status of a drift record.
     */
    @Transactional
    public DriftRecordResponse updateDriftStatus(
            UUID workspaceId,
            UUID driftId,
            UpdateDriftStatusRequest request,
            UUID callerUserId
    ) {
        if (request == null || request.status() == null) {
            throw ApiException.badRequest("Target drift status is required");
        }

        DriftRecord record = driftRecordRepository.findByIdAndWorkspaceId(driftId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Drift record not found in this workspace"));

        effectiveAccessService.checkPermission(
                workspaceId, record.getProjectId(), record.getEnvironmentId(), record.getSecretId(),
                AccessPermission.DRIFT_MANAGE, callerUserId
        );

        Instant now = Instant.now();
        DriftStatus oldStatus = record.getStatus();
        DriftStatus newStatus = request.status();

        record.setStatus(newStatus);
        record.setUpdatedAt(now);

        if (newStatus == DriftStatus.RESOLVED) {
            record.setResolvedAt(now);
            record.setResolvedBy(callerUserId);
            record.setResolutionReason(request.resolutionReason() != null ? request.resolutionReason() : "Manually resolved");
        } else if (newStatus == DriftStatus.OPEN) {
            record.setResolvedAt(null);
            record.setResolvedBy(null);
            record.setResolutionReason(null);
        }

        DriftRecord updated = driftRecordRepository.save(record);

        // Security Telemetry & Audit
        auditService.recordAudit(
                null, workspaceId, callerUserId, "USER",
                AuditAction.DRIFT_STATUS_UPDATED, "DRIFT_RECORD",
                updated.getId(), null, null, "SUCCESS"
        );

        if (newStatus == DriftStatus.RESOLVED) {
            securityEventService.recordEvent(
                    workspaceId, updated.getProjectId(), updated.getEnvironmentId(),
                    callerUserId, SecurityEventType.DRIFT_RESOLVED,
                    SecurityEventSeverity.LOW, SecurityEventOutcome.SUCCESS,
                    "USER", null, null, null,
                    Map.of(
                            "driftId", updated.getId().toString(),
                            "previousStatus", oldStatus.name(),
                            "newStatus", newStatus.name()
                    )
            );
        }

        return DriftRecordResponse.fromEntity(updated);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastDetectedAt"));
        }

        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(100, Math.max(1, pageable.getPageSize()));

        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                safeOrders.add(new Sort.Order(order.getDirection(), order.getProperty()));
            }
        }

        if (safeOrders.isEmpty()) {
            safeOrders.add(new Sort.Order(Sort.Direction.DESC, "lastDetectedAt"));
        }

        return PageRequest.of(pageNumber, pageSize, Sort.by(safeOrders));
    }
}

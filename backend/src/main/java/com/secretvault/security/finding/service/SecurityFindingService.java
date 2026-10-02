package com.secretvault.security.finding.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.dto.AssignFindingRequest;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.dto.SecurityFindingResponse;
import com.secretvault.security.finding.dto.UpdateFindingStatusRequest;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SecurityFindingService {

    private static final Logger log = LoggerFactory.getLogger(SecurityFindingService.class);

    private final SecurityFindingRepository findingRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final SecurityEventService eventService;

    public SecurityFindingService(
            SecurityFindingRepository findingRepository,
            WorkspaceRepository workspaceRepository,
            EffectiveAccessService effectiveAccessService,
            SecurityEventService eventService
    ) {
        this.findingRepository = findingRepository;
        this.workspaceRepository = workspaceRepository;
        this.effectiveAccessService = effectiveAccessService;
        this.eventService = eventService;
    }

    /**
     * Idempotently upserts a security finding using its deterministic fingerprint.
     */
    @Transactional
    public SecurityFinding upsertFinding(SecurityFindingDraft draft) {
        if (draft == null || draft.workspaceId() == null) {
            throw ApiException.badRequest("Invalid finding draft: workspace ID is required");
        }

        String fingerprint = draft.generateFingerprint();
        String evidenceJson = draft.serializeEvidence();

        Optional<SecurityFinding> existingOpt = findingRepository.findByWorkspaceIdAndFingerprint(
                draft.workspaceId(), fingerprint
        );

        if (existingOpt.isPresent()) {
            SecurityFinding existing = existingOpt.get();
            existing.incrementOccurrenceCount();
            existing.setLastObservedAt(Instant.now());
            existing.setEvidenceJson(evidenceJson);
            existing.setRemediationGuidance(draft.remediationGuidance());
            existing.setSeverity(draft.severity());
            existing.setUpdatedAt(Instant.now());

            // If finding was previously resolved or marked false positive, re-open on active regression
            if (existing.getStatus() == FindingStatus.RESOLVED) {
                existing.setStatus(FindingStatus.OPEN);
                existing.setResolvedAt(null);
                existing.setResolutionReason(null);
                existing.setResolvedByUserId(null);
                log.info("Re-opened resolved finding [{}] in workspace [{}] due to regression detection",
                        existing.getId(), existing.getWorkspaceId());
            }

            return findingRepository.save(existing);
        }

        SecurityFinding newFinding = new SecurityFinding(
                draft.workspaceId(),
                draft.projectId(),
                draft.environmentId(),
                draft.category(),
                draft.severity(),
                draft.confidence(),
                draft.title(),
                draft.safeDescription(),
                draft.remediationGuidance(),
                evidenceJson,
                fingerprint
        );

        SecurityFinding saved = findingRepository.save(newFinding);
        log.info("Created new security finding [{}] category=[{}] severity=[{}] in workspace [{}]",
                saved.getId(), saved.getCategory(), saved.getSeverity(), saved.getWorkspaceId());

        // Emit audit/security event
        eventService.recordEvent(
                saved.getWorkspaceId(),
                saved.getProjectId(),
                saved.getEnvironmentId(),
                null,
                SecurityEventType.SECURITY_FINDING_CREATED,
                mapToEventSeverity(saved.getSeverity()),
                SecurityEventOutcome.SUCCESS,
                "ANALYSIS_ENGINE",
                null, null, null,
                Map.of(
                        "findingId", saved.getId().toString(),
                        "category", saved.getCategory().name(),
                        "title", saved.getTitle(),
                        "severity", saved.getSeverity().name()
                )
        );

        return saved;
    }

    /**
     * Retrieves paginated security findings for a workspace with strict tenant isolation.
     */
    @Transactional(readOnly = true)
    public Page<SecurityFindingResponse> getFindings(
            UUID workspaceId,
            FindingStatus status,
            FindingSeverity severity,
            FindingCategory category,
            UUID projectId,
            UUID environmentId,
            String search,
            UUID callerUserId,
            Pageable pageable
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_VIEW, callerUserId
        );

        return findingRepository.searchFindings(
                workspaceId,
                status,
                severity,
                category,
                projectId,
                environmentId,
                search,
                pageable
        ).map(SecurityFindingResponse::fromEntity);
    }

    /**
     * Retrieves a single security finding with strict tenant verification.
     */
    @Transactional(readOnly = true)
    public SecurityFindingResponse getFindingDetails(UUID workspaceId, UUID findingId, UUID callerUserId) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_VIEW, callerUserId
        );

        SecurityFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security finding not found in this workspace"));

        return SecurityFindingResponse.fromEntity(finding);
    }

    /**
     * Updates the status of a finding with explicit state transition validation.
     */
    @Transactional
    public SecurityFindingResponse updateStatus(
            UUID workspaceId,
            UUID findingId,
            UpdateFindingStatusRequest req,
            UUID callerUserId
    ) {
        if (req == null || req.status() == null) {
            throw ApiException.badRequest("New status must not be null");
        }

        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_MANAGE, callerUserId
        );

        SecurityFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security finding not found in this workspace"));

        FindingStatus currentStatus = finding.getStatus();
        FindingStatus newStatus = req.status();

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw ApiException.badRequest(String.format(
                    "Invalid finding status transition from %s to %s", currentStatus, newStatus
            ));
        }

        finding.setStatus(newStatus);
        finding.setUpdatedAt(Instant.now());

        if (newStatus == FindingStatus.ACKNOWLEDGED && finding.getAcknowledgedAt() == null) {
            finding.setAcknowledgedAt(Instant.now());
        }

        if (newStatus == FindingStatus.RESOLVED || newStatus == FindingStatus.FALSE_POSITIVE) {
            finding.setResolvedAt(Instant.now());
            finding.setResolvedByUserId(callerUserId);
            finding.setResolutionReason(req.resolutionReason());

            eventService.recordEvent(
                    workspaceId,
                    finding.getProjectId(),
                    finding.getEnvironmentId(),
                    callerUserId,
                    SecurityEventType.SECURITY_FINDING_RESOLVED,
                    SecurityEventSeverity.INFO,
                    SecurityEventOutcome.SUCCESS,
                    "API",
                    null, null, null,
                    Map.of(
                            "findingId", finding.getId().toString(),
                            "category", finding.getCategory().name(),
                            "resolvedStatus", newStatus.name(),
                            "reason", req.resolutionReason() != null ? req.resolutionReason() : "None provided"
                    )
            );
        } else if (newStatus == FindingStatus.OPEN) {
            finding.setResolvedAt(null);
            finding.setResolvedByUserId(null);
            finding.setResolutionReason(null);
        }

        SecurityFinding saved = findingRepository.save(finding);
        return SecurityFindingResponse.fromEntity(saved);
    }

    /**
     * Assigns a finding to an investigator user.
     */
    @Transactional
    public SecurityFindingResponse assignFinding(
            UUID workspaceId,
            UUID findingId,
            AssignFindingRequest req,
            UUID callerUserId
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_MANAGE, callerUserId
        );

        SecurityFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security finding not found in this workspace"));

        finding.setAssigneeUserId(req != null ? req.assigneeUserId() : null);
        finding.setUpdatedAt(Instant.now());

        SecurityFinding saved = findingRepository.save(finding);
        return SecurityFindingResponse.fromEntity(saved);
    }

    private SecurityEventSeverity mapToEventSeverity(FindingSeverity severity) {
        if (severity == null) {
            return SecurityEventSeverity.INFO;
        }
        return switch (severity) {
            case CRITICAL -> SecurityEventSeverity.CRITICAL;
            case HIGH -> SecurityEventSeverity.HIGH;
            case MEDIUM -> SecurityEventSeverity.MEDIUM;
            case LOW -> SecurityEventSeverity.LOW;
        };
    }
}

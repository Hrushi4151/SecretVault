package com.secretvault.incident.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.incident.entity.IncidentRelationshipType;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncident;
import com.secretvault.incident.entity.SecurityIncidentEvent;
import com.secretvault.incident.repository.SecurityIncidentEventRepository;
import com.secretvault.incident.repository.SecurityIncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SecurityIncidentService {

    private static final Logger log = LoggerFactory.getLogger(SecurityIncidentService.class);

    private final SecurityIncidentRepository incidentRepository;
    private final SecurityIncidentEventRepository incidentEventRepository;
    private final EventPublisher eventPublisher;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;

    public SecurityIncidentService(
            SecurityIncidentRepository incidentRepository,
            SecurityIncidentEventRepository incidentEventRepository,
            EventPublisher eventPublisher,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService
    ) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.eventPublisher = eventPublisher;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public SecurityIncident createIncident(
            UUID workspaceId,
            String title,
            String description,
            IncidentSeverity severity,
            String category,
            UUID sourceEventId,
            UUID actorId
    ) {
        if (workspaceId == null) {
            throw ApiException.badRequest("Workspace ID is required for incident creation");
        }

        long count = incidentRepository.countByWorkspaceId(workspaceId);
        String incidentNumber = String.format("INC-%04d", count + 1);

        SecurityIncident incident = new SecurityIncident();
        incident.setWorkspaceId(workspaceId);
        incident.setIncidentNumber(incidentNumber);
        incident.setTitle(title != null ? title : "Security Incident " + incidentNumber);
        incident.setDescription(description != null ? description : "Automated security incident created from event");
        incident.setSeverity(severity != null ? severity : IncidentSeverity.HIGH);
        incident.setCategory(category != null ? category : "SECURITY_ANOMALY");
        incident.setStatus(IncidentStatus.OPEN);
        incident.setSourceEventId(sourceEventId);
        incident.setCreatedBy(actorId);

        SecurityIncident saved = incidentRepository.save(incident);

        if (sourceEventId != null) {
            correlateEvent(saved.getId(), sourceEventId, IncidentRelationshipType.ROOT_CAUSE, "Initial triggering event");
        }

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "SYSTEM",
                AuditAction.SECURITY_INCIDENT_CREATED,
                "SECURITY_INCIDENT",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        // Publish domain event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentNumber", saved.getIncidentNumber());
        metadata.put("severity", saved.getSeverity().name());
        metadata.put("category", saved.getCategory());

        eventPublisher.publish(
                EventType.SECURITY_INCIDENT_CREATED,
                workspaceId,
                "SECURITY_INCIDENT",
                saved.getId().toString(),
                EventSeverity.CRITICAL,
                metadata
        );

        log.info("Created Security Incident [{}] (ID: {}) in workspace [{}]", saved.getIncidentNumber(), saved.getId(), workspaceId);
        return saved;
    }

    @Transactional
    public void correlateEvent(UUID incidentId, UUID eventId, IncidentRelationshipType relType, String notes) {
        if (incidentId == null || eventId == null) return;
        if (incidentEventRepository.existsByIncidentIdAndEventId(incidentId, eventId)) {
            return;
        }

        SecurityIncidentEvent incidentEvent = new SecurityIncidentEvent(
                incidentId,
                eventId,
                relType != null ? relType : IncidentRelationshipType.CORRELATED,
                notes
        );
        incidentEventRepository.save(incidentEvent);
    }

    @Transactional
    public SecurityIncident updateIncidentStatus(
            UUID workspaceId,
            UUID incidentId,
            IncidentStatus newStatus,
            String resolutionSummary,
            UUID actorId
    ) {
        SecurityIncident incident = incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security incident not found in this workspace"));

        IncidentStatus oldStatus = incident.getStatus();
        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) {
            incident.setResolvedAt(Instant.now());
            incident.setResolvedBy(actorId);
            incident.setResolutionSummary(resolutionSummary);
        }

        SecurityIncident saved = incidentRepository.save(incident);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                newStatus == IncidentStatus.RESOLVED ? AuditAction.SECURITY_INCIDENT_RESOLVED : AuditAction.SECURITY_INCIDENT_UPDATED,
                "SECURITY_INCIDENT",
                saved.getId(),
                null,
                null,
                "Status changed from " + oldStatus + " to " + newStatus
        );

        // Publish domain event
        EventType evtType = (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED)
                ? EventType.SECURITY_INCIDENT_RESOLVED
                : EventType.SECURITY_INCIDENT_UPDATED;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentNumber", saved.getIncidentNumber());
        metadata.put("oldStatus", oldStatus.name());
        metadata.put("newStatus", newStatus.name());
        if (resolutionSummary != null) metadata.put("resolutionSummary", resolutionSummary);

        eventPublisher.publish(
                evtType,
                workspaceId,
                "SECURITY_INCIDENT",
                saved.getId().toString(),
                EventSeverity.HIGH,
                metadata
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public SecurityIncident getIncident(UUID workspaceId, UUID incidentId, UUID actorId) {
        verifySecurityAccess(workspaceId, actorId);
        return incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security incident not found in this workspace"));
    }

    @Transactional(readOnly = true)
    public Page<SecurityIncident> listIncidents(
            UUID workspaceId,
            IncidentStatus status,
            IncidentSeverity severity,
            Pageable pageable,
            UUID actorId
    ) {
        verifySecurityAccess(workspaceId, actorId);
        if (status != null) {
            return incidentRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, pageable);
        }
        if (severity != null) {
            return incidentRepository.findByWorkspaceIdAndSeverityOrderByCreatedAtDesc(workspaceId, severity, pageable);
        }
        return incidentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public List<SecurityIncidentEvent> getIncidentEvents(UUID workspaceId, UUID incidentId, UUID actorId) {
        verifySecurityAccess(workspaceId, actorId);
        getIncident(workspaceId, incidentId, actorId); // verify existence & workspace
        return incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    /**
     * Correlate incoming event to open incidents or create new incident for critical conditions.
     */
    @Transactional
    public void processDomainEventCorrelation(DomainEvent event) {
        if (event == null || event.workspaceId() == null) return;

        // Auto-correlate with active incidents if matching aggregate
        List<SecurityIncident> activeIncidents = incidentRepository.findActiveIncidents(event.workspaceId());
        boolean correlated = false;

        for (SecurityIncident incident : activeIncidents) {
            if (incident.getSourceEventId() != null && incident.getSourceEventId().equals(event.eventId())) {
                continue;
            }
            // If aggregate matches or related category
            correlateEvent(incident.getId(), event.eventId(), IncidentRelationshipType.CORRELATED, "Auto-correlated domain event " + event.eventType());
            correlated = true;
            break;
        }

        // If it's a critical security event and no active incident exists, auto-create one
        if (!correlated && isIncidentTriggerEventType(event.eventType())) {
            IncidentSeverity severity = event.severity() == EventSeverity.CRITICAL ? IncidentSeverity.CRITICAL : IncidentSeverity.HIGH;
            createIncident(
                    event.workspaceId(),
                    "Auto Incident: " + event.eventType().name(),
                    "Triggered automatically by critical security event: " + event.eventType().name() + " on " + event.aggregateType() + ":" + event.aggregateId(),
                    severity,
                    event.eventType().name(),
                    event.eventId(),
                    null
            );
        }
    }

    private boolean isIncidentTriggerEventType(EventType type) {
        if (type == null) return false;
        return switch (type) {
            case SECRET_COMPROMISED, MACHINE_REVOKED, MACHINE_SUSPENDED, SECURITY_POLICY_VIOLATION, ANOMALY_DETECTED -> true;
            default -> false;
        };
    }

    private void verifySecurityAccess(UUID workspaceId, UUID actorId) {
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing SECURITY_VIEW permission");
        }
    }
}

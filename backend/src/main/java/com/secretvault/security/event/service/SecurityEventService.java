package com.secretvault.security.event.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.security.event.dto.RecordSecurityEventRequest;
import com.secretvault.security.event.dto.SecurityEventResponse;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.event.util.SafeEventMetadataSanitizer;
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
import java.util.UUID;

@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);

    private final SecurityEventRepository eventRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EffectiveAccessService effectiveAccessService;

    public SecurityEventService(
            SecurityEventRepository eventRepository,
            WorkspaceRepository workspaceRepository,
            EffectiveAccessService effectiveAccessService
    ) {
        this.eventRepository = eventRepository;
        this.workspaceRepository = workspaceRepository;
        this.effectiveAccessService = effectiveAccessService;
    }

    /**
     * Programmatically records a sanitized security event.
     */
    @Transactional
    public SecurityEvent recordEvent(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID actorUserId,
            SecurityEventType eventType,
            SecurityEventSeverity severity,
            SecurityEventOutcome outcome,
            String source,
            String ipAddress,
            String userAgent,
            String requestId,
            Map<String, ?> metadata
    ) {
        if (workspaceId == null) {
            log.warn("Attempted to record security event without workspaceId");
            return null;
        }

        String safeMetadataJson = SafeEventMetadataSanitizer.sanitizeAndSerialize(metadata);

        SecurityEvent event = new SecurityEvent(
                workspaceId,
                projectId,
                environmentId,
                actorUserId,
                eventType,
                severity != null ? severity : SecurityEventSeverity.INFO,
                outcome != null ? outcome : SecurityEventOutcome.SUCCESS,
                source != null ? source : "SYSTEM",
                ipAddress,
                userAgent,
                requestId,
                safeMetadataJson
        );

        return eventRepository.save(event);
    }

    /**
     * Records a security event from a DTO.
     */
    @Transactional
    public SecurityEventResponse recordEvent(UUID workspaceId, RecordSecurityEventRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Request payload must not be null");
        }
        SecurityEvent saved = recordEvent(
                workspaceId,
                req.projectId(),
                req.environmentId(),
                req.actorUserId(),
                req.eventType(),
                req.severity(),
                req.outcome(),
                req.source(),
                req.ipAddress(),
                req.userAgent(),
                req.requestId(),
                req.metadata()
        );
        return SecurityEventResponse.fromEntity(saved);
    }

    /**
     * Retrieves paginated security events for a workspace with strict tenant isolation.
     */
    @Transactional(readOnly = true)
    public Page<SecurityEventResponse> getEvents(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID actorUserId,
            SecurityEventType eventType,
            SecurityEventSeverity severity,
            SecurityEventOutcome outcome,
            Instant fromTime,
            Instant toTime,
            UUID callerUserId,
            Pageable pageable
    ) {
        // Assert authorization: caller must have SECURITY_VIEW permission
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_VIEW, callerUserId
        );

        return eventRepository.searchEvents(
                workspaceId,
                projectId,
                environmentId,
                actorUserId,
                eventType,
                severity,
                outcome,
                fromTime,
                toTime,
                pageable
        ).map(SecurityEventResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public long countAuthorizationDenialsSince(UUID workspaceId, Instant since) {
        return eventRepository.countAuthorizationDenialsSince(workspaceId, since);
    }

    @Transactional(readOnly = true)
    public long countAdminChangesSince(UUID workspaceId, Instant since) {
        return eventRepository.countAdminChangesSince(workspaceId, since);
    }

    @Transactional(readOnly = true)
    public long countJitActivitySince(UUID workspaceId, Instant since) {
        return eventRepository.countJitActivitySince(workspaceId, since);
    }

    @Transactional(readOnly = true)
    public List<SecurityEvent> findRecentEvents(UUID workspaceId, Instant since) {
        return eventRepository.findRecentEvents(workspaceId, since);
    }

    @Transactional(readOnly = true)
    public List<SecurityEvent> findByWorkspaceIdAndActorUserIdSince(UUID workspaceId, UUID actorUserId, Instant since) {
        return eventRepository.findByWorkspaceIdAndActorUserIdSince(workspaceId, actorUserId, since);
    }
}

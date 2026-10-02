package com.secretvault.security.event.dto;

import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.util.SafeEventMetadataSanitizer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SecurityEventResponse(
        UUID id,
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
        Instant timestamp,
        Map<String, Object> metadata
) {
    public static SecurityEventResponse fromEntity(SecurityEvent event) {
        if (event == null) {
            return null;
        }
        return new SecurityEventResponse(
                event.getId(),
                event.getWorkspaceId(),
                event.getProjectId(),
                event.getEnvironmentId(),
                event.getActorUserId(),
                event.getEventType(),
                event.getSeverity(),
                event.getOutcome(),
                event.getSource(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getRequestId(),
                event.getTimestamp(),
                SafeEventMetadataSanitizer.deserialize(event.getMetadataJson())
        );
    }
}

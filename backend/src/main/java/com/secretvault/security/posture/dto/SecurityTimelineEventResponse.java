package com.secretvault.security.posture.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SecurityTimelineEventResponse(
        String id,
        String timelineType, // "SECURITY_EVENT", "AUDIT_EVENT", "FINDING_OBSERVED"
        String title,
        String safeDescription,
        String severity,
        String outcome,
        UUID actorUserId,
        UUID projectId,
        UUID environmentId,
        Instant timestamp,
        Map<String, Object> metadata
) {}

package com.secretvault.security.event.dto;

import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record RecordSecurityEventRequest(
        @NotNull(message = "Event type is required")
        SecurityEventType eventType,

        SecurityEventSeverity severity,

        SecurityEventOutcome outcome,

        UUID projectId,

        UUID environmentId,

        UUID actorUserId,

        String source,

        String ipAddress,

        String userAgent,

        String requestId,

        Map<String, ?> metadata
) {}

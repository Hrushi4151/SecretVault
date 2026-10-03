package com.secretvault.secret.reveal.dto;

import java.time.Instant;
import java.util.UUID;

public record SecretRevealAuditResponse(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String actorEmail,
        String action,
        UUID secretId,
        String secretName,
        Integer versionNumber,
        UUID environmentId,
        String environmentName,
        String result,
        String reason,
        String ipAddress,
        String requestId,
        Instant timestamp
) {
}

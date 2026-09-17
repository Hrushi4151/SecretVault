package com.secretvault.secret.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Sensitive payload returned exclusively by the explicit POST .../reveal endpoint.
 * Never logged or persisted.
 */
public record SecretRevealResponse(
        UUID id,
        UUID environmentId,
        String name,
        Integer versionNumber,
        String value,
        Instant revealedAt
) {
}

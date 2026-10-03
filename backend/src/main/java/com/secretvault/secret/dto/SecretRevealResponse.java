package com.secretvault.secret.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Sensitive payload returned exclusively by the explicit POST .../reveal endpoint.
 * Never logged or persisted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecretRevealResponse(
        UUID id,
        UUID environmentId,
        String name,
        Integer versionNumber,
        String value,
        Instant revealedAt,
        Integer maxDisplayDurationSeconds,
        Boolean copyAllowed,
        Integer clipboardTimeoutSeconds,
        RevealPolicyLevel policyLevel
) {
    public SecretRevealResponse(
            UUID id,
            UUID environmentId,
            String name,
            Integer versionNumber,
            String value,
            Instant revealedAt
    ) {
        this(id, environmentId, name, versionNumber, value, revealedAt, 60, true, 15, RevealPolicyLevel.DEFAULT);
    }
}

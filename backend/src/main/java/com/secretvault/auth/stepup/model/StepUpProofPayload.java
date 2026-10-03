package com.secretvault.auth.stepup.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ephemeral Redis state representing a validated, short-lived Step-Up Authentication proof.
 * Bound strictly to user, session, action, and resource context.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StepUpProofPayload(
        String proofToken,
        UUID userId,
        String sessionIdentifier,
        StepUpAction action,
        StepUpContext context,
        StepUpFactor factorUsed,
        Instant issuedAt,
        Instant expiresAt
) {

    public static StepUpProofPayload of(
            String proofToken,
            UUID userId,
            String sessionIdentifier,
            StepUpAction action,
            StepUpContext context,
            StepUpFactor factorUsed,
            long ttlSeconds
    ) {
        Instant now = Instant.now();
        return new StepUpProofPayload(
                proofToken,
                userId,
                sessionIdentifier,
                action,
                context != null ? context : StepUpContext.empty(),
                factorUsed,
                now,
                now.plusSeconds(ttlSeconds)
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean matches(UUID targetUserId, String targetSessionIdentifier, StepUpAction targetAction, StepUpContext targetContext) {
        if (!Objects.equals(this.userId, targetUserId)) return false;
        if (this.sessionIdentifier != null && !Objects.equals(this.sessionIdentifier, targetSessionIdentifier)) return false;
        if (this.action != targetAction) return false;
        if (this.context != null && targetContext != null) {
            return this.context.matches(targetContext);
        }
        return Objects.equals(this.context, targetContext);
    }
}

package com.secretvault.auth.stepup.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ephemeral Redis state for an active step-up re-authentication challenge.
 * Contains safe identifiers and expiration criteria.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StepUpChallengePayload(
        String challengeId,
        UUID userId,
        String sessionIdentifier,
        StepUpAction action,
        StepUpContext context,
        List<StepUpFactor> supportedFactors,
        Instant issuedAt,
        Instant expiresAt,
        int maxAttempts
) {

    public static StepUpChallengePayload of(
            String challengeId,
            UUID userId,
            String sessionIdentifier,
            StepUpAction action,
            StepUpContext context,
            List<StepUpFactor> supportedFactors,
            long ttlSeconds,
            int maxAttempts
    ) {
        Instant now = Instant.now();
        return new StepUpChallengePayload(
                challengeId,
                userId,
                sessionIdentifier,
                action,
                context != null ? context : StepUpContext.empty(),
                supportedFactors != null ? List.copyOf(supportedFactors) : List.of(StepUpFactor.PASSWORD),
                now,
                now.plusSeconds(ttlSeconds),
                maxAttempts
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean matchesUserAndSession(UUID targetUserId, String targetSessionIdentifier) {
        return Objects.equals(this.userId, targetUserId)
                && (this.sessionIdentifier == null || Objects.equals(this.sessionIdentifier, targetSessionIdentifier));
    }
}

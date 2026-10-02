package com.secretvault.auth.mfa.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Non-sensitive metadata stored in Redis for short-lived MFA authentication challenges.
 * Strict invariant: Contains zero credentials, passwords, TOTP secrets, or recovery codes.
 */
public record MfaChallengePayload(
        @JsonProperty("challengeId") String challengeId,
        @JsonProperty("userId") UUID userId,
        @JsonProperty("purpose") String purpose,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("expiresAt") Instant expiresAt,
        @JsonProperty("maxAttempts") int maxAttempts
) {

    @JsonCreator
    public MfaChallengePayload {
        Objects.requireNonNull(challengeId, "challengeId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static MfaChallengePayload of(String challengeId, UUID userId, String purpose, long ttlSeconds, int maxAttempts) {
        Instant now = Instant.now();
        return new MfaChallengePayload(
                challengeId,
                userId,
                purpose,
                now,
                now.plusSeconds(ttlSeconds),
                maxAttempts
        );
    }
}

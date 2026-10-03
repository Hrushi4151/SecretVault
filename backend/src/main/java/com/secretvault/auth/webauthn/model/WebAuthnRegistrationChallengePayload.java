package com.secretvault.auth.webauthn.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ephemeral Redis state for an in-flight WebAuthn credential registration ceremony.
 * Single-use, short-lived, bound strictly to the active user and session.
 */
public class WebAuthnRegistrationChallengePayload {

    private final String challengeId;
    private final UUID userId;
    private final String sessionIdentifier;
    private final String friendlyName;
    private final String requestJson;
    private final Instant createdAt;
    private final Instant expiresAt;

    @JsonCreator
    public WebAuthnRegistrationChallengePayload(
            @JsonProperty("challengeId") String challengeId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("sessionIdentifier") String sessionIdentifier,
            @JsonProperty("friendlyName") String friendlyName,
            @JsonProperty("requestJson") String requestJson,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("expiresAt") Instant expiresAt
    ) {
        this.challengeId = Objects.requireNonNull(challengeId, "challengeId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.sessionIdentifier = sessionIdentifier;
        this.friendlyName = friendlyName;
        this.requestJson = Objects.requireNonNull(requestJson, "requestJson must not be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt != null ? expiresAt : Instant.now().plusSeconds(300);
    }

    public static WebAuthnRegistrationChallengePayload of(
            String challengeId,
            UUID userId,
            String sessionIdentifier,
            String friendlyName,
            String requestJson,
            long ttlSeconds
    ) {
        Instant now = Instant.now();
        return new WebAuthnRegistrationChallengePayload(
                challengeId,
                userId,
                sessionIdentifier,
                friendlyName,
                requestJson,
                now,
                now.plusSeconds(ttlSeconds)
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public String getChallengeId() {
        return challengeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSessionIdentifier() {
        return sessionIdentifier;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

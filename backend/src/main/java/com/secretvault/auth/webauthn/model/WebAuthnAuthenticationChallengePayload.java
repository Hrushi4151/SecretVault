package com.secretvault.auth.webauthn.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ephemeral Redis state for an in-flight WebAuthn authentication/assertion ceremony.
 * Single-use, short-lived, bound to user, session, and ceremony type.
 */
public class WebAuthnAuthenticationChallengePayload {

    private final String challengeId;
    private final UUID userId;
    private final String sessionIdentifier;
    private final WebAuthnCeremonyType ceremonyType;
    private final String requestJson;
    private final StepUpAction stepUpAction;
    private final StepUpContext stepUpContext;
    private final Instant createdAt;
    private final Instant expiresAt;

    @JsonCreator
    public WebAuthnAuthenticationChallengePayload(
            @JsonProperty("challengeId") String challengeId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("sessionIdentifier") String sessionIdentifier,
            @JsonProperty("ceremonyType") WebAuthnCeremonyType ceremonyType,
            @JsonProperty("requestJson") String requestJson,
            @JsonProperty("stepUpAction") StepUpAction stepUpAction,
            @JsonProperty("stepUpContext") StepUpContext stepUpContext,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("expiresAt") Instant expiresAt
    ) {
        this.challengeId = Objects.requireNonNull(challengeId, "challengeId must not be null");
        this.userId = userId;
        this.sessionIdentifier = sessionIdentifier;
        this.ceremonyType = ceremonyType != null ? ceremonyType : WebAuthnCeremonyType.LOGIN;
        this.requestJson = Objects.requireNonNull(requestJson, "requestJson must not be null");
        this.stepUpAction = stepUpAction;
        this.stepUpContext = stepUpContext;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt != null ? expiresAt : Instant.now().plusSeconds(300);
    }

    public static WebAuthnAuthenticationChallengePayload of(
            String challengeId,
            UUID userId,
            String sessionIdentifier,
            WebAuthnCeremonyType ceremonyType,
            String requestJson,
            StepUpAction stepUpAction,
            StepUpContext stepUpContext,
            long ttlSeconds
    ) {
        Instant now = Instant.now();
        return new WebAuthnAuthenticationChallengePayload(
                challengeId,
                userId,
                sessionIdentifier,
                ceremonyType,
                requestJson,
                stepUpAction,
                stepUpContext,
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

    public WebAuthnCeremonyType getCeremonyType() {
        return ceremonyType;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public StepUpAction getStepUpAction() {
        return stepUpAction;
    }

    public StepUpContext getStepUpContext() {
        return stepUpContext;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

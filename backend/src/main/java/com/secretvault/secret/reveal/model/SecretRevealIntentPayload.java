package com.secretvault.secret.reveal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ephemeral Redis state for an authorized, single-use Secret Reveal Intent.
 * Bound strictly to user, session, workspace, project, environment, secret, and target version.
 * Never contains secret plaintext.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecretRevealIntentPayload(
        String intentToken,
        UUID userId,
        String sessionIdentifier,
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        UUID secretId,
        Integer versionNumber,
        RevealPolicyLevel policyLevel,
        boolean copyAllowed,
        int maxDisplayDurationSeconds,
        int clipboardTimeoutSeconds,
        String reason,
        Instant issuedAt,
        Instant expiresAt
) {

    public static SecretRevealIntentPayload create(
            String intentToken,
            UUID userId,
            String sessionIdentifier,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            RevealPolicyLevel policyLevel,
            boolean copyAllowed,
            int maxDisplayDurationSeconds,
            int clipboardTimeoutSeconds,
            String reason,
            long ttlSeconds
    ) {
        Instant now = Instant.now();
        return new SecretRevealIntentPayload(
                intentToken,
                userId,
                sessionIdentifier,
                workspaceId,
                projectId,
                environmentId,
                secretId,
                versionNumber,
                policyLevel,
                copyAllowed,
                maxDisplayDurationSeconds,
                clipboardTimeoutSeconds,
                reason,
                now,
                now.plusSeconds(ttlSeconds)
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Strictly verifies that the execution parameters match the contextual binding of this intent token.
     */
    public boolean matches(
            UUID callerUserId,
            String callerSessionIdentifier,
            UUID targetWorkspaceId,
            UUID targetProjectId,
            UUID targetEnvironmentId,
            UUID targetSecretId,
            Integer targetVersionNumber
    ) {
        if (!Objects.equals(this.userId, callerUserId)) return false;
        if (this.sessionIdentifier != null && callerSessionIdentifier != null
                && !Objects.equals(this.sessionIdentifier, callerSessionIdentifier)) {
            return false;
        }
        if (!Objects.equals(this.workspaceId, targetWorkspaceId)) return false;
        if (!Objects.equals(this.projectId, targetProjectId)) return false;
        if (!Objects.equals(this.environmentId, targetEnvironmentId)) return false;
        if (!Objects.equals(this.secretId, targetSecretId)) return false;
        if (this.versionNumber != null && targetVersionNumber != null
                && !Objects.equals(this.versionNumber, targetVersionNumber)) {
            return false;
        }
        return true;
    }
}

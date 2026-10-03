package io.secretvault.sdk.model;

import java.time.Instant;

/**
 * Event fired when a secret is refreshed, rotated, or revoked at runtime.
 */
public record SecretChangeEvent(
        String secretName,
        int oldVersion,
        int newVersion,
        ChangeType type,
        Instant timestamp
) {
    public enum ChangeType {
        ROTATED,
        REFRESHED,
        REVOKED,
        EXPIRED
    }
}

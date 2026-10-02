package com.secretvault.common.security.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Envelope wrapper for short-lived security state objects stored in Redis.
 * Tracks creation time, expiration, attempt counters, and single-use consumption state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecurityStateEnvelope<T>(
        String category,
        String identifier,
        T payload,
        long createdAtEpochMs,
        long expiresAtEpochMs,
        int attempts,
        boolean consumed
) {
    public static <T> SecurityStateEnvelope<T> create(String category, String identifier, T payload, long ttlSeconds) {
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + (ttlSeconds * 1000);
        return new SecurityStateEnvelope<>(category, identifier, payload, now, expiresAt, 0, false);
    }

    @JsonIgnore
    public boolean isExpired() {
        return Instant.now().toEpochMilli() > expiresAtEpochMs;
    }
}

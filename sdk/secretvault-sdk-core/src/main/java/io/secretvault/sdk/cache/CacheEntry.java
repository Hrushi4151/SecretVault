package io.secretvault.sdk.cache;

import io.secretvault.sdk.model.SecretValue;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Encapsulates an in-memory cached secret with granular freshness and bounded stale timestamps.
 */
public final class CacheEntry {

    private final SecretValue secretValue;
    private final Instant fetchedAt;
    private final Instant expiresAt;
    private final Instant staleUntil;

    public CacheEntry(SecretValue secretValue, Duration ttl, Duration maxStale) {
        this.secretValue = Objects.requireNonNull(secretValue, "SecretValue cannot be null");
        this.fetchedAt = Instant.now();
        this.expiresAt = fetchedAt.plus(ttl != null ? ttl : Duration.ofSeconds(60));
        this.staleUntil = expiresAt.plus(maxStale != null ? maxStale : Duration.ofMinutes(5));
    }

    public SecretValue getSecretValue() {
        return secretValue;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getStaleUntil() {
        return staleUntil;
    }

    public boolean isFresh() {
        return Instant.now().isBefore(expiresAt);
    }

    public boolean isStaleUsable() {
        Instant now = Instant.now();
        return now.isAfter(expiresAt) && now.isBefore(staleUntil);
    }

    public boolean isCompletelyExpired() {
        return Instant.now().isAfter(staleUntil);
    }
}

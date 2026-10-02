package com.secretvault.common.ratelimit;

import java.time.Instant;

/**
 * Immutable decision record produced by distributed rate limiting checks.
 */
public record RateLimitResult(
        boolean allowed,
        long remainingRequests,
        long retryAfterSeconds,
        long resetTimeEpochSeconds
) {
    public static RateLimitResult allow(long remainingRequests, long ttlSeconds) {
        long resetTime = Instant.now().getEpochSecond() + Math.max(0, ttlSeconds);
        return new RateLimitResult(true, Math.max(0, remainingRequests), 0, resetTime);
    }

    public static RateLimitResult deny(long retryAfterSeconds) {
        long effectiveRetryAfter = Math.max(1, retryAfterSeconds);
        long resetTime = Instant.now().getEpochSecond() + effectiveRetryAfter;
        return new RateLimitResult(false, 0, effectiveRetryAfter, resetTime);
    }
}

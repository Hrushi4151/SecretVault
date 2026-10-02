package com.secretvault.common.ratelimit;

import java.time.Duration;

/**
 * Distributed rate limiter contract backed by Redis.
 * Guarantees cross-instance atomic throttling across all SecretVault APIs.
 */
public interface DistributedRateLimiter {

    /**
     * Checks if a request identified by key is allowed within the specified limit and time window.
     *
     * @param key         Redis rate limit key
     * @param maxRequests Maximum allowed requests in the time window
     * @param window      Time window duration
     * @return RateLimitResult indicating allowed/denied status and retry metrics
     */
    RateLimitResult checkRateLimit(String key, int maxRequests, Duration window);

    /**
     * Checks rate limit and throws {@link RateLimitExceededException} if limit is exceeded.
     *
     * @param key         Redis rate limit key
     * @param maxRequests Maximum allowed requests in the time window
     * @param window      Time window duration
     * @param message     Custom error message
     * @throws RateLimitExceededException if limit exceeded
     */
    void checkRateLimitOrThrow(String key, int maxRequests, Duration window, String message);

    /**
     * Resets/clears the rate limit counter for a specific key (e.g. on successful login reset).
     *
     * @param key Redis rate limit key
     */
    void reset(String key);
}

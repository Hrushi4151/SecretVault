package com.secretvault.common.ratelimit;

import com.secretvault.common.exception.ApiException;
import com.secretvault.common.observability.RedisMetrics;
import com.secretvault.common.redis.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Atomic Redis-backed distributed rate limiter using Lua script execution.
 * Protects APIs across multiple backend instances against race conditions,
 * burst abuse, and credential stuffing.
 */
@Component
public class RedisRateLimiter implements DistributedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String RATE_LIMIT_LUA =
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if tonumber(current) == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n" +
            "end\n" +
            "local ttl = redis.call('TTL', KEYS[1])\n" +
            "if ttl < 0 then\n" +
            "    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n" +
            "    ttl = tonumber(ARGV[1])\n" +
            "end\n" +
            "return { current, ttl }";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisProperties redisProperties;
    private final RedisMetrics redisMetrics;
    private final RedisScript<List> rateLimitScript;

    public RedisRateLimiter(
            StringRedisTemplate stringRedisTemplate,
            RedisProperties redisProperties,
            RedisMetrics redisMetrics
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisProperties = redisProperties;
        this.redisMetrics = redisMetrics;
        this.rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_LUA, List.class);
    }

    @Override
    public RateLimitResult checkRateLimit(String key, int maxRequests, Duration window) {
        if (!redisProperties.getRateLimit().isEnabled()) {
            return RateLimitResult.allow(maxRequests, 0);
        }

        long windowSeconds = Math.max(1, window.toSeconds());

        try {
            List<Long> result = stringRedisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(windowSeconds)
            );

            if (result == null || result.size() < 2) {
                log.warn("Rate limit script returned invalid result for key [{}], defaulting to allow", sanitizeKey(key));
                return RateLimitResult.allow(maxRequests, 0);
            }

            long currentCount = result.get(0);
            long ttlSeconds = result.get(1);

            if (currentCount <= maxRequests) {
                redisMetrics.rateLimitAllowed(extractCategoryFromKey(key));
                long remaining = maxRequests - currentCount;
                return RateLimitResult.allow(remaining, ttlSeconds);
            } else {
                redisMetrics.rateLimitDenied(extractCategoryFromKey(key));
                log.warn("Rate limit exceeded for key [{}]. Current: {}, Max: {}, TTL: {}s",
                        sanitizeKey(key), currentCount, maxRequests, ttlSeconds);
                return RateLimitResult.deny(ttlSeconds);
            }
        } catch (Exception ex) {
            redisMetrics.redisFailure("ratelimit");
            log.error("Redis rate limiting error for key [{}]: {}", sanitizeKey(key), ex.getMessage());

            if (redisProperties.getRateLimit().isFailOpen()) {
                log.warn("Rate limit policy is FAIL_OPEN. Permitting request on Redis failure.");
                return RateLimitResult.allow(maxRequests, 0);
            } else {
                log.warn("Rate limit policy is FAIL_CLOSED. Blocking request on Redis failure.");
                throw ApiException.internal("RATE_LIMIT_UNAVAILABLE", "Rate limiting service temporarily unavailable", ex);
            }
        }
    }

    @Override
    public void checkRateLimitOrThrow(String key, int maxRequests, Duration window, String message) {
        RateLimitResult result = checkRateLimit(key, maxRequests, window);
        if (!result.allowed()) {
            throw new RateLimitExceededException(
                    message != null ? message : "Too many requests. Please try again later.",
                    result.retryAfterSeconds()
            );
        }
    }

    @Override
    public void reset(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ex) {
            redisMetrics.redisFailure("ratelimit_reset");
            log.error("Failed to reset rate limit key [{}]: {}", sanitizeKey(key), ex.getMessage());
        }
    }

    private String extractCategoryFromKey(String key) {
        if (key == null) return "unknown";
        String[] parts = key.split(":");
        return parts.length >= 4 ? parts[3] : "general";
    }

    private String sanitizeKey(String key) {
        if (key == null) return "null";
        // Strip sensitive sub-identifiers in log output
        return key.replaceAll(":[a-f0-9-]{36}", ":[uuid]");
    }
}

package com.secretvault.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sanitized Micrometer metrics recorder for Redis operations, rate limits, and caches.
 * Uses only low-cardinality tags (category, domain, outcome) to prevent metric cardinality explosion.
 */
@Component
public class RedisMetrics {

    private final MeterRegistry meterRegistry;

    public RedisMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void rateLimitAllowed(String category) {
        if (meterRegistry != null) {
            Counter.builder("secretvault.redis.ratelimit.allowed")
                    .tag("category", sanitizeTag(category))
                    .description("Count of allowed rate limit checks")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void rateLimitDenied(String category) {
        if (meterRegistry != null) {
            Counter.builder("secretvault.redis.ratelimit.denied")
                    .tag("category", sanitizeTag(category))
                    .description("Count of denied rate limit checks")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void cacheHit(String domain) {
        if (meterRegistry != null) {
            Counter.builder("secretvault.redis.cache.hit")
                    .tag("domain", sanitizeTag(domain))
                    .description("Count of cache hits")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void cacheMiss(String domain) {
        if (meterRegistry != null) {
            Counter.builder("secretvault.redis.cache.miss")
                    .tag("domain", sanitizeTag(domain))
                    .description("Count of cache misses")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void securityStatePut(String category) {
        if (meterRegistry != null) {
            Counter.builder("secretvault.redis.security_state.put")
                    .tag("category", sanitizeTag(category))
                    .description("Count of security state entries stored")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void securityStateConsumed(String category) {
        if (meterRegistry != null) {
            Counter.builder("secretvault.redis.security_state.consumed")
                    .tag("category", sanitizeTag(category))
                    .description("Count of security state entries atomically consumed")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void redisFailure(String operation) {
        if (meterRegistry != null) {
            Counter.builder("secretvault.redis.failure")
                    .tag("operation", sanitizeTag(operation))
                    .description("Count of Redis infrastructure failures")
                    .register(meterRegistry)
                    .increment();
        }
    }

    private String sanitizeTag(String tag) {
        return tag != null ? tag.toLowerCase().replaceAll("[^a-z0-9_-]", "") : "unknown";
    }
}

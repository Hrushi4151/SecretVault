package com.secretvault.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.observability.RedisMetrics;
import com.secretvault.common.redis.RedisKeyBuilder;
import com.secretvault.common.redis.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Cache-aside metadata cache implementation with Redis.
 * Protects PostgreSQL with TTL-bounded caching while strictly rejecting
 * secret data and dynamic authorization decisions.
 */
@Service
public class RedisSafeCacheService implements SafeCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisSafeCacheService.class);

    private static final Set<String> FORBIDDEN_DOMAINS = Set.of(
            "secret", "secretversion", "accessdecision", "effectiveaccess",
            "grant", "jit", "token", "password", "key"
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final RedisProperties redisProperties;
    private final RedisMetrics redisMetrics;
    private final ObjectMapper objectMapper;

    public RedisSafeCacheService(
            StringRedisTemplate stringRedisTemplate,
            RedisKeyBuilder keyBuilder,
            RedisProperties redisProperties,
            RedisMetrics redisMetrics,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyBuilder = keyBuilder;
        this.redisProperties = redisProperties;
        this.redisMetrics = redisMetrics;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T getOrCompute(String domain, String identifier, Class<T> type, Duration ttl, Supplier<T> dbSupplier) {
        validateCacheSafety(domain, type);

        if (!redisProperties.getCache().isEnabled()) {
            return dbSupplier.get();
        }

        String key = keyBuilder.cacheKey(domain, identifier);

        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                redisMetrics.cacheHit(domain);
                return objectMapper.readValue(json, type);
            }
        } catch (Exception ex) {
            redisMetrics.redisFailure("cache_read");
            log.warn("Redis cache read error for key [{}], falling back to database: {}", sanitizeKey(key), ex.getMessage());
        }

        redisMetrics.cacheMiss(domain);
        T result = dbSupplier.get();

        if (result != null) {
            put(domain, identifier, result, ttl);
        }

        return result;
    }

    @Override
    public <T> void put(String domain, String identifier, T value, Duration ttl) {
        if (value == null || !redisProperties.getCache().isEnabled()) {
            return;
        }

        validateCacheSafety(domain, value.getClass());
        String key = keyBuilder.cacheKey(domain, identifier);
        long ttlSeconds = Math.max(1, ttl.toSeconds());

        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            redisMetrics.redisFailure("cache_write");
            log.warn("Failed to populate cache for key [{}]: {}", sanitizeKey(key), ex.getMessage());
        }
    }

    @Override
    public <T> Optional<T> get(String domain, String identifier, Class<T> type) {
        validateCacheSafety(domain, type);

        if (!redisProperties.getCache().isEnabled()) {
            return Optional.empty();
        }

        String key = keyBuilder.cacheKey(domain, identifier);

        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                redisMetrics.cacheHit(domain);
                return Optional.ofNullable(objectMapper.readValue(json, type));
            }
            redisMetrics.cacheMiss(domain);
            return Optional.empty();
        } catch (Exception ex) {
            redisMetrics.redisFailure("cache_get");
            log.warn("Redis cache get error for key [{}]: {}", sanitizeKey(key), ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void evict(String domain, String identifier) {
        String key = keyBuilder.cacheKey(domain, identifier);
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ex) {
            redisMetrics.redisFailure("cache_evict");
            log.warn("Failed to evict cache key [{}]: {}", sanitizeKey(key), ex.getMessage());
        }
    }

    private void validateCacheSafety(String domain, Class<?> type) {
        String domainLower = domain != null ? domain.toLowerCase() : "";
        String typeLower = type != null ? type.getSimpleName().toLowerCase() : "";

        for (String forbidden : FORBIDDEN_DOMAINS) {
            if (domainLower.contains(forbidden) || typeLower.contains(forbidden)) {
                log.error("CRITICAL SECURITY VIOLATION: Attempted to cache prohibited security entity [{}] under domain [{}]",
                        typeLower, domainLower);
                throw ApiException.internal("UNSAFE_CACHE_VIOLATION",
                        "Caching security credentials, authorization decisions, or secret payloads is strictly forbidden");
            }
        }
    }

    private String sanitizeKey(String key) {
        if (key == null) return "null";
        return key.replaceAll(":[a-f0-9-]{36}", ":[uuid]");
    }
}

package com.secretvault.common.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside contract for safe application metadata.
 * Explicitly rejects storage of sensitive entities and dynamic authorization decisions.
 */
public interface SafeCacheService {

    /**
     * Reads from Redis cache, falling back to database supplier on miss or Redis outage.
     * Automatically repopulates cache on DB hit.
     */
    <T> T getOrCompute(String domain, String identifier, Class<T> type, Duration ttl, Supplier<T> dbSupplier);

    /**
     * Manually puts a safe DTO into the cache.
     */
    <T> void put(String domain, String identifier, T value, Duration ttl);

    /**
     * Retrieves from cache without DB fallback.
     */
    <T> Optional<T> get(String domain, String identifier, Class<T> type);

    /**
     * Evicts a specific cache entry upon update or deletion in PostgreSQL.
     */
    void evict(String domain, String identifier);
}

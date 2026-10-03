package io.secretvault.sdk.cache;

import io.secretvault.sdk.model.SecretValue;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe, bounded, multi-tenant in-memory cache for SecretVault secrets.
 * Zero persistence to disk; LRU eviction when maximum capacity is exceeded.
 */
public class SecretCache {

    private final int maxCapacity;
    private final Duration ttl;
    private final Duration maxStale;
    private final Map<String, CacheEntry> store;

    public SecretCache() {
        this(500, Duration.ofSeconds(60), Duration.ofMinutes(5));
    }

    public SecretCache(int maxCapacity, Duration ttl, Duration maxStale) {
        this.maxCapacity = Math.max(10, maxCapacity);
        this.ttl = ttl != null ? ttl : Duration.ofSeconds(60);
        this.maxStale = maxStale != null ? maxStale : Duration.ofMinutes(5);

        // LRU bounded map
        this.store = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > SecretCache.this.maxCapacity;
            }
        });
    }

    public void put(CacheKey key, SecretValue value) {
        if (key == null || value == null) return;
        CacheEntry entry = new CacheEntry(value, ttl, maxStale);
        store.put(key.toKeyString(), entry);
    }

    public Optional<SecretValue> get(CacheKey key, boolean allowStale) {
        if (key == null) return Optional.empty();
        CacheEntry entry = store.get(key.toKeyString());
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isFresh()) {
            return Optional.of(entry.getSecretValue());
        }

        if (allowStale && entry.isStaleUsable()) {
            return Optional.of(entry.getSecretValue());
        }

        if (entry.isCompletelyExpired()) {
            store.remove(key.toKeyString());
        }

        return Optional.empty();
    }

    public void evict(CacheKey key) {
        if (key != null) {
            store.remove(key.toKeyString());
        }
    }

    public void evictScope(String workspace, String project, String environment) {
        String prefix = workspace + "::" + project + "::" + environment + "::";
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    public Duration getTtl() {
        return ttl;
    }

    public Duration getMaxStale() {
        return maxStale;
    }
}

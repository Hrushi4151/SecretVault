package io.secretvault.sdk.cache;

import io.secretvault.sdk.model.SecretValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecretCacheTest {

    @Test
    @DisplayName("Cache stores and retrieves fresh secrets within TTL")
    void testFreshCacheHit() {
        SecretCache cache = new SecretCache(100, Duration.ofSeconds(10), Duration.ofMinutes(1));
        CacheKey key = CacheKey.ofLatest("ws-a", "proj-a", "dev", "DB_PASS");
        SecretValue value = SecretValue.of("DB_PASS", "my-password", 1, "dev");

        cache.put(key, value);

        Optional<SecretValue> hit = cache.get(key, false);
        assertThat(hit).isPresent();
        assertThat(hit.get().value()).isEqualTo("my-password");
    }

    @Test
    @DisplayName("Cache enforces multi-tenant boundary isolation")
    void testTenantIsolation() {
        SecretCache cache = new SecretCache(100, Duration.ofSeconds(10), Duration.ofMinutes(1));
        CacheKey keyTenantA = CacheKey.ofLatest("ws-tenant-A", "proj-1", "prod", "STRIPE_KEY");
        CacheKey keyTenantB = CacheKey.ofLatest("ws-tenant-B", "proj-1", "prod", "STRIPE_KEY");

        cache.put(keyTenantA, SecretValue.of("STRIPE_KEY", "key-A", 1, "prod"));

        assertThat(cache.get(keyTenantA, false)).isPresent();
        assertThat(cache.get(keyTenantB, false)).isEmpty();
    }

    @Test
    @DisplayName("Cache honors bounded capacity with LRU eviction")
    void testLruEviction() {
        // Max capacity = 10 (minimum enforced is 10)
        SecretCache cache = new SecretCache(10, Duration.ofSeconds(60), Duration.ofMinutes(1));

        for (int i = 1; i <= 15; i++) {
            CacheKey key = CacheKey.ofLatest("ws", "proj", "dev", "KEY_" + i);
            cache.put(key, SecretValue.of("KEY_" + i, "val_" + i, 1, "dev"));
        }

        assertThat(cache.size()).isLessThanOrEqualTo(10);
        // Oldest entries (KEY_1, KEY_2, ...) should have been evicted
        assertThat(cache.get(CacheKey.ofLatest("ws", "proj", "dev", "KEY_1"), false)).isEmpty();
        // Newest entry (KEY_15) must exist
        assertThat(cache.get(CacheKey.ofLatest("ws", "proj", "dev", "KEY_15"), false)).isPresent();
    }

    @Test
    @DisplayName("Cache supports bounded stale retrieval in FAIL_OPEN mode")
    void testStaleCacheAccess() throws Exception {
        // TTL 50ms, maxStale 500ms
        SecretCache cache = new SecretCache(100, Duration.ofMillis(50), Duration.ofMillis(500));
        CacheKey key = CacheKey.ofLatest("ws", "proj", "dev", "API_KEY");
        cache.put(key, SecretValue.of("API_KEY", "stale-fallback-val", 1, "dev"));

        // Wait past TTL
        Thread.sleep(80);

        // Strict fresh get should be empty
        assertThat(cache.get(key, false)).isEmpty();

        // Stale get should succeed
        Optional<SecretValue> staleHit = cache.get(key, true);
        assertThat(staleHit).isPresent();
        assertThat(staleHit.get().value()).isEqualTo("stale-fallback-val");

        // Wait past maxStale
        Thread.sleep(550);

        // Even stale get must now be empty
        assertThat(cache.get(key, true)).isEmpty();
    }
}

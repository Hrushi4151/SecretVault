package com.secretvault.common.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisKeyBuilderTest {

    @Test
    @DisplayName("Builds environment-isolated rate limit key with correct namespace")
    void testRateLimitKey() {
        RedisKeyBuilder builder = new RedisKeyBuilder("production");
        String key = builder.rateLimitKey("auth_login", "192.168.1.1");
        assertEquals("secretvault:production:ratelimit:auth_login:192.168.1.1", key);
    }

    @Test
    @DisplayName("Builds environment-isolated security state key")
    void testSecurityStateKey() {
        RedisKeyBuilder builder = new RedisKeyBuilder("staging");
        String key = builder.securityStateKey("mfa_challenge", "chal-12345");
        assertEquals("secretvault:staging:security:mfa_challenge:chal-12345", key);
    }

    @Test
    @DisplayName("Builds application metadata cache key")
    void testCacheKey() {
        RedisKeyBuilder builder = new RedisKeyBuilder("dev");
        String key = builder.cacheKey("workspace", "ws-abcd-999");
        assertEquals("secretvault:dev:cache:workspace:ws-abcd-999", key);
    }

    @Test
    @DisplayName("Builds idempotency key")
    void testIdempotencyKey() {
        RedisKeyBuilder builder = new RedisKeyBuilder("local");
        String key = builder.idempotencyKey("secret_create", "idem-uuid-111");
        assertEquals("secretvault:local:idempotency:secret_create:idem-uuid-111", key);
    }

    @Test
    @DisplayName("Sanitizes spaces, colons, and illegal characters in keys to prevent key injection")
    void testKeySanitization() {
        RedisKeyBuilder builder = new RedisKeyBuilder("prod");
        String key = builder.buildKey("rate limit", "auth:login", "user@corp.com:bad-key!");
        assertEquals("secretvault:prod:rate_limit:auth_login:usercorp.com_bad-key", key);
    }

    @Test
    @DisplayName("Handles empty or null environment gracefully by defaulting")
    void testDefaultEnvironment() {
        RedisKeyBuilder builder = new RedisKeyBuilder("");
        assertEquals("default", builder.getEnvironment());
    }
}

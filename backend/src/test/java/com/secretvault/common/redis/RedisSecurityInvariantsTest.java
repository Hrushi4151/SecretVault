package com.secretvault.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.cache.RedisSafeCacheService;
import com.secretvault.common.cache.dto.WorkspaceSummaryCacheDto;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.observability.RedisMetrics;
import com.secretvault.common.ratelimit.RateLimitExceededException;
import com.secretvault.common.ratelimit.RedisRateLimiter;
import com.secretvault.common.security.state.RedisSecurityStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Security invariant verification suite for Redis infrastructure.
 * Verifies zero secret leakage, fail-closed security policies, replay protection,
 * and tenant isolation.
 */
@ExtendWith(MockitoExtension.class)
public class RedisSecurityInvariantsTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisMetrics redisMetrics;

    private RedisKeyBuilder keyBuilder;
    private RedisProperties redisProperties;
    private ObjectMapper objectMapper;

    private RedisRateLimiter rateLimiter;
    private RedisSecurityStateStore stateStore;
    private RedisSafeCacheService cacheService;

    @BeforeEach
    void setUp() {
        keyBuilder = new RedisKeyBuilder("test");
        redisProperties = new RedisProperties();
        redisProperties.getRateLimit().setEnabled(true);
        redisProperties.getRateLimit().setFailOpen(false);
        redisProperties.getCache().setEnabled(true);

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        rateLimiter = new RedisRateLimiter(stringRedisTemplate, redisProperties, redisMetrics);
        stateStore = new RedisSecurityStateStore(stringRedisTemplate, keyBuilder, redisMetrics, objectMapper);
        cacheService = new RedisSafeCacheService(stringRedisTemplate, keyBuilder, redisProperties, redisMetrics, objectMapper);
    }

    public record SafeChallenge(String challengeId, String userId) {}

    @Test
    @DisplayName("Security Invariant 1: Plaintext secret entities are strictly prohibited from Redis storage")
    void testPlaintextSecretStorageProhibited() {
        class SecretValuePayload {
            public String decryptedValue = "super_secret_plaintext_value";
        }

        ApiException ex = assertThrows(ApiException.class, () ->
                stateStore.put("secret_data", "sec-123", new SecretValuePayload(), Duration.ofMinutes(5))
        );

        assertEquals("SECURITY_INVARIANT_VIOLATION", ex.getCode());
    }

    @Test
    @DisplayName("Security Invariant 2: Dynamic authorization decisions are strictly rejected from application cache")
    void testAuthorizationDecisionCachingProhibited() {
        ApiException ex = assertThrows(ApiException.class, () ->
                cacheService.getOrCompute("accessdecision", "user-grant-1", String.class, Duration.ofMinutes(5), () -> "ALLOW")
        );

        assertEquals("UNSAFE_CACHE_VIOLATION", ex.getCode());
    }

    @Test
    @DisplayName("Security Invariant 3: Single-use atomic consumption prevents replay of security challenges")
    void testChallengeReplayProtection() throws Exception {
        SafeChallenge challenge = new SafeChallenge("chal-uuid", "user-uuid");
        com.secretvault.common.security.state.SecurityStateEnvelope<SafeChallenge> envelope =
                com.secretvault.common.security.state.SecurityStateEnvelope.create("mfa", "chal-uuid", challenge, 300);
        String json = objectMapper.writeValueAsString(envelope);

        // First consume returns challenge
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("secretvault:test:security:mfa:chal-uuid"))))
                .thenReturn(json);

        Optional<SafeChallenge> first = stateStore.consumeAtomic("mfa", "chal-uuid", SafeChallenge.class);
        assertTrue(first.isPresent());
        assertEquals("chal-uuid", first.get().challengeId());

        // Second consume (replay) returns null
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(null);

        Optional<SafeChallenge> replay = stateStore.consumeAtomic("mfa", "chal-uuid", SafeChallenge.class);
        assertTrue(replay.isEmpty());
    }

    @Test
    @DisplayName("Security Invariant 4: Rate limit counter excess throws HTTP 429 with correct Retry-After")
    void testRateLimitThrows429WithRetryAfter() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of(25L, 30L));

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                rateLimiter.checkRateLimitOrThrow("secretvault:test:ratelimit:auth_login:ip_1.2.3.4", 10, Duration.ofSeconds(60), "Rate limit exceeded")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals(30, ex.getRetryAfterSeconds());
        assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
    }

    @Test
    @DisplayName("Security Invariant 5: Safe metadata cache evicts entry on update/deletion")
    void testCacheEvictionOnMutation() {
        UUID wsId = UUID.randomUUID();
        cacheService.evict("workspace", wsId.toString());

        verify(stringRedisTemplate).delete("secretvault:test:cache:workspace:" + wsId);
    }

    @Test
    @DisplayName("Security Invariant 6: Tenant cache isolation ensures distinct keys per workspace")
    void testTenantCacheIsolation() {
        UUID wsA = UUID.randomUUID();
        UUID wsB = UUID.randomUUID();

        String keyA = keyBuilder.cacheKey("workspace", wsA.toString());
        String keyB = keyBuilder.cacheKey("workspace", wsB.toString());

        assertNotEquals(keyA, keyB);
        assertTrue(keyA.startsWith("secretvault:test:cache:workspace:"));
        assertTrue(keyB.startsWith("secretvault:test:cache:workspace:"));
    }
}

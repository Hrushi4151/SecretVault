package com.secretvault.common.security.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.observability.RedisMetrics;
import com.secretvault.common.redis.RedisKeyBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSecurityStateStoreTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisMetrics redisMetrics;

    private RedisKeyBuilder keyBuilder;
    private ObjectMapper objectMapper;
    private RedisSecurityStateStore stateStore;

    @BeforeEach
    void setUp() {
        keyBuilder = new RedisKeyBuilder("test");
        objectMapper = new ObjectMapper();
        stateStore = new RedisSecurityStateStore(stringRedisTemplate, keyBuilder, redisMetrics, objectMapper);
    }

    public record TestMfaChallenge(String challengeId, String userId, String email) {}

    @Test
    @DisplayName("Store and retrieve valid non-expired security challenge")
    void testPutAndGet() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        TestMfaChallenge challenge = new TestMfaChallenge("chal-123", "user-456", "user@test.com");
        SecurityStateEnvelope<TestMfaChallenge> envelope = SecurityStateEnvelope.create("mfa_challenge", "chal-123", challenge, 300);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            when(valueOperations.get(anyString())).thenReturn(json);
        } catch (Exception e) {
            fail(e);
        }

        Optional<TestMfaChallenge> result = stateStore.get("mfa_challenge", "chal-123", TestMfaChallenge.class);

        assertTrue(result.isPresent());
        assertEquals("chal-123", result.get().challengeId());
        assertEquals("user-456", result.get().userId());
    }

    @Test
    @DisplayName("consumeAtomic atomically retrieves payload and deletes key (replay protection)")
    void testConsumeAtomicSingleUse() throws Exception {
        TestMfaChallenge challenge = new TestMfaChallenge("chal-123", "user-456", "user@test.com");
        SecurityStateEnvelope<TestMfaChallenge> envelope = SecurityStateEnvelope.create("mfa_challenge", "chal-123", challenge, 300);
        String json = objectMapper.writeValueAsString(envelope);

        // First atomic consume call returns the stored JSON
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("secretvault:test:security:mfa_challenge:chal-123"))))
                .thenReturn(json);

        Optional<TestMfaChallenge> firstCall = stateStore.consumeAtomic("mfa_challenge", "chal-123", TestMfaChallenge.class);
        assertTrue(firstCall.isPresent());
        assertEquals("chal-123", firstCall.get().challengeId());
        verify(redisMetrics).securityStateConsumed("mfa_challenge");

        // Second call (or replay attack) returns null from Redis
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(null);

        Optional<TestMfaChallenge> replayCall = stateStore.consumeAtomic("mfa_challenge", "chal-123", TestMfaChallenge.class);
        assertTrue(replayCall.isEmpty());
    }

    @Test
    @DisplayName("Increment attempts counter atomically via Lua script")
    void testIncrementAttempts() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(3L);

        long attempts = stateStore.incrementAttempts("mfa_challenge", "chal-123", Duration.ofMinutes(5));
        assertEquals(3L, attempts);
    }

    @Test
    @DisplayName("Fail closed: Redis failure during state get or consume returns empty Optional (blocks bypass)")
    void testFailClosedOnRedisOutage() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        Optional<TestMfaChallenge> result = stateStore.consumeAtomic("mfa_challenge", "chal-123", TestMfaChallenge.class);

        assertTrue(result.isEmpty());
        verify(redisMetrics).redisFailure("security_state_consume");
    }

    @Test
    @DisplayName("Security invariant: Rejects storing raw secret or password classes in Redis")
    void testSecurityInvariantViolation() {
        class SecretPayload {
            public String secretValue = "plaintext";
        }

        ApiException ex = assertThrows(ApiException.class, () ->
                stateStore.put("secret_backup", "sec-1", new SecretPayload(), Duration.ofMinutes(5))
        );

        assertEquals("SECURITY_INVARIANT_VIOLATION", ex.getCode());
    }
}

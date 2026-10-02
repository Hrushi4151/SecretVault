package com.secretvault.common.security.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.observability.RedisMetrics;
import com.secretvault.common.redis.RedisKeyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * Redis-backed atomic short-lived security state store.
 * Enforces single-use atomic token consumption (Lua atomic GET-and-DEL),
 * attempt counting, and fail-closed security policies.
 */
@Component
public class RedisSecurityStateStore implements SecurityStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSecurityStateStore.class);

    private static final String CONSUME_ATOMIC_LUA =
            "local val = redis.call('GET', KEYS[1])\n" +
            "if val then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "    return val\n" +
            "end\n" +
            "return nil";

    private static final String INCR_ATTEMPTS_LUA =
            "local key = KEYS[1] .. ':attempts'\n" +
            "local count = redis.call('INCR', key)\n" +
            "if tonumber(count) == 1 then\n" +
            "    redis.call('EXPIRE', key, tonumber(ARGV[1]))\n" +
            "end\n" +
            "return count";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final RedisMetrics redisMetrics;
    private final ObjectMapper objectMapper;
    private final RedisScript<String> consumeScript;
    private final RedisScript<Long> incrAttemptsScript;

    public RedisSecurityStateStore(
            StringRedisTemplate stringRedisTemplate,
            RedisKeyBuilder keyBuilder,
            RedisMetrics redisMetrics,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyBuilder = keyBuilder;
        this.redisMetrics = redisMetrics;
        this.objectMapper = objectMapper;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_ATOMIC_LUA, String.class);
        this.incrAttemptsScript = new DefaultRedisScript<>(INCR_ATTEMPTS_LUA, Long.class);
    }

    @Override
    public <T> void put(String category, String identifier, T payload, Duration ttl) {
        validateSecurityInvariant(category, payload);
        String key = keyBuilder.securityStateKey(category, identifier);
        long ttlSeconds = Math.max(1, ttl.toSeconds());

        try {
            SecurityStateEnvelope<T> envelope = SecurityStateEnvelope.create(category, identifier, payload, ttlSeconds);
            String json = objectMapper.writeValueAsString(envelope);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            redisMetrics.securityStatePut(category);
        } catch (Exception ex) {
            redisMetrics.redisFailure("security_state_put");
            log.error("Failed to store security state for key [{}]: {}", sanitizeKey(key), ex.getMessage());
            throw ApiException.internal("SECURITY_STATE_ERROR", "Failed to initialize security challenge", ex);
        }
    }

    @Override
    public <T> Optional<T> get(String category, String identifier, Class<T> type) {
        String key = keyBuilder.securityStateKey(category, identifier);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }

            SecurityStateEnvelope<T> envelope = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructParametricType(SecurityStateEnvelope.class, type)
            );

            if (envelope.isExpired()) {
                delete(category, identifier);
                return Optional.empty();
            }

            return Optional.ofNullable(envelope.payload());
        } catch (Exception ex) {
            redisMetrics.redisFailure("security_state_get");
            log.error("Failed to read security state for key [{}]: {}", sanitizeKey(key), ex.getMessage());
            // Fail closed: never return corrupt or forged security state
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> consumeAtomic(String category, String identifier, Class<T> type) {
        String key = keyBuilder.securityStateKey(category, identifier);
        try {
            String json = stringRedisTemplate.execute(consumeScript, Collections.singletonList(key));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }

            SecurityStateEnvelope<T> envelope = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructParametricType(SecurityStateEnvelope.class, type)
            );

            if (envelope.isExpired()) {
                return Optional.empty();
            }

            redisMetrics.securityStateConsumed(category);
            return Optional.ofNullable(envelope.payload());
        } catch (Exception ex) {
            redisMetrics.redisFailure("security_state_consume");
            log.error("Failed to atomically consume security state for key [{}]: {}", sanitizeKey(key), ex.getMessage());
            // Fail closed: return empty, preventing bypass
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String category, String identifier) {
        String key = keyBuilder.securityStateKey(category, identifier);
        try {
            Boolean hasKey = stringRedisTemplate.hasKey(key);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception ex) {
            redisMetrics.redisFailure("security_state_exists");
            log.error("Failed to check security state existence for key [{}]: {}", sanitizeKey(key), ex.getMessage());
            return false;
        }
    }

    @Override
    public void delete(String category, String identifier) {
        String key = keyBuilder.securityStateKey(category, identifier);
        try {
            stringRedisTemplate.delete(key);
            stringRedisTemplate.delete(key + ":attempts");
        } catch (Exception ex) {
            redisMetrics.redisFailure("security_state_delete");
            log.error("Failed to delete security state for key [{}]: {}", sanitizeKey(key), ex.getMessage());
        }
    }

    @Override
    public long incrementAttempts(String category, String identifier, Duration ttl) {
        String key = keyBuilder.securityStateKey(category, identifier);
        long ttlSeconds = Math.max(1, ttl.toSeconds());
        try {
            Long count = stringRedisTemplate.execute(
                    incrAttemptsScript,
                    Collections.singletonList(key),
                    String.valueOf(ttlSeconds)
            );
            return count != null ? count : 1L;
        } catch (Exception ex) {
            redisMetrics.redisFailure("security_state_increment_attempts");
            log.error("Failed to increment security attempts for key [{}]: {}", sanitizeKey(key), ex.getMessage());
            return 1L;
        }
    }

    private <T> void validateSecurityInvariant(String category, T payload) {
        if (payload == null) {
            throw ApiException.badRequest("Security state payload cannot be null");
        }
        String payloadClass = payload.getClass().getSimpleName().toLowerCase();
        if (payloadClass.contains("secret") || payloadClass.contains("password") || payloadClass.contains("totp") || payloadClass.contains("privatekey")) {
            log.error("CRITICAL SECURITY VIOLATION: Attempted to store sensitive entity [{}] in Redis", payloadClass);
            throw ApiException.internal("SECURITY_INVARIANT_VIOLATION", "Storing raw secret/credential types in Redis is strictly forbidden");
        }
    }

    private String sanitizeKey(String key) {
        if (key == null) return "null";
        return key.replaceAll(":[a-f0-9-]{36}", ":[uuid]");
    }
}

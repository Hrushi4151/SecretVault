package com.secretvault.rotation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed Lock coordinator for Secret Rotations.
 * Uses Redis set-if-absent with safe TTL, with in-memory fallback if Redis is unreachable.
 */
@Component
public class RotationDistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RotationDistributedLock.class);
    private static final String LOCK_PREFIX = "secretvault:lock:rotation:";

    private final StringRedisTemplate redisTemplate;
    private final Map<UUID, String> localLocks = new ConcurrentHashMap<>();

    public RotationDistributedLock(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to acquire an exclusive rotation lock for a secret.
     *
     * @param secretId  The secret UUID to lock
     * @param lockToken Unique lock identifier / worker ID
     * @param ttl       Time-to-live for the lock
     * @return true if acquired, false otherwise
     */
    public boolean acquireLock(UUID secretId, String lockToken, Duration ttl) {
        String key = LOCK_PREFIX + secretId.toString();

        if (redisTemplate != null) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, lockToken, ttl);
                if (Boolean.TRUE.equals(acquired)) {
                    log.debug("Acquired Redis rotation lock for secret {} with token {}", secretId, lockToken);
                    return true;
                }
                log.warn("Redis rotation lock for secret {} is already held by another worker", secretId);
                return false;
            } catch (Exception e) {
                log.warn("Redis lock acquisition failed, using memory fallback: {}", e.getMessage());
            }
        }

        // Local fallback
        String existing = localLocks.putIfAbsent(secretId, lockToken);
        return existing == null;
    }

    /**
     * Releases the rotation lock if the token matches.
     */
    public void releaseLock(UUID secretId, String lockToken) {
        String key = LOCK_PREFIX + secretId.toString();

        if (redisTemplate != null) {
            try {
                String current = redisTemplate.opsForValue().get(key);
                if (lockToken.equals(current)) {
                    redisTemplate.delete(key);
                    log.debug("Released Redis rotation lock for secret {}", secretId);
                }
            } catch (Exception e) {
                log.warn("Redis lock release failed: {}", e.getMessage());
            }
        }

        localLocks.remove(secretId, lockToken);
    }
}

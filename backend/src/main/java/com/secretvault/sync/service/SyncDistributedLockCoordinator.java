package com.secretvault.sync.service;

import com.secretvault.common.exception.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cross-instance distributed lock coordinator for SecretVault Sync Center.
 * Coordinates reconciliation executions and scheduled scans across multi-instance deployments.
 * <p>
 * Supported capabilities:
 * - Redis set-if-absent (SET NX PX) leases with unique ownership tokens
 * - Monotonically increasing fencing sequence generation per target
 * - Active lease renewal heartbeat to prevent premature lease expiration during long mutations
 * - Ownership verification before each operation and before lock release
 * - Fail-closed enforcement: if Redis is unavailable, throws 503 unless explicit local fallback is configured
 * - Atomic Lua script renewal (pexpire) and release (del)
 */
@Component
public class SyncDistributedLockCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SyncDistributedLockCoordinator.class);

    private static final String EXECUTION_LOCK_PREFIX = "secretvault:lock:sync:exec:";
    private static final String SCAN_LOCK_PREFIX = "secretvault:lock:sync:scan:";
    private static final String FENCING_KEY_PREFIX = "secretvault:lock:sync:fence:";

    // Atomic Lua script to ensure release only succeeds if the token matches
    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(RELEASE_LOCK_LUA, Long.class);

    // Atomic Lua script to extend lease duration only if ownership token matches
    private static final String RENEW_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";
    private static final DefaultRedisScript<Long> RENEW_SCRIPT =
            new DefaultRedisScript<>(RENEW_LOCK_LUA, Long.class);

    @Value("${secretvault.sync.lock.lease-duration-ms:60000}")
    private long defaultLeaseDurationMs = 60000; // 60 seconds default (fast failure recovery)

    @Value("${secretvault.sync.lock.renewal-interval-ms:10000}")
    private long defaultRenewalIntervalMs = 10000; // 10 seconds default (6 renewals per lease window)

    @Value("${secretvault.sync.lock.allow-local-fallback:false}")
    private boolean allowLocalFallback = false; // FAIL CLOSED by default in production!

    @Autowired(required = false)
    private Environment environment;

    private StringRedisTemplate redisTemplate;

    // Fallback store when explicit local fallback is permitted (e.g. dev/test profiles)
    private final Map<String, FallbackLockEntry> fallbackLocks = new ConcurrentHashMap<>();
    private final AtomicLong fallbackFencingSequence = new AtomicLong(0);

    // Heartbeat scheduler for lease renewals
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "sync-lock-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public SyncDistributedLockCoordinator(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public SyncDistributedLockCoordinator() {
        this(null, false);
    }

    public SyncDistributedLockCoordinator(StringRedisTemplate redisTemplate, boolean allowLocalFallback) {
        this.redisTemplate = redisTemplate;
        this.allowLocalFallback = allowLocalFallback;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (environment != null) {
            String[] activeProfiles = environment.getActiveProfiles();
            boolean isProd = false;
            for (String profile : activeProfiles) {
                if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                    isProd = true;
                    break;
                }
            }
            String redisEnv = environment.getProperty("secretvault.redis.environment");
            if ("production".equalsIgnoreCase(redisEnv) || isProd) {
                if (this.allowLocalFallback) {
                    log.warn("CRITICAL: Local fallback was enabled in a production environment! Overriding allowLocalFallback=false for strict fail-closed distributed safety.");
                    this.allowLocalFallback = false;
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
    }

    /**
     * Acquires an exclusive execution lock handle with automatic heartbeat renewal and fencing token.
     *
     * @param workspaceId     The tenant/workspace UUID
     * @param scopeResourceId Target resource UUID (or null for workspace-wide)
     * @param ttl             Lease duration
     * @param renewalInterval Heartbeat renewal interval (or null for default)
     * @return SyncLockHandle if acquired, null if already held by another worker
     * @throws ApiException (503 Service Unavailable) if Redis is unavailable and fallback is disabled
     */
    public SyncLockHandle acquireExecutionLockHandle(
            UUID workspaceId,
            UUID scopeResourceId,
            Duration ttl,
            Duration renewalInterval
    ) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Duration effectiveTtl = ttl != null ? ttl : Duration.ofMillis(defaultLeaseDurationMs);
        Duration effectiveRenewal = renewalInterval != null ? renewalInterval : Duration.ofMillis(defaultRenewalIntervalMs);

        String lockKey = buildExecutionKey(workspaceId, scopeResourceId);
        String lockToken = UUID.randomUUID().toString();
        long fencingToken;

        if (redisTemplate != null) {
            try {
                String fenceKey = buildFencingKey(workspaceId, scopeResourceId);
                Long incVal = redisTemplate.opsForValue().increment(fenceKey);
                fencingToken = incVal != null ? incVal : System.currentTimeMillis();

                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, effectiveTtl);
                if (!Boolean.TRUE.equals(acquired)) {
                    log.info("Distributed lock [{}] is already held by another instance/worker", lockKey);
                    return null;
                }
                log.debug("Acquired distributed lock [{}] with token [{}] and fencing token [{}] for {}", lockKey, lockToken, fencingToken, effectiveTtl);
            } catch (Exception e) {
                handleRedisFailure("acquireExecutionLock", lockKey, e);
                // If fallback is enabled, proceed to fallback logic below
                fencingToken = fallbackFencingSequence.incrementAndGet();
                boolean acquired = tryAcquireLocal(lockKey, lockToken, effectiveTtl);
                if (!acquired) return null;
            }
        } else {
            if (!allowLocalFallback) {
                log.error("Redis is unavailable and allow-local-fallback is false. Failing closed.");
                throw ApiException.serviceUnavailable("Distributed lock coordination is unavailable: Redis is not configured. Failing closed to prevent uncoordinated mutations.");
            }
            fencingToken = fallbackFencingSequence.incrementAndGet();
            boolean acquired = tryAcquireLocal(lockKey, lockToken, effectiveTtl);
            if (!acquired) return null;
        }

        SyncLockHandle handle = new SyncLockHandle(this, lockKey, lockToken, fencingToken, effectiveTtl);

        // Schedule lease renewal heartbeat
        if (effectiveRenewal.toMillis() > 0 && effectiveRenewal.compareTo(effectiveTtl) < 0) {
            ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (!handle.isActive()) return;
                    boolean renewed = handle.renewLease();
                    if (!renewed) {
                        log.warn("Distributed lock lease renewal failed for [{}] with token [{}]. Ownership lost.", lockKey, lockToken);
                    }
                } catch (Exception e) {
                    log.warn("Exception during lease renewal heartbeat for [{}]: {}", lockKey, e.getMessage());
                    handle.markOwnershipLost();
                }
            }, effectiveRenewal.toMillis(), effectiveRenewal.toMillis(), TimeUnit.MILLISECONDS);
            handle.setRenewalFuture(future);
        }

        return handle;
    }

    public SyncLockHandle acquireExecutionLockHandle(UUID workspaceId, UUID scopeResourceId) {
        return acquireExecutionLockHandle(workspaceId, scopeResourceId, Duration.ofMillis(defaultLeaseDurationMs), Duration.ofMillis(defaultRenewalIntervalMs));
    }

    /**
     * Backward-compatible simple execution lock acquisition.
     */
    public boolean acquireExecutionLock(UUID workspaceId, UUID scopeResourceId, String lockToken, Duration ttl) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(lockToken, "lockToken must not be null");
        Duration effectiveTtl = ttl != null ? ttl : Duration.ofMillis(defaultLeaseDurationMs);

        String key = buildExecutionKey(workspaceId, scopeResourceId);
        return tryAcquire(key, lockToken, effectiveTtl);
    }

    /**
     * Backward-compatible simple execution lock release.
     */
    public void releaseExecutionLock(UUID workspaceId, UUID scopeResourceId, String lockToken) {
        if (workspaceId == null || lockToken == null) return;
        String key = buildExecutionKey(workspaceId, scopeResourceId);
        tryRelease(key, lockToken);
    }

    /**
     * Attempts to acquire an exclusive drift scan lock for a workspace.
     */
    public boolean acquireScanLock(UUID workspaceId, String lockToken, Duration ttl) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(lockToken, "lockToken must not be null");
        Duration effectiveTtl = ttl != null ? ttl : Duration.ofMinutes(5);

        String key = SCAN_LOCK_PREFIX + workspaceId;
        return tryAcquire(key, lockToken, effectiveTtl);
    }

    /**
     * Releases a drift scan lock if the ownership token matches.
     */
    public void releaseScanLock(UUID workspaceId, String lockToken) {
        if (workspaceId == null || lockToken == null) return;
        String key = SCAN_LOCK_PREFIX + workspaceId;
        tryRelease(key, lockToken);
    }

    /**
     * Extends lease expiration atomically if the ownership token matches.
     */
    public boolean renewLease(String key, String lockToken, Duration ttl) {
        if (key == null || lockToken == null) return false;
        long ttlMs = ttl != null ? ttl.toMillis() : defaultLeaseDurationMs;

        if (redisTemplate != null) {
            try {
                Long result = redisTemplate.execute(RENEW_SCRIPT, Collections.singletonList(key), lockToken, String.valueOf(ttlMs));
                return result != null && result > 0;
            } catch (Exception e) {
                log.warn("Redis error renewing lease for [{}]: {}", key, e.getMessage());
                if (!allowLocalFallback) {
                    return false;
                }
            }
        }

        // Local fallback renewal
        Instant now = Instant.now();
        synchronized (fallbackLocks) {
            FallbackLockEntry existing = fallbackLocks.get(key);
            if (existing != null && existing.token().equals(lockToken) && existing.expiresAt().isAfter(now)) {
                fallbackLocks.put(key, new FallbackLockEntry(lockToken, now.plus(ttl != null ? ttl : Duration.ofMillis(defaultLeaseDurationMs))));
                return true;
            }
            return false;
        }
    }

    /**
     * Verifies whether the specified lock key is currently owned by lockToken.
     */
    public boolean verifyOwnership(String key, String lockToken) {
        if (key == null || lockToken == null) return false;

        if (redisTemplate != null) {
            try {
                String current = redisTemplate.opsForValue().get(key);
                return lockToken.equals(current);
            } catch (Exception e) {
                log.warn("Redis error verifying lock ownership for [{}]: {}", key, e.getMessage());
                if (!allowLocalFallback) {
                    return false;
                }
            }
        }

        Instant now = Instant.now();
        synchronized (fallbackLocks) {
            FallbackLockEntry existing = fallbackLocks.get(key);
            return existing != null && existing.token().equals(lockToken) && existing.expiresAt().isAfter(now);
        }
    }

    public void tryRelease(String key, String lockToken) {
        if (redisTemplate != null) {
            try {
                Long result = redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), lockToken);
                if (result != null && result > 0) {
                    log.debug("Atomically released distributed lock [{}] for token [{}]", key, lockToken);
                } else {
                    log.debug("Lock [{}] was either expired or held by different token; release skipped", key);
                }
            } catch (Exception e) {
                log.warn("Redis lock release error on [{}]: {}", key, e.getMessage());
            }
        }

        synchronized (fallbackLocks) {
            FallbackLockEntry existing = fallbackLocks.get(key);
            if (existing != null && existing.token().equals(lockToken)) {
                fallbackLocks.remove(key);
            }
        }
    }

    public void evictLockForTesting(String key) {
        fallbackLocks.remove(key);
    }

    private boolean tryAcquire(String key, String lockToken, Duration ttl) {
        if (redisTemplate != null) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, lockToken, ttl);
                if (Boolean.TRUE.equals(acquired)) {
                    log.debug("Acquired distributed lock [{}] with token [{}] for {}", key, lockToken, ttl);
                    return true;
                }
                log.info("Distributed lock [{}] is already held by another instance/worker", key);
                return false;
            } catch (Exception e) {
                handleRedisFailure("tryAcquire", key, e);
            }
        } else if (!allowLocalFallback) {
            log.error("Redis is unavailable and allow-local-fallback is false. Failing closed.");
            throw ApiException.serviceUnavailable("Distributed lock coordination is unavailable: Redis is not configured. Failing closed to prevent uncoordinated mutations.");
        }

        return tryAcquireLocal(key, lockToken, ttl);
    }

    private boolean tryAcquireLocal(String key, String lockToken, Duration ttl) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);

        synchronized (fallbackLocks) {
            FallbackLockEntry existing = fallbackLocks.get(key);
            if (existing != null && existing.expiresAt().isAfter(now)) {
                return false;
            }
            fallbackLocks.put(key, new FallbackLockEntry(lockToken, expiry));
            return true;
        }
    }

    private void handleRedisFailure(String operation, String key, Exception e) {
        if (!allowLocalFallback) {
            log.error("Redis coordination failure during [{}] on [{}]: {}. Failing closed to prevent multi-instance split-brain.", operation, key, e.getMessage());
            throw ApiException.serviceUnavailable("Distributed lock coordination is unavailable: Redis connection failed. Failing closed to prevent concurrent mutations.");
        }
        log.warn("Redis lock error during [{}] on [{}], falling back to local coordination (DEV/TEST MODE ONLY): {}", operation, key, e.getMessage());
    }

    private String buildExecutionKey(UUID workspaceId, UUID scopeResourceId) {
        return EXECUTION_LOCK_PREFIX + workspaceId + ":" + (scopeResourceId != null ? scopeResourceId : "ALL");
    }

    private String buildFencingKey(UUID workspaceId, UUID scopeResourceId) {
        return FENCING_KEY_PREFIX + workspaceId + ":" + (scopeResourceId != null ? scopeResourceId : "ALL");
    }

    public boolean isAllowLocalFallback() {
        return allowLocalFallback;
    }

    public void setAllowLocalFallback(boolean allowLocalFallback) {
        this.allowLocalFallback = allowLocalFallback;
    }

    public long getDefaultLeaseDurationMs() {
        return defaultLeaseDurationMs;
    }

    public void setDefaultLeaseDurationMs(long defaultLeaseDurationMs) {
        this.defaultLeaseDurationMs = defaultLeaseDurationMs;
    }

    public long getDefaultRenewalIntervalMs() {
        return defaultRenewalIntervalMs;
    }

    public void setDefaultRenewalIntervalMs(long defaultRenewalIntervalMs) {
        this.defaultRenewalIntervalMs = defaultRenewalIntervalMs;
    }

    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private record FallbackLockEntry(String token, Instant expiresAt) {}

    /**
     * Managed lock handle providing active lease tracking, heartbeat renewal, and ownership verification.
     */
    public static class SyncLockHandle implements AutoCloseable {
        private final SyncDistributedLockCoordinator coordinator;
        private final String lockKey;
        private final String lockToken;
        private final long fencingToken;
        private final Duration leaseDuration;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean ownershipLost = new AtomicBoolean(false);
        private ScheduledFuture<?> renewalFuture;

        public SyncLockHandle(
                SyncDistributedLockCoordinator coordinator,
                String lockKey,
                String lockToken,
                long fencingToken,
                Duration leaseDuration
        ) {
            this.coordinator = coordinator;
            this.lockKey = lockKey;
            this.lockToken = lockToken;
            this.fencingToken = fencingToken;
            this.leaseDuration = leaseDuration;
        }

        public String getLockKey() { return lockKey; }
        public String getLockToken() { return lockToken; }
        public long getFencingToken() { return fencingToken; }
        public boolean isActive() { return active.get(); }
        public boolean isOwnershipLost() { return ownershipLost.get(); }

        public void markOwnershipLost() {
            ownershipLost.set(true);
            if (renewalFuture != null) {
                renewalFuture.cancel(false);
            }
        }

        void setRenewalFuture(ScheduledFuture<?> renewalFuture) {
            this.renewalFuture = renewalFuture;
        }

        /**
         * Checks whether the current lease is actively held and unexpired.
         * Fails fast if renewal previously failed or if a remote ownership check detects a lost lock.
         */
        public boolean isOwnershipValid() {
            if (!active.get() || ownershipLost.get()) {
                return false;
            }
            boolean valid = coordinator.verifyOwnership(lockKey, lockToken);
            if (!valid) {
                markOwnershipLost();
            }
            return valid;
        }

        /**
         * Actively renews the lease duration with the coordinator.
         */
        public boolean renewLease() {
            if (!active.get() || ownershipLost.get()) {
                return false;
            }
            boolean renewed = coordinator.renewLease(lockKey, lockToken, leaseDuration);
            if (!renewed) {
                markOwnershipLost();
            }
            return renewed;
        }

        @Override
        public void close() {
            active.set(false);
            if (renewalFuture != null) {
                renewalFuture.cancel(false);
            }
            coordinator.tryRelease(lockKey, lockToken);
        }
    }
}

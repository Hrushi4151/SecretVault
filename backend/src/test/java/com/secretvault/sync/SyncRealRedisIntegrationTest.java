package com.secretvault.sync;

import com.secretvault.sync.service.SyncDistributedLockCoordinator;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real Redis Integration Tests executing against a live Redis instance (redis:7-alpine).
 * Validates:
 * - Real Redis SET NX PX acquisition and mutual exclusion
 * - Atomic Lua script heartbeat lease renewal (pexpire)
 * - Atomic Lua script token-checked safe release (del)
 * - Mismatched token deletion immunity
 * - Ownership loss on real TTL expiration
 * - Atomic monotonically increasing fencing counters
 * - Multi-threaded concurrent worker contention
 * - Redis container restart and connection recovery
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SyncRealRedisIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static boolean redisAvailable = false;

    private SyncDistributedLockCoordinator coordinator;
    private final List<SyncDistributedLockCoordinator.SyncLockHandle> activeHandles = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void initRedis() {
        try {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
            connectionFactory = new LettuceConnectionFactory(config);
            connectionFactory.afterPropertiesSet();

            redisTemplate = new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();

            // Ping Redis to verify connectivity
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(ping)) {
                redisAvailable = true;
            }
        } catch (Exception e) {
            redisAvailable = false;
        }
    }

    @AfterAll
    static void cleanupConnection() {
        if (connectionFactory != null) {
            try {
                connectionFactory.destroy();
            } catch (Exception ignored) {}
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(redisAvailable, "Real Redis on localhost:6379 is required for this integration test");
        coordinator = new SyncDistributedLockCoordinator(redisTemplate, false);
    }

    @AfterEach
    void tearDown() {
        for (SyncDistributedLockCoordinator.SyncLockHandle handle : activeHandles) {
            try {
                handle.close();
            } catch (Exception ignored) {}
        }
        activeHandles.clear();

        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Real Redis: Exclusive lock acquisition and mutual exclusion between independent workers")
    void testRealRedis_acquisitionAndMutualExclusion() {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId1 = UUID.randomUUID();
        UUID resourceId2 = UUID.randomUUID();

        // Worker 1 acquires lock on Resource 1
        SyncDistributedLockCoordinator.SyncLockHandle worker1 =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId1, Duration.ofSeconds(10), Duration.ofSeconds(2));
        assertThat(worker1).isNotNull();
        activeHandles.add(worker1);

        // Verify key exists in real Redis with Worker 1's token
        String storedToken = redisTemplate.opsForValue().get(worker1.getLockKey());
        assertThat(storedToken).isEqualTo(worker1.getLockToken());

        // Worker 2 attempts to acquire lock on the same resource -> rejected
        SyncDistributedLockCoordinator.SyncLockHandle worker2 =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId1, Duration.ofSeconds(10), Duration.ofSeconds(2));
        assertThat(worker2).isNull();

        // Worker 3 acquires lock on Resource 2 -> succeeds (isolated resource scope)
        SyncDistributedLockCoordinator.SyncLockHandle worker3 =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId2, Duration.ofSeconds(10), Duration.ofSeconds(2));
        assertThat(worker3).isNotNull();
        activeHandles.add(worker3);
    }

    @Test
    @Order(2)
    @DisplayName("Real Redis: Heartbeat actively renews lease in real Redis via atomic Lua script")
    void testRealRedis_atomicHeartbeatRenewal() throws InterruptedException {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // Initial lease 1200ms, renewal interval 300ms
        SyncDistributedLockCoordinator.SyncLockHandle handle =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofMillis(1200), Duration.ofMillis(300));
        assertThat(handle).isNotNull();
        activeHandles.add(handle);

        // Sleep 1800ms (exceeding initial 1200ms lease)
        Thread.sleep(1800);

        // Verify lease was renewed in real Redis
        assertThat(handle.isOwnershipValid()).isTrue();
        assertThat(handle.isOwnershipLost()).isFalse();

        Long remainingTtl = redisTemplate.getExpire(handle.getLockKey(), TimeUnit.MILLISECONDS);
        assertThat(remainingTtl).isNotNull();
        assertThat(remainingTtl).isGreaterThan(0L);
    }

    @Test
    @Order(3)
    @DisplayName("Real Redis: Token-checked safe release via Lua script only deletes matching token")
    void testRealRedis_tokenCheckedSafeRelease() {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        SyncDistributedLockCoordinator.SyncLockHandle handle =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofSeconds(10), Duration.ofSeconds(2));
        assertThat(handle).isNotNull();
        activeHandles.add(handle);

        String lockKey = handle.getLockKey();

        // Mismatched token release attempt: Lua script must NOT delete the key
        coordinator.tryRelease(lockKey, "WRONG-FOREIGN-TOKEN-9999");
        assertThat(redisTemplate.hasKey(lockKey)).isTrue();
        assertThat(redisTemplate.opsForValue().get(lockKey)).isEqualTo(handle.getLockToken());

        // Correct token release: key must be deleted atomically
        handle.close();
        assertThat(redisTemplate.hasKey(lockKey)).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("Real Redis: Ownership loss detected when key expires without renewal")
    void testRealRedis_ownershipLossOnTtlExpiration() throws InterruptedException {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // Lease 400ms, renewal disabled (renewal interval > lease)
        SyncDistributedLockCoordinator.SyncLockHandle handle =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofMillis(400), Duration.ofMillis(1000));
        assertThat(handle).isNotNull();
        activeHandles.add(handle);

        // Wait 700ms for real Redis key expiration
        Thread.sleep(700);

        // Verification must detect that ownership was lost
        assertThat(handle.isOwnershipValid()).isFalse();
        assertThat(handle.isOwnershipLost()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("Real Redis: Fencing counter atomically increments across successive acquisitions")
    void testRealRedis_monotonicallyIncreasingFencingCounter() {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        List<Long> fencingTokens = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            SyncDistributedLockCoordinator.SyncLockHandle handle =
                    coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofSeconds(5), Duration.ofSeconds(1));
            assertThat(handle).isNotNull();
            fencingTokens.add(handle.getFencingToken());
            handle.close();
        }

        // Assert strictly monotonic increase
        for (int i = 1; i < fencingTokens.size(); i++) {
            assertThat(fencingTokens.get(i))
                    .as("Fencing token at index %d must be greater than previous", i)
                    .isGreaterThan(fencingTokens.get(i - 1));
        }
    }

    @Test
    @Order(6)
    @DisplayName("Real Redis: 10 concurrent threads racing for same lock produces exactly 1 winner")
    void testRealRedis_multiThreadedWorkerRace() throws InterruptedException {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        int numWorkers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numWorkers);

        AtomicInteger successfulAcquisitions = new AtomicInteger(0);
        List<SyncDistributedLockCoordinator.SyncLockHandle> acquiredHandles = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numWorkers; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    SyncDistributedLockCoordinator.SyncLockHandle handle =
                            coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofSeconds(10), Duration.ofSeconds(2));
                    if (handle != null) {
                        successfulAcquisitions.incrementAndGet();
                        acquiredHandles.add(handle);
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successfulAcquisitions.get()).isEqualTo(1);
        assertThat(acquiredHandles).hasSize(1);

        // Winner closes lock
        SyncDistributedLockCoordinator.SyncLockHandle winner = acquiredHandles.get(0);
        winner.close();

        // New worker can now acquire
        SyncDistributedLockCoordinator.SyncLockHandle postRelease =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofSeconds(5), Duration.ofSeconds(1));
        assertThat(postRelease).isNotNull();
        postRelease.close();
    }

    @Test
    @Order(7)
    @DisplayName("Real Redis: Container restart and connection recovery")
    void testRealRedis_containerRestartAndRecovery() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // 1. Initial acquisition succeeds
        SyncDistributedLockCoordinator.SyncLockHandle preRestart =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofSeconds(5), Duration.ofSeconds(1));
        assertThat(preRestart).isNotNull();
        preRestart.close();

        // 2. Restart Redis container via Docker CLI
        Process process = Runtime.getRuntime().exec(new String[]{"docker", "restart", "secretvault-redis-dev"});
        int exitCode = process.waitFor();
        assertThat(exitCode).isEqualTo(0);

        // 3. Poll until Redis responds to ping again (up to 10 seconds)
        boolean recovered = false;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                String ping = redisTemplate.getConnectionFactory().getConnection().ping();
                if ("PONG".equalsIgnoreCase(ping)) {
                    recovered = true;
                    break;
                }
            } catch (Exception e) {
                Thread.sleep(500);
            }
        }
        assertThat(recovered).as("Redis container must recover and respond to ping").isTrue();

        // 4. Verify lock acquisition succeeds after container restart
        SyncDistributedLockCoordinator.SyncLockHandle postRestart =
                coordinator.acquireExecutionLockHandle(workspaceId, resourceId, Duration.ofSeconds(5), Duration.ofSeconds(1));
        assertThat(postRestart).isNotNull();
        assertThat(postRestart.isOwnershipValid()).isTrue();
        postRestart.close();
    }
}

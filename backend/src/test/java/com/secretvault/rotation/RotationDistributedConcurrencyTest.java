package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.provider.DefaultCryptoRotator;
import com.secretvault.rotation.provider.SecretRotatorRegistry;
import com.secretvault.rotation.repository.RotationAttemptRepository;
import com.secretvault.rotation.repository.RotationJobRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.rotation.service.RotationDistributedLock;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 12.1 Distributed Concurrency, Lock Failure, and Idempotency Certification Test.
 */
@ExtendWith(MockitoExtension.class)
class RotationDistributedConcurrencyTest {

    @Mock private RotationPolicyRepository policyRepository;
    @Mock private RotationJobRepository jobRepository;
    @Mock private RotationAttemptRepository attemptRepository;
    @Mock private SecretRepository secretRepository;
    @Mock private SecretVersionRepository versionRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private SecretLeaseRepository leaseRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private RotationValidationEngine validationEngine;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private AuditService auditService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RotationDistributedLock distributedLock;
    private RotationService rotationService;
    private SecretRotatorRegistry rotatorRegistry;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID actorId;
    private Secret testSecret;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        testSecret = new Secret();
        testSecret.setId(secretId);
        testSecret.setEnvironmentId(environmentId);
        testSecret.setName("DB_PASSWORD");
        testSecret.setCurrentVersionNumber(1);

        testEnv = new Environment();
        testEnv.setId(environmentId);
        testEnv.setProjectId(projectId);
        testEnv.setName("production");

        distributedLock = new RotationDistributedLock(null); // Local concurrency lock testing
        DefaultCryptoRotator cryptoRotator = new DefaultCryptoRotator(new com.secretvault.rotation.engine.SecretGenerationEngine(new com.fasterxml.jackson.databind.ObjectMapper()));
        rotatorRegistry = new SecretRotatorRegistry(List.of(cryptoRotator));

        rotationService = new RotationService(
                policyRepository, jobRepository, attemptRepository, secretRepository,
                versionRepository, environmentRepository, leaseRepository, encryptionService,
                rotatorRegistry, validationEngine, distributedLock, effectiveAccessService, auditService
        );
    }

    @Test
    @DisplayName("Mutual exclusion: Worker A acquires lock -> Worker B cannot acquire same lock simultaneously")
    void testMutualExclusionForSameSecret() {
        String tokenA = "worker-instance-A";
        String tokenB = "worker-instance-B";

        boolean acquiredA = distributedLock.acquireLock(secretId, tokenA, Duration.ofMinutes(5));
        assertThat(acquiredA).isTrue();

        boolean acquiredB = distributedLock.acquireLock(secretId, tokenB, Duration.ofMinutes(5));
        assertThat(acquiredB).isFalse();

        // Release by A allows B to acquire
        distributedLock.releaseLock(secretId, tokenA);
        boolean acquiredBAfterRelease = distributedLock.acquireLock(secretId, tokenB, Duration.ofMinutes(5));
        assertThat(acquiredBAfterRelease).isTrue();

        distributedLock.releaseLock(secretId, tokenB);
    }

    @Test
    @DisplayName("Redis integration: Distributed lock acquires and releases with key prefix")
    void testRedisDistributedLockIntegration() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("secretvault:lock:rotation:" + secretId), anyString(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        RotationDistributedLock redisLock = new RotationDistributedLock(redisTemplate);
        boolean first = redisLock.acquireLock(secretId, "token-1", Duration.ofMinutes(5));
        boolean second = redisLock.acquireLock(secretId, "token-2", Duration.ofMinutes(5));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("Redis failure degradation: Safely falls back to local concurrency map when Redis throws")
    void testRedisFailureFallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RotationDistributedLock resilientLock = new RotationDistributedLock(redisTemplate);

        // First attempt catches Redis exception and successfully falls back to local lock
        boolean acquired = resilientLock.acquireLock(secretId, "token-fallback-1", Duration.ofMinutes(5));
        assertThat(acquired).isTrue();

        // Second attempt correctly recognizes local lock already held
        boolean second = resilientLock.acquireLock(secretId, "token-fallback-2", Duration.ofMinutes(5));
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("Duplicate request test: Same Idempotency-Key returns identical logical rotation job")
    void testIdempotencyKeyDeduplication() {
        String idempotencyKey = "idem-key-" + UUID.randomUUID();

        RotationJob existingJob = new RotationJob();
        existingJob.setId(UUID.randomUUID());
        existingJob.setWorkspaceId(workspaceId);
        existingJob.setSecretId(secretId);
        existingJob.setIdempotencyKey(idempotencyKey);
        existingJob.setStatus(RotationStatus.ACTIVE);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(secretId), eq(AccessPermission.SECRET_ROTATION_CREATE), eq(actorId)))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_CREATE, com.secretvault.access.model.AccessScope.SECRET, com.secretvault.access.model.AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(jobRepository.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey))
                .thenReturn(Optional.of(existingJob));

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Manual request", false, false);
        RotationJobResponse res1 = rotationService.triggerRotation(workspaceId, secretId, req, actorId, idempotencyKey);
        RotationJobResponse res2 = rotationService.triggerRotation(workspaceId, secretId, req, actorId, idempotencyKey);

        assertThat(res1.id()).isEqualTo(existingJob.getId());
        assertThat(res2.id()).isEqualTo(existingJob.getId());
        verify(jobRepository, never()).save(any(RotationJob.class));
    }

    @Test
    @DisplayName("Duplicate request conflict: Same Idempotency-Key used for different secret throws 409 Conflict")
    void testIdempotencyKeyConflictDifferentSecret() {
        String idempotencyKey = "idem-key-conflict";
        UUID otherSecretId = UUID.randomUUID();

        RotationJob existingJob = new RotationJob();
        existingJob.setId(UUID.randomUUID());
        existingJob.setWorkspaceId(workspaceId);
        existingJob.setSecretId(otherSecretId); // Different secret
        existingJob.setIdempotencyKey(idempotencyKey);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_CREATE, com.secretvault.access.model.AccessScope.SECRET, com.secretvault.access.model.AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(jobRepository.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey))
                .thenReturn(Optional.of(existingJob));

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Manual request", false, false);

        assertThatThrownBy(() -> rotationService.triggerRotation(workspaceId, secretId, req, actorId, idempotencyKey))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("previously used for a different secret");
    }

    @Test
    @DisplayName("100 concurrent threads acquiring lock on same secret ensures exactly ONE winner")
    void testConcurrent100ThreadsLockAcquisition() throws InterruptedException {
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String token = "worker-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (distributedLock.acquireLock(secretId, token, Duration.ofMinutes(1))) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
    }
}

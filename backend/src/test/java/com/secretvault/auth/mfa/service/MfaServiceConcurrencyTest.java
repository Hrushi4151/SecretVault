package com.secretvault.auth.mfa.service;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.mfa.model.MfaActivationResult;
import com.secretvault.auth.mfa.model.MfaChallengeInfo;
import com.secretvault.auth.mfa.model.MfaEnrollmentResponse;
import com.secretvault.auth.mfa.model.MfaVerificationResult;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.security.state.SecurityStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MfaServiceConcurrencyTest.TestSecurityStateConfig.class)
@DisplayName("MfaService 20+ Thread Concurrency & Race-Condition Tests")
class MfaServiceConcurrencyTest {

    @TestConfiguration
    static class TestSecurityStateConfig {
        @Bean
        @Primary
        public SecurityStateStore inMemorySecurityStateStore() {
            return new SecurityStateStore() {
                private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
                private final ConcurrentHashMap<String, AtomicLong> attempts = new ConcurrentHashMap<>();

                private String key(String category, String id) {
                    return category + ":" + id;
                }

                @Override
                public <T> void put(String category, String identifier, T payload, Duration ttl) {
                    store.put(key(category, identifier), payload);
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T> Optional<T> get(String category, String identifier, Class<T> type) {
                    Object val = store.get(key(category, identifier));
                    return Optional.ofNullable((T) val);
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T> Optional<T> consumeAtomic(String category, String identifier, Class<T> type) {
                    Object removed = store.remove(key(category, identifier));
                    return Optional.ofNullable((T) removed);
                }

                @Override
                public boolean exists(String category, String identifier) {
                    return store.containsKey(key(category, identifier));
                }

                @Override
                public void delete(String category, String identifier) {
                    store.remove(key(category, identifier));
                    attempts.remove(key(category, identifier));
                }

                @Override
                public long incrementAttempts(String category, String identifier, Duration ttl) {
                    return attempts.computeIfAbsent(key(category, identifier), k -> new AtomicLong(0)).incrementAndGet();
                }
            };
        }
    }

    @Autowired
    private MfaService mfaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TotpService totpService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;
    private String secret;

    @BeforeEach
    void setUp() {
        testUser = new User("mfa-concurrency-" + UUID.randomUUID() + "@example.com", "hash", "Concurrent User");
        testUser = userRepository.save(testUser);

        MfaEnrollmentResponse enrollment = mfaService.beginEnrollment(testUser.getId());
        secret = enrollment.secret();
        String activeCode = totpService.generateCode(secret);
        mfaService.activateMfa(testUser.getId(), activeCode);
    }

    @Test
    @DisplayName("25 concurrent threads verifying the EXACT SAME login challenge: EXACTLY ONE succeeds")
    void testConcurrentChallengeVerification() throws Exception {
        UUID userId = testUser.getId();
        MfaChallengeInfo challenge = mfaService.createLoginChallenge(userId);
        String challengeId = challenge.challengeId();
        String validTotp = totpService.generateCode(secret);

        int concurrency = 25;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                tasks.add(() -> {
                    MfaVerificationResult result = txTemplate.execute(status ->
                            mfaService.verifyLoginTotp(challengeId, userId, validTotp)
                    );
                    if (result != null && result.success()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }

            // Invariant: Exactly ONE thread succeeds in consuming the challenge
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(concurrency - 1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("25 concurrent threads attempting to consume the EXACT SAME recovery code: EXACTLY ONE succeeds")
    void testConcurrentRecoveryCodeConsumption() throws Exception {
        UUID userId = testUser.getId();

        // 1. Reset and re-enroll to get fresh recovery codes
        mfaService.disableMfa(userId);
        MfaEnrollmentResponse enrollment = mfaService.beginEnrollment(userId);
        MfaActivationResult activation = mfaService.activateMfa(userId, totpService.generateCode(enrollment.secret()));
        String targetRecoveryCode = activation.recoveryCodes().getFirst();

        int concurrency = 25;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // Pre-create 25 individual challenges for the user
        List<String> challengeIds = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            challengeIds.add(mfaService.createLoginChallenge(userId).challengeId());
        }

        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                final String challengeId = challengeIds.get(i);
                tasks.add(() -> {
                    MfaVerificationResult result = txTemplate.execute(status ->
                            mfaService.verifyLoginRecoveryCode(challengeId, userId, targetRecoveryCode)
                    );
                    if (result != null && result.success()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }

            // Invariant: Exactly ONE thread succeeds in consuming the single-use recovery code
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(concurrency - 1);
        } finally {
            executor.shutdown();
        }
    }
}

package com.secretvault.auth.mfa.service;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.mfa.entity.MfaStatus;
import com.secretvault.auth.mfa.entity.UserMfa;
import com.secretvault.auth.mfa.model.AuthenticationState;
import com.secretvault.auth.mfa.model.MfaActivationResult;
import com.secretvault.auth.mfa.model.MfaChallengeInfo;
import com.secretvault.auth.mfa.model.MfaEnrollmentResponse;
import com.secretvault.auth.mfa.model.MfaStatusInfo;
import com.secretvault.auth.mfa.model.MfaVerificationResult;
import com.secretvault.auth.mfa.repository.MfaRecoveryCodeRepository;
import com.secretvault.auth.mfa.repository.UserMfaRepository;
import com.secretvault.auth.mfa.totp.Base32;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.encryption.service.EncryptionService;
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
@Import(MfaServiceIntegrationTest.TestSecurityStateConfig.class)
@DisplayName("MfaService End-to-End Integration Tests")
class MfaServiceIntegrationTest {

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
    private UserMfaRepository userMfaRepository;

    @Autowired
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private TotpService totpService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private SecurityStateStore securityStateStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("mfa-integ-" + UUID.randomUUID() + "@example.com", "hash", "Integ User");
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Complete MFA Lifecycle: Enroll -> Activate -> Challenge -> TOTP Verify -> Status -> Disable")
    void testCompleteMfaLifecycle() {
        UUID userId = testUser.getId();

        // 1. Initial Status: MFA disabled
        assertThat(mfaService.isMfaEnabled(userId)).isFalse();
        MfaStatusInfo initialStatus = mfaService.getStatus(userId);
        assertThat(initialStatus.enabled()).isFalse();

        // 2. Begin Enrollment
        MfaEnrollmentResponse enrollment = mfaService.beginEnrollment(userId);
        assertThat(enrollment.secret()).isNotBlank();
        assertThat(enrollment.provisioningUri()).contains(enrollment.secret());
        assertThat(mfaService.isMfaEnabled(userId)).isFalse(); // Still not enabled (PENDING)

        // 3. Activate MFA with valid TOTP code
        String activeCode = totpService.generateCode(enrollment.secret());
        MfaActivationResult activation = mfaService.activateMfa(userId, activeCode);
        assertThat(activation.status()).isEqualTo(MfaStatus.ENABLED);
        assertThat(activation.recoveryCodes()).hasSize(10);
        assertThat(mfaService.isMfaEnabled(userId)).isTrue();

        // 4. Create Login Challenge
        MfaChallengeInfo challenge = mfaService.createLoginChallenge(userId);
        assertThat(challenge.challengeId()).isNotBlank();
        assertThat(challenge.state()).isEqualTo(AuthenticationState.MFA_REQUIRED);

        // 5. Verify TOTP Login Challenge
        String loginTotp = totpService.generateCode(enrollment.secret());
        MfaVerificationResult verifyResult = mfaService.verifyLoginTotp(challenge.challengeId(), userId, loginTotp);
        assertThat(verifyResult.success()).isTrue();
        assertThat(verifyResult.state()).isEqualTo(AuthenticationState.MFA_VERIFIED);
        assertThat(verifyResult.userId()).isEqualTo(userId);

        // 6. Challenge Replay Prevention: Re-submitting the same challenge must fail (already consumed)
        MfaVerificationResult replayResult = mfaService.verifyLoginTotp(challenge.challengeId(), userId, loginTotp);
        assertThat(replayResult.success()).isFalse();
        assertThat(replayResult.state()).isEqualTo(AuthenticationState.MFA_CHALLENGE_EXPIRED);

        // 7. Verify Status Reflection
        MfaStatusInfo activeStatus = mfaService.getStatus(userId);
        assertThat(activeStatus.enabled()).isTrue();
        assertThat(activeStatus.remainingRecoveryCodes()).isEqualTo(10L);

        // 8. Disable MFA
        mfaService.disableMfa(userId);
        assertThat(mfaService.isMfaEnabled(userId)).isFalse();
        MfaStatusInfo disabledStatus = mfaService.getStatus(userId);
        assertThat(disabledStatus.enabled()).isFalse();
        assertThat(disabledStatus.status()).isEqualTo(MfaStatus.DISABLED);
    }

    @Test
    @DisplayName("Recovery Code Verification: Consume backup code atomically during challenge")
    void testRecoveryCodeVerification() {
        UUID userId = testUser.getId();

        // 1. Enroll and activate MFA
        MfaEnrollmentResponse enrollment = mfaService.beginEnrollment(userId);
        String activeCode = totpService.generateCode(enrollment.secret());
        MfaActivationResult activation = mfaService.activateMfa(userId, activeCode);
        List<String> recoveryCodes = activation.recoveryCodes();

        String codeToUse = recoveryCodes.getFirst();

        // 2. Create login challenge
        MfaChallengeInfo challenge = mfaService.createLoginChallenge(userId);

        // 3. Verify using Recovery Code
        MfaVerificationResult result = mfaService.verifyLoginRecoveryCode(challenge.challengeId(), userId, codeToUse);
        assertThat(result.success()).isTrue();
        assertThat(result.state()).isEqualTo(AuthenticationState.MFA_VERIFIED);

        // 4. Check remaining recovery codes decremented to 9
        MfaStatusInfo status = mfaService.getStatus(userId);
        assertThat(status.remainingRecoveryCodes()).isEqualTo(9L);

        // 5. Reusing the SAME recovery code in a new challenge must fail
        MfaChallengeInfo secondChallenge = mfaService.createLoginChallenge(userId);
        MfaVerificationResult reuseResult = mfaService.verifyLoginRecoveryCode(secondChallenge.challengeId(), userId, codeToUse);
        assertThat(reuseResult.success()).isFalse();
        assertThat(reuseResult.state()).isEqualTo(AuthenticationState.MFA_REQUIRED);
    }
}

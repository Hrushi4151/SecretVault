package com.secretvault.auth.mfa;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.mfa.entity.MfaRecoveryCode;
import com.secretvault.auth.mfa.entity.MfaStatus;
import com.secretvault.auth.mfa.entity.UserMfa;
import com.secretvault.auth.mfa.repository.MfaRecoveryCodeRepository;
import com.secretvault.auth.mfa.repository.UserMfaRepository;
import com.secretvault.auth.mfa.totp.Base32;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.kms.KmsKeyProvider;
import com.secretvault.encryption.kms.LocalDevKmsKeyProvider;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.AesGcmEnvelopeEncryptionService;
import com.secretvault.encryption.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MFA Persistence Cryptographic, Tamper-Resistance & Concurrency Tests")
class MfaPersistenceSecurityTest {

    @Autowired
    private UserMfaRepository userMfaRepository;

    @Autowired
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private TotpService totpService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("security-user-" + UUID.randomUUID() + "@example.com", "hash", "Sec User");
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("End-to-end envelope encryption, storage, retrieval, decryption, and TOTP generation")
    void testFullEncryptionPersistenceRoundTrip() {
        // 1. Generate fresh Base32 secret and get raw bytes
        String base32Secret = totpService.generateSecret();
        byte[] rawSecretBytes = Base32.decode(base32Secret);

        // 2. Encrypt using AES-256-GCM with AAD bound to user MFA context
        String aadContext = "user-mfa:" + testUser.getId();
        EncryptedPayload encryptedPayload = encryptionService.encrypt(rawSecretBytes, aadContext);

        // 3. Persist in PostgreSQL
        UserMfa userMfa = new UserMfa(testUser.getId(), encryptedPayload);
        userMfa.enable();
        userMfa = userMfaRepository.save(userMfa);

        // 4. Retrieve and verify reconstitution
        UserMfa loaded = userMfaRepository.findByUserId(testUser.getId()).orElseThrow();
        EncryptedPayload loadedPayload = loaded.toEncryptedPayload();
        assertThat(loadedPayload).isEqualTo(encryptedPayload);

        // 5. Decrypt in memory with correct AAD
        byte[] decryptedBytes = encryptionService.decrypt(loadedPayload, aadContext);
        assertThat(decryptedBytes).isEqualTo(rawSecretBytes);

        // 6. Verify TOTP calculations match
        String decryptedBase32 = Base32.encode(decryptedBytes, false);
        assertThat(decryptedBase32).isEqualTo(base32Secret);

        Instant now = Instant.now();
        String code1 = totpService.generateCode(base32Secret, now);
        String code2 = totpService.generateCode(decryptedBase32, now);
        assertThat(code1).isEqualTo(code2);
        assertThat(totpService.verifyCode(decryptedBase32, code1, now)).isTrue();
    }

    @Test
    @DisplayName("Cryptographic tamper resistance: tampered payload or wrong AAD fails decryption")
    void testTamperResistance() {
        byte[] rawSecret = Base32.decode(totpService.generateSecret());
        String correctAad = "user-mfa:" + testUser.getId();
        EncryptedPayload original = encryptionService.encrypt(rawSecret, correctAad);

        // Wrong AAD (e.g. cross-user transplant attack)
        String attackerAad = "user-mfa:" + UUID.randomUUID();
        assertThatThrownBy(() -> encryptionService.decrypt(original, attackerAad))
                .isInstanceOf(ApiException.class);

        // Tampered ciphertext
        byte[] tamperedCiphertext = original.ciphertext().clone();
        tamperedCiphertext[0] ^= 0xFF;
        EncryptedPayload tamperedPayload = new EncryptedPayload(tamperedCiphertext, original.encryptedDek(), original.iv(), original.authTag(), original.keyReference());
        assertThatThrownBy(() -> encryptionService.decrypt(tamperedPayload, correctAad))
                .isInstanceOf(ApiException.class);

        // Tampered auth tag
        byte[] tamperedTag = original.authTag().clone();
        tamperedTag[0] ^= 0xFF;
        EncryptedPayload tamperedTagPayload = new EncryptedPayload(original.ciphertext(), original.encryptedDek(), original.iv(), tamperedTag, original.keyReference());
        assertThatThrownBy(() -> encryptionService.decrypt(tamperedTagPayload, correctAad))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Atomic recovery-code consumption under 10 concurrent racing threads")
    void testConcurrentRecoveryCodeConsumption() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        UUID codeId = txTemplate.execute(status -> {
            EncryptedPayload payload = encryptionService.encrypt("secret".getBytes(StandardCharsets.UTF_8), "user-mfa:" + testUser.getId());
            UserMfa userMfa = userMfaRepository.save(new UserMfa(testUser.getId(), payload));
            MfaRecoveryCode code = new MfaRecoveryCode(userMfa.getId(), "$2a$10$hash", 0);
            MfaRecoveryCode savedCode = recoveryCodeRepository.save(code);
            return savedCode.getId();
        });

        int concurrency = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                tasks.add(() -> {
                    txTemplate.execute(status -> {
                        int updated = recoveryCodeRepository.markUsedIfUnused(codeId, Instant.now());
                        if (updated == 1) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                        return null;
                    });
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }

            // Exactly ONE thread succeeded in consuming the single-use code
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(concurrency - 1);

            MfaRecoveryCode finalState = recoveryCodeRepository.findById(codeId).orElseThrow();
            assertThat(finalState.isUsed()).isTrue();
            assertThat(finalState.getUsedAt()).isNotNull();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("No plaintext TOTP or recovery-code fields exist in entity reflection")
    void testNoPlaintextDatabaseFields() {
        for (Field field : UserMfa.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertThat(name).doesNotContain("totpsecret", "plaintextsecret", "secretkey", "rawsecret", "otpcode");
        }

        for (Field field : MfaRecoveryCode.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertThat(name).doesNotContain("plaintextcode", "rawcode", "recoverycode");
        }
    }

    @Test
    @DisplayName("toString implementations are sanitized and never leak sensitive data")
    void testSanitizedToString() {
        EncryptedPayload payload = encryptionService.encrypt("secret123".getBytes(StandardCharsets.UTF_8), "user-mfa:" + testUser.getId());
        UserMfa userMfa = new UserMfa(testUser.getId(), payload);

        String userMfaStr = userMfa.toString();
        assertThat(userMfaStr)
                .doesNotContain("secret123")
                .doesNotContain("ciphertext")
                .doesNotContain("encryptedDek");

        MfaRecoveryCode code = new MfaRecoveryCode(UUID.randomUUID(), "$2a$10$supersecretbcryptcodehash", 1);
        String codeStr = code.toString();
        assertThat(codeStr).doesNotContain("$2a$10$supersecretbcryptcodehash");
    }
}

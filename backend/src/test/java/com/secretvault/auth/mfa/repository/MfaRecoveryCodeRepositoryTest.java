package com.secretvault.auth.mfa.repository;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.mfa.entity.MfaRecoveryCode;
import com.secretvault.auth.mfa.entity.UserMfa;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.encryption.model.EncryptedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MfaRecoveryCodeRepository JPA Persistence & Atomic Query Tests")
class MfaRecoveryCodeRepositoryTest {

    @Autowired
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private UserMfaRepository userMfaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserMfa testUserMfa;

    @BeforeEach
    void setUp() {
        User user = new User("recovery-user-" + UUID.randomUUID() + "@example.com", "hash", "Recovery User");
        user = userRepository.save(user);

        EncryptedPayload payload = new EncryptedPayload(
                "cipher".getBytes(StandardCharsets.UTF_8),
                "dek".getBytes(StandardCharsets.UTF_8),
                new byte[12],
                new byte[16],
                "local-dev-kek-v1"
        );
        testUserMfa = userMfaRepository.saveAndFlush(new UserMfa(user.getId(), payload));
    }

    @Test
    @DisplayName("Successfully stores and retrieves batch of recovery codes")
    void testStoreAndFindBatch() {
        for (int i = 0; i < 10; i++) {
            MfaRecoveryCode code = new MfaRecoveryCode(testUserMfa.getId(), "$2a$10$dummyhash" + i, i);
            recoveryCodeRepository.save(code);
        }
        recoveryCodeRepository.flush();

        List<MfaRecoveryCode> allCodes = recoveryCodeRepository.findByUserMfaIdOrderByCodeIndexAsc(testUserMfa.getId());
        assertThat(allCodes).hasSize(10);

        long unusedCount = recoveryCodeRepository.countByUserMfaIdAndUsedFalse(testUserMfa.getId());
        assertThat(unusedCount).isEqualTo(10);
    }

    @Test
    @DisplayName("Enforces unique constraint on (user_mfa_id, code_index)")
    void testUniqueCodeIndexConstraint() {
        MfaRecoveryCode first = new MfaRecoveryCode(testUserMfa.getId(), "$2a$10$dummyhash1", 0);
        recoveryCodeRepository.saveAndFlush(first);

        MfaRecoveryCode duplicateIndex = new MfaRecoveryCode(testUserMfa.getId(), "$2a$10$dummyhash2", 0);
        assertThatThrownBy(() -> recoveryCodeRepository.saveAndFlush(duplicateIndex))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("markUsedIfUnused atomically updates unused code and is single-use")
    void testMarkUsedIfUnusedAtomic() {
        MfaRecoveryCode code = new MfaRecoveryCode(testUserMfa.getId(), "$2a$10$dummyhash", 0);
        code = recoveryCodeRepository.saveAndFlush(code);

        // 1st Consumption: must return 1 (success)
        Instant usedTimestamp = Instant.now();
        int rowsUpdated = recoveryCodeRepository.markUsedIfUnused(code.getId(), usedTimestamp);
        assertThat(rowsUpdated).isEqualTo(1);

        entityManager.clear(); // Clear L1 cache to verify database state

        MfaRecoveryCode consumed = recoveryCodeRepository.findById(code.getId()).orElseThrow();
        assertThat(consumed.isUsed()).isTrue();
        assertThat(consumed.getUsedAt()).isNotNull();

        // 2nd Consumption attempt: must return 0 (already used)
        int secondAttempt = recoveryCodeRepository.markUsedIfUnused(code.getId(), Instant.now());
        assertThat(secondAttempt).isEqualTo(0);

        // Unused list should now exclude this code
        List<MfaRecoveryCode> unused = recoveryCodeRepository.findByUserMfaIdAndUsedFalseOrderByCodeIndexAsc(testUserMfa.getId());
        assertThat(unused).isEmpty();
    }
}

package com.secretvault.auth.mfa.repository;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.entity.MfaStatus;
import com.secretvault.auth.mfa.entity.UserMfa;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.encryption.model.EncryptedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserMfaRepository JPA Persistence Tests")
class UserMfaRepositoryTest {

    @Autowired
    private UserMfaRepository userMfaRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("mfa-user-" + UUID.randomUUID() + "@example.com", "hash", "MFA Test User");
        testUser = userRepository.save(testUser);
    }

    private EncryptedPayload createSamplePayload() {
        return new EncryptedPayload(
                "sample-ciphertext".getBytes(StandardCharsets.UTF_8),
                "sample-encrypted-dek".getBytes(StandardCharsets.UTF_8),
                new byte[12],
                new byte[16],
                "local-dev-kek-v1"
        );
    }

    @Test
    @DisplayName("Successfully saves and retrieves UserMfa with envelope fields")
    void testSaveAndFind() {
        EncryptedPayload payload = createSamplePayload();
        UserMfa userMfa = new UserMfa(testUser.getId(), payload);

        UserMfa saved = userMfaRepository.saveAndFlush(userMfa);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(testUser.getId());
        assertThat(saved.getStatus()).isEqualTo(MfaStatus.PENDING_VERIFICATION);
        assertThat(saved.getKeyReference()).isEqualTo("local-dev-kek-v1");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<UserMfa> found = userMfaRepository.findByUserId(testUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().toEncryptedPayload()).isEqualTo(payload);
    }

    @Test
    @DisplayName("Enforces unique user constraint on user_mfa")
    void testUniqueUserConstraint() {
        UserMfa first = new UserMfa(testUser.getId(), createSamplePayload());
        userMfaRepository.saveAndFlush(first);

        UserMfa duplicate = new UserMfa(testUser.getId(), createSamplePayload());
        assertThatThrownBy(() -> userMfaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByUserIdAndStatus filters correctly by lifecycle status")
    void testFindByUserIdAndStatus() {
        UserMfa userMfa = new UserMfa(testUser.getId(), createSamplePayload());
        userMfaRepository.saveAndFlush(userMfa);

        assertThat(userMfaRepository.findByUserIdAndStatus(testUser.getId(), MfaStatus.PENDING_VERIFICATION)).isPresent();
        assertThat(userMfaRepository.findByUserIdAndStatus(testUser.getId(), MfaStatus.ENABLED)).isEmpty();

        userMfa.enable();
        userMfaRepository.saveAndFlush(userMfa);

        assertThat(userMfaRepository.findByUserIdAndStatus(testUser.getId(), MfaStatus.ENABLED)).isPresent();
        assertThat(userMfaRepository.findByUserIdAndStatus(testUser.getId(), MfaStatus.PENDING_VERIFICATION)).isEmpty();
    }

    @Test
    @DisplayName("Deletes UserMfa configuration by userId")
    void testDeleteByUserId() {
        UserMfa userMfa = new UserMfa(testUser.getId(), createSamplePayload());
        userMfaRepository.saveAndFlush(userMfa);

        assertThat(userMfaRepository.existsByUserId(testUser.getId())).isTrue();

        userMfaRepository.deleteByUserId(testUser.getId());
        userMfaRepository.flush();

        assertThat(userMfaRepository.existsByUserId(testUser.getId())).isFalse();
    }
}

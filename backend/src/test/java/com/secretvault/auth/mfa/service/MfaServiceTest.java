package com.secretvault.auth.mfa.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.mfa.entity.MfaRecoveryCode;
import com.secretvault.auth.mfa.entity.MfaStatus;
import com.secretvault.auth.mfa.entity.UserMfa;
import com.secretvault.auth.mfa.model.AuthenticationState;
import com.secretvault.auth.mfa.model.MfaActivationResult;
import com.secretvault.auth.mfa.model.MfaChallengeInfo;
import com.secretvault.auth.mfa.model.MfaChallengePayload;
import com.secretvault.auth.mfa.model.MfaEnrollmentResponse;
import com.secretvault.auth.mfa.model.MfaStatusInfo;
import com.secretvault.auth.mfa.model.MfaVerificationResult;
import com.secretvault.auth.mfa.recovery.RecoveryCodeService;
import com.secretvault.auth.mfa.repository.MfaRecoveryCodeRepository;
import com.secretvault.auth.mfa.repository.UserMfaRepository;
import com.secretvault.auth.mfa.totp.Base32;
import com.secretvault.auth.mfa.totp.TotpProperties;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MfaService Unit & Security State Machine Tests")
class MfaServiceTest {

    @Mock
    private UserMfaRepository userMfaRepository;

    @Mock
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private SecurityStateStore securityStateStore;

    @Mock
    private AuditService auditService;

    private TotpService totpService;
    private RecoveryCodeService recoveryCodeService;
    private PasswordEncoder passwordEncoder;
    private DefaultMfaService mfaService;

    private UUID userId;
    private User user;
    private String testSecret;
    private byte[] testSecretBytes;
    private EncryptedPayload samplePayload;

    @BeforeEach
    void setUp() {
        totpService = new TotpService(new TotpProperties());
        passwordEncoder = new BCryptPasswordEncoder();
        recoveryCodeService = new RecoveryCodeService(passwordEncoder);

        mfaService = new DefaultMfaService(
                userMfaRepository,
                recoveryCodeRepository,
                userRepository,
                totpService,
                recoveryCodeService,
                encryptionService,
                securityStateStore,
                auditService,
                passwordEncoder
        );

        userId = UUID.randomUUID();
        user = new User("alice@example.com", "hash", "Alice Developer");
        user.setId(userId);

        testSecret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        testSecretBytes = Base32.decode(testSecret);

        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        samplePayload = new EncryptedPayload(
                "cipher".getBytes(StandardCharsets.UTF_8),
                "dek".getBytes(StandardCharsets.UTF_8),
                new byte[12],
                new byte[16],
                "kms-key-v1"
        );
    }

    // ==========================================
    // 1. Status & Enrollment Tests
    // ==========================================

    @Test
    @DisplayName("isMfaEnabled returns true only when status is ENABLED")
    void testIsMfaEnabled() {
        UserMfa pendingMfa = new UserMfa(userId, samplePayload);
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(pendingMfa));
        assertThat(mfaService.isMfaEnabled(userId)).isFalse();

        pendingMfa.enable();
        assertThat(mfaService.isMfaEnabled(userId)).isTrue();

        pendingMfa.disable();
        assertThat(mfaService.isMfaEnabled(userId)).isFalse();

        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
        assertThat(mfaService.isMfaEnabled(userId)).isFalse();
    }

    @Test
    @DisplayName("getStatus returns non-sensitive metadata only")
    void testGetStatus() {
        UserMfa userMfa = new UserMfa(userId, samplePayload);
        userMfa.enable();
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(userMfa));
        when(recoveryCodeRepository.countByUserMfaIdAndUsedFalse(userMfa.getId())).thenReturn(8L);

        MfaStatusInfo status = mfaService.getStatus(userId);
        assertThat(status.enabled()).isTrue();
        assertThat(status.status()).isEqualTo(MfaStatus.ENABLED);
        assertThat(status.remainingRecoveryCodes()).isEqualTo(8L);
    }

    @Test
    @DisplayName("beginEnrollment successfully initiates PENDING_VERIFICATION state")
    void testBeginEnrollmentSuccess() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(encryptionService.encrypt(any(byte[].class), eq("user-mfa:" + userId))).thenReturn(samplePayload);
        when(userMfaRepository.save(any(UserMfa.class))).thenAnswer(inv -> inv.getArgument(0));

        MfaEnrollmentResponse response = mfaService.beginEnrollment(userId);

        assertThat(response.secret()).isNotBlank();
        assertThat(response.provisioningUri()).startsWith("otpauth://totp/");
        assertThat(response.accountName()).isEqualTo("alice@example.com");

        ArgumentCaptor<UserMfa> captor = ArgumentCaptor.forClass(UserMfa.class);
        verify(userMfaRepository).save(captor.capture());
        UserMfa saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(MfaStatus.PENDING_VERIFICATION);

        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_ENROLLMENT_STARTED), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("beginEnrollment rejects if MFA is already actively ENABLED")
    void testBeginEnrollmentRejectsWhenAlreadyEnabled() {
        UserMfa activeMfa = new UserMfa(userId, samplePayload);
        activeMfa.enable();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(activeMfa));

        assertThatThrownBy(() -> mfaService.beginEnrollment(userId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already enabled");
    }

    // ==========================================
    // 2. Activation Tests
    // ==========================================

    @Test
    @DisplayName("activateMfa validates code, transitions to ENABLED, and generates recovery codes")
    void testActivateMfaSuccess() {
        UserMfa pendingMfa = new UserMfa(userId, samplePayload);
        pendingMfa.setId(UUID.randomUUID());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(pendingMfa));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq("user-mfa:" + userId))).thenReturn(testSecretBytes);

        String validCode = totpService.generateCode(testSecret);

        MfaActivationResult result = mfaService.activateMfa(userId, validCode);

        assertThat(result.status()).isEqualTo(MfaStatus.ENABLED);
        assertThat(result.recoveryCodes()).hasSize(10);
        assertThat(pendingMfa.isEnabled()).isTrue();
        assertThat(user.isMfaEnabled()).isTrue();

        verify(recoveryCodeRepository).deleteByUserMfaId(pendingMfa.getId());
        verify(recoveryCodeRepository, org.mockito.Mockito.times(10)).save(any(MfaRecoveryCode.class));
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_ACTIVATED), any(), any(), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("activateMfa rejects invalid code and increments failed attempts")
    void testActivateMfaInvalidCode() {
        UserMfa pendingMfa = new UserMfa(userId, samplePayload);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(pendingMfa));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq("user-mfa:" + userId))).thenReturn(testSecretBytes);

        assertThatThrownBy(() -> mfaService.activateMfa(userId, "000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid verification code");

        assertThat(pendingMfa.getFailedAttempts()).isEqualTo(1);
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_VERIFICATION_FAILED), any(), any(), any(), any(), eq("INVALID_CODE"));
    }

    // ==========================================
    // 3. Challenge Creation & Redis State Tests
    // ==========================================

    @Test
    @DisplayName("createLoginChallenge stores short-lived challenge in Redis")
    void testCreateLoginChallengeSuccess() {
        UserMfa activeMfa = new UserMfa(userId, samplePayload);
        activeMfa.enable();
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(activeMfa));

        MfaChallengeInfo challengeInfo = mfaService.createLoginChallenge(userId);

        assertThat(challengeInfo.challengeId()).isNotBlank();
        assertThat(challengeInfo.userId()).isEqualTo(userId);
        assertThat(challengeInfo.state()).isEqualTo(AuthenticationState.MFA_REQUIRED);

        verify(securityStateStore).put(eq("mfa_challenge"), eq(challengeInfo.challengeId()), any(MfaChallengePayload.class), eq(Duration.ofSeconds(300)));
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_CHALLENGE_ISSUED), any(), any(), eq(challengeInfo.challengeId()), any(), eq("SUCCESS"));
    }

    // ==========================================
    // 4. TOTP Challenge Verification Tests
    // ==========================================

    @Test
    @DisplayName("verifyLoginTotp successfully validates code and atomically consumes challenge")
    void testVerifyLoginTotpSuccess() {
        String challengeId = UUID.randomUUID().toString();
        MfaChallengePayload challenge = MfaChallengePayload.of(challengeId, userId, "LOGIN_MFA", 300L, 5);

        UserMfa activeMfa = new UserMfa(userId, samplePayload);
        activeMfa.enable();

        when(securityStateStore.get("mfa_challenge", challengeId, MfaChallengePayload.class)).thenReturn(Optional.of(challenge));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(activeMfa));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq("user-mfa:" + userId))).thenReturn(testSecretBytes);
        when(securityStateStore.consumeAtomic("mfa_challenge", challengeId, MfaChallengePayload.class)).thenReturn(Optional.of(challenge));

        String validCode = totpService.generateCode(testSecret);

        MfaVerificationResult result = mfaService.verifyLoginTotp(challengeId, userId, validCode);

        assertThat(result.success()).isTrue();
        assertThat(result.state()).isEqualTo(AuthenticationState.MFA_VERIFIED);
        assertThat(result.userId()).isEqualTo(userId);

        verify(securityStateStore).consumeAtomic("mfa_challenge", challengeId, MfaChallengePayload.class);
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_VERIFICATION_SUCCESS), any(), any(), eq(challengeId), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("verifyLoginTotp rejects challenge belonging to another user")
    void testVerifyLoginTotpUserMismatch() {
        String challengeId = UUID.randomUUID().toString();
        UUID attackerId = UUID.randomUUID();
        MfaChallengePayload challenge = MfaChallengePayload.of(challengeId, userId, "LOGIN_MFA", 300L, 5);

        when(securityStateStore.get("mfa_challenge", challengeId, MfaChallengePayload.class)).thenReturn(Optional.of(challenge));

        MfaVerificationResult result = mfaService.verifyLoginTotp(challengeId, attackerId, "123456");

        assertThat(result.success()).isFalse();
        assertThat(result.state()).isEqualTo(AuthenticationState.AUTHENTICATION_FAILED);

        verify(securityStateStore, never()).consumeAtomic(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("verifyLoginTotp locks challenge when maximum attempts exceeded")
    void testVerifyLoginTotpMaxAttemptsExceeded() {
        String challengeId = UUID.randomUUID().toString();
        MfaChallengePayload challenge = MfaChallengePayload.of(challengeId, userId, "LOGIN_MFA", 300L, 5);

        UserMfa activeMfa = new UserMfa(userId, samplePayload);
        activeMfa.enable();

        when(securityStateStore.get("mfa_challenge", challengeId, MfaChallengePayload.class)).thenReturn(Optional.of(challenge));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(activeMfa));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq("user-mfa:" + userId))).thenReturn(testSecretBytes);
        when(securityStateStore.incrementAttempts("mfa_challenge", challengeId, Duration.ofSeconds(300))).thenReturn(5L);

        MfaVerificationResult result = mfaService.verifyLoginTotp(challengeId, userId, "000000");

        assertThat(result.success()).isFalse();
        assertThat(result.state()).isEqualTo(AuthenticationState.MFA_CHALLENGE_LOCKED);

        verify(securityStateStore).delete("mfa_challenge", challengeId);
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_CHALLENGE_LOCKED), any(), eq(userId), eq(challengeId), any(), eq("MAX_ATTEMPTS_EXCEEDED"));
    }

    // ==========================================
    // 5. Recovery Code Verification Tests
    // ==========================================

    @Test
    @DisplayName("verifyLoginRecoveryCode successfully consumes code and challenge")
    void testVerifyLoginRecoveryCodeSuccess() {
        String challengeId = UUID.randomUUID().toString();
        MfaChallengePayload challenge = MfaChallengePayload.of(challengeId, userId, "LOGIN_MFA", 300L, 5);

        UserMfa activeMfa = new UserMfa(userId, samplePayload);
        activeMfa.setId(UUID.randomUUID());
        activeMfa.enable();

        String rawCode = "2345-6789-ABCD";
        String hashedCode = recoveryCodeService.hash(rawCode);
        MfaRecoveryCode codeEntity = new MfaRecoveryCode(activeMfa.getId(), hashedCode, 0);
        codeEntity.setId(UUID.randomUUID());

        when(securityStateStore.get("mfa_challenge", challengeId, MfaChallengePayload.class)).thenReturn(Optional.of(challenge));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(activeMfa));
        when(recoveryCodeRepository.findByUserMfaIdAndUsedFalseOrderByCodeIndexAsc(activeMfa.getId())).thenReturn(List.of(codeEntity));
        when(recoveryCodeRepository.markUsedIfUnused(eq(codeEntity.getId()), any(Instant.class))).thenReturn(1);
        when(securityStateStore.consumeAtomic("mfa_challenge", challengeId, MfaChallengePayload.class)).thenReturn(Optional.of(challenge));

        MfaVerificationResult result = mfaService.verifyLoginRecoveryCode(challengeId, userId, rawCode);

        assertThat(result.success()).isTrue();
        assertThat(result.state()).isEqualTo(AuthenticationState.MFA_VERIFIED);

        verify(recoveryCodeRepository).markUsedIfUnused(eq(codeEntity.getId()), any(Instant.class));
        verify(securityStateStore).consumeAtomic("mfa_challenge", challengeId, MfaChallengePayload.class);
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_RECOVERY_CODE_USED), any(), eq(codeEntity.getId()), eq(challengeId), any(), eq("SUCCESS"));
    }

    // ==========================================
    // 6. Disable MFA Tests
    // ==========================================

    @Test
    @DisplayName("disableMfa removes recovery codes and disables UserMfa state via admin override")
    void testDisableMfaSuccess() {
        UserMfa activeMfa = new UserMfa(userId, samplePayload);
        activeMfa.setId(UUID.randomUUID());
        activeMfa.enable();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(activeMfa));

        mfaService.disableMfa(userId);

        assertThat(activeMfa.isEnabled()).isFalse();
        assertThat(activeMfa.getStatus()).isEqualTo(MfaStatus.DISABLED);
        assertThat(user.isMfaEnabled()).isFalse();

        verify(recoveryCodeRepository).deleteByUserMfaId(activeMfa.getId());
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_DISABLED), any(), eq(activeMfa.getId()), any(), any(), eq("ADMIN_OVERRIDE"));
    }

    @Test
    @DisplayName("disableMfa with step-up succeeds with valid password and TOTP code")
    void testDisableMfaStepUpWithTotpSuccess() {
        user.setPasswordHash(passwordEncoder.encode("SecurePass123!"));

        UserMfa activeMfa = new UserMfa(userId, samplePayload);
        activeMfa.setId(UUID.randomUUID());
        activeMfa.enable();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(activeMfa));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq("user-mfa:" + userId))).thenReturn(testSecretBytes);

        String currentCode = totpService.generateCode(testSecret);

        mfaService.disableMfa(userId, "SecurePass123!", currentCode, null);

        assertThat(activeMfa.isEnabled()).isFalse();
        assertThat(activeMfa.getStatus()).isEqualTo(MfaStatus.DISABLED);
        assertThat(user.isMfaEnabled()).isFalse();

        verify(recoveryCodeRepository).deleteByUserMfaId(activeMfa.getId());
        verify(auditService).recordAudit(any(), any(), eq(userId), eq("USER"), eq(AuditAction.MFA_DISABLED), any(), eq(activeMfa.getId()), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("disableMfa with step-up throws when password is invalid")
    void testDisableMfaStepUpInvalidPassword() {
        user.setPasswordHash(passwordEncoder.encode("SecurePass123!"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mfaService.disableMfa(userId, "WrongPassword!", "123456", null))
                .isInstanceOf(com.secretvault.common.exception.ApiException.class)
                .hasMessageContaining("Invalid current password");
    }
}

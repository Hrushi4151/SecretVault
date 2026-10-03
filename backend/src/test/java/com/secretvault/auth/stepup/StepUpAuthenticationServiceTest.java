package com.secretvault.auth.stepup;

import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.stepup.dto.StepUpChallengeResponse;
import com.secretvault.auth.stepup.dto.StepUpProofResponse;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpChallengePayload;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.stepup.model.StepUpFactor;
import com.secretvault.auth.stepup.model.StepUpProofPayload;
import com.secretvault.auth.stepup.service.DefaultStepUpAuthenticationService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepUpAuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private MfaService mfaService;

    @Mock
    private SecurityStateStore securityStateStore;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository userWebAuthnCredentialRepository;

    @Mock
    private com.secretvault.auth.webauthn.service.WebAuthnService webAuthnService;

    @InjectMocks
    private DefaultStepUpAuthenticationService stepUpService;

    private UUID userId;
    private String sessionIdentifier;
    private User activeUser;
    private UserSession activeSession;
    private StepUpContext secretContext;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionIdentifier = "sess_test1234567890abcdef";

        activeUser = new User("alice@example.com", "$2a$12$hashedpwd", "Alice Admin");
        activeUser.setId(userId);

        activeSession = new UserSession(
                userId,
                sessionIdentifier,
                AuthMethod.PASSWORD,
                "127.0.0.1",
                "Mozilla/5.0",
                "Desktop",
                "Chrome",
                "Linux",
                Instant.now().plusSeconds(3600)
        );

        secretContext = StepUpContext.forSecret(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("Create challenge: Password only when MFA is not enabled")
    void testCreateChallenge_mfaDisabled() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(mfaService.isMfaEnabled(userId)).thenReturn(false);

        StepUpChallengeResponse response = stepUpService.createChallenge(
                userId,
                sessionIdentifier,
                StepUpAction.SECRET_REVEAL,
                secretContext
        );

        assertNotNull(response.challengeId());
        assertEquals(StepUpAction.SECRET_REVEAL, response.action());
        assertEquals(List.of(StepUpFactor.PASSWORD), response.supportedFactors());
        verify(securityStateStore).put(eq("step_up_challenge"), eq(response.challengeId()), any(StepUpChallengePayload.class), any(Duration.class));
        verify(auditService).recordAudit(isNull(), eq(secretContext.workspaceId()), eq(userId), eq("USER"), eq(AuditAction.STEP_UP_CHALLENGE_CREATED), eq("STEP_UP_CHALLENGE"), eq(userId), eq(response.challengeId()), isNull(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("Create challenge: Password, TOTP, and Recovery Code when MFA is enabled")
    void testCreateChallenge_mfaEnabled() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(mfaService.isMfaEnabled(userId)).thenReturn(true);

        StepUpChallengeResponse response = stepUpService.createChallenge(
                userId,
                sessionIdentifier,
                StepUpAction.SECRET_REVEAL,
                secretContext
        );

        assertNotNull(response.challengeId());
        assertTrue(response.supportedFactors().contains(StepUpFactor.PASSWORD));
        assertTrue(response.supportedFactors().contains(StepUpFactor.TOTP));
        assertTrue(response.supportedFactors().contains(StepUpFactor.RECOVERY_CODE));
    }

    @Test
    @DisplayName("Create challenge: Rejects inactive user")
    void testCreateChallenge_inactiveUser() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.createChallenge(userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext));
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("Create challenge: Rejects revoked session")
    void testCreateChallenge_revokedSession() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        activeSession.revoke("Security logout");
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.createChallenge(userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext));
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("Verify password: Valid password generates proof token and cleans up challenge")
    void testVerifyPassword_success() {
        String challengeId = UUID.randomUUID().toString();
        StepUpChallengePayload challenge = StepUpChallengePayload.of(
                challengeId, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext, List.of(StepUpFactor.PASSWORD), 300, 5
        );

        when(securityStateStore.get("step_up_challenge", challengeId, StepUpChallengePayload.class))
                .thenReturn(Optional.of(challenge));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("CorrectPassword123!", activeUser.getPasswordHash())).thenReturn(true);

        StepUpProofResponse response = stepUpService.verifyPassword(challengeId, userId, sessionIdentifier, "CorrectPassword123!");

        assertNotNull(response.proofToken());
        assertTrue(response.proofToken().startsWith("stup_"));
        assertEquals(StepUpAction.SECRET_REVEAL, response.action());
        assertEquals(StepUpFactor.PASSWORD, response.factorUsed());

        verify(securityStateStore).delete("step_up_challenge", challengeId);
        verify(securityStateStore).put(eq("step_up_proof"), eq(response.proofToken()), any(StepUpProofPayload.class), any(Duration.class));
        verify(auditService).recordAudit(isNull(), eq(secretContext.workspaceId()), eq(userId), eq("USER"), eq(AuditAction.STEP_UP_VERIFICATION_SUCCESS), eq("STEP_UP_PROOF"), eq(userId), eq(challengeId), isNull(), eq("PASSWORD"));
    }

    @Test
    @DisplayName("Verify password: Wrong password increments attempts, audits failure, and rejects")
    void testVerifyPassword_wrongPassword() {
        String challengeId = UUID.randomUUID().toString();
        StepUpChallengePayload challenge = StepUpChallengePayload.of(
                challengeId, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext, List.of(StepUpFactor.PASSWORD), 300, 5
        );

        when(securityStateStore.get("step_up_challenge", challengeId, StepUpChallengePayload.class))
                .thenReturn(Optional.of(challenge));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("WrongPassword", activeUser.getPasswordHash())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyPassword(challengeId, userId, sessionIdentifier, "WrongPassword"));
        assertEquals(401, ex.getStatus().value());

        verify(securityStateStore).incrementAttempts(eq("step_up_challenge"), eq(challengeId), any(Duration.class));
        verify(auditService).recordAudit(isNull(), eq(secretContext.workspaceId()), eq(userId), eq("USER"), eq(AuditAction.STEP_UP_VERIFICATION_FAILED), eq("STEP_UP_CHALLENGE"), eq(userId), eq(challengeId), isNull(), eq("INVALID_PASSWORD"));
    }

    @Test
    @DisplayName("Verify TOTP: Valid code issues step-up proof")
    void testVerifyTotp_success() {
        String challengeId = UUID.randomUUID().toString();
        StepUpChallengePayload challenge = StepUpChallengePayload.of(
                challengeId, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext, List.of(StepUpFactor.TOTP), 300, 5
        );

        when(securityStateStore.get("step_up_challenge", challengeId, StepUpChallengePayload.class))
                .thenReturn(Optional.of(challenge));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(mfaService.isMfaEnabled(userId)).thenReturn(true);
        when(mfaService.verifyTotp(userId, "123456")).thenReturn(true);

        StepUpProofResponse response = stepUpService.verifyTotp(challengeId, userId, sessionIdentifier, "123456");

        assertNotNull(response.proofToken());
        assertTrue(response.proofToken().startsWith("stup_"));
        assertEquals(StepUpFactor.TOTP, response.factorUsed());
        verify(securityStateStore).delete("step_up_challenge", challengeId);
    }

    @Test
    @DisplayName("Verify TOTP: Invalid code fails and audits failure")
    void testVerifyTotp_invalidCode() {
        String challengeId = UUID.randomUUID().toString();
        StepUpChallengePayload challenge = StepUpChallengePayload.of(
                challengeId, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext, List.of(StepUpFactor.TOTP), 300, 5
        );

        when(securityStateStore.get("step_up_challenge", challengeId, StepUpChallengePayload.class))
                .thenReturn(Optional.of(challenge));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(mfaService.isMfaEnabled(userId)).thenReturn(true);
        when(mfaService.verifyTotp(userId, "000000")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyTotp(challengeId, userId, sessionIdentifier, "000000"));
        assertEquals(400, ex.getStatus().value());
        verify(auditService).recordAudit(isNull(), eq(secretContext.workspaceId()), eq(userId), eq("USER"), eq(AuditAction.STEP_UP_VERIFICATION_FAILED), eq("STEP_UP_CHALLENGE"), eq(userId), eq(challengeId), isNull(), eq("INVALID_TOTP"));
    }

    @Test
    @DisplayName("Verify recovery code: Valid code issues step-up proof")
    void testVerifyRecoveryCode_success() {
        String challengeId = UUID.randomUUID().toString();
        StepUpChallengePayload challenge = StepUpChallengePayload.of(
                challengeId, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext, List.of(StepUpFactor.RECOVERY_CODE), 300, 5
        );

        when(securityStateStore.get("step_up_challenge", challengeId, StepUpChallengePayload.class))
                .thenReturn(Optional.of(challenge));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(mfaService.isMfaEnabled(userId)).thenReturn(true);
        when(mfaService.verifyAndConsumeRecoveryCode(userId, "REC-1234-5678")).thenReturn(true);

        StepUpProofResponse response = stepUpService.verifyRecoveryCode(challengeId, userId, sessionIdentifier, "REC-1234-5678");

        assertNotNull(response.proofToken());
        assertEquals(StepUpFactor.RECOVERY_CODE, response.factorUsed());
    }

    @Test
    @DisplayName("verifyAndConsumeProof: Valid proof consumed atomically")
    void testVerifyAndConsumeProof_success() {
        String proofToken = "stup_1234567890abcdef1234567890abcdef";
        StepUpProofPayload proof = StepUpProofPayload.of(
                proofToken, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext, StepUpFactor.PASSWORD, 300
        );

        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.of(proof));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));

        assertDoesNotThrow(() ->
                stepUpService.verifyAndConsumeProof(proofToken, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, secretContext));

        verify(auditService).recordAudit(isNull(), eq(secretContext.workspaceId()), eq(userId), eq("USER"), eq(AuditAction.STEP_UP_PROOF_CONSUMED), eq("STEP_UP_PROOF"), eq(userId), eq(proofToken), isNull(), eq("SUCCESS"));
    }
}

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
class StepUpSecurityTest {

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

    private UUID userA;
    private UUID userB;
    private String sessionA;
    private String sessionB;
    private User activeUserA;
    private UserSession activeSessionA;
    private UserSession activeSessionB;

    private UUID workspaceId;
    private UUID projectId;
    private UUID env1Id;
    private UUID env2Id;
    private UUID secret1Id;
    private UUID secret2Id;

    private StepUpContext contextSecret1;
    private StepUpContext contextSecret2;
    private StepUpContext contextEnv2Secret1;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        sessionA = "sess_user_a_1234567890abcdef";
        sessionB = "sess_user_b_0987654321fedcba";

        activeUserA = new User("userA@example.com", "$2a$12$hashedpwd", "User A");
        activeUserA.setId(userA);

        activeSessionA = new UserSession(userA, sessionA, AuthMethod.PASSWORD, "127.0.0.1", "Browser", "Device", "Chrome", "Linux", Instant.now().plusSeconds(3600));
        activeSessionB = new UserSession(userB, sessionB, AuthMethod.PASSWORD, "127.0.0.1", "Browser", "Device", "Chrome", "Linux", Instant.now().plusSeconds(3600));

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        env1Id = UUID.randomUUID();
        env2Id = UUID.randomUUID();
        secret1Id = UUID.randomUUID();
        secret2Id = UUID.randomUUID();

        contextSecret1 = StepUpContext.forSecret(workspaceId, projectId, env1Id, secret1Id);
        contextSecret2 = StepUpContext.forSecret(workspaceId, projectId, env1Id, secret2Id);
        contextEnv2Secret1 = StepUpContext.forSecret(workspaceId, projectId, env2Id, secret1Id);
    }

    @Test
    @DisplayName("SECURITY: Replay protection - Second consumption of same proof token is REJECTED")
    void testSecurity_replayProtection() {
        String proofToken = "stup_token_replay_test_123";

        // First call consumes the token; second call returns empty from Redis
        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1));

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_INVALID", ex.getCode());

        verify(auditService).recordAudit(isNull(), eq(workspaceId), eq(userA), eq("USER"), eq(AuditAction.STEP_UP_REPLAY_REJECTED), eq("STEP_UP_PROOF"), eq(userA), eq(proofToken), isNull(), eq("EXPIRED_OR_REPLAYED"));
    }

    @Test
    @DisplayName("SECURITY: User isolation - Proof issued to User A cannot be consumed by User B")
    void testSecurity_crossUserReplay_rejected() {
        String proofToken = "stup_token_user_a";
        StepUpProofPayload proofForUserA = StepUpProofPayload.of(
                proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1, StepUpFactor.PASSWORD, 300
        );

        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.of(proofForUserA));

        // User B attempts to consume User A's proof
        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userB, sessionB, StepUpAction.SECRET_REVEAL, contextSecret1));

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_MISMATCH", ex.getCode());
        verify(auditService).recordAudit(isNull(), eq(workspaceId), eq(userB), eq("USER"), eq(AuditAction.STEP_UP_REPLAY_REJECTED), eq("STEP_UP_PROOF"), eq(userB), eq(proofToken), isNull(), eq("USER_MISMATCH"));
    }

    @Test
    @DisplayName("SECURITY: Session binding - Proof issued for Session A cannot be consumed by Session B")
    void testSecurity_crossSessionReplay_rejected() {
        String proofToken = "stup_token_session_a";
        StepUpProofPayload proofForSessionA = StepUpProofPayload.of(
                proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1, StepUpFactor.PASSWORD, 300
        );

        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.of(proofForSessionA));

        // Caller on sessionB attempts to use proof from sessionA
        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userA, sessionB, StepUpAction.SECRET_REVEAL, contextSecret1));

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("SECURITY: Revocation enforcement - If session was revoked after proof issuance, consumption fails")
    void testSecurity_revokedSessionInvalidatesProof() {
        String proofToken = "stup_token_revoked_session";
        StepUpProofPayload proof = StepUpProofPayload.of(
                proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1, StepUpFactor.PASSWORD, 300
        );

        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.of(proof));

        activeSessionA.revoke("Admin revocation");
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionA, userA))
                .thenReturn(Optional.of(activeSessionA));

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1));

        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("SECURITY: Action isolation - Proof for SECRET_REVEAL cannot authorize SECRET_DELETE")
    void testSecurity_actionSubstitution_rejected() {
        String proofToken = "stup_token_reveal_action";
        StepUpProofPayload proofForReveal = StepUpProofPayload.of(
                proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1, StepUpFactor.PASSWORD, 300
        );

        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.of(proofForReveal));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionA, userA))
                .thenReturn(Optional.of(activeSessionA));

        // Attempting SECRET_DELETE with SECRET_REVEAL proof
        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userA, sessionA, StepUpAction.SECRET_DELETE, contextSecret1));

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("SECURITY: Secret resource isolation - Proof for Secret 1 cannot authorize Secret 2")
    void testSecurity_secretResourceSubstitution_rejected() {
        String proofToken = "stup_token_secret1";
        StepUpProofPayload proofForSecret1 = StepUpProofPayload.of(
                proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1, StepUpFactor.PASSWORD, 300
        );

        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.of(proofForSecret1));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionA, userA))
                .thenReturn(Optional.of(activeSessionA));

        // Attempting to reveal Secret 2 using proof for Secret 1
        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret2));

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("SECURITY: Environment isolation - Proof for Environment 1 cannot authorize Environment 2")
    void testSecurity_environmentSubstitution_rejected() {
        String proofToken = "stup_token_env1";
        StepUpProofPayload proofForEnv1 = StepUpProofPayload.of(
                proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1, StepUpFactor.PASSWORD, 300
        );

        when(securityStateStore.consumeAtomic("step_up_proof", proofToken, StepUpProofPayload.class))
                .thenReturn(Optional.of(proofForEnv1));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionA, userA))
                .thenReturn(Optional.of(activeSessionA));

        // Attempting to reveal secret in Environment 2 using proof for Environment 1
        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userA, sessionA, StepUpAction.SECRET_REVEAL, contextEnv2Secret1));

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("SECURITY: Brute force lockout - 5 failed password attempts locks and deletes challenge")
    void testSecurity_bruteForceLockout() {
        String challengeId = "challenge_lockout_test";
        StepUpChallengePayload challenge = StepUpChallengePayload.of(
                challengeId, userA, sessionA, StepUpAction.SECRET_REVEAL, contextSecret1, List.of(StepUpFactor.PASSWORD), 300, 5
        );

        when(securityStateStore.get("step_up_challenge", challengeId, StepUpChallengePayload.class))
                .thenReturn(Optional.of(challenge));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionA, userA))
                .thenReturn(Optional.of(activeSessionA));
        when(userRepository.findById(userA)).thenReturn(Optional.of(activeUserA));
        when(passwordEncoder.matches(eq("WrongPwd"), any())).thenReturn(false);
        when(securityStateStore.incrementAttempts(eq("step_up_challenge"), eq(challengeId), any(Duration.class)))
                .thenReturn(5L);

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyPassword(challengeId, userA, sessionA, "WrongPwd"));

        assertEquals(401, ex.getStatus().value());
        // Challenge is deleted upon reaching max attempts
        verify(securityStateStore).delete("step_up_challenge", challengeId);
    }
}

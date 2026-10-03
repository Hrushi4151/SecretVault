package com.secretvault.auth.webauthn;

import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
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
import com.secretvault.auth.stepup.service.DefaultStepUpAuthenticationService;
import com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationOptionsResponse;
import com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository;
import com.secretvault.auth.webauthn.service.WebAuthnService;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepUpWebAuthnTest {

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
    private UserWebAuthnCredentialRepository userWebAuthnCredentialRepository;

    @Mock
    private WebAuthnService webAuthnService;

    @InjectMocks
    private DefaultStepUpAuthenticationService stepUpService;

    private UUID userId;
    private String sessionIdentifier;
    private User activeUser;
    private UserSession activeSession;
    private StepUpContext context;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionIdentifier = "sess_stepup_webauthn_123456";

        activeUser = new User("alice@example.com", "$2a$12$passwordHash", "Alice Admin");
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

        context = StepUpContext.forSecret(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("Create challenge: Includes StepUpFactor.WEBAUTHN when user has active WebAuthn credentials")
    void testCreateChallenge_includesWebAuthnFactor() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));
        when(mfaService.isMfaEnabled(userId)).thenReturn(true);
        when(userWebAuthnCredentialRepository.countByUserIdAndRevokedAtIsNull(userId)).thenReturn(2L);

        StepUpChallengeResponse response = stepUpService.createChallenge(
                userId,
                sessionIdentifier,
                StepUpAction.SECRET_REVEAL,
                context
        );

        assertNotNull(response);
        assertTrue(response.supportedFactors().contains(StepUpFactor.WEBAUTHN));
        assertTrue(response.supportedFactors().contains(StepUpFactor.TOTP));
        assertTrue(response.supportedFactors().contains(StepUpFactor.PASSWORD));
    }

    @Test
    @DisplayName("Step-Up WebAuthn: Generates assertion options for active step-up challenge")
    void testCreateWebAuthnStepUpOptions_delegatesToWebAuthnService() {
        WebAuthnAuthenticationOptionsResponse mockOpts = new WebAuthnAuthenticationOptionsResponse(
                "stepup_chlg_123",
                "{\"mock\":\"stepup_options\"}",
                Instant.now().plusSeconds(300)
        );

        when(webAuthnService.startStepUpAssertion(userId, sessionIdentifier, "chlg_123"))
                .thenReturn(mockOpts);

        WebAuthnAuthenticationOptionsResponse result = stepUpService.createWebAuthnStepUpOptions(
                "chlg_123",
                userId,
                sessionIdentifier
        );

        assertNotNull(result);
        assertEquals("stepup_chlg_123", result.challengeId());
        verify(webAuthnService).startStepUpAssertion(userId, sessionIdentifier, "chlg_123");
    }

    @Test
    @DisplayName("Step-Up WebAuthn: Verifies assertion and issues step-up proof token")
    void testVerifyWebAuthn_delegatesToWebAuthnService() {
        StepUpProofResponse mockProof = new StepUpProofResponse(
                "stup_webauthn_proof_token_123",
                StepUpAction.SECRET_REVEAL,
                StepUpFactor.WEBAUTHN,
                Instant.now().plusSeconds(300)
        );

        when(webAuthnService.finishStepUpAssertion(userId, sessionIdentifier, "chlg_123", "{\"mock\":\"assertion\"}"))
                .thenReturn(mockProof);

        StepUpProofResponse result = stepUpService.verifyWebAuthn(
                "chlg_123",
                userId,
                sessionIdentifier,
                "{\"mock\":\"assertion\"}"
        );

        assertNotNull(result);
        assertEquals("stup_webauthn_proof_token_123", result.proofToken());
        verify(webAuthnService).finishStepUpAssertion(userId, sessionIdentifier, "chlg_123", "{\"mock\":\"assertion\"}");
    }

    @Test
    @DisplayName("Step-Up Security: Proof issued for SECRET_REVEAL cannot be used for SECRET_DELETE (Action Swapping)")
    void testStepUpProof_actionSwapping_rejected() {
        String proofToken = "stup_webauthn_reveal_proof";
        com.secretvault.auth.stepup.model.StepUpProofPayload proof = com.secretvault.auth.stepup.model.StepUpProofPayload.of(
                proofToken,
                userId,
                sessionIdentifier,
                StepUpAction.SECRET_REVEAL,
                context,
                StepUpFactor.WEBAUTHN,
                300
        );

        when(securityStateStore.consumeAtomic(eq("step_up_proof"), eq(proofToken), eq(com.secretvault.auth.stepup.model.StepUpProofPayload.class)))
                .thenReturn(Optional.of(proof));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userId, sessionIdentifier, StepUpAction.SECRET_DELETE, context)
        );

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("Step-Up Security: Proof issued for Secret A cannot be used for Secret B (Resource Swapping)")
    void testStepUpProof_resourceSwapping_rejected() {
        String proofToken = "stup_webauthn_secret_a_proof";
        com.secretvault.auth.stepup.model.StepUpProofPayload proof = com.secretvault.auth.stepup.model.StepUpProofPayload.of(
                proofToken,
                userId,
                sessionIdentifier,
                StepUpAction.SECRET_REVEAL,
                context,
                StepUpFactor.WEBAUTHN,
                300
        );

        StepUpContext differentSecretContext = StepUpContext.forSecret(
                context.workspaceId(),
                context.projectId(),
                context.environmentId(),
                UUID.randomUUID() // Different secret ID
        );

        when(securityStateStore.consumeAtomic(eq("step_up_proof"), eq(proofToken), eq(com.secretvault.auth.stepup.model.StepUpProofPayload.class)))
                .thenReturn(Optional.of(proof));
        when(sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId))
                .thenReturn(Optional.of(activeSession));

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, differentSecretContext)
        );

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("Step-Up Security: Proof token is single-use and cannot be replayed")
    void testStepUpProof_replay_rejected() {
        String proofToken = "stup_already_used_token";

        when(securityStateStore.consumeAtomic(eq("step_up_proof"), eq(proofToken), eq(com.secretvault.auth.stepup.model.StepUpProofPayload.class)))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                stepUpService.verifyAndConsumeProof(proofToken, userId, sessionIdentifier, StepUpAction.SECRET_REVEAL, context)
        );

        assertEquals(403, ex.getStatus().value());
        assertEquals("STEP_UP_INVALID", ex.getCode());
    }
}

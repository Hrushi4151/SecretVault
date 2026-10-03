package com.secretvault.auth.webauthn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.webauthn.config.WebAuthnProperties;
import com.secretvault.auth.webauthn.dto.WebAuthnCredentialResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnRegistrationOptionsResponse;
import com.secretvault.auth.webauthn.entity.UserWebAuthnCredential;
import com.secretvault.auth.webauthn.model.WebAuthnRegistrationChallengePayload;
import com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository;
import com.secretvault.auth.webauthn.service.DefaultWebAuthnService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebAuthnRegistrationTest {

    @Mock
    private RelyingParty relyingParty;

    @Mock
    private WebAuthnProperties properties;

    @Mock
    private UserWebAuthnCredentialRepository credentialRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private SecurityStateStore securityStateStore;

    @Mock
    private SessionService sessionService;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuditService auditService;

    @Mock
    private MfaService mfaService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DefaultWebAuthnService webAuthnService;

    private UUID userId;
    private String sessionIdentifier;
    private User activeUser;
    private UserSession activeSession;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionIdentifier = "sess_webauthn_reg_test_123456";

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
                "macOS",
                Instant.now().plusSeconds(3600)
        );

        lenient().when(properties.getChallengeTtlSeconds()).thenReturn(300L);
        lenient().when(properties.getTimeoutSeconds()).thenReturn(60L);
        lenient().when(properties.getUserVerification()).thenReturn("PREFERRED");
    }

    @Test
    @DisplayName("Start registration: successfully generates creation options and stores challenge in Redis")
    void testStartRegistration_success() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(sessionRepository.findBySessionIdentifier(sessionIdentifier)).thenReturn(Optional.of(activeSession));

        PublicKeyCredentialCreationOptions mockOptions = mock(PublicKeyCredentialCreationOptions.class);
        when(mockOptions.toJson()).thenReturn("{\"mock\":\"creation_options\"}");
        when(relyingParty.startRegistration(any())).thenReturn(mockOptions);

        WebAuthnRegistrationOptionsResponse response = webAuthnService.startRegistration(
                userId,
                sessionIdentifier,
                "My MacBook Touch ID"
        );

        assertNotNull(response);
        assertNotNull(response.challengeId());
        assertEquals("{\"mock\":\"creation_options\"}", response.optionsJson());
        assertNotNull(response.expiresAt());

        verify(securityStateStore).put(
                eq("webauthn_reg_challenge"),
                eq(response.challengeId()),
                any(WebAuthnRegistrationChallengePayload.class),
                eq(Duration.ofSeconds(300))
        );

        verify(auditService).recordAudit(
                isNull(),
                isNull(),
                eq(userId),
                eq("USER"),
                eq(AuditAction.WEBAUTHN_REGISTRATION_STARTED),
                eq("USER"),
                eq(userId),
                eq(response.challengeId()),
                isNull(),
                eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Start registration: rejects inactive user")
    void testStartRegistration_inactiveUser() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.startRegistration(userId, sessionIdentifier, "Passkey")
        );

        assertEquals(403, ex.getStatus().value());
        verify(securityStateStore, never()).put(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Start registration: rejects revoked session")
    void testStartRegistration_revokedSession() {
        activeSession.revoke("Security policy");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(sessionRepository.findBySessionIdentifier(sessionIdentifier)).thenReturn(Optional.of(activeSession));

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.startRegistration(userId, sessionIdentifier, "Passkey")
        );

        assertEquals(401, ex.getStatus().value());
        verify(securityStateStore, never()).put(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Finish registration: rejects expired or missing challenge")
    void testFinishRegistration_expiredChallenge() {
        when(sessionRepository.findBySessionIdentifier(sessionIdentifier)).thenReturn(Optional.of(activeSession));
        when(securityStateStore.consumeAtomic(eq("webauthn_reg_challenge"), eq("invalid_chlg"), eq(WebAuthnRegistrationChallengePayload.class)))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.finishRegistration(userId, sessionIdentifier, "invalid_chlg", "Key", "{}")
        );

        assertEquals(400, ex.getStatus().value());
        assertEquals("WEBAUTHN_CHALLENGE_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("Finish registration: rejects cross-user challenge completion")
    void testFinishRegistration_crossUser() {
        UUID otherUserId = UUID.randomUUID();
        when(sessionRepository.findBySessionIdentifier(sessionIdentifier)).thenReturn(Optional.of(activeSession));

        WebAuthnRegistrationChallengePayload payload = WebAuthnRegistrationChallengePayload.of(
                "chlg_1",
                otherUserId,
                sessionIdentifier,
                "Other Key",
                "{}",
                300
        );

        when(securityStateStore.consumeAtomic(eq("webauthn_reg_challenge"), eq("chlg_1"), eq(WebAuthnRegistrationChallengePayload.class)))
                .thenReturn(Optional.of(payload));

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.finishRegistration(userId, sessionIdentifier, "chlg_1", "Key", "{}")
        );

        assertEquals(403, ex.getStatus().value());
    }
}

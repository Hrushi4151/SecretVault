package com.secretvault.auth.webauthn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.webauthn.config.WebAuthnProperties;
import com.secretvault.auth.webauthn.dto.WebAuthnCredentialResponse;
import com.secretvault.auth.webauthn.entity.UserWebAuthnCredential;
import com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository;
import com.secretvault.auth.webauthn.service.DefaultWebAuthnService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import com.yubico.webauthn.RelyingParty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebAuthnSecurityTest {

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

    private UUID userA;
    private UUID userB;
    private UUID credId;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        credId = UUID.randomUUID();

        lenient().when(properties.getChallengeTtlSeconds()).thenReturn(300L);
        lenient().when(properties.getTimeoutSeconds()).thenReturn(60L);
        lenient().when(properties.getUserVerification()).thenReturn("PREFERRED");
    }

    @Test
    @DisplayName("IDOR: User A cannot rename User B's WebAuthn credential")
    void testRenameCredential_idorProtected() {
        when(credentialRepository.findByIdAndUserId(credId, userA)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.renameCredential(userA, credId, "Hacked Name")
        );

        assertEquals(404, ex.getStatus().value());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("IDOR: User A cannot revoke User B's WebAuthn credential")
    void testRevokeCredential_idorProtected() {
        when(credentialRepository.findByIdAndUserId(credId, userA)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.revokeCredential(userA, credId)
        );

        assertEquals(404, ex.getStatus().value());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lockout Prevention: Cannot delete only remaining credential if no password or MFA")
    void testRevokeCredential_lockoutPrevention() {
        UserWebAuthnCredential cred = new UserWebAuthnCredential(
                userA,
                "cred_123",
                new byte[]{1, 2, 3},
                1L,
                "aaguid",
                "none",
                "internal",
                true,
                false,
                false,
                true,
                "Sole Passkey"
        );
        cred.setId(credId);

        User userWithoutPassword = new User("alice@example.com", null, "Alice PasskeyOnly");
        userWithoutPassword.setId(userA);

        when(credentialRepository.findByIdAndUserId(credId, userA)).thenReturn(Optional.of(cred));
        when(userRepository.findById(userA)).thenReturn(Optional.of(userWithoutPassword));
        when(credentialRepository.countByUserIdAndRevokedAtIsNull(userA)).thenReturn(1L);
        when(mfaService.isMfaEnabled(userA)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.revokeCredential(userA, credId)
        );

        assertEquals(400, ex.getStatus().value());
        assertEquals("LOCKOUT_PREVENTION", ex.getCode());
        assertFalse(cred.isRevoked());
    }

    @Test
    @DisplayName("List credentials: User A only sees their own active credentials")
    void testListCredentials_isolated() {
        UserWebAuthnCredential credA = new UserWebAuthnCredential(
                userA,
                "cred_A",
                new byte[]{1, 2, 3},
                5L,
                "aaguidA",
                "none",
                "internal",
                true,
                false,
                false,
                true,
                "Alice Passkey"
        );

        when(credentialRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userA))
                .thenReturn(List.of(credA));

        List<WebAuthnCredentialResponse> list = webAuthnService.listCredentials(userA);

        assertEquals(1, list.size());
        assertEquals("Alice Passkey", list.getFirst().friendlyName());
        verify(credentialRepository).findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userA);
    }

    @Test
    @DisplayName("Fail-Closed: Redis outage during registration options generation fails closed")
    void testStartRegistration_redisOutage_failsClosed() throws Exception {
        User user = new User("alice@example.com", "hash", "Alice");
        user.setId(userA);
        when(userRepository.findById(userA)).thenReturn(Optional.of(user));
        when(sessionRepository.findBySessionIdentifier("sess_123")).thenReturn(Optional.of(new com.secretvault.auth.session.entity.UserSession(
                userA, "sess_123", com.secretvault.auth.session.enums.AuthMethod.PASSWORD, "127.0.0.1", "agent", "Desktop", "Chrome", "macOS", java.time.Instant.now().plusSeconds(3600)
        )));

        com.yubico.webauthn.data.PublicKeyCredentialCreationOptions mockOpts = mock(com.yubico.webauthn.data.PublicKeyCredentialCreationOptions.class);
        when(mockOpts.toJson()).thenReturn("{\"mock\":\"options\"}");
        when(relyingParty.startRegistration(any())).thenReturn(mockOpts);

        doThrow(new RuntimeException("Redis connection refused"))
                .when(securityStateStore).put(anyString(), anyString(), any(), any());

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.startRegistration(userA, "sess_123", "My Key")
        );

        assertEquals(500, ex.getStatus().value());
        assertEquals("WEBAUTHN_REGISTRATION_FAILED", ex.getCode());
    }

    @Test
    @DisplayName("Fail-Closed: Redis outage during authentication options generation fails closed")
    void testStartAuthentication_redisOutage_failsClosed() throws Exception {
        com.yubico.webauthn.AssertionRequest mockReq = mock(com.yubico.webauthn.AssertionRequest.class);
        when(mockReq.toJson()).thenReturn("{\"mock\":\"assertion\"}");
        when(relyingParty.startAssertion(any())).thenReturn(mockReq);

        doThrow(new RuntimeException("Redis timeout"))
                .when(securityStateStore).put(anyString(), anyString(), any(), any());

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.startAuthentication(null)
        );

        assertEquals(500, ex.getStatus().value());
        assertEquals("WEBAUTHN_AUTH_OPTIONS_FAILED", ex.getCode());
    }

    @Test
    @DisplayName("Fail-Closed: Redis outage or missing challenge during authentication verification fails closed")
    void testFinishAuthentication_redisOutage_failsClosed() {
        when(securityStateStore.consumeAtomic(eq("webauthn_auth_challenge"), anyString(), eq(com.secretvault.auth.webauthn.model.WebAuthnAuthenticationChallengePayload.class)))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.finishAuthentication("chlg_missing", "{}")
        );

        assertEquals(400, ex.getStatus().value());
        assertEquals("WEBAUTHN_CHALLENGE_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("Input Validation: Renaming credential with blank name is rejected")
    void testRenameCredential_blankName_rejected() {
        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.renameCredential(userA, credId, "   ")
        );

        assertEquals(400, ex.getStatus().value());
    }

    @Test
    @DisplayName("Lifecycle: Renaming already-revoked credential is rejected")
    void testRenameCredential_alreadyRevoked_rejected() {
        UserWebAuthnCredential revokedCred = new UserWebAuthnCredential(
                userA,
                "cred_revoked",
                new byte[]{1, 2, 3},
                1L,
                "aaguid",
                "none",
                "internal",
                true,
                false,
                false,
                true,
                "Revoked Key"
        );
        revokedCred.setId(credId);
        revokedCred.revoke("User removed");

        when(credentialRepository.findByIdAndUserId(credId, userA)).thenReturn(Optional.of(revokedCred));

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.renameCredential(userA, credId, "New Name")
        );

        assertEquals(400, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("Cannot rename a revoked credential"));
    }
}

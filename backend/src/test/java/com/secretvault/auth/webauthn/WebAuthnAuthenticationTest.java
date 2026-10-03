package com.secretvault.auth.webauthn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.dto.AuthResponse;
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
import com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationOptionsResponse;
import com.secretvault.auth.webauthn.entity.UserWebAuthnCredential;
import com.secretvault.auth.webauthn.model.WebAuthnAuthenticationChallengePayload;
import com.secretvault.auth.webauthn.model.WebAuthnCeremonyType;
import com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository;
import com.secretvault.auth.webauthn.service.DefaultWebAuthnService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.RelyingParty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebAuthnAuthenticationTest {

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
    private User activeUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        activeUser = new User("bob@example.com", "$2a$12$passwordHash", "Bob Developer");
        activeUser.setId(userId);

        lenient().when(properties.getChallengeTtlSeconds()).thenReturn(300L);
        lenient().when(properties.getTimeoutSeconds()).thenReturn(60L);
        lenient().when(properties.getUserVerification()).thenReturn("PREFERRED");
    }

    @Test
    @DisplayName("Start authentication: successfully generates assertion request for discoverable passkey")
    void testStartAuthentication_discoverablePasskey() throws Exception {
        AssertionRequest mockRequest = mock(AssertionRequest.class);
        when(mockRequest.toJson()).thenReturn("{\"mock\":\"assertion_request\"}");
        when(relyingParty.startAssertion(any())).thenReturn(mockRequest);

        WebAuthnAuthenticationOptionsResponse response = webAuthnService.startAuthentication(null);

        assertNotNull(response);
        assertNotNull(response.challengeId());
        assertEquals("{\"mock\":\"assertion_request\"}", response.optionsJson());

        verify(securityStateStore).put(
                eq("webauthn_auth_challenge"),
                eq(response.challengeId()),
                any(WebAuthnAuthenticationChallengePayload.class),
                eq(Duration.ofSeconds(300))
        );

        verify(auditService).recordAudit(
                isNull(),
                isNull(),
                isNull(),
                eq("USER"),
                eq(AuditAction.WEBAUTHN_AUTHENTICATION_STARTED),
                eq("WEBAUTHN_CHALLENGE"),
                isNull(),
                eq(response.challengeId()),
                isNull(),
                eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Start authentication: successfully generates assertion request for known email")
    void testStartAuthentication_withEmail() throws Exception {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(activeUser));

        AssertionRequest mockRequest = mock(AssertionRequest.class);
        when(mockRequest.toJson()).thenReturn("{\"mock\":\"assertion_request_for_bob\"}");
        when(relyingParty.startAssertion(any())).thenReturn(mockRequest);

        WebAuthnAuthenticationOptionsResponse response = webAuthnService.startAuthentication("bob@example.com");

        assertNotNull(response);
        assertNotNull(response.challengeId());
        assertEquals("{\"mock\":\"assertion_request_for_bob\"}", response.optionsJson());
    }

    @Test
    @DisplayName("Start authentication: rejects inactive user email")
    void testStartAuthentication_inactiveUser() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(activeUser));

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.startAuthentication("bob@example.com")
        );

        assertEquals(403, ex.getStatus().value());
        verify(securityStateStore, never()).put(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Finish authentication: rejects expired challenge")
    void testFinishAuthentication_expiredChallenge() {
        when(securityStateStore.consumeAtomic(eq("webauthn_auth_challenge"), eq("invalid_auth_chlg"), eq(WebAuthnAuthenticationChallengePayload.class)))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                webAuthnService.finishAuthentication("invalid_auth_chlg", "{}")
        );

        assertEquals(400, ex.getStatus().value());
        assertEquals("WEBAUTHN_CHALLENGE_INVALID", ex.getCode());
    }
}

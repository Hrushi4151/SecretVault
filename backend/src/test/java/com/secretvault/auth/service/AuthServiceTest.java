package com.secretvault.auth.service;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RefreshTokenRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.entity.RefreshToken;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.dto.MfaRecoveryVerifyRequest;
import com.secretvault.auth.mfa.dto.MfaTotpVerifyRequest;
import com.secretvault.auth.mfa.model.AuthenticationState;
import com.secretvault.auth.mfa.model.MfaChallengeInfo;
import com.secretvault.auth.mfa.model.MfaVerificationResult;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.organization.entity.Organization;
import com.secretvault.organization.repository.OrganizationRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private MfaService mfaService;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AuthService authService;

    private UUID userId;
    private UUID orgId;
    private UUID workspaceId;
    private UUID sessionId;
    private User testUser;
    private Organization testOrg;
    private Workspace testWorkspace;
    private WorkspaceMembership testMembership;
    private UserSession testSession;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        testUser = new User("alice@example.com", "hashed_password", "Alice Vance");
        testUser.setId(userId);

        testOrg = new Organization("Alice Vance's Organization", "alice-vance-org", "FREE");
        testOrg.setId(orgId);

        testWorkspace = new Workspace(orgId, "Default Workspace", "default", true);
        testWorkspace.setId(workspaceId);

        testMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);

        testSession = new UserSession(
                userId,
                "sess_test1234567890",
                AuthMethod.PASSWORD,
                "127.0.0.1",
                "JUnit Test Client",
                "JUnit Device",
                "Chrome",
                "Windows",
                Instant.now().plusSeconds(86400 * 30)
        );
        testSession.setId(sessionId);
    }

    @Test
    @DisplayName("Should register new user, provision org, default workspace, establish session, and issue tokens")
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "Password123!", "Alice Vance", null);

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(organizationRepository.existsBySlug(anyString())).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(testOrg);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(testWorkspace);
        when(membershipRepository.save(any(WorkspaceMembership.class))).thenReturn(testMembership);
        when(sessionService.createSession(eq(userId), eq(AuthMethod.PASSWORD), any(), any(), any()))
                .thenReturn(testSession);
        when(tokenProvider.generateAccessToken(eq(userId), eq("alice@example.com"), eq("Alice Vance"), eq("sess_test1234567890")))
                .thenReturn("mock.jwt.token");
        when(tokenProvider.generateRefreshToken()).thenReturn("mock_refresh_token");
        when(tokenProvider.hashToken(anyString())).thenReturn("hashed_refresh_token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.accessToken());
        assertEquals("mock_refresh_token", response.refreshToken());
        assertEquals("alice@example.com", response.user().email());
        assertEquals("Default Workspace", response.activeWorkspace().name());
        assertEquals(WorkspaceRole.OWNER, response.activeWorkspace().role());

        verify(userRepository).save(any(User.class));
        verify(organizationRepository).save(any(Organization.class));
        verify(workspaceRepository).save(any(Workspace.class));
        verify(membershipRepository).save(any(WorkspaceMembership.class));
        verify(sessionService).createSession(eq(userId), eq(AuthMethod.PASSWORD), any(), any(), any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when email is already in use")
    void testRegisterEmailCollision() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "Password123!", "Alice", null);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(request));
        assertEquals("RESOURCE_CONFLICT", ex.getCode());
        verify(userRepository, never()).save(any());
        verify(sessionService, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should authenticate user, establish session, and issue tokens when MFA is disabled")
    void testLoginSuccessMfaDisabled() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123!");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_password")).thenReturn(true);
        when(mfaService.isMfaEnabled(userId)).thenReturn(false);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(sessionService.createSession(eq(userId), eq(AuthMethod.PASSWORD), any(), any(), any()))
                .thenReturn(testSession);
        when(tokenProvider.generateAccessToken(eq(userId), eq("alice@example.com"), eq("Alice Vance"), eq("sess_test1234567890")))
                .thenReturn("mock.jwt.token");
        when(tokenProvider.generateRefreshToken()).thenReturn("mock_refresh_token");
        when(tokenProvider.hashToken(anyString())).thenReturn("hashed_refresh_token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertFalse(response.mfaRequired());
        assertNull(response.mfaChallengeId());
        assertEquals("mock.jwt.token", response.accessToken());
        assertEquals(testUser.getEmail(), response.user().email());
        verify(sessionService).createSession(eq(userId), eq(AuthMethod.PASSWORD), any(), any(), any());
    }

    @Test
    @DisplayName("Should return MFA challenge without creating session or tokens when MFA is enabled")
    void testLoginSuccessMfaEnabled() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123!");
        Instant expiry = Instant.now().plusSeconds(300);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_password")).thenReturn(true);
        when(mfaService.isMfaEnabled(userId)).thenReturn(true);
        when(mfaService.createLoginChallenge(userId)).thenReturn(new MfaChallengeInfo("challenge-123", userId, expiry, AuthenticationState.MFA_REQUIRED));

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertTrue(response.mfaRequired());
        assertEquals("challenge-123", response.mfaChallengeId());
        assertEquals(expiry, response.mfaExpiresAt());
        assertNull(response.accessToken(), "Access token MUST NOT be issued prior to MFA verification");
        assertNull(response.refreshToken(), "Refresh token MUST NOT be issued prior to MFA verification");
        assertNull(response.user());
        assertNull(response.activeWorkspace());
        verify(sessionService, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject login with invalid password")
    void testLoginInvalidPassword() {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "hashed_password")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(sessionService, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully complete MFA TOTP login, establish session, and issue tokens")
    void testCompleteMfaTotpLoginSuccess() {
        MfaTotpVerifyRequest request = new MfaTotpVerifyRequest("challenge-123", "123456");

        when(mfaService.verifyLoginTotp("challenge-123", "123456"))
                .thenReturn(MfaVerificationResult.success(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(sessionService.createSession(eq(userId), eq(AuthMethod.PASSWORD_MFA_TOTP), any(), any(), any()))
                .thenReturn(testSession);
        when(tokenProvider.generateAccessToken(eq(userId), eq("alice@example.com"), eq("Alice Vance"), eq("sess_test1234567890")))
                .thenReturn("mfa.jwt.token");
        when(tokenProvider.generateRefreshToken()).thenReturn("mfa_refresh_token");
        when(tokenProvider.hashToken(anyString())).thenReturn("hashed_mfa_refresh_token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.completeMfaTotpLogin(request);

        assertNotNull(response);
        assertFalse(response.mfaRequired());
        assertEquals("mfa.jwt.token", response.accessToken());
        assertEquals("mfa_refresh_token", response.refreshToken());
        assertEquals("alice@example.com", response.user().email());
        verify(sessionService).createSession(eq(userId), eq(AuthMethod.PASSWORD_MFA_TOTP), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject MFA TOTP login when verification fails")
    void testCompleteMfaTotpLoginFailed() {
        MfaTotpVerifyRequest request = new MfaTotpVerifyRequest("challenge-123", "000000");

        when(mfaService.verifyLoginTotp("challenge-123", "000000"))
                .thenReturn(MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Invalid TOTP code"));

        ApiException ex = assertThrows(ApiException.class, () -> authService.completeMfaTotpLogin(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(sessionService, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully complete MFA recovery code login, establish session, and issue tokens")
    void testCompleteMfaRecoveryLoginSuccess() {
        MfaRecoveryVerifyRequest request = new MfaRecoveryVerifyRequest("challenge-123", "2345-6789-ABCD");

        when(mfaService.verifyLoginRecoveryCode("challenge-123", "2345-6789-ABCD"))
                .thenReturn(MfaVerificationResult.success(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(sessionService.createSession(eq(userId), eq(AuthMethod.PASSWORD_MFA_RECOVERY), any(), any(), any()))
                .thenReturn(testSession);
        when(tokenProvider.generateAccessToken(eq(userId), eq("alice@example.com"), eq("Alice Vance"), eq("sess_test1234567890")))
                .thenReturn("recovery.jwt.token");
        when(tokenProvider.generateRefreshToken()).thenReturn("recovery_refresh_token");
        when(tokenProvider.hashToken(anyString())).thenReturn("hashed_recovery_refresh_token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.completeMfaRecoveryLogin(request);

        assertNotNull(response);
        assertFalse(response.mfaRequired());
        assertEquals("recovery.jwt.token", response.accessToken());
        assertEquals("recovery_refresh_token", response.refreshToken());
        assertEquals("alice@example.com", response.user().email());
        verify(sessionService).createSession(eq(userId), eq(AuthMethod.PASSWORD_MFA_RECOVERY), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject MFA recovery code login when verification fails")
    void testCompleteMfaRecoveryLoginFailed() {
        MfaRecoveryVerifyRequest request = new MfaRecoveryVerifyRequest("challenge-123", "INVALID-CODE");

        when(mfaService.verifyLoginRecoveryCode("challenge-123", "INVALID-CODE"))
                .thenReturn(MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Invalid recovery code"));

        ApiException ex = assertThrows(ApiException.class, () -> authService.completeMfaRecoveryLogin(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(sessionService, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should rotate refresh token, update session activity, and issue new token pair")
    void testRefreshTokenRotation() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_raw_refresh_token");
        RefreshToken oldToken = new RefreshToken(userId, sessionId, "hashed_old_token", Instant.now().plusSeconds(3600));

        when(tokenProvider.hashToken("valid_raw_refresh_token")).thenReturn("hashed_old_token");
        when(refreshTokenRepository.markRevokedIfActive("hashed_old_token")).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash("hashed_old_token")).thenReturn(Optional.of(oldToken));
        when(sessionService.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(sessionService.recordSessionActivity(eq(sessionId), any(), any())).thenReturn(testSession);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(tokenProvider.generateAccessToken(eq(userId), eq("alice@example.com"), eq("Alice Vance"), eq("sess_test1234567890")))
                .thenReturn("new.jwt.token");
        when(tokenProvider.generateRefreshToken()).thenReturn("new_raw_refresh_token");
        when(tokenProvider.hashToken("new_raw_refresh_token")).thenReturn("hashed_new_token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.refresh(request);

        assertNotNull(response);
        assertEquals("new.jwt.token", response.accessToken());
        assertEquals("new_raw_refresh_token", response.refreshToken());
        verify(sessionService).recordSessionActivity(eq(sessionId), any(), any());
    }

    @Test
    @DisplayName("Should reject refresh when associated session is revoked")
    void testRefreshRejectedWhenSessionRevoked() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_raw_refresh_token");
        RefreshToken token = new RefreshToken(userId, sessionId, "hashed_old_token", Instant.now().plusSeconds(3600));
        testSession.revoke("USER_REVOKED");

        when(tokenProvider.hashToken("valid_raw_refresh_token")).thenReturn("hashed_old_token");
        when(refreshTokenRepository.markRevokedIfActive("hashed_old_token")).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash("hashed_old_token")).thenReturn(Optional.of(token));
        when(sessionService.findById(sessionId)).thenReturn(Optional.of(testSession));

        ApiException ex = assertThrows(ApiException.class, () -> authService.refresh(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    @DisplayName("Should reject refresh when refresh token is already revoked (replay protection)")
    void testRefreshReplayProtection() {
        RefreshTokenRequest request = new RefreshTokenRequest("reused_raw_refresh_token");
        RefreshToken revokedToken = new RefreshToken(userId, sessionId, "hashed_reused_token", Instant.now().plusSeconds(3600));
        revokedToken.setRevoked(true);

        when(tokenProvider.hashToken("reused_raw_refresh_token")).thenReturn("hashed_reused_token");
        when(refreshTokenRepository.markRevokedIfActive("hashed_reused_token")).thenReturn(0);
        when(refreshTokenRepository.findByTokenHash("hashed_reused_token")).thenReturn(Optional.of(revokedToken));
        when(sessionService.findById(sessionId)).thenReturn(Optional.of(testSession));

        ApiException ex = assertThrows(ApiException.class, () -> authService.refresh(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(sessionService).revokeSession(eq(userId), eq("sess_test1234567890"));
    }

    @Test
    @DisplayName("Should reject refresh when user account is deactivated or suspended")
    void testRefreshDisabledAccount() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_raw_refresh_token");
        RefreshToken token = new RefreshToken(userId, sessionId, "hashed_old_token", Instant.now().plusSeconds(3600));
        testUser.setStatus(UserStatus.SUSPENDED);

        when(tokenProvider.hashToken("valid_raw_refresh_token")).thenReturn("hashed_old_token");
        when(refreshTokenRepository.markRevokedIfActive("hashed_old_token")).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash("hashed_old_token")).thenReturn(Optional.of(token));
        when(sessionService.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        ApiException ex = assertThrows(ApiException.class, () -> authService.refresh(request));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should delegate logout with sessionIdentifier to SessionService")
    void testLogoutWithSession() {
        authService.logout(userId, "sess_test1234567890");
        verify(sessionService).revokeSession(userId, "sess_test1234567890");
        verify(sessionService, never()).revokeAllSessions(any());
    }

    @Test
    @DisplayName("Should delegate global logout without sessionIdentifier to SessionService")
    void testLogoutGlobal() {
        authService.logout(userId, null);
        verify(sessionService).revokeAllSessions(userId);
        verify(sessionService, never()).revokeSession(any(), any());
    }
}

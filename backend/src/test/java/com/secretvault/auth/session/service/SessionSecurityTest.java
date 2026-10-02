package com.secretvault.auth.session.service;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RefreshTokenRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.service.AuthService;
import com.secretvault.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SessionSecurityTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Security: Pending MFA challenge creates ZERO sessions and ZERO refresh tokens")
    void testMfaChallengeCreatesNoSession() {
        String email = "mfa_session_sec_" + UUID.randomUUID() + "@example.com";
        AuthResponse initial = authService.register(new RegisterRequest(email, "Password123!Secure", "MFA User", "MFA Org"));
        UUID userId = initial.user().id();

        // 1. Enroll & activate MFA properly
        var enrollment = mfaService.beginEnrollment(userId);
        String validTotp = totpService.generateCode(enrollment.secret());
        mfaService.activateMfa(userId, validTotp);

        int sessionsBefore = sessionService.listUserSessions(userId, null).size();

        // 2. Attempt primary password login
        AuthResponse challengeResponse = authService.login(new LoginRequest(email, "Password123!Secure"));

        assertTrue(challengeResponse.mfaRequired());
        assertNotNull(challengeResponse.mfaChallengeId());
        assertNull(challengeResponse.accessToken(), "Access token must not be issued on pending challenge");
        assertNull(challengeResponse.refreshToken(), "Refresh token must not be issued on pending challenge");

        int sessionsAfter = sessionService.listUserSessions(userId, null).size();
        assertEquals(sessionsBefore, sessionsAfter, "No new session should be created for a pending MFA challenge");
    }

    @Test
    @DisplayName("Security: Deactivated / suspended user cannot refresh existing session")
    void testDeactivatedUserSessionBlocked() {
        String email = "deactivated_user_" + UUID.randomUUID() + "@example.com";
        AuthResponse reg = authService.register(new RegisterRequest(email, "Password123!Secure", "Deact User", "Deact Org"));

        // Deactivate account
        User user = userRepository.findById(reg.user().id()).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.refresh(new RefreshTokenRequest(reg.refreshToken())));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Security: Session identifiers have high entropy and cannot be guessed")
    void testSessionIdentifierEntropy() {
        String email = "entropy_user_" + UUID.randomUUID() + "@example.com";
        AuthResponse reg = authService.register(new RegisterRequest(email, "Password123!Secure", "Entropy User", "Entropy Org"));

        var sessions = sessionService.listUserSessions(reg.user().id(), null);
        assertFalse(sessions.isEmpty());
        String sid = sessions.get(0).id();

        assertTrue(sid.startsWith("sess_"));
        assertEquals(37, sid.length(), "sess_ (5 chars) + 32 hex chars = 37 chars of 128-bit entropy");
        assertFalse(sid.contains(email), "Session identifier must not leak user email");
        assertFalse(sid.contains(reg.user().id().toString()), "Session identifier must not leak user UUID");
    }

    @Test
    @DisplayName("Security: Cross-user session revocation (IDOR) fails with 404")
    void testCrossUserRevocationIdor() {
        String email1 = "idor_user1_" + UUID.randomUUID() + "@example.com";
        String email2 = "idor_user2_" + UUID.randomUUID() + "@example.com";

        AuthResponse user1 = authService.register(new RegisterRequest(email1, "Password123!Secure", "User 1", "Org 1"));
        AuthResponse user2 = authService.register(new RegisterRequest(email2, "Password123!Secure", "User 2", "Org 2"));

        var user1Sessions = sessionService.listUserSessions(user1.user().id(), null);
        String session1Id = user1Sessions.get(0).id();

        // User 2 attempts to revoke User 1's session
        ApiException ex = assertThrows(ApiException.class, () ->
                sessionService.revokeSession(user2.user().id(), session1Id));

        assertEquals("RESOURCE_NOT_FOUND", ex.getCode());

        // Verify session 1 is still active
        var recheck = sessionService.findByIdentifier(session1Id).orElseThrow();
        assertFalse(recheck.isRevoked());
    }
}

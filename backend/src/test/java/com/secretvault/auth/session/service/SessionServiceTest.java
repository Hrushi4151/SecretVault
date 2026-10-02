package com.secretvault.auth.session.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.session.dto.SessionResponse;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DefaultSessionService sessionService;

    private UUID userId;
    private UUID otherUserId;
    private UUID sessionId;
    private UserSession session1;
    private UserSession session2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        session1 = new UserSession(
                userId,
                "sess_alpha1234567890",
                AuthMethod.PASSWORD,
                "192.168.1.50",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                "Chrome on Windows",
                "Chrome",
                "Windows",
                Instant.now().plusSeconds(86400 * 30)
        );
        session1.setId(sessionId);

        session2 = new UserSession(
                userId,
                "sess_beta1234567890",
                AuthMethod.PASSWORD_MFA_TOTP,
                "10.0.0.1",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
                "Safari on macOS",
                "Safari",
                "macOS",
                Instant.now().plusSeconds(86400 * 30)
        );
        session2.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should create session with client device metadata and record audit event")
    void testCreateSessionSuccess() {
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession s = invocation.getArgument(0);
            s.setId(sessionId);
            return s;
        });

        UserSession created = sessionService.createSession(
                userId,
                AuthMethod.PASSWORD,
                "192.168.1.50",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36",
                Instant.now().plusSeconds(86400 * 30)
        );

        assertNotNull(created);
        assertNotNull(created.getSessionIdentifier());
        assertTrue(created.getSessionIdentifier().startsWith("sess_"));
        assertEquals("Chrome", created.getBrowser());
        assertEquals("Windows", created.getOperatingSystem());
        assertEquals("Chrome on Windows", created.getDeviceName());
        assertEquals(AuthMethod.PASSWORD, created.getAuthMethod());

        verify(sessionRepository).save(any(UserSession.class));
        verify(auditService).recordAudit(
                isNull(), isNull(), eq(userId), eq("USER"),
                eq(AuditAction.SESSION_CREATED), eq("USER_SESSION"),
                eq(sessionId), isNull(), eq("192.168.1.50"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should update session activity and metadata upon token refresh")
    void testRecordSessionActivity() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session1));
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(i -> i.getArgument(0));

        UserSession updated = sessionService.recordSessionActivity(
                sessionId,
                "192.168.1.99",
                "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"
        );

        assertNotNull(updated);
        assertEquals("192.168.1.99", updated.getIpAddress());
        assertEquals("Firefox", updated.getBrowser());
        assertEquals("Linux", updated.getOperatingSystem());
        verify(sessionRepository).save(session1);
    }

    @Test
    @DisplayName("Should not update activity on revoked session")
    void testRecordSessionActivityRevokedSession() {
        session1.revoke("USER_REVOKED");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session1));

        UserSession result = sessionService.recordSessionActivity(sessionId, "1.2.3.4", "UA");
        assertSame(session1, result);
        verify(sessionRepository, never()).save(session1);
    }

    @Test
    @DisplayName("Should list user sessions marking current session and sorting active first")
    void testListUserSessions() {
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(session1, session2));

        List<SessionResponse> list = sessionService.listUserSessions(userId, "sess_beta1234567890");

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("sess_beta1234567890", list.get(0).id());
        assertTrue(list.get(0).current());
        assertEquals("Safari on macOS", list.get(0).deviceName());

        assertEquals("sess_alpha1234567890", list.get(1).id());
        assertFalse(list.get(1).current());
    }

    @Test
    @DisplayName("Should successfully revoke individual session and invalidate refresh tokens")
    void testRevokeSessionSuccess() {
        when(sessionRepository.findBySessionIdentifier("sess_alpha1234567890"))
                .thenReturn(Optional.of(session1));

        sessionService.revokeSession(userId, "sess_alpha1234567890");

        assertTrue(session1.isRevoked());
        assertEquals("USER_REVOKED", session1.getRevocationReason());
        verify(sessionRepository).save(session1);
        verify(refreshTokenRepository).revokeAllBySessionId(sessionId);
        verify(auditService).recordAudit(
                isNull(), isNull(), eq(userId), eq("USER"),
                eq(AuditAction.SESSION_REVOKED), eq("USER_SESSION"),
                eq(sessionId), isNull(), isNull(), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should reject IDOR attempt when user tries to revoke another user's session")
    void testRevokeSessionIdorRejection() {
        when(sessionRepository.findBySessionIdentifier("sess_alpha1234567890"))
                .thenReturn(Optional.of(session1));

        // otherUserId attempts to revoke session1 belonging to userId
        ApiException ex = assertThrows(ApiException.class, () ->
                sessionService.revokeSession(otherUserId, "sess_alpha1234567890"));

        assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
        assertFalse(session1.isRevoked());
        verify(sessionRepository, never()).save(session1);
        verify(refreshTokenRepository, never()).revokeAllBySessionId(any());
    }

    @Test
    @DisplayName("Should be idempotent when revoking an already revoked session")
    void testRevokeSessionAlreadyRevokedIdempotency() {
        session1.revoke("USER_REVOKED");
        when(sessionRepository.findBySessionIdentifier("sess_alpha1234567890"))
                .thenReturn(Optional.of(session1));

        sessionService.revokeSession(userId, "sess_alpha1234567890");

        verify(sessionRepository, never()).save(session1);
        verify(refreshTokenRepository, never()).revokeAllBySessionId(any());
    }

    @Test
    @DisplayName("Should revoke all other sessions preserving caller active session")
    void testRevokeAllOtherSessions() {
        when(sessionRepository.findBySessionIdentifierAndUserId("sess_alpha1234567890", userId))
                .thenReturn(Optional.of(session1));
        when(sessionRepository.findOtherActiveSessions(userId, "sess_alpha1234567890"))
                .thenReturn(List.of(session2));

        sessionService.revokeAllOtherSessions(userId, "sess_alpha1234567890");

        assertFalse(session1.isRevoked(), "Current session must remain active");
        assertTrue(session2.isRevoked(), "Other session must be revoked");
        assertEquals("REVOKE_OTHERS", session2.getRevocationReason());

        verify(sessionRepository).save(session2);
        verify(refreshTokenRepository).revokeOtherSessionsByUserId(userId, sessionId);
        verify(auditService).recordAudit(
                isNull(), isNull(), eq(userId), eq("USER"),
                eq(AuditAction.SESSION_REVOKED_ALL_OTHERS), eq("USER_SESSION"),
                eq(sessionId), isNull(), isNull(), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should revoke all user sessions and refresh tokens")
    void testRevokeAllSessions() {
        when(sessionRepository.revokeAllByUserId(eq(userId), any(Instant.class), eq("REVOKE_ALL")))
                .thenReturn(2);

        sessionService.revokeAllSessions(userId);

        verify(sessionRepository).revokeAllByUserId(eq(userId), any(Instant.class), eq("REVOKE_ALL"));
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(auditService).recordAudit(
                isNull(), isNull(), eq(userId), eq("USER"),
                eq(AuditAction.SESSION_REVOKED_ALL), eq("USER_SESSION"),
                eq(userId), isNull(), isNull(), eq("SUCCESS")
        );
    }
}

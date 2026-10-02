package com.secretvault.auth.session.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.session.dto.SessionResponse;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.session.util.UserAgentParser;
import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link SessionService}.
 * Enforces cryptographic session generation, strict user boundary isolation,
 * refresh token invalidation, and immutable audit trailing.
 */
@Service
public class DefaultSessionService implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSessionService.class);

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultSessionService(
            UserSessionRepository sessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public UserSession createSession(
            UUID userId,
            AuthMethod authMethod,
            String ipAddress,
            String userAgent,
            Instant expiresAt) {

        String sessionIdentifier = generateSessionIdentifier();
        UserAgentParser.DeviceMetadata metadata = UserAgentParser.parse(userAgent);

        UserSession session = new UserSession(
                userId,
                sessionIdentifier,
                authMethod != null ? authMethod : AuthMethod.PASSWORD,
                ipAddress,
                metadata.sanitizedUserAgent(),
                metadata.deviceName(),
                metadata.browser(),
                metadata.operatingSystem(),
                expiresAt
        );

        UserSession saved = sessionRepository.save(session);
        log.info("Created new authenticated session [{}] for user [{}] via auth method [{}]",
                saved.getSessionIdentifier(), userId, saved.getAuthMethod());

        auditService.recordAudit(
                null,
                null,
                userId,
                "USER",
                AuditAction.SESSION_CREATED,
                "USER_SESSION",
                saved.getId(),
                null,
                ipAddress,
                "SUCCESS"
        );

        return saved;
    }

    @Override
    @Transactional
    public UserSession recordSessionActivity(UUID sessionId, String ipAddress, String userAgent) {
        Optional<UserSession> optionalSession = sessionRepository.findById(sessionId);
        if (optionalSession.isEmpty()) {
            return null;
        }

        UserSession session = optionalSession.get();
        if (!session.isActive()) {
            return session;
        }

        session.setLastUsedAt(Instant.now());

        if (ipAddress != null && !ipAddress.isBlank()) {
            session.setIpAddress(ipAddress);
        }

        if (userAgent != null && !userAgent.isBlank()) {
            UserAgentParser.DeviceMetadata metadata = UserAgentParser.parse(userAgent);
            session.setUserAgent(metadata.sanitizedUserAgent());
            session.setDeviceName(metadata.deviceName());
            session.setBrowser(metadata.browser());
            session.setOperatingSystem(metadata.operatingSystem());
        }

        return sessionRepository.save(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> listUserSessions(UUID userId, String currentSessionIdentifier) {
        List<UserSession> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return sessions.stream()
                .sorted(Comparator
                        .comparing((UserSession s) -> Objects.equals(s.getSessionIdentifier(), currentSessionIdentifier)).reversed()
                        .thenComparing(UserSession::isActive).reversed()
                        .thenComparing(UserSession::getLastUsedAt).reversed()
                )
                .map(s -> SessionResponse.fromEntity(
                        s,
                        currentSessionIdentifier != null && currentSessionIdentifier.equals(s.getSessionIdentifier())
                ))
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(UUID userId, String sessionIdentifier) {
        if (sessionIdentifier == null || sessionIdentifier.isBlank()) {
            throw ApiException.badRequest("Session identifier is required");
        }

        UserSession session = sessionRepository.findBySessionIdentifier(sessionIdentifier.trim())
                .orElseThrow(() -> ApiException.notFound("Session could not be found"));

        // Strict User Authorization Boundary & IDOR Prevention
        if (!session.getUserId().equals(userId)) {
            log.warn("IDOR attempt: User [{}] attempted to revoke session [{}] owned by user [{}]",
                    userId, sessionIdentifier, session.getUserId());
            throw ApiException.notFound("Session could not be found");
        }

        if (session.isRevoked()) {
            log.debug("Session [{}] is already revoked; idempotent completion", sessionIdentifier);
            return;
        }

        session.revoke("USER_REVOKED");
        sessionRepository.save(session);
        // Invalidate all associated refresh tokens
        refreshTokenRepository.revokeAllBySessionId(session.getId());

        log.info("User [{}] revoked session [{}]", userId, sessionIdentifier);

        auditService.recordAudit(
                null,
                null,
                userId,
                "USER",
                AuditAction.SESSION_REVOKED,
                "USER_SESSION",
                session.getId(),
                null,
                null,
                "SUCCESS"
        );
    }

    @Override
    @Transactional
    public void revokeAllOtherSessions(UUID userId, String currentSessionIdentifier) {
        if (currentSessionIdentifier == null || currentSessionIdentifier.isBlank()) {
            throw ApiException.badRequest("Current session identifier required to preserve active session");
        }

        UserSession currentSession = sessionRepository.findBySessionIdentifierAndUserId(currentSessionIdentifier.trim(), userId)
                .orElseThrow(() -> ApiException.unauthorized("Current active session could not be verified"));

        List<UserSession> otherSessions = sessionRepository.findOtherActiveSessions(userId, currentSession.getSessionIdentifier());

        Instant now = Instant.now();
        for (UserSession s : otherSessions) {
            s.setRevokedAt(now);
            s.setRevocationReason("REVOKE_OTHERS");
            sessionRepository.save(s);
        }

        refreshTokenRepository.revokeOtherSessionsByUserId(userId, currentSession.getId());

        log.info("User [{}] revoked [{}] other active sessions preserving session [{}]",
                userId, otherSessions.size(), currentSessionIdentifier);

        auditService.recordAudit(
                null,
                null,
                userId,
                "USER",
                AuditAction.SESSION_REVOKED_ALL_OTHERS,
                "USER_SESSION",
                currentSession.getId(),
                null,
                null,
                "SUCCESS"
        );
    }

    @Override
    @Transactional
    public void revokeAllSessions(UUID userId) {
        Instant now = Instant.now();
        int revokedCount = sessionRepository.revokeAllByUserId(userId, now, "REVOKE_ALL");
        refreshTokenRepository.revokeAllByUserId(userId);

        log.info("Revoked all [{}] sessions and all refresh tokens for user [{}]", revokedCount, userId);

        auditService.recordAudit(
                null,
                null,
                userId,
                "USER",
                AuditAction.SESSION_REVOKED_ALL,
                "USER_SESSION",
                userId,
                null,
                null,
                "SUCCESS"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSession> findById(UUID sessionId) {
        return sessionRepository.findById(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSession> findByIdentifier(String sessionIdentifier) {
        return sessionRepository.findBySessionIdentifier(sessionIdentifier);
    }

    private String generateSessionIdentifier() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return "sess_" + HexFormat.of().formatHex(bytes);
    }
}

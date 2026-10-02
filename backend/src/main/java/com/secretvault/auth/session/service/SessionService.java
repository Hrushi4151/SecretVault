package com.secretvault.auth.session.service;

import com.secretvault.auth.session.dto.SessionResponse;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract governing authenticated user sessions, device tracking,
 * session lifecycle, and session-level cryptographic invalidation.
 */
public interface SessionService {

    /**
     * Creates and persists a new authenticated user session with client device metadata.
     */
    UserSession createSession(
            UUID userId,
            AuthMethod authMethod,
            String ipAddress,
            String userAgent,
            Instant expiresAt
    );

    /**
     * Updates the last-used timestamp and active metadata for a session upon token refresh.
     */
    UserSession recordSessionActivity(UUID sessionId, String ipAddress, String userAgent);

    /**
     * Retrieves all active and historical sessions for a user, marking the current session.
     */
    List<SessionResponse> listUserSessions(UUID userId, String currentSessionIdentifier);

    /**
     * Revokes a specific session belonging to the user and invalidates all associated refresh tokens.
     */
    void revokeSession(UUID userId, String sessionIdentifier);

    /**
     * Revokes all active sessions for the user except the caller's current session.
     */
    void revokeAllOtherSessions(UUID userId, String currentSessionIdentifier);

    /**
     * Revokes all sessions and refresh tokens for the user account.
     */
    void revokeAllSessions(UUID userId);

    /**
     * Finds a session by its database UUID.
     */
    Optional<UserSession> findById(UUID sessionId);

    /**
     * Finds a session by its public opaque identifier.
     */
    Optional<UserSession> findByIdentifier(String sessionIdentifier);
}

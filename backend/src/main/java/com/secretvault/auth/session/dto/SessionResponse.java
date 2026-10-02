package com.secretvault.auth.session.dto;

import com.secretvault.auth.session.entity.UserSession;

import java.time.Instant;

/**
 * Public Data Transfer Object representing safe authenticated session metadata.
 * Strictly avoids exposing internal IDs, hashes, secrets, or raw cryptographic material.
 */
public record SessionResponse(
        String id,
        boolean current,
        String authMethod,
        String deviceName,
        String browser,
        String operatingSystem,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean revoked
) {
    public static SessionResponse fromEntity(UserSession session, boolean isCurrent) {
        return new SessionResponse(
                session.getSessionIdentifier(),
                isCurrent,
                session.getAuthMethod() != null ? session.getAuthMethod().name() : "PASSWORD",
                session.getDeviceName() != null ? session.getDeviceName() : "Unknown Device",
                session.getBrowser() != null ? session.getBrowser() : "Unknown Browser",
                session.getOperatingSystem() != null ? session.getOperatingSystem() : "Unknown OS",
                session.getIpAddress() != null ? session.getIpAddress() : "N/A",
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt(),
                session.isRevoked()
        );
    }
}

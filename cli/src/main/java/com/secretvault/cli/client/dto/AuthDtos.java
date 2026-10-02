package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(
            String email,
            String password
    ) {}

    public record RefreshTokenRequest(
            String refreshToken
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            boolean isMfaEnabled,
            String status,
            Instant createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserResponse user,
            WorkspaceDto activeWorkspace,
            boolean mfaRequired,
            String mfaChallengeId,
            Instant mfaExpiresAt
    ) {}
}

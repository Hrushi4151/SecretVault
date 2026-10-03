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

    public record OidcTokenExchangeRequest(
            UUID providerId,
            String issuer,
            String token
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MachineIdentityDto(
            UUID id,
            UUID workspaceId,
            String name,
            String description,
            String type,
            String status,
            Instant expiresAt,
            Instant lastAuthenticatedAt,
            Instant lastUsedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OidcTokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            MachineIdentityDto machineIdentity
    ) {}

    public record MfaTotpVerifyRequest(
            String challengeId,
            String code
    ) {}

    public record MfaRecoveryVerifyRequest(
            String challengeId,
            String recoveryCode
    ) {}
}

package com.secretvault.auth.dto;

import com.secretvault.workspace.dto.WorkspaceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Successful authentication payload containing tokens, user profile, and active workspace context,
 * or an MFA challenge notification when verification is required to complete authentication.
 */
@Schema(description = "Authentication tokens and session payload")
public record AuthResponse(
        @Schema(description = "JWT Access Token for Authorization header")
        String accessToken,

        @Schema(description = "Refresh token for session renewal")
        String refreshToken,

        @Schema(description = "Token type identifier", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifespan in seconds", example = "86400")
        long expiresIn,

        @Schema(description = "Authenticated user profile")
        UserResponse user,

        @Schema(description = "Active primary workspace context")
        WorkspaceResponse activeWorkspace,

        @Schema(description = "Whether multi-factor authentication challenge is required to complete login")
        boolean mfaRequired,

        @Schema(description = "MFA Challenge ID required when mfaRequired is true", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String mfaChallengeId,

        @Schema(description = "Expiry timestamp of the MFA challenge")
        Instant mfaExpiresAt
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user, WorkspaceResponse activeWorkspace) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                user,
                activeWorkspace,
                false,
                null,
                null
        );
    }

    public static AuthResponse mfaRequired(String mfaChallengeId, Instant mfaExpiresAt) {
        return new AuthResponse(
                null,
                null,
                null,
                0,
                null,
                null,
                true,
                mfaChallengeId,
                mfaExpiresAt
        );
    }
}

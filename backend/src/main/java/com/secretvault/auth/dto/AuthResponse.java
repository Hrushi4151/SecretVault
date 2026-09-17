package com.secretvault.auth.dto;

import com.secretvault.workspace.dto.WorkspaceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Successful authentication payload containing tokens, user profile, and active workspace context.
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
        WorkspaceResponse activeWorkspace
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user, WorkspaceResponse activeWorkspace) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                user,
                activeWorkspace
        );
    }
}

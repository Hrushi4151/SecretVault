package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for refreshing an expired access token.
 */
@Schema(description = "Refresh token rotation payload")
public record RefreshTokenRequest(
        @Schema(description = "Opaque refresh token string", example = "4fa815660749410a8dc4b77873ad9be0")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}

package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for user authentication.
 */
@Schema(description = "User login credentials payload")
public record LoginRequest(
        @Schema(description = "Registered email address", example = "alice@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "Account password", example = "SecretVault2026!Secure")
        @NotBlank(message = "Password is required")
        String password
) {
}

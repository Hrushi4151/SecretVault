package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for new user registration and organization provisioning.
 */
@Schema(description = "User registration payload")
public record RegisterRequest(
        @Schema(description = "Valid email address", example = "alice@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "Account password (minimum 8 characters)", example = "SecretVault2026!Secure")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @Schema(description = "Full name of the user", example = "Alice Vance")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
        String fullName,

        @Schema(description = "Initial organization name (optional; defaults to personal org if omitted)", example = "Acme Corp")
        @Size(max = 255, message = "Organization name cannot exceed 255 characters")
        String organizationName
) {
}

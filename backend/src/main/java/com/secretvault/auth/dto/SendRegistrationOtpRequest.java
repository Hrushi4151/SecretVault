package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to initiate registration OTP dispatch")
public record SendRegistrationOtpRequest(
        @Schema(description = "Gmail address", example = "alice@gmail.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "Full name of the user", example = "Alice Vance")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
        String fullName,

        @Schema(description = "Account password (minimum 8 characters)", example = "SecretVault2026!Secure")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @Schema(description = "Initial organization name (optional)", example = "Acme Corp")
        @Size(max = 255, message = "Organization name cannot exceed 255 characters")
        String organizationName
) {}

package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to set new password after full verification")
public record ResetPasswordRequest(
        @Schema(description = "Verified reset token", example = "a1b2c3d4-e5f6-7890-1234-56789abcdef0")
        @NotBlank(message = "Reset token is required")
        String resetToken,

        @Schema(description = "New master password (minimum 8 characters)", example = "NewSecretPassword2026!Secure")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String newPassword
) {}

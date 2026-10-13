package com.secretvault.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to verify second-factor MFA or recovery code during password reset")
public record VerifyPasswordResetMfaRequest(
        @Schema(description = "Reset token from previous OTP verification step", example = "a1b2c3d4-e5f6-7890-1234-56789abcdef0")
        @NotBlank(message = "Reset token is required")
        String resetToken,

        @Schema(description = "6-digit TOTP code from authenticator app", example = "123456")
        String code,

        @Schema(description = "Emergency backup recovery code (if TOTP is unavailable)", example = "RC-1A2B-3C4D")
        String recoveryCode
) {}

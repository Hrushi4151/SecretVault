package com.secretvault.auth.mfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for disabling MFA protection.
 * Requires user's current password and a valid second factor (TOTP code or backup recovery code)
 * for step-up authentication to prevent unauthorized MFA removal.
 */
@Schema(description = "MFA disable request payload with step-up verification")
public record MfaDisableRequest(
        @NotBlank(message = "Current password is required to disable MFA")
        @Schema(description = "User's current password for identity verification", example = "SecurePassword123!")
        String password,

        @Schema(description = "Current 6 to 8 digit TOTP code from authenticator app", example = "123456")
        String code,

        @Schema(description = "Single-use 12-character backup recovery code (alternative to TOTP code)", example = "2345-6789-ABCD")
        String recoveryCode
) {
    @Override
    public String toString() {
        return "MfaDisableRequest[password=[REDACTED], code=[REDACTED], recoveryCode=[REDACTED]]";
    }
}

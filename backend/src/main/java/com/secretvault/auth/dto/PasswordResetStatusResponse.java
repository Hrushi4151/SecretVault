package com.secretvault.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload describing password reset stage and requirements")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordResetStatusResponse(
        @Schema(description = "Operation success indicator")
        boolean success,

        @Schema(description = "Whether the account has MFA enabled and requires second-factor verification")
        boolean mfaRequired,

        @Schema(description = "Reset session token for subsequent steps")
        String resetToken,

        @Schema(description = "Human-readable status or guidance message")
        String message
) {
    public static PasswordResetStatusResponse otpVerified(String resetToken, boolean mfaRequired) {
        String msg = mfaRequired
                ? "OTP verified successfully. Multi-Factor Authentication (MFA) step-up verification is required for this account."
                : "OTP verified successfully. You may now set your new password.";
        return new PasswordResetStatusResponse(true, mfaRequired, resetToken, msg);
    }

    public static PasswordResetStatusResponse mfaVerified(String resetToken) {
        return new PasswordResetStatusResponse(true, true, resetToken, "MFA second-factor verified successfully. You may now set your new password.");
    }

    public static PasswordResetStatusResponse completed() {
        return new PasswordResetStatusResponse(true, false, null, "Password reset successfully completed. All prior sessions have been revoked.");
    }
}

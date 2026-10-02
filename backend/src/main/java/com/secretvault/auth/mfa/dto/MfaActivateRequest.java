package com.secretvault.auth.mfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload to complete MFA enrollment and activate MFA protection.
 */
@Schema(description = "MFA activation request payload")
public record MfaActivateRequest(
        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "^\\d{6,8}$", message = "Verification code must be 6 to 8 digits")
        @Schema(description = "Verification code from the authenticator app", example = "123456")
        String code
) {}

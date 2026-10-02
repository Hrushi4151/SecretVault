package com.secretvault.auth.mfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for verifying a TOTP code against an active MFA login challenge.
 */
@Schema(description = "TOTP challenge verification request payload")
public record MfaTotpVerifyRequest(
        @NotBlank(message = "Challenge ID is required")
        @Schema(description = "Active MFA challenge identifier issued during password authentication", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String challengeId,

        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "^\\d{6,8}$", message = "Verification code must be 6 to 8 digits")
        @Schema(description = "6 to 8 digit numerical time-based one-time password", example = "123456")
        String code
) {}

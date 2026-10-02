package com.secretvault.auth.mfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for verifying a backup recovery code against an active MFA login challenge.
 */
@Schema(description = "Backup recovery-code challenge verification request payload")
public record MfaRecoveryVerifyRequest(
        @NotBlank(message = "Challenge ID is required")
        @Schema(description = "Active MFA challenge identifier issued during password authentication", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String challengeId,

        @NotBlank(message = "Recovery code is required")
        @Schema(description = "12-character backup recovery code (with or without dashes)", example = "2345-6789-ABCD")
        String recoveryCode
) {}

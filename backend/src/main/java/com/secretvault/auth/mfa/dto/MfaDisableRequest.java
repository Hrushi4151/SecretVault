package com.secretvault.auth.mfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Optional request payload for disabling MFA.
 */
@Schema(description = "MFA disable request payload")
public record MfaDisableRequest(
        @Schema(description = "Optional confirmation password or verification code")
        String confirmation
) {}

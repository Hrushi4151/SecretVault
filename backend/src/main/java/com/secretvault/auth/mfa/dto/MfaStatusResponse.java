package com.secretvault.auth.mfa.dto;

import com.secretvault.auth.mfa.model.MfaStatusInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Public response representing the current user's MFA enrollment status and safe metadata.
 */
@Schema(description = "User MFA configuration status response")
public record MfaStatusResponse(
        @Schema(description = "Whether MFA is actively enabled on the account")
        boolean enabled,

        @Schema(description = "MFA lifecycle state", example = "ENABLED")
        String status,

        @Schema(description = "Timestamp when MFA was initially enrolled")
        Instant enrolledAt,

        @Schema(description = "Timestamp when MFA was verified and activated")
        Instant verifiedAt,

        @Schema(description = "Timestamp when MFA was last used for authentication")
        Instant lastUsedAt,

        @Schema(description = "Number of remaining unused backup recovery codes", example = "10")
        long remainingRecoveryCodes
) {
    public static MfaStatusResponse from(MfaStatusInfo info) {
        return new MfaStatusResponse(
                info.enabled(),
                info.status() != null ? info.status().name() : "DISABLED",
                info.enrolledAt(),
                info.verifiedAt(),
                info.lastUsedAt(),
                info.remainingRecoveryCodes()
        );
    }
}

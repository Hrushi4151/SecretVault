package com.secretvault.auth.mfa.dto;

import com.secretvault.auth.mfa.model.MfaActivationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response returned upon successful MFA activation containing the one-time backup recovery codes.
 */
@Schema(description = "MFA activation success payload with recovery codes")
public record MfaActivateResponse(
        @Schema(description = "Updated MFA status", example = "ENABLED")
        String status,

        @Schema(description = "One-time list of 10 backup recovery codes. Store these safely.")
        List<String> recoveryCodes
) {
    public static MfaActivateResponse from(MfaActivationResult result) {
        return new MfaActivateResponse(
                result.status().name(),
                result.recoveryCodes()
        );
    }

    @Override
    public String toString() {
        return "MfaActivateResponse[status=" + status +
                ", recoveryCodesCount=" + (recoveryCodes != null ? recoveryCodes.size() : 0) +
                ", recoveryCodes=[REDACTED]]";
    }
}

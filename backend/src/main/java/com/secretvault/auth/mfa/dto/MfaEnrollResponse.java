package com.secretvault.auth.mfa.dto;

import com.secretvault.auth.mfa.model.MfaEnrollmentResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transient response returned when initiating MFA enrollment.
 * Contains the Base32 TOTP secret and provisioning URI for authenticator QR setup.
 */
@Schema(description = "MFA enrollment initiation payload")
public record MfaEnrollResponse(
        @Schema(description = "Base32-encoded TOTP secret key for manual entry", example = "JBSWY3DPEHPK3PXP")
        String secret,

        @Schema(description = "RFC 6238 Key URI for QR code generation in authenticator app", example = "otpauth://totp/SecretVault:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=SecretVault")
        String provisioningUri,

        @Schema(description = "Issuer name", example = "SecretVault")
        String issuer,

        @Schema(description = "Account identifier / email", example = "user@example.com")
        String accountName
) {
    public static MfaEnrollResponse from(MfaEnrollmentResponse response) {
        return new MfaEnrollResponse(
                response.secret(),
                response.provisioningUri(),
                response.issuer(),
                response.accountName()
        );
    }

    @Override
    public String toString() {
        return "MfaEnrollResponse[issuer=" + issuer + ", accountName=" + accountName + ", secret=[REDACTED], provisioningUri=[REDACTED]]";
    }
}

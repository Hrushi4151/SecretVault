package com.secretvault.auth.webauthn.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for verifying a WebAuthn assertion during step-up re-authentication.
 */
public record WebAuthnStepUpVerifyRequest(
        @NotBlank(message = "credentialJson is required")
        String credentialJson
) {
}

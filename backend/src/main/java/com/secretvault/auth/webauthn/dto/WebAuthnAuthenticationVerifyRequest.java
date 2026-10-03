package com.secretvault.auth.webauthn.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO containing client assertion response JSON for WebAuthn authentication/assertion.
 */
public record WebAuthnAuthenticationVerifyRequest(
        @NotBlank(message = "challengeId is required")
        String challengeId,

        @NotBlank(message = "credentialJson is required")
        String credentialJson
) {
}

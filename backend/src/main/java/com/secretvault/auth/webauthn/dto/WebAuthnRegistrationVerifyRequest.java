package com.secretvault.auth.webauthn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO containing client credential registration response JSON and friendly credential name.
 */
public record WebAuthnRegistrationVerifyRequest(
        @NotBlank(message = "challengeId is required")
        String challengeId,

        @NotBlank(message = "friendlyName is required")
        @Size(max = 100, message = "friendlyName must not exceed 100 characters")
        String friendlyName,

        @NotBlank(message = "credentialJson is required")
        String credentialJson
) {
}

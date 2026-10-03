package com.secretvault.auth.webauthn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating a WebAuthn credential friendly name.
 */
public record WebAuthnCredentialUpdateRequest(
        @NotBlank(message = "friendlyName is required")
        @Size(max = 100, message = "friendlyName must not exceed 100 characters")
        String friendlyName
) {
}

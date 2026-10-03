package com.secretvault.auth.webauthn.dto;

/**
 * Request DTO for generating WebAuthn assertion options.
 * Optional email filters credentials to the specified user; if omitted, supports discoverable passkeys.
 */
public record WebAuthnAuthenticationOptionsRequest(
        String email
) {
}

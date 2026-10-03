package com.secretvault.auth.webauthn.dto;

import java.time.Instant;

/**
 * Response DTO containing PublicKeyCredentialRequestOptions JSON for WebAuthn authentication/assertion.
 */
public record WebAuthnAuthenticationOptionsResponse(
        String challengeId,
        String optionsJson,
        Instant expiresAt
) {
}

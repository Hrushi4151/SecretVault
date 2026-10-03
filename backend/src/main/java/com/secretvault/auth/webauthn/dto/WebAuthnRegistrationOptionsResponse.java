package com.secretvault.auth.webauthn.dto;

import java.time.Instant;

/**
 * Response DTO containing PublicKeyCredentialCreationOptions JSON for WebAuthn registration.
 */
public record WebAuthnRegistrationOptionsResponse(
        String challengeId,
        String optionsJson,
        Instant expiresAt
) {
}

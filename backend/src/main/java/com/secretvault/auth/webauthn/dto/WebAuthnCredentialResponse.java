package com.secretvault.auth.webauthn.dto;

import com.secretvault.auth.webauthn.entity.UserWebAuthnCredential;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO exposing safe public metadata for a user's registered WebAuthn credential.
 * NEVER exposes private keys or unnecessary cryptographic blobs.
 */
public record WebAuthnCredentialResponse(
        UUID id,
        String friendlyName,
        String credentialId,
        String aaguid,
        String transports,
        Boolean userVerifiedCapable,
        Boolean backupEligible,
        Boolean backupState,
        Boolean discoverable,
        Instant createdAt,
        Instant lastUsedAt,
        String lastUsedIp,
        boolean active
) {
    public static WebAuthnCredentialResponse fromEntity(UserWebAuthnCredential credential) {
        return new WebAuthnCredentialResponse(
                credential.getId(),
                credential.getFriendlyName(),
                credential.getCredentialId(),
                credential.getAaguid(),
                credential.getTransports(),
                credential.getUserVerifiedCapable(),
                credential.getBackupEligible(),
                credential.getBackupState(),
                credential.getDiscoverable(),
                credential.getCreatedAt(),
                credential.getLastUsedAt(),
                credential.getLastUsedIp(),
                credential.isActive()
        );
    }
}

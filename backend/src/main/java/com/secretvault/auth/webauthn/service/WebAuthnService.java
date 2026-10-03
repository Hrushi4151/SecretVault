package com.secretvault.auth.webauthn.service;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.stepup.dto.StepUpProofResponse;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationOptionsResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnCredentialResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnRegistrationOptionsResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service managing WebAuthn / FIDO2 Passkey registration, authentication, step-up integration,
 * credential management, and clone detection.
 */
public interface WebAuthnService {

    /**
     * Initiates WebAuthn credential registration ceremony for an authenticated user.
     */
    WebAuthnRegistrationOptionsResponse startRegistration(UUID userId, String sessionIdentifier, String friendlyName);

    /**
     * Verifies the client registration ceremony response, stores the public credential, and logs audit events.
     */
    WebAuthnCredentialResponse finishRegistration(UUID userId, String sessionIdentifier, String challengeId, String friendlyName, String credentialJson);

    /**
     * Initiates WebAuthn authentication ceremony (login or discoverable passkey).
     */
    WebAuthnAuthenticationOptionsResponse startAuthentication(String email);

    /**
     * Verifies the client assertion response for login authentication, establishes session, and issues tokens.
     */
    AuthResponse finishAuthentication(String challengeId, String credentialJson);

    /**
     * Initiates WebAuthn assertion ceremony for step-up re-authentication.
     */
    WebAuthnAuthenticationOptionsResponse startStepUpAssertion(UUID userId, String sessionIdentifier, String stepUpChallengeId);

    /**
     * Verifies the client assertion response for step-up re-authentication, issuing a single-use step-up proof.
     */
    StepUpProofResponse finishStepUpAssertion(UUID userId, String sessionIdentifier, String stepUpChallengeId, String credentialJson);

    /**
     * Lists active registered WebAuthn credentials for a user (safe metadata only).
     */
    List<WebAuthnCredentialResponse> listCredentials(UUID userId);

    /**
     * Renames a registered WebAuthn credential.
     */
    WebAuthnCredentialResponse renameCredential(UUID userId, UUID credentialId, String newFriendlyName);

    /**
     * Revokes a registered WebAuthn credential with lockout prevention checks.
     */
    void revokeCredential(UUID userId, UUID credentialId);

    /**
     * Checks if a user has at least one active WebAuthn credential registered.
     */
    boolean hasWebAuthnCredentials(UUID userId);
}

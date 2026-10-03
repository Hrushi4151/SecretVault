package com.secretvault.auth.stepup.service;

import com.secretvault.auth.stepup.dto.StepUpChallengeResponse;
import com.secretvault.auth.stepup.dto.StepUpProofResponse;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;

import java.util.UUID;

/**
 * Service contract for Generalized Step-Up Authentication.
 * Manages challenges, factor verification (Password, TOTP, Recovery Code),
 * and single-use proof generation / atomic consumption.
 */
public interface StepUpAuthenticationService {

    /**
     * Creates a short-lived step-up challenge for an authenticated user and active session.
     * Validates that the user has base authorization for the requested action before issuing challenge.
     */
    StepUpChallengeResponse createChallenge(UUID userId, String sessionIdentifier, StepUpAction action, StepUpContext context);

    /**
     * Verifies the user's current password against the step-up challenge.
     * On success, consumes the challenge and issues a short-lived, context-bound proof token.
     */
    StepUpProofResponse verifyPassword(String challengeId, UUID userId, String sessionIdentifier, String password);

    /**
     * Verifies a TOTP authenticator code against the step-up challenge.
     * On success, consumes the challenge and issues a short-lived, context-bound proof token.
     */
    StepUpProofResponse verifyTotp(String challengeId, UUID userId, String sessionIdentifier, String code);

    /**
     * Verifies and consumes a backup recovery code against the step-up challenge.
     * On success, consumes the challenge and issues a short-lived, context-bound proof token.
     */
    StepUpProofResponse verifyRecoveryCode(String challengeId, UUID userId, String sessionIdentifier, String recoveryCode);

    /**
     * Generates WebAuthn assertion options for an active step-up challenge.
     */
    com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationOptionsResponse createWebAuthnStepUpOptions(String challengeId, UUID userId, String sessionIdentifier);

    /**
     * Verifies a WebAuthn assertion against the step-up challenge.
     * On success, consumes the challenge and issues a short-lived, context-bound proof token.
     */
    StepUpProofResponse verifyWebAuthn(String challengeId, UUID userId, String sessionIdentifier, String credentialJson);

    /**
     * Atomically consumes and validates a step-up proof token for a sensitive operation.
     * Enforces user binding, session binding, action binding, resource-context binding, and active session status.
     *
     * @throws com.secretvault.common.exception.ApiException if proof is missing, invalid, expired, replayed, or mismatched
     */
    void verifyAndConsumeProof(String proofToken, UUID userId, String sessionIdentifier, StepUpAction action, StepUpContext context);
}

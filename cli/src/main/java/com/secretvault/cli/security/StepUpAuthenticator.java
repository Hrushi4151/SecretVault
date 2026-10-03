package com.secretvault.cli.security;

import com.secretvault.cli.client.ApiClientException;
import com.secretvault.cli.client.ErrorCode;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.StepUpDtos;
import com.secretvault.cli.output.ConsolePrinter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Orchestrates Generalized Step-Up Authentication for sensitive CLI operations:
 * - Creates contextual Step-Up challenges
 * - Interactively prompts user for strong authentication factors (TOTP, Password, Recovery Code)
 * - Verifies factors and receives short-lived proof tokens bound to user, session, action, and resource
 * - Never persists, logs, or leaks step-up proofs or credentials
 */
public class StepUpAuthenticator {

    /**
     * Executes Step-Up ceremony for a protected action and context.
     *
     * @param client  authenticated API client
     * @param action  Step-Up action being performed
     * @param context resource binding context (e.g. workspace, project, environment, secret)
     * @param printer console output printer
     * @return short-lived single-use step-up proof token
     */
    public static String authenticateStepUp(
            SecretVaultApiClient client,
            StepUpDtos.StepUpAction action,
            StepUpDtos.StepUpContext context,
            ConsolePrinter printer
    ) {
        if (client == null) {
            throw new IllegalArgumentException("API client cannot be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("Step-Up action cannot be null");
        }

        // 1. Create challenge
        StepUpDtos.StepUpChallengeRequest req = new StepUpDtos.StepUpChallengeRequest(action, context);
        StepUpDtos.StepUpChallengeResponse challenge = client.createStepUpChallenge(req);

        if (challenge == null || challenge.challengeId() == null) {
            throw new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 401,
                    "Failed to create Step-Up authentication challenge.", null);
        }

        if (printer != null) {
            printer.warn("Additional authentication required for this action (" + action + ").");
        }

        List<StepUpDtos.StepUpFactor> factors = challenge.supportedFactors();
        if (factors == null || factors.isEmpty()) {
            factors = List.of(StepUpDtos.StepUpFactor.TOTP, StepUpDtos.StepUpFactor.PASSWORD);
        }

        // 2. Perform factor verification in order of preference: TOTP -> Password -> Recovery Code
        if (factors.contains(StepUpDtos.StepUpFactor.TOTP)) {
            char[] codeChars = readHiddenInput("Enter your 6-digit authenticator code: ");
            if (codeChars == null || codeChars.length == 0) {
                throw new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 400, "MFA code cannot be empty.", null);
            }
            try {
                String code = new String(codeChars).trim();
                StepUpDtos.StepUpProofResponse proof = client.verifyStepUpTotp(challenge.challengeId(), code);
                if (proof != null && proof.proofToken() != null) {
                    return proof.proofToken();
                }
            } finally {
                RedactionHelper.wipe(codeChars);
            }
        } else if (factors.contains(StepUpDtos.StepUpFactor.PASSWORD)) {
            char[] pwdChars = readHiddenInput("Enter your account password: ");
            if (pwdChars == null || pwdChars.length == 0) {
                throw new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 400, "Password cannot be empty.", null);
            }
            try {
                String pwd = new String(pwdChars);
                StepUpDtos.StepUpProofResponse proof = client.verifyStepUpPassword(challenge.challengeId(), pwd);
                if (proof != null && proof.proofToken() != null) {
                    return proof.proofToken();
                }
            } finally {
                RedactionHelper.wipe(pwdChars);
            }
        } else if (factors.contains(StepUpDtos.StepUpFactor.RECOVERY_CODE)) {
            char[] recChars = readHiddenInput("Enter a backup recovery code: ");
            if (recChars == null || recChars.length == 0) {
                throw new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 400, "Recovery code cannot be empty.", null);
            }
            try {
                String recCode = new String(recChars).trim();
                StepUpDtos.StepUpProofResponse proof = client.verifyStepUpRecoveryCode(challenge.challengeId(), recCode);
                if (proof != null && proof.proofToken() != null) {
                    return proof.proofToken();
                }
            } finally {
                RedactionHelper.wipe(recChars);
            }
        } else if (factors.contains(StepUpDtos.StepUpFactor.WEBAUTHN)) {
            throw new ApiClientException(ErrorCode.FORBIDDEN, 403,
                    "This action requires WebAuthn (passkey) step-up authentication, which must be performed in the Web Console.", null);
        }

        throw new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 401,
                "No supported authentication factors available for Step-Up verification.", null);
    }

    private static char[] readHiddenInput(String prompt) {
        if (System.console() != null) {
            return System.console().readPassword(prompt);
        } else {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                System.out.print(prompt);
                String line = reader.readLine();
                return line != null ? line.toCharArray() : new char[0];
            } catch (Exception e) {
                return new char[0];
            }
        }
    }
}

package com.secretvault.cli.security;

import com.secretvault.cli.client.ApiClientException;
import com.secretvault.cli.client.ErrorCode;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.client.dto.StepUpDtos;
import com.secretvault.cli.output.ConsolePrinter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Orchestrates Phase 5.8.5 Secret Reveal Protection flow for the CLI:
 * - Evaluates hierarchical secret reveal policy
 * - Prompts for audit reason when required by policy
 * - Triggers Generalized Step-Up Authentication when required
 * - Obtains short-lived single-use reveal intent token
 * - Executes protected reveal with atomic intent consumption and zeroization
 */
public class SecretRevealHelper {

    /**
     * Executes the protected reveal sequence against authoritative backend security controls.
     *
     * @param client         authenticated API client
     * @param workspaceId    workspace UUID
     * @param projectId      project UUID
     * @param environmentId  environment UUID
     * @param secretId       secret UUID
     * @param version        optional version number (null for latest active)
     * @param explicitReason optional pre-supplied audit reason
     * @param printer        console printer
     * @return decrypted secret reveal DTO
     */
    public static SecretDtos.SecretRevealDto revealProtectedSecret(
            SecretVaultApiClient client,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer version,
            String explicitReason,
            ConsolePrinter printer
    ) {
        if (client == null) {
            throw new IllegalArgumentException("API client cannot be null");
        }
        if (workspaceId == null || projectId == null || environmentId == null || secretId == null) {
            throw new IllegalArgumentException("Workspace, project, environment, and secret IDs are required");
        }

        SecretDtos.SecretRevealPolicyEvaluation policy = null;
        try {
            policy = client.getSecretRevealPolicy(workspaceId, projectId, environmentId, secretId);
        } catch (ApiClientException e) {
            // If reveal-policy is not supported (404), fall back to standard reveal
            if (e.getHttpStatus() == 404) {
                return client.revealSecret(workspaceId, projectId, environmentId, secretId, version);
            }
            throw e;
        }

        String reason = explicitReason;
        String stepUpProof = null;

        if (policy != null) {
            // 1. Check if policy strictly mandates hardware WebAuthn passkey
            if (policy.requireWebAuthnOnly()) {
                throw new ApiClientException(ErrorCode.FORBIDDEN, 403,
                        "This secret is protected by a WebAuthn-only reveal policy and must be revealed via the Web Console.", null);
            }

            // 2. Reason requirement
            if (policy.requireReason()) {
                int minLen = policy.minReasonLength() > 0 ? policy.minReasonLength() : 10;
                int maxLen = policy.maxReasonLength() > 0 ? policy.maxReasonLength() : 500;

                if (reason == null || reason.isBlank()) {
                    reason = promptForReason(minLen, printer);
                }

                if (reason == null || reason.trim().length() < minLen) {
                    throw new ApiClientException(ErrorCode.VALIDATION_ERROR, 400,
                            "Reveal reason must be at least " + minLen + " characters (provided: " + (reason == null ? 0 : reason.trim().length()) + ").", null);
                }
                if (reason.trim().length() > maxLen) {
                    throw new ApiClientException(ErrorCode.VALIDATION_ERROR, 400,
                            "Reveal reason must not exceed " + maxLen + " characters.", null);
                }
                reason = reason.trim();
            }

            // 3. Step-Up requirement
            if (policy.requireStepUp()) {
                StepUpDtos.StepUpContext ctx = StepUpDtos.StepUpContext.forSecret(workspaceId, projectId, environmentId, secretId);
                stepUpProof = StepUpAuthenticator.authenticateStepUp(client, StepUpDtos.StepUpAction.SECRET_REVEAL, ctx, printer);
            }
        } else {
            return client.revealSecret(workspaceId, projectId, environmentId, secretId, version);
        }

        // 4. Request single-use Reveal Intent
        SecretDtos.CreateRevealIntentRequest intentReq = new SecretDtos.CreateRevealIntentRequest(version, reason, stepUpProof);
        SecretDtos.SecretRevealIntentResponse intentResp = client.createSecretRevealIntent(workspaceId, projectId, environmentId, secretId, intentReq);

        String intentToken = null;
        if (intentResp != null) {
            intentToken = intentResp.intentToken();
            if (intentResp.requireStepUp() && (stepUpProof == null || stepUpProof.isBlank())) {
                StepUpDtos.StepUpContext ctx = StepUpDtos.StepUpContext.forSecret(workspaceId, projectId, environmentId, secretId);
                stepUpProof = StepUpAuthenticator.authenticateStepUp(client, StepUpDtos.StepUpAction.SECRET_REVEAL, ctx, printer);
            }
        }

        // 5. Execute Protected Reveal
        return client.revealSecret(workspaceId, projectId, environmentId, secretId, version, intentToken, stepUpProof, reason);
    }

    private static String promptForReason(int minLength, ConsolePrinter printer) {
        String prompt = "Why do you need to reveal this secret? (min " + minLength + " chars): ";
        if (System.console() != null) {
            return System.console().readLine(prompt);
        } else {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                System.out.print(prompt);
                return reader.readLine();
            } catch (Exception e) {
                return null;
            }
        }
    }
}

package com.secretvault.secret.reveal.service;

import com.secretvault.secret.reveal.dto.SecretRevealPolicyResponse;
import com.secretvault.secret.reveal.dto.UpdateSecretRevealPolicyRequest;
import com.secretvault.secret.reveal.model.SecretRevealPolicyEvaluation;

import java.util.List;
import java.util.UUID;

public interface SecretRevealPolicyService {

    /**
     * Resolves and evaluates the effective reveal policy for a target secret/environment context.
     * Evaluates custom database policies in hierarchical order: Secret -> Environment -> Project -> Workspace.
     * Falls back to environment classification defaults (e.g. PRODUCTION => PRODUCTION_CRITICAL, STAGING => SENSITIVE, etc.).
     */
    SecretRevealPolicyEvaluation evaluatePolicy(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, UUID userId);

    /**
     * Validates a business justification reason against policy rules.
     * Throws ApiException if reason is missing, too short, too long, contains repetitive spam, or insecure patterns.
     */
    void validateReason(String reason, SecretRevealPolicyEvaluation policy);

    /**
     * Lists custom reveal policies configured for a workspace.
     */
    List<SecretRevealPolicyResponse> getWorkspacePolicies(UUID workspaceId, UUID actorUserId);

    /**
     * Creates or updates a custom reveal policy override.
     */
    SecretRevealPolicyResponse setPolicy(UUID workspaceId, UpdateSecretRevealPolicyRequest request, UUID actorUserId);

    /**
     * Deletes a custom reveal policy override.
     */
    void deletePolicy(UUID workspaceId, UUID policyId, UUID actorUserId);
}

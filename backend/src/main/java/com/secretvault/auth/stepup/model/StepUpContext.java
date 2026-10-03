package com.secretvault.auth.stepup.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side contextual binding for step-up challenges and proofs.
 * Contains only safe resource identifiers. Never contains sensitive secret values or tokens.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepUpContext(
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        UUID secretId,
        UUID targetUserId,
        Map<String, String> attributes
) {

    public StepUpContext {
        attributes = attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
    }

    public static StepUpContext forSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId) {
        return new StepUpContext(workspaceId, projectId, environmentId, secretId, null, Collections.emptyMap());
    }

    public static StepUpContext forEnvironment(UUID workspaceId, UUID projectId, UUID environmentId) {
        return new StepUpContext(workspaceId, projectId, environmentId, null, null, Collections.emptyMap());
    }

    public static StepUpContext forWorkspace(UUID workspaceId) {
        return new StepUpContext(workspaceId, null, null, null, null, Collections.emptyMap());
    }

    public static StepUpContext empty() {
        return new StepUpContext(null, null, null, null, null, Collections.emptyMap());
    }

    /**
     * Strictly verifies that the actual execution context matches the bound context in the step-up proof.
     * Prevents cross-resource, cross-environment, or cross-project privilege escalation.
     */
    public boolean matches(StepUpContext other) {
        if (other == null) return false;
        if (!Objects.equals(this.workspaceId, other.workspaceId)) return false;
        if (!Objects.equals(this.projectId, other.projectId)) return false;
        if (!Objects.equals(this.environmentId, other.environmentId)) return false;
        if (!Objects.equals(this.secretId, other.secretId)) return false;
        if (!Objects.equals(this.targetUserId, other.targetUserId)) return false;
        return Objects.equals(this.attributes, other.attributes);
    }
}

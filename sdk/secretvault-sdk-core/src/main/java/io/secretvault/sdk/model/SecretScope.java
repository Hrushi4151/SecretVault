package io.secretvault.sdk.model;

import java.util.Objects;

/**
 * Defines the hierarchical resource selector (Workspace, Project, Environment) for secret operations.
 */
public record SecretScope(
        String workspace,
        String project,
        String environment
) {
    public SecretScope {
        Objects.requireNonNull(workspace, "Workspace identifier cannot be null");
        Objects.requireNonNull(project, "Project identifier cannot be null");
        Objects.requireNonNull(environment, "Environment identifier cannot be null");
    }

    public static SecretScope of(String workspace, String project, String environment) {
        return new SecretScope(workspace, project, environment);
    }
}

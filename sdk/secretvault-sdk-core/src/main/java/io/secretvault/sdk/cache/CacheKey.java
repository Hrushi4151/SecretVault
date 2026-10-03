package io.secretvault.sdk.cache;

import java.util.Objects;

/**
 * Multi-tenant safe cache key ensuring full isolation across workspaces, projects, environments, and versions.
 */
public record CacheKey(
        String workspace,
        String project,
        String environment,
        String secretName,
        Integer version
) {
    public CacheKey {
        Objects.requireNonNull(workspace, "Workspace cannot be null");
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(environment, "Environment cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");
    }

    public static CacheKey of(String workspace, String project, String environment, String secretName, Integer version) {
        return new CacheKey(workspace, project, environment, secretName, version);
    }

    public static CacheKey ofLatest(String workspace, String project, String environment, String secretName) {
        return new CacheKey(workspace, project, environment, secretName, null);
    }

    public String toKeyString() {
        return workspace + "::" + project + "::" + environment + "::" + secretName +
                (version != null ? "::v" + version : "::latest");
    }
}

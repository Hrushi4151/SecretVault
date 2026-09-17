package com.secretvault.workspace.entity;

/**
 * Role assigned to a user within a specific Workspace.
 * Defines RBAC permissions for workspaces, projects, environments, and secrets.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    DEVELOPER,
    VIEWER;

    public boolean canManageWorkspace() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canCreateProjects() {
        return this == OWNER || this == ADMIN || this == DEVELOPER;
    }

    public boolean canManageProjects() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageEnvironments() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canWriteSecrets() {
        return this == OWNER || this == ADMIN || this == DEVELOPER;
    }

    public boolean canReadSecrets() {
        return true;
    }
}

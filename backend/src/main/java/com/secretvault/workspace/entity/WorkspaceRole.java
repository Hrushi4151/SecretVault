package com.secretvault.workspace.entity;

/**
 * Role assigned to a user within a specific Workspace.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    DEVELOPER,
    VIEWER;

    public boolean canManageWorkspace() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canWriteSecrets() {
        return this == OWNER || this == ADMIN || this == DEVELOPER;
    }

    public boolean canReadSecrets() {
        return true; // All roles can read, subject to environment restrictions
    }
}

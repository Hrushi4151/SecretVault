package com.secretvault.workspace.context;

import java.util.UUID;

/**
 * ThreadLocal container providing secure, multi-tenant workspace context isolation across request threads.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_WORKSPACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_ORGANIZATION_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setWorkspaceId(UUID workspaceId) {
        CURRENT_WORKSPACE_ID.set(workspaceId);
    }

    public static UUID getWorkspaceId() {
        return CURRENT_WORKSPACE_ID.get();
    }

    public static void setOrganizationId(UUID organizationId) {
        CURRENT_ORGANIZATION_ID.set(organizationId);
    }

    public static UUID getOrganizationId() {
        return CURRENT_ORGANIZATION_ID.get();
    }

    public static void clear() {
        CURRENT_WORKSPACE_ID.remove();
        CURRENT_ORGANIZATION_ID.remove();
    }
}

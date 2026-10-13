package com.secretvault.ai.tool;

import java.util.Set;
import java.util.UUID;

public record AiToolInvocationContext(
        UUID workspaceId,
        UUID userId,
        String userEmail,
        String userRole,
        Set<String> effectivePermissions,
        UUID conversationId,
        String correlationId
) {
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if ("OWNER".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole)) {
            return true;
        }
        return effectivePermissions != null && effectivePermissions.contains(permission);
    }
}

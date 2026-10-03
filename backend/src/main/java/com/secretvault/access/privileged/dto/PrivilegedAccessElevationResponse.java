package com.secretvault.access.privileged.dto;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.privileged.entity.PrivilegedAccessElevation;
import com.secretvault.access.privileged.model.ElevationStatus;
import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;

import java.time.Instant;
import java.util.UUID;

public record PrivilegedAccessElevationResponse(
        UUID id,
        UUID workspaceId,
        UUID requestId,
        UUID userId,
        String userEmail,
        String userName,
        PrivilegedAction action,
        PrivilegedPolicyScope scopeType,
        UUID projectId,
        String projectName,
        UUID environmentId,
        String environmentName,
        UUID secretId,
        String secretName,
        AccessPermission grantedPermission,
        boolean isBreakGlass,
        ElevationStatus status,
        Instant startsAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID revokedBy,
        String revocationReason,
        Instant createdAt,
        boolean active
) {
    public static PrivilegedAccessElevationResponse of(
            PrivilegedAccessElevation elevation,
            String userEmail,
            String userName,
            String projectName,
            String environmentName,
            String secretName,
            Instant now
    ) {
        return new PrivilegedAccessElevationResponse(
                elevation.getId(),
                elevation.getWorkspaceId(),
                elevation.getRequestId(),
                elevation.getUserId(),
                userEmail,
                userName,
                elevation.getAction(),
                elevation.getScopeType(),
                elevation.getProjectId(),
                projectName,
                elevation.getEnvironmentId(),
                environmentName,
                elevation.getSecretId(),
                secretName,
                elevation.getGrantedPermission(),
                elevation.isBreakGlass(),
                elevation.getStatus(),
                elevation.getStartsAt(),
                elevation.getExpiresAt(),
                elevation.getRevokedAt(),
                elevation.getRevokedBy(),
                elevation.getRevocationReason(),
                elevation.getCreatedAt(),
                elevation.isActive(now)
        );
    }
}

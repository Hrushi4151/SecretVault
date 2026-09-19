package com.secretvault.access.grant.dto;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;

import java.time.Instant;
import java.util.UUID;

public record AccessGrantResponse(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String userEmail,
        String userFullName,
        AccessScope scopeType,
        UUID projectId,
        String projectName,
        UUID environmentId,
        String environmentName,
        UUID secretId,
        String secretKey,
        AccessPermission permission,
        String permissionCode,
        UUID grantedBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccessGrantResponse fromEntity(
            AccessGrant grant,
            String userEmail,
            String userFullName,
            String projectName,
            String environmentName,
            String secretKey
    ) {
        return new AccessGrantResponse(
                grant.getId(),
                grant.getWorkspaceId(),
                grant.getUserId(),
                userEmail,
                userFullName,
                grant.getScopeType(),
                grant.getProjectId(),
                projectName,
                grant.getEnvironmentId(),
                environmentName,
                grant.getSecretId(),
                secretKey,
                grant.getPermission(),
                grant.getPermission().getCode(),
                grant.getGrantedBy(),
                grant.getCreatedAt(),
                grant.getUpdatedAt()
        );
    }
}

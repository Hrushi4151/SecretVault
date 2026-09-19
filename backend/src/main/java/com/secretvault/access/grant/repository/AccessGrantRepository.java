package com.secretvault.access.grant.repository;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    List<AccessGrant> findByWorkspaceId(UUID workspaceId);

    List<AccessGrant> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<AccessGrant> findByEnvironmentIdAndUserId(UUID environmentId, UUID userId);

    List<AccessGrant> findBySecretIdAndUserId(UUID secretId, UUID userId);

    Optional<AccessGrant> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndUserIdAndScopeTypeAndProjectIdAndEnvironmentIdAndSecretIdAndPermission(
            UUID workspaceId,
            UUID userId,
            AccessScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission
    );

    List<AccessGrant> findByWorkspaceIdAndUserIdAndPermission(UUID workspaceId, UUID userId, AccessPermission permission);
}

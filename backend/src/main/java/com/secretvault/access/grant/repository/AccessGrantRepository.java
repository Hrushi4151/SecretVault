package com.secretvault.access.grant.repository;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID>, JpaSpecificationExecutor<AccessGrant> {

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

    @Query("SELECT g FROM AccessGrant g WHERE g.workspaceId = :workspaceId " +
           "AND (:userId IS NULL OR g.userId = :userId) " +
           "AND (:scopeType IS NULL OR g.scopeType = :scopeType) " +
           "AND (:permission IS NULL OR g.permission = :permission) " +
           "AND (:projectId IS NULL OR g.projectId = :projectId) " +
           "AND (:environmentId IS NULL OR g.environmentId = :environmentId) " +
           "AND (:secretId IS NULL OR g.secretId = :secretId)")
    Page<AccessGrant> findFilteredGrants(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("scopeType") AccessScope scopeType,
            @Param("permission") AccessPermission permission,
            @Param("projectId") UUID projectId,
            @Param("environmentId") UUID environmentId,
            @Param("secretId") UUID secretId,
            Pageable pageable
    );
}

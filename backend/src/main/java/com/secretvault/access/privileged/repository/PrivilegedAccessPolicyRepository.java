package com.secretvault.access.privileged.repository;

import com.secretvault.access.privileged.entity.PrivilegedAccessPolicy;
import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivilegedAccessPolicyRepository extends JpaRepository<PrivilegedAccessPolicy, UUID> {

    List<PrivilegedAccessPolicy> findByWorkspaceId(UUID workspaceId);

    List<PrivilegedAccessPolicy> findByWorkspaceIdAndEnabledTrue(UUID workspaceId);

    Optional<PrivilegedAccessPolicy> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("SELECT p FROM PrivilegedAccessPolicy p WHERE p.workspaceId = :workspaceId AND p.enabled = true AND (p.action = :action OR p.action IS NULL)")
    List<PrivilegedAccessPolicy> findApplicablePolicies(
            @Param("workspaceId") UUID workspaceId,
            @Param("action") PrivilegedAction action
    );

    Optional<PrivilegedAccessPolicy> findByWorkspaceIdAndScopeTypeAndProjectIdAndEnvironmentIdAndSecretIdAndAction(
            UUID workspaceId,
            PrivilegedPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            PrivilegedAction action
    );
}

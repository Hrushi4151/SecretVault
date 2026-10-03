package com.secretvault.secret.reveal.repository;

import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.secret.reveal.entity.SecretRevealPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretRevealPolicyRepository extends JpaRepository<SecretRevealPolicy, UUID> {

    List<SecretRevealPolicy> findByWorkspaceId(UUID workspaceId);

    Optional<SecretRevealPolicy> findByWorkspaceIdAndScopeType(UUID workspaceId, PrivilegedPolicyScope scopeType);

    Optional<SecretRevealPolicy> findByWorkspaceIdAndProjectIdAndScopeType(UUID workspaceId, UUID projectId, PrivilegedPolicyScope scopeType);

    Optional<SecretRevealPolicy> findByWorkspaceIdAndEnvironmentIdAndScopeType(UUID workspaceId, UUID environmentId, PrivilegedPolicyScope scopeType);

    Optional<SecretRevealPolicy> findByWorkspaceIdAndSecretIdAndScopeType(UUID workspaceId, UUID secretId, PrivilegedPolicyScope scopeType);

    @Query("SELECT p FROM SecretRevealPolicy p WHERE p.workspaceId = :workspaceId AND p.enabled = true " +
           "AND ((p.scopeType = 'SECRET' AND p.secretId = :secretId) " +
           "  OR (p.scopeType = 'ENVIRONMENT' AND p.environmentId = :environmentId) " +
           "  OR (p.scopeType = 'PROJECT' AND p.projectId = :projectId) " +
           "  OR (p.scopeType = 'WORKSPACE')) " +
           "ORDER BY CASE p.scopeType " +
           "  WHEN 'SECRET' THEN 1 " +
           "  WHEN 'ENVIRONMENT' THEN 2 " +
           "  WHEN 'PROJECT' THEN 3 " +
           "  WHEN 'WORKSPACE' THEN 4 " +
           "  ELSE 5 END ASC")
    List<SecretRevealPolicy> findMatchingPolicies(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectId") UUID projectId,
            @Param("environmentId") UUID environmentId,
            @Param("secretId") UUID secretId
    );
}

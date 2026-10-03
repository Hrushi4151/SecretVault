package com.secretvault.rotation.repository;

import com.secretvault.rotation.entity.RotationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RotationPolicyRepository extends JpaRepository<RotationPolicy, UUID> {

    Optional<RotationPolicy> findBySecretId(UUID secretId);

    List<RotationPolicy> findByWorkspaceId(UUID workspaceId);

    List<RotationPolicy> findByEnabledTrueAndNextRotationDueAtLessThanEqual(Instant now);

    @Query("SELECT p FROM RotationPolicy p WHERE p.enabled = true AND p.nextRotationDueAt <= :now")
    List<RotationPolicy> findDuePolicies(@Param("now") Instant now);

    @Query("SELECT COUNT(p) FROM RotationPolicy p WHERE p.workspaceId = :workspaceId AND p.enabled = true")
    long countEnabledByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT p FROM RotationPolicy p WHERE p.workspaceId = :workspaceId AND p.enabled = true AND p.nextRotationDueAt <= :now")
    List<RotationPolicy> findOverdueByWorkspaceId(@Param("workspaceId") UUID workspaceId, @Param("now") Instant now);
}

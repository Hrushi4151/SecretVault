package com.secretvault.rotation.repository;

import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.LeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretLeaseRepository extends JpaRepository<SecretLease, UUID> {

    List<SecretLease> findByWorkspaceId(UUID workspaceId);

    Page<SecretLease> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<SecretLease> findBySecretId(UUID secretId, Pageable pageable);

    List<SecretLease> findBySecretIdAndStatus(UUID secretId, LeaseStatus status);

    List<SecretLease> findByWorkspaceIdAndStatus(UUID workspaceId, LeaseStatus status);

    List<SecretLease> findByStatusAndExpiresAtLessThanEqual(LeaseStatus status, Instant now);

    List<SecretLease> findByMachineIdentityId(UUID machineIdentityId);

    @Query("SELECT l FROM SecretLease l WHERE l.status = 'ACTIVE' AND l.expiresAt <= :now")
    List<SecretLease> findExpiredActiveLeases(@Param("now") Instant now);

    @Query("SELECT COUNT(l) FROM SecretLease l WHERE l.workspaceId = :workspaceId AND l.status = 'ACTIVE'")
    long countActiveByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT COUNT(l) FROM SecretLease l WHERE l.workspaceId = :workspaceId AND l.status = 'EXPIRED'")
    long countExpiredByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}

package com.secretvault.repository.repository;

import com.secretvault.repository.entity.FindingAllowlist;
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
public interface FindingAllowlistRepository extends JpaRepository<FindingAllowlist, UUID> {

    Optional<FindingAllowlist> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<FindingAllowlist> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    List<FindingAllowlist> findByWorkspaceIdAndActiveTrue(UUID workspaceId);

    @Query("SELECT a FROM FindingAllowlist a WHERE a.workspaceId = :workspaceId AND a.active = true " +
           "AND (a.repositoryId IS NULL OR a.repositoryId = :repositoryId) " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    List<FindingAllowlist> findActiveAllowlists(
            @Param("workspaceId") UUID workspaceId,
            @Param("repositoryId") UUID repositoryId,
            @Param("now") Instant now);

    boolean existsByWorkspaceIdAndFingerprintAndActiveTrue(UUID workspaceId, String fingerprint);
}

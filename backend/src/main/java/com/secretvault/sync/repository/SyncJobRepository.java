package com.secretvault.sync.repository;

import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.model.SyncJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for SyncJob entities.
 */
@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    Optional<SyncJob> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<SyncJob> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<SyncJob> findByWorkspaceIdAndStatus(UUID workspaceId, SyncJobStatus status, Pageable pageable);

    List<SyncJob> findByWorkspaceIdAndStatusIn(UUID workspaceId, List<SyncJobStatus> statuses);

    @Query("SELECT j FROM SyncJob j WHERE j.workspaceId = :workspaceId " +
           "AND (:status IS NULL OR j.status = :status) " +
           "AND (:dryRun IS NULL OR j.dryRun = :dryRun)")
    Page<SyncJob> findWithFilters(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") SyncJobStatus status,
            @Param("dryRun") Boolean dryRun,
            Pageable pageable
    );
}

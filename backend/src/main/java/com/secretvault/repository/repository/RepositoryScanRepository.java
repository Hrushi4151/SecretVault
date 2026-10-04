package com.secretvault.repository.repository;

import com.secretvault.repository.entity.RepositoryScan;
import com.secretvault.repository.model.ScanStatus;
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
public interface RepositoryScanRepository extends JpaRepository<RepositoryScan, UUID> {

    Optional<RepositoryScan> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<RepositoryScan> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<RepositoryScan> findByWorkspaceIdAndRepositoryId(UUID workspaceId, UUID repositoryId, Pageable pageable);

    Page<RepositoryScan> findByWorkspaceIdAndStatus(UUID workspaceId, ScanStatus status, Pageable pageable);

    List<RepositoryScan> findByWorkspaceIdAndRepositoryIdOrderByCreatedAtDesc(UUID workspaceId, UUID repositoryId);

    Optional<RepositoryScan> findFirstByWorkspaceIdAndRepositoryIdAndStatusOrderByCompletedAtDesc(
            UUID workspaceId, UUID repositoryId, ScanStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, ScanStatus status);

    @Query("SELECT s FROM RepositoryScan s WHERE s.status IN ('QUEUED', 'CLONING', 'INDEXING', 'SCANNING', 'CLASSIFYING', 'VALIDATING', 'FINALIZING') AND s.createdAt < :staleTime")
    List<RepositoryScan> findStaleActiveScans(@Param("staleTime") Instant staleTime);

    boolean existsByWorkspaceIdAndRepositoryIdAndStatusIn(UUID workspaceId, UUID repositoryId, List<ScanStatus> statuses);
}

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

    List<RepositoryScan> findByStatusOrderByCreatedAtAsc(ScanStatus status);

    Optional<RepositoryScan> findFirstByIdempotencyKeyAndStatusIn(String idempotencyKey, List<ScanStatus> statuses);

    Optional<RepositoryScan> findFirstByIdempotencyKey(String idempotencyKey);

    Optional<RepositoryScan> findByRepositoryIdAndIdempotencyKey(UUID repositoryId, String idempotencyKey);

    boolean existsByRepositoryIdAndIdempotencyKey(UUID repositoryId, String idempotencyKey);

    boolean existsByWorkspaceIdAndRepositoryIdAndStatusIn(UUID workspaceId, UUID repositoryId, List<ScanStatus> statuses);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RepositoryScan s SET s.status = :newStatus, s.startedAt = :now, s.lockedAt = :now, s.lockedBy = :workerId, s.claimToken = :claimToken, s.attemptCount = s.attemptCount + 1 " +
            "WHERE s.id = :id AND (s.status = :queuedStatus OR (s.status IN (:activeStatuses) AND s.lockedAt < :staleCutoff))")
    int claimScanAtomic(
            @Param("id") UUID id,
            @Param("newStatus") ScanStatus newStatus,
            @Param("queuedStatus") ScanStatus queuedStatus,
            @Param("activeStatuses") List<ScanStatus> activeStatuses,
            @Param("staleCutoff") Instant staleCutoff,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RepositoryScan s SET s.status = :newStatus, s.completedAt = :now, s.durationMs = :durationMs, " +
            "s.filesScanned = :filesScanned, s.commitsScanned = :commitsScanned, s.findingsCount = :findingsCount, " +
            "s.highRiskCount = :highRiskCount, s.commitSha = :commitSha, s.lockedAt = NULL, s.lockedBy = NULL, s.claimToken = NULL " +
            "WHERE s.id = :id AND s.lockedBy = :workerId AND s.claimToken = :claimToken")
    int completeScanGuarded(
            @Param("id") UUID id,
            @Param("newStatus") ScanStatus newStatus,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken,
            @Param("filesScanned") int filesScanned,
            @Param("commitsScanned") int commitsScanned,
            @Param("findingsCount") int findingsCount,
            @Param("highRiskCount") int highRiskCount,
            @Param("commitSha") String commitSha,
            @Param("durationMs") Long durationMs,
            @Param("now") Instant now
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RepositoryScan s SET s.status = :newStatus, s.completedAt = :now, s.errorMessage = :errorMessage, " +
            "s.lockedAt = NULL, s.lockedBy = NULL, s.claimToken = NULL " +
            "WHERE s.id = :id AND s.lockedBy = :workerId AND s.claimToken = :claimToken")
    int failScanGuarded(
            @Param("id") UUID id,
            @Param("newStatus") ScanStatus newStatus,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RepositoryScan s SET s.status = :newStatus " +
            "WHERE s.id = :id AND s.lockedBy = :workerId AND s.claimToken = :claimToken")
    int updateScanStatusGuarded(
            @Param("id") UUID id,
            @Param("newStatus") ScanStatus newStatus,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RepositoryScan s SET s.status = :queuedStatus, s.lockedAt = NULL, s.lockedBy = NULL, s.claimToken = NULL " +
            "WHERE s.id = :id AND s.status IN (:activeStatuses) AND s.lockedAt < :staleCutoff")
    int recoverStaleScanAtomic(
            @Param("id") UUID id,
            @Param("queuedStatus") ScanStatus queuedStatus,
            @Param("activeStatuses") List<ScanStatus> activeStatuses,
            @Param("staleCutoff") Instant staleCutoff
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RepositoryScan s SET s.lockedAt = :now " +
            "WHERE s.id = :id AND s.lockedBy = :workerId AND s.claimToken = :claimToken")
    int renewLeaseGuarded(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );
}


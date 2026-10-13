package com.secretvault.automation.repository;

import com.secretvault.automation.entity.AutomationApproval;
import com.secretvault.automation.entity.AutomationApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomationApprovalRepository extends JpaRepository<AutomationApproval, UUID> {

    Optional<AutomationApproval> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    Optional<AutomationApproval> findByWorkspaceIdAndExecutionId(UUID workspaceId, UUID executionId);

    Page<AutomationApproval> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<AutomationApproval> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, AutomationApprovalStatus status, Pageable pageable);

    List<AutomationApproval> findByStatusAndExpiresAtLessThanEqual(AutomationApprovalStatus status, Instant cutoff);

    long countByWorkspaceIdAndStatus(UUID workspaceId, AutomationApprovalStatus status);

    @Modifying
    @Query("""
        UPDATE AutomationApproval a
        SET a.status = :newStatus,
            a.decidedBy = :decidedBy,
            a.decidedAt = :decidedAt,
            a.rejectionReason = :rejectionReason,
            a.updatedAt = :now
        WHERE a.id = :id
          AND a.workspaceId = :workspaceId
          AND a.status = :expectedStatus
    """)
    int updateStatusGuarded(
            @Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId,
            @Param("expectedStatus") AutomationApprovalStatus expectedStatus,
            @Param("newStatus") AutomationApprovalStatus newStatus,
            @Param("decidedBy") UUID decidedBy,
            @Param("decidedAt") Instant decidedAt,
            @Param("rejectionReason") String rejectionReason,
            @Param("now") Instant now
    );
}

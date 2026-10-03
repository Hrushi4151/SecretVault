package com.secretvault.automation.repository;

import com.secretvault.automation.entity.AutomationApproval;
import com.secretvault.automation.entity.AutomationApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

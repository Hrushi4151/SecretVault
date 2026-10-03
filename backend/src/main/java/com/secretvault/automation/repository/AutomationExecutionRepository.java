package com.secretvault.automation.repository;

import com.secretvault.automation.entity.AutomationExecution;
import com.secretvault.automation.entity.AutomationExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {

    Optional<AutomationExecution> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    Page<AutomationExecution> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<AutomationExecution> findByWorkspaceIdAndPolicyIdOrderByCreatedAtDesc(UUID workspaceId, UUID policyId, Pageable pageable);

    List<AutomationExecution> findByWorkspaceIdAndEventId(UUID workspaceId, UUID eventId);

    long countByWorkspaceIdAndPolicyIdAndCreatedAtGreaterThanEqual(UUID workspaceId, UUID policyId, Instant since);

    long countByWorkspaceIdAndStatus(UUID workspaceId, AutomationExecutionStatus status);

    Page<AutomationExecution> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, AutomationExecutionStatus status, Pageable pageable);
}


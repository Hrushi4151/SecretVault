package com.secretvault.ai.domain.repository;

import com.secretvault.ai.domain.entity.AiRemediationPlan;
import com.secretvault.ai.domain.model.AiPlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiRemediationPlanRepository extends JpaRepository<AiRemediationPlan, UUID> {
    Page<AiRemediationPlan> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
    Page<AiRemediationPlan> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, AiPlanStatus status, Pageable pageable);
    Optional<AiRemediationPlan> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<AiRemediationPlan> findByWorkspaceIdAndTargetResourceTypeAndTargetResourceIdAndStatus(
            UUID workspaceId, String targetResourceType, String targetResourceId, AiPlanStatus status);
    List<AiRemediationPlan> findByStatusAndExpiresAtBefore(AiPlanStatus status, Instant now);
}

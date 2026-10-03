package com.secretvault.automation.repository;

import com.secretvault.automation.entity.AutomationPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomationPolicyRepository extends JpaRepository<AutomationPolicy, UUID> {

    Optional<AutomationPolicy> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    default Optional<AutomationPolicy> findByIdAndWorkspaceId(UUID id, UUID workspaceId) {
        return findByWorkspaceIdAndId(workspaceId, id);
    }

    Optional<AutomationPolicy> findByWorkspaceIdAndName(UUID workspaceId, String name);

    Page<AutomationPolicy> findByWorkspaceIdOrderByPriorityAscCreatedAtDesc(UUID workspaceId, Pageable pageable);

    default Page<AutomationPolicy> findByWorkspaceIdOrderByPriorityAsc(UUID workspaceId, Pageable pageable) {
        return findByWorkspaceIdOrderByPriorityAscCreatedAtDesc(workspaceId, pageable);
    }

    List<AutomationPolicy> findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(UUID workspaceId);

    @Query("SELECT p FROM AutomationPolicy p WHERE p.workspaceId = :workspaceId AND p.enabled = true " +
            "AND (p.triggerEventTypes LIKE %:eventType% OR p.triggerEventTypes LIKE '%*%' OR p.triggerEventTypes LIKE '%ALL%') " +
            "ORDER BY p.priority ASC")
    List<AutomationPolicy> findMatchingPolicies(
            @Param("workspaceId") UUID workspaceId,
            @Param("eventType") String eventType
    );

    long countByWorkspaceId(UUID workspaceId);
}


package com.secretvault.ai.domain.repository;

import com.secretvault.ai.domain.entity.AiRcaReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiRcaReportRepository extends JpaRepository<AiRcaReport, UUID> {
    Page<AiRcaReport> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
    Optional<AiRcaReport> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<AiRcaReport> findByWorkspaceIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(UUID workspaceId, String targetType, String targetId);
    Optional<AiRcaReport> findFirstByWorkspaceIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(UUID workspaceId, String targetType, String targetId);
}

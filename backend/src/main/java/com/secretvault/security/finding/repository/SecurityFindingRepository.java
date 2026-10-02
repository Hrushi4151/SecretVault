package com.secretvault.security.finding.repository;

import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, UUID> {

    Optional<SecurityFinding> findByWorkspaceIdAndFingerprint(UUID workspaceId, String fingerprint);

    Optional<SecurityFinding> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<SecurityFinding> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    @Query("""
        SELECT f FROM SecurityFinding f
        WHERE f.workspaceId = :workspaceId
          AND (:status IS NULL OR f.status = :status)
          AND (:severity IS NULL OR f.severity = :severity)
          AND (:category IS NULL OR f.category = :category)
          AND (:projectId IS NULL OR f.projectId = :projectId)
          AND (:environmentId IS NULL OR f.environmentId = :environmentId)
          AND (:search IS NULL OR LOWER(f.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(f.safeDescription) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY f.lastObservedAt DESC
    """)
    Page<SecurityFinding> searchFindings(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") FindingStatus status,
            @Param("severity") FindingSeverity severity,
            @Param("category") FindingCategory category,
            @Param("projectId") UUID projectId,
            @Param("environmentId") UUID environmentId,
            @Param("search") String search,
            Pageable pageable
    );

    List<SecurityFinding> findByWorkspaceIdAndStatusIn(UUID workspaceId, Collection<FindingStatus> statuses);

    long countByWorkspaceIdAndStatusIn(UUID workspaceId, Collection<FindingStatus> statuses);

    long countByWorkspaceIdAndSeverityAndStatusIn(
            UUID workspaceId,
            FindingSeverity severity,
            Collection<FindingStatus> statuses
    );

    long countByWorkspaceIdAndCategoryInAndStatusIn(
            UUID workspaceId,
            Collection<FindingCategory> categories,
            Collection<FindingStatus> statuses
    );
}

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
public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SecurityFinding> {

    Optional<SecurityFinding> findByWorkspaceIdAndFingerprint(UUID workspaceId, String fingerprint);

    Optional<SecurityFinding> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<SecurityFinding> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    default Page<SecurityFinding> searchFindings(
            UUID workspaceId,
            FindingStatus status,
            FindingSeverity severity,
            FindingCategory category,
            UUID projectId,
            UUID environmentId,
            String search,
            Pageable pageable
    ) {
        org.springframework.data.jpa.domain.Specification<SecurityFinding> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("workspaceId"), workspaceId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (environmentId != null) {
                predicates.add(cb.equal(root.get("environmentId"), environmentId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("safeDescription")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return findAll(spec, pageable);
    }

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

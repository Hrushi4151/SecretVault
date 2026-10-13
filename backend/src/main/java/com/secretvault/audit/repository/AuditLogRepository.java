package com.secretvault.audit.repository;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Append-only repository for querying security and lifecycle audit trails.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    org.springframework.data.domain.Page<AuditLog> findByWorkspaceIdOrderByCreatedAtDesc(
            UUID workspaceId,
            org.springframework.data.domain.Pageable pageable
    );

    org.springframework.data.domain.Page<AuditLog> findByWorkspaceIdAndActionInOrderByCreatedAtDesc(
            UUID workspaceId,
            java.util.Collection<AuditAction> actions,
            org.springframework.data.domain.Pageable pageable
    );

    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, UUID resourceId);

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    default org.springframework.data.domain.Page<AuditLog> searchAuditLogs(
            UUID workspaceId,
            AuditAction action,
            String outcome,
            String resourceType,
            UUID actorId,
            String search,
            java.time.Instant from,
            java.time.Instant to,
            org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("workspaceId"), workspaceId));
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (outcome != null && !outcome.isBlank()) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("resourceType")), pattern),
                        cb.like(cb.lower(root.get("ipAddress")), pattern),
                        cb.like(cb.lower(root.get("requestId")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return findAll(spec, pageable);
    }

    default java.util.List<AuditLog> exportAuditLogs(
            UUID workspaceId,
            AuditAction action,
            String outcome,
            String resourceType,
            UUID actorId,
            String search,
            java.time.Instant from,
            java.time.Instant to,
            int maxLimit
    ) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0, Math.min(1000, Math.max(1, maxLimit)),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );
        return searchAuditLogs(workspaceId, action, outcome, resourceType, actorId, search, from, to, pageable).getContent();
    }
}


package com.secretvault.security.event.repository;

import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SecurityEvent> {

    Page<SecurityEvent> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    default Page<SecurityEvent> searchEvents(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID actorUserId,
            SecurityEventType eventType,
            SecurityEventSeverity severity,
            SecurityEventOutcome outcome,
            Instant fromTime,
            Instant toTime,
            Pageable pageable
    ) {
        org.springframework.data.jpa.domain.Specification<SecurityEvent> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("workspaceId"), workspaceId));
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (environmentId != null) {
                predicates.add(cb.equal(root.get("environmentId"), environmentId));
            }
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (fromTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromTime));
            }
            if (toTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toTime));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return findAll(spec, pageable);
    }

    @Query("""
        SELECT COUNT(e) FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.outcome = com.secretvault.security.event.model.SecurityEventOutcome.DENIED
          AND e.timestamp >= :since
    """)
    long countAuthorizationDenialsSince(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    @Query("""
        SELECT COUNT(e) FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.eventType IN (
            com.secretvault.security.event.model.SecurityEventType.MEMBER_ADDED,
            com.secretvault.security.event.model.SecurityEventType.MEMBER_REMOVED,
            com.secretvault.security.event.model.SecurityEventType.MEMBER_ROLE_CHANGED,
            com.secretvault.security.event.model.SecurityEventType.PROJECT_ACCESS_CHANGED,
            com.secretvault.security.event.model.SecurityEventType.ENVIRONMENT_ACCESS_CHANGED,
            com.secretvault.security.event.model.SecurityEventType.ACCESS_GRANT_CREATED,
            com.secretvault.security.event.model.SecurityEventType.ACCESS_GRANT_REVOKED
          )
          AND e.timestamp >= :since
    """)
    long countAdminChangesSince(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    @Query("""
        SELECT COUNT(e) FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.eventType IN (
            com.secretvault.security.event.model.SecurityEventType.JIT_REQUESTED,
            com.secretvault.security.event.model.SecurityEventType.JIT_APPROVED,
            com.secretvault.security.event.model.SecurityEventType.JIT_REJECTED,
            com.secretvault.security.event.model.SecurityEventType.JIT_EXPIRED,
            com.secretvault.security.event.model.SecurityEventType.JIT_REVOKED
          )
          AND e.timestamp >= :since
    """)
    long countJitActivitySince(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    @Query("""
        SELECT e FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.timestamp >= :since
        ORDER BY e.timestamp DESC
    """)
    List<SecurityEvent> findRecentEvents(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    @Query("""
        SELECT e FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.actorUserId = :actorUserId
          AND e.timestamp >= :since
        ORDER BY e.timestamp DESC
    """)
    List<SecurityEvent> findByWorkspaceIdAndActorUserIdSince(
            @Param("workspaceId") UUID workspaceId,
            @Param("actorUserId") UUID actorUserId,
            @Param("since") Instant since
    );

    long countByWorkspaceIdAndActorUserId(UUID workspaceId, UUID actorUserId);
}

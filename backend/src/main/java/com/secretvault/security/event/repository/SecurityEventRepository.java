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
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    Page<SecurityEvent> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    @Query("""
        SELECT e FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND (:projectId IS NULL OR e.projectId = :projectId)
          AND (:environmentId IS NULL OR e.environmentId = :environmentId)
          AND (:actorUserId IS NULL OR e.actorUserId = :actorUserId)
          AND (:eventType IS NULL OR e.eventType = :eventType)
          AND (:severity IS NULL OR e.severity = :severity)
          AND (:outcome IS NULL OR e.outcome = :outcome)
          AND (:fromTime IS NULL OR e.timestamp >= :fromTime)
          AND (:toTime IS NULL OR e.timestamp <= :toTime)
        ORDER BY e.timestamp DESC
    """)
    Page<SecurityEvent> searchEvents(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectId") UUID projectId,
            @Param("environmentId") UUID environmentId,
            @Param("actorUserId") UUID actorUserId,
            @Param("eventType") SecurityEventType eventType,
            @Param("severity") SecurityEventSeverity severity,
            @Param("outcome") SecurityEventOutcome outcome,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(e) FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.outcome = 'DENIED'
          AND e.timestamp >= :since
    """)
    long countAuthorizationDenialsSince(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    @Query("""
        SELECT COUNT(e) FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.eventType IN (
            'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_ROLE_CHANGED',
            'PROJECT_ACCESS_CHANGED', 'ENVIRONMENT_ACCESS_CHANGED',
            'ACCESS_GRANT_CREATED', 'ACCESS_GRANT_REVOKED',
            'WORKSPACE_SETTINGS_UPDATED'
          )
          AND e.timestamp >= :since
    """)
    long countAdminChangesSince(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    @Query("""
        SELECT COUNT(e) FROM SecurityEvent e
        WHERE e.workspaceId = :workspaceId
          AND e.eventType IN ('JIT_REQUESTED', 'JIT_APPROVED', 'JIT_REJECTED', 'JIT_EXPIRED', 'JIT_REVOKED')
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

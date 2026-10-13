package com.secretvault.events.repository;

import com.secretvault.events.entity.OutboxEvent;
import com.secretvault.events.entity.OutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    Optional<OutboxEvent> findByEventId(UUID eventId);

    Optional<OutboxEvent> findByWorkspaceIdAndEventId(UUID workspaceId, UUID eventId);

    List<OutboxEvent> findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(OutboxStatus status, Instant now, Pageable pageable);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND e.lockedAt < :cutoff")
    List<OutboxEvent> findStaleLockedEvents(@Param("status") OutboxStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);

    Page<OutboxEvent> findByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId, Pageable pageable);

    Page<OutboxEvent> findByWorkspaceIdAndEventTypeOrderByOccurredAtDesc(UUID workspaceId, String eventType, Pageable pageable);

    Page<OutboxEvent> findByWorkspaceIdAndAggregateTypeAndAggregateIdOrderByOccurredAtDesc(
            UUID workspaceId, String aggregateType, String aggregateId, Pageable pageable);

    @Query("SELECT e FROM OutboxEvent e WHERE e.workspaceId = :workspaceId " +
            "AND (:eventType IS NULL OR e.eventType = :eventType) " +
            "AND (:aggregateType IS NULL OR e.aggregateType = :aggregateType) " +
            "AND (:aggregateId IS NULL OR e.aggregateId = :aggregateId) " +
            "AND (:fromTime IS NULL OR e.occurredAt >= :fromTime) " +
            "AND (:toTime IS NULL OR e.occurredAt <= :toTime) " +
            "ORDER BY e.occurredAt DESC")
    Page<OutboxEvent> filterEvents(
            @Param("workspaceId") UUID workspaceId,
            @Param("eventType") String eventType,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );

    long countByWorkspaceIdAndStatus(UUID workspaceId, OutboxStatus status);

    Page<OutboxEvent> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, OutboxStatus status, Pageable pageable);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :newStatus, e.lockedAt = :now, e.lockedBy = :workerId, e.claimToken = :claimToken, e.attemptCount = e.attemptCount + 1 " +
            "WHERE e.id = :id AND (e.status = :pendingStatus OR (e.status = :failedStatus AND e.availableAt <= :now) OR (e.status = :processingStatus AND e.lockedAt < :staleCutoff))")
    int claimEventAtomic(
            @Param("id") UUID id,
            @Param("newStatus") OutboxStatus newStatus,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("failedStatus") OutboxStatus failedStatus,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("staleCutoff") Instant staleCutoff,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :newStatus, e.processedAt = :now, e.lastError = NULL, e.lockedAt = NULL, e.lockedBy = NULL, e.claimToken = NULL " +
            "WHERE e.id = :id AND e.lockedBy = :workerId AND e.claimToken = :claimToken")
    int completeEventGuarded(
            @Param("id") UUID id,
            @Param("newStatus") OutboxStatus newStatus,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :newStatus, e.availableAt = :availableAt, e.lastError = :error, e.lockedAt = NULL, e.lockedBy = NULL, e.claimToken = NULL " +
            "WHERE e.id = :id AND e.lockedBy = :workerId AND e.claimToken = :claimToken")
    int failEventGuarded(
            @Param("id") UUID id,
            @Param("newStatus") OutboxStatus newStatus,
            @Param("availableAt") Instant availableAt,
            @Param("error") String error,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken
    );

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :newStatus, e.lockedAt = NULL, e.lockedBy = NULL, e.claimToken = NULL " +
            "WHERE e.status = :processingStatus AND e.lockedAt < :cutoff")
    int recoverStaleLockedEventsAtomic(
            @Param("newStatus") OutboxStatus newStatus,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("cutoff") Instant cutoff
    );

    Page<OutboxEvent> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<OutboxEvent> findByWorkspaceIdAndEventTypeOrderByCreatedAtDesc(UUID workspaceId, String eventType, Pageable pageable);

    Page<OutboxEvent> findByWorkspaceIdAndAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            UUID workspaceId, String aggregateType, String aggregateId, Pageable pageable);
}


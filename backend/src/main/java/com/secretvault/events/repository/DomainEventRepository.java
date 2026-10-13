package com.secretvault.events.repository;

import com.secretvault.events.entity.DomainEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DomainEventRepository extends JpaRepository<DomainEventEntity, UUID> {

    Optional<DomainEventEntity> findByEventId(UUID eventId);

    Optional<DomainEventEntity> findByWorkspaceIdAndEventId(UUID workspaceId, UUID eventId);

    Page<DomainEventEntity> findByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId, Pageable pageable);

    Page<DomainEventEntity> findByWorkspaceIdAndEventTypeOrderByOccurredAtDesc(UUID workspaceId, String eventType, Pageable pageable);

    Page<DomainEventEntity> findByWorkspaceIdAndAggregateTypeAndAggregateIdOrderByOccurredAtDesc(
            UUID workspaceId, String aggregateType, String aggregateId, Pageable pageable);

    @Query("SELECT e FROM DomainEventEntity e WHERE e.workspaceId = :workspaceId " +
            "AND (:eventType IS NULL OR e.eventType = :eventType) " +
            "AND (:aggregateType IS NULL OR e.aggregateType = :aggregateType) " +
            "AND (:aggregateId IS NULL OR e.aggregateId = :aggregateId) " +
            "AND (:fromTime IS NULL OR e.occurredAt >= :fromTime) " +
            "AND (:toTime IS NULL OR e.occurredAt <= :toTime) " +
            "ORDER BY e.occurredAt DESC")
    Page<DomainEventEntity> filterEvents(
            @Param("workspaceId") UUID workspaceId,
            @Param("eventType") String eventType,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );

    long countByWorkspaceId(UUID workspaceId);

    @Override
    default void delete(DomainEventEntity entity) {
        throw new UnsupportedOperationException("DomainEventEntity is strictly immutable and cannot be deleted");
    }

    @Override
    default void deleteById(UUID id) {
        throw new UnsupportedOperationException("DomainEventEntity is strictly immutable and cannot be deleted");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("DomainEventEntity is strictly immutable and cannot be deleted");
    }

    @Override
    default void deleteAll(Iterable<? extends DomainEventEntity> entities) {
        throw new UnsupportedOperationException("DomainEventEntity is strictly immutable and cannot be deleted");
    }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) {
        throw new UnsupportedOperationException("DomainEventEntity is strictly immutable and cannot be deleted");
    }
}

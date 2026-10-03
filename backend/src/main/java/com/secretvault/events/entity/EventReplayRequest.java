package com.secretvault.events.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_replay_requests")
public class EventReplayRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "target_event_id")
    private UUID targetEventId;

    @Column(name = "event_type_filter", length = 128)
    private String eventTypeFilter;

    @Column(name = "aggregate_type", length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 128)
    private String aggregateId;

    @Column(name = "from_timestamp")
    private Instant fromTimestamp;

    @Column(name = "to_timestamp")
    private Instant toTimestamp;

    @Column(name = "reexecute_side_effects", nullable = false)
    private boolean reexecuteSideEffects = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReplayStatus status = ReplayStatus.PENDING;

    @Column(name = "events_replayed_count", nullable = false)
    private int eventsReplayedCount = 0;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EventReplayRequest() {}

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getTargetEventId() { return targetEventId; }
    public void setTargetEventId(UUID targetEventId) { this.targetEventId = targetEventId; }
    public String getEventTypeFilter() { return eventTypeFilter; }
    public void setEventTypeFilter(String eventTypeFilter) { this.eventTypeFilter = eventTypeFilter; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public Instant getFromTimestamp() { return fromTimestamp; }
    public void setFromTimestamp(Instant fromTimestamp) { this.fromTimestamp = fromTimestamp; }
    public Instant getToTimestamp() { return toTimestamp; }
    public void setToTimestamp(Instant toTimestamp) { this.toTimestamp = toTimestamp; }
    public boolean isReexecuteSideEffects() { return reexecuteSideEffects; }
    public void setReexecuteSideEffects(boolean reexecuteSideEffects) { this.reexecuteSideEffects = reexecuteSideEffects; }
    public ReplayStatus getStatus() { return status; }
    public void setStatus(ReplayStatus status) { this.status = status; }
    public int getEventsReplayedCount() { return eventsReplayedCount; }
    public void setEventsReplayedCount(int eventsReplayedCount) { this.eventsReplayedCount = eventsReplayedCount; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

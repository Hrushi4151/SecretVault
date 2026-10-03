package com.secretvault.events.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_processing_log", uniqueConstraints = {
        @UniqueConstraint(name = "uq_event_consumer", columnNames = {"event_id", "consumer_name"})
})
public class EventProcessingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, length = 128)
    private String consumerName;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PROCESSED"; // 'PROCESSING', 'PROCESSED', 'FAILED', 'SKIPPED'

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EventProcessingLog() {}

    public EventProcessingLog(UUID eventId, String consumerName, UUID workspaceId, String status, String correlationId) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.workspaceId = workspaceId;
        this.status = status;
        this.correlationId = correlationId;
        this.processedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (processedAt == null) processedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String consumerName) { this.consumerName = consumerName; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public Instant getCreatedAt() { return createdAt; }
}

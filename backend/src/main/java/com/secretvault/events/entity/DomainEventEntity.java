package com.secretvault.events.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Authoritative, immutable domain event record stored in the append-only event stream.
 * Invariant: Entity updates are forbidden to maintain audit integrity.
 */
@Entity
@Table(name = "domain_events")
public class DomainEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion = 1;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "environment_id", updatable = false)
    private UUID environmentId;

    @Column(name = "secret_id", updatable = false)
    private UUID secretId;

    @Column(name = "aggregate_type", nullable = false, length = 64, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128, updatable = false)
    private String aggregateId;

    @Column(name = "aggregate_version", updatable = false)
    private Integer aggregateVersion;

    @Column(name = "actor_type", nullable = false, length = 64, updatable = false)
    private String actorType = "USER";

    @Column(name = "actor_id", length = 128, updatable = false)
    private String actorId;

    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    @Column(name = "causation_id", length = 128, updatable = false)
    private String causationId;

    @Column(name = "request_id", length = 128, updatable = false)
    private String requestId;

    @Column(name = "source", nullable = false, length = 128, updatable = false)
    private String source = "secretvault-core";

    @Column(name = "severity", nullable = false, length = 32, updatable = false)
    private String severity = "INFO";

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payloadJson;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String metadataJson = "{}";

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    public DomainEventEntity() {}

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (recordedAt == null) recordedAt = Instant.now();
        if (occurredAt == null) occurredAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        throw new UnsupportedOperationException("DomainEventEntity is strictly immutable and cannot be updated.");
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public UUID getEnvironmentId() { return environmentId; }
    public UUID getSecretId() { return secretId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public Integer getAggregateVersion() { return aggregateVersion; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getRequestId() { return requestId; }
    public String getSource() { return source; }
    public String getSeverity() { return severity; }
    public String getPayloadJson() { return payloadJson; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }

    // Setters (used during construction/persistence only)
    public void setId(UUID id) { this.id = id; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setEventVersion(int eventVersion) { this.eventVersion = eventVersion; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }
    public void setSecretId(UUID secretId) { this.secretId = secretId; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public void setAggregateVersion(Integer aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setCausationId(String causationId) { this.causationId = causationId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setSource(String source) { this.source = source; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}

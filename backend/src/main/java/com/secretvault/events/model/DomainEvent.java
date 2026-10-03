package com.secretvault.events.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable contract for domain events across SecretVault.
 * Invariant: Never contains secret plaintext, raw passwords, or decrypted DEKs.
 */
public interface DomainEvent {
    UUID getEventId();
    EventType getEventType();
    int getEventVersion();
    Instant getOccurredAt();
    Instant getRecordedAt();
    UUID getWorkspaceId();
    UUID getProjectId();
    UUID getEnvironmentId();
    UUID getSecretId();
    String getActorType();
    String getActorId();
    String getCorrelationId();
    String getCausationId();
    String getRequestId();
    String getSource();
    EventSeverity getSeverity();
    String getAggregateType();
    String getAggregateId();
    Map<String, Object> getMetadata();

    // Convenient aliases matching record accessors
    default UUID eventId() { return getEventId(); }
    default EventType eventType() { return getEventType(); }
    default int eventVersion() { return getEventVersion(); }
    default Instant occurredAt() { return getOccurredAt(); }
    default Instant recordedAt() { return getRecordedAt(); }
    default UUID workspaceId() { return getWorkspaceId(); }
    default UUID projectId() { return getProjectId(); }
    default UUID environmentId() { return getEnvironmentId(); }
    default UUID secretId() { return getSecretId(); }
    default String actorType() { return getActorType(); }
    default String actorId() { return getActorId(); }
    default String correlationId() { return getCorrelationId(); }
    default String causationId() { return getCausationId(); }
    default String requestId() { return getRequestId(); }
    default String source() { return getSource(); }
    default EventSeverity severity() { return getSeverity(); }
    default String aggregateType() { return getAggregateType(); }
    default String aggregateId() { return getAggregateId(); }
    default Map<String, Object> metadata() { return getMetadata(); }
}

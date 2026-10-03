package com.secretvault.events.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable standard record implementation of DomainEvent.
 * Automatically validates and redacts sensitive metadata fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseDomainEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("eventType") EventType eventType,
        @JsonProperty("eventVersion") int eventVersion,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("recordedAt") Instant recordedAt,
        @JsonProperty("workspaceId") UUID workspaceId,
        @JsonProperty("projectId") UUID projectId,
        @JsonProperty("environmentId") UUID environmentId,
        @JsonProperty("secretId") UUID secretId,
        @JsonProperty("actorType") String actorType,
        @JsonProperty("actorId") String actorId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("causationId") String causationId,
        @JsonProperty("requestId") String requestId,
        @JsonProperty("source") String source,
        @JsonProperty("severity") EventSeverity severity,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("metadata") Map<String, Object> metadata
) implements DomainEvent {

    public BaseDomainEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
        if (recordedAt == null) recordedAt = Instant.now();
        if (eventVersion <= 0) eventVersion = 1;
        if (severity == null) severity = EventSeverity.INFO;
        if (metadata != null) {
            metadata = Collections.unmodifiableMap(sanitizeMetadata(metadata));
        } else {
            metadata = Collections.emptyMap();
        }
    }

    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public EventType getEventType() { return eventType; }
    @Override
    public int getEventVersion() { return eventVersion; }
    @Override
    public Instant getOccurredAt() { return occurredAt; }
    @Override
    public Instant getRecordedAt() { return recordedAt; }
    @Override
    public UUID getWorkspaceId() { return workspaceId; }
    @Override
    public UUID getProjectId() { return projectId; }
    @Override
    public UUID getEnvironmentId() { return environmentId; }
    @Override
    public UUID getSecretId() { return secretId; }
    @Override
    public String getActorType() { return actorType; }
    @Override
    public String getActorId() { return actorId; }
    @Override
    public String getCorrelationId() { return correlationId; }
    @Override
    public String getCausationId() { return causationId; }
    @Override
    public String getRequestId() { return requestId; }
    @Override
    public String getSource() { return source; }
    @Override
    public EventSeverity getSeverity() { return severity; }
    @Override
    public String getAggregateType() { return aggregateType; }
    @Override
    public String getAggregateId() { return aggregateId; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }

    private static Map<String, Object> sanitizeMetadata(Map<String, Object> input) {
        Map<String, Object> clean = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String lower = key.toLowerCase();
            if (lower.contains("secret") || lower.contains("password") || lower.contains("token")
                    || lower.contains("plaintext") || lower.contains("dek")
                    || lower.contains("apikey") || lower.contains("api_key")
                    || (lower.contains("key") && (lower.contains("raw") || lower.contains("dek") || lower.contains("private") || lower.contains("api")))
                    || lower.contains("credentials") || lower.contains("auth")) {
                clean.put(key, "[REDACTED]");
            } else {
                clean.put(key, entry.getValue());
            }
        }
        return clean;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID eventId = UUID.randomUUID();
        private EventType eventType;
        private int eventVersion = 1;
        private Instant occurredAt = Instant.now();
        private Instant recordedAt = Instant.now();
        private UUID workspaceId;
        private UUID projectId;
        private UUID environmentId;
        private UUID secretId;
        private String actorType = "USER";
        private String actorId;
        private String correlationId;
        private String causationId;
        private String requestId;
        private String source = "secretvault-core";
        private EventSeverity severity = EventSeverity.INFO;
        private String aggregateType = "UNKNOWN";
        private String aggregateId = "UNKNOWN";
        private Map<String, Object> metadata = new HashMap<>();

        public Builder eventId(UUID eventId) { this.eventId = eventId; return this; }
        public Builder eventType(EventType eventType) { this.eventType = eventType; return this; }
        public Builder eventVersion(int eventVersion) { this.eventVersion = eventVersion; return this; }
        public Builder occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder recordedAt(Instant recordedAt) { this.recordedAt = recordedAt; return this; }
        public Builder workspaceId(UUID workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder projectId(UUID projectId) { this.projectId = projectId; return this; }
        public Builder environmentId(UUID environmentId) { this.environmentId = environmentId; return this; }
        public Builder secretId(UUID secretId) { this.secretId = secretId; return this; }
        public Builder actorType(String actorType) { this.actorType = actorType; return this; }
        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder causationId(String causationId) { this.causationId = causationId; return this; }
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder severity(EventSeverity severity) { this.severity = severity; return this; }
        public Builder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
        public Builder aggregateId(String aggregateId) { this.aggregateId = aggregateId; return this; }
        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) this.metadata = new HashMap<>(metadata);
            return this;
        }
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public BaseDomainEvent build() {
            if (workspaceId == null) {
                throw new IllegalArgumentException("workspaceId is mandatory for domain events");
            }
            if (eventType == null) {
                throw new IllegalArgumentException("eventType is mandatory for domain events");
            }
            return new BaseDomainEvent(
                    eventId, eventType, eventVersion, occurredAt, recordedAt,
                    workspaceId, projectId, environmentId, secretId,
                    actorType, actorId, correlationId, causationId, requestId,
                    source, severity, aggregateType, aggregateId, metadata
            );
        }
    }
}

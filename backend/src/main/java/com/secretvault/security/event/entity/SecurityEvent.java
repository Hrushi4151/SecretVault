package com.secretvault.security.event.entity;

import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable security and audit event capturing structured, sanitized telemetry.
 */
@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private SecurityEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private SecurityEventSeverity severity = SecurityEventSeverity.INFO;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private SecurityEventOutcome outcome = SecurityEventOutcome.SUCCESS;

    @Column(name = "source", nullable = false, length = 64)
    private String source = "SYSTEM";

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    public SecurityEvent() {
    }

    public SecurityEvent(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID actorUserId,
            SecurityEventType eventType,
            SecurityEventSeverity severity,
            SecurityEventOutcome outcome,
            String source,
            String ipAddress,
            String userAgent,
            String requestId,
            String metadataJson
    ) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.actorUserId = actorUserId;
        this.eventType = eventType;
        this.severity = severity != null ? severity : SecurityEventSeverity.INFO;
        this.outcome = outcome != null ? outcome : SecurityEventOutcome.SUCCESS;
        this.source = source != null ? source : "SYSTEM";
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.metadataJson = metadataJson;
        this.timestamp = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public SecurityEventType getEventType() {
        return eventType;
    }

    public SecurityEventSeverity getSeverity() {
        return severity;
    }

    public SecurityEventOutcome getOutcome() {
        return outcome;
    }

    public String getSource() {
        return source;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}

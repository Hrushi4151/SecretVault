package com.secretvault.webhook.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints", uniqueConstraints = {
        @UniqueConstraint(name = "uq_webhook_endpoint_name", columnNames = {"workspace_id", "name"})
})
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "destination_url", nullable = false, length = 1024)
    private String destinationUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "subscribed_events_json", nullable = false, columnDefinition = "TEXT")
    private String subscribedEventsJson = "[\"*\"]";

    @Column(name = "signing_secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String signingSecretEncrypted;

    @Column(name = "secret_prefix", nullable = false, length = 16)
    private String secretPrefix;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public WebhookEndpoint() {}

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDestinationUrl() { return destinationUrl; }
    public void setDestinationUrl(String destinationUrl) { this.destinationUrl = destinationUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSubscribedEventsJson() { return subscribedEventsJson; }
    public void setSubscribedEventsJson(String subscribedEventsJson) { this.subscribedEventsJson = subscribedEventsJson; }
    public String getSigningSecretEncrypted() { return signingSecretEncrypted; }
    public void setSigningSecretEncrypted(String signingSecretEncrypted) { this.signingSecretEncrypted = signingSecretEncrypted; }
    public String getSecretPrefix() { return secretPrefix; }
    public void setSecretPrefix(String secretPrefix) { this.secretPrefix = secretPrefix; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public Instant getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(Instant lastFailureAt) { this.lastFailureAt = lastFailureAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

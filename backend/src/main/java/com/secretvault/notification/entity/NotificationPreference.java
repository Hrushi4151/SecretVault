package com.secretvault.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uq_notif_pref_user", columnNames = {"workspace_id", "user_id"})
})
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "channel_in_app", nullable = false)
    private boolean channelInApp = true;

    @Column(name = "channel_webhook", nullable = false)
    private boolean channelWebhook = true;

    @Column(name = "channel_email", nullable = false)
    private boolean channelEmail = false;

    @Column(name = "min_severity", nullable = false, length = 32)
    private String minSeverity = "INFO";

    @Column(name = "muted_event_types_json", nullable = false, columnDefinition = "TEXT")
    private String mutedEventTypesJson = "[]";

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start", length = 8)
    private String quietHoursStart = "22:00";

    @Column(name = "quiet_hours_end", length = 8)
    private String quietHoursEnd = "07:00";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public NotificationPreference() {}

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
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public boolean isChannelInApp() { return channelInApp; }
    public void setChannelInApp(boolean channelInApp) { this.channelInApp = channelInApp; }
    public boolean isChannelWebhook() { return channelWebhook; }
    public void setChannelWebhook(boolean channelWebhook) { this.channelWebhook = channelWebhook; }
    public boolean isChannelEmail() { return channelEmail; }
    public void setChannelEmail(boolean channelEmail) { this.channelEmail = channelEmail; }
    public String getMinSeverity() { return minSeverity; }
    public void setMinSeverity(String minSeverity) { this.minSeverity = minSeverity; }
    public String getMutedEventTypesJson() { return mutedEventTypesJson; }
    public void setMutedEventTypesJson(String mutedEventTypesJson) { this.mutedEventTypesJson = mutedEventTypesJson; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public void setQuietHoursEnabled(boolean quietHoursEnabled) { this.quietHoursEnabled = quietHoursEnabled; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

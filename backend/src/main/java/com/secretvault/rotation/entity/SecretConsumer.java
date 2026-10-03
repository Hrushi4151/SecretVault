package com.secretvault.rotation.entity;

import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.ConsumerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Registry of active application instances and workload consumers consuming secrets at runtime.
 */
@Entity
@Table(
        name = "secret_consumers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_consumer_name_env", columnNames = {"environment_id", "name", "instance_id"})
        }
)
public class SecretConsumer {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "consumer_type", nullable = false, length = 32)
    private ConsumerType consumerType = ConsumerType.APPLICATION;

    @Column(name = "machine_identity_id")
    private UUID machineIdentityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConsumerStatus status = ConsumerStatus.ACTIVE;

    @Column(name = "runtime_framework", length = 64)
    private String runtimeFramework;

    @Column(name = "sdk_version", length = 32)
    private String sdkVersion;

    @Column(name = "instance_id", length = 128)
    private String instanceId;

    @Column(name = "hostname", length = 128)
    private String hostname;

    @Column(name = "supports_dynamic_refresh", nullable = false)
    private boolean supportsDynamicRefresh = true;

    @Column(name = "requires_restart", nullable = false)
    private boolean requiresRestart = false;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt = Instant.now();

    @Column(name = "last_refresh_acknowledged_at")
    private Instant lastRefreshAcknowledgedAt;

    @Column(name = "current_acknowledged_version")
    private Integer currentAcknowledgedVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SecretConsumer() {
    }

    public SecretConsumer(UUID workspaceId, UUID projectId, UUID environmentId, String name, ConsumerType consumerType, UUID machineIdentityId, String instanceId) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.name = name;
        this.consumerType = consumerType != null ? consumerType : ConsumerType.APPLICATION;
        this.machineIdentityId = machineIdentityId;
        this.instanceId = instanceId;
        this.status = ConsumerStatus.ACTIVE;
        this.supportsDynamicRefresh = true;
        this.requiresRestart = false;
        this.lastHeartbeatAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(UUID environmentId) {
        this.environmentId = environmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConsumerType getConsumerType() {
        return consumerType;
    }

    public void setConsumerType(ConsumerType consumerType) {
        this.consumerType = consumerType;
    }

    public UUID getMachineIdentityId() {
        return machineIdentityId;
    }

    public void setMachineIdentityId(UUID machineIdentityId) {
        this.machineIdentityId = machineIdentityId;
    }

    public ConsumerStatus getStatus() {
        return status;
    }

    public void setStatus(ConsumerStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getRuntimeFramework() {
        return runtimeFramework;
    }

    public void setRuntimeFramework(String runtimeFramework) {
        this.runtimeFramework = runtimeFramework;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public void setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public boolean isSupportsDynamicRefresh() {
        return supportsDynamicRefresh;
    }

    public void setSupportsDynamicRefresh(boolean supportsDynamicRefresh) {
        this.supportsDynamicRefresh = supportsDynamicRefresh;
    }

    public boolean isRequiresRestart() {
        return requiresRestart;
    }

    public void setRequiresRestart(boolean requiresRestart) {
        this.requiresRestart = requiresRestart;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.updatedAt = Instant.now();
    }

    public Instant getLastRefreshAcknowledgedAt() {
        return lastRefreshAcknowledgedAt;
    }

    public void setLastRefreshAcknowledgedAt(Instant lastRefreshAcknowledgedAt) {
        this.lastRefreshAcknowledgedAt = lastRefreshAcknowledgedAt;
    }

    public Integer getCurrentAcknowledgedVersion() {
        return currentAcknowledgedVersion;
    }

    public void setCurrentAcknowledgedVersion(Integer currentAcknowledgedVersion) {
        this.currentAcknowledgedVersion = currentAcknowledgedVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

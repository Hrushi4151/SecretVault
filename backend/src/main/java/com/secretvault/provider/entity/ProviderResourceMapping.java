package com.secretvault.provider.entity;

import com.secretvault.provider.model.ProviderResourceType;
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
 * Mapping between SecretVault project/environment and an external platform project/service/environment.
 */
@Entity
@Table(
        name = "provider_resource_mappings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_provider_map_ws_int_proj_env", columnNames = {"workspace_id", "integration_id", "project_id", "environment_id"})
        }
)
public class ProviderResourceMapping {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_resource_type", nullable = false, length = 64)
    private ProviderResourceType providerResourceType;

    @Column(name = "provider_resource_id", nullable = false, length = 255)
    private String providerResourceId;

    @Column(name = "provider_resource_name", nullable = false, length = 255)
    private String providerResourceName;

    @Column(name = "provider_environment", nullable = false, length = 64)
    private String providerEnvironment;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    private String metadataJson = "{}";

    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ProviderResourceMapping() {
    }

    public ProviderResourceMapping(
            UUID workspaceId,
            UUID integrationId,
            UUID projectId,
            UUID environmentId,
            ProviderResourceType providerResourceType,
            String providerResourceId,
            String providerResourceName,
            String providerEnvironment,
            String metadataJson,
            boolean syncEnabled
    ) {
        this.workspaceId = workspaceId;
        this.integrationId = integrationId;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.providerResourceType = providerResourceType;
        this.providerResourceId = providerResourceId;
        this.providerResourceName = providerResourceName;
        this.providerEnvironment = providerEnvironment;
        this.metadataJson = metadataJson != null ? metadataJson : "{}";
        this.syncEnabled = syncEnabled;
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

    public UUID getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(UUID integrationId) {
        this.integrationId = integrationId;
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

    public ProviderResourceType getProviderResourceType() {
        return providerResourceType;
    }

    public void setProviderResourceType(ProviderResourceType providerResourceType) {
        this.providerResourceType = providerResourceType;
    }

    public String getProviderResourceId() {
        return providerResourceId;
    }

    public void setProviderResourceId(String providerResourceId) {
        this.providerResourceId = providerResourceId;
    }

    public String getProviderResourceName() {
        return providerResourceName;
    }

    public void setProviderResourceName(String providerResourceName) {
        this.providerResourceName = providerResourceName;
    }

    public String getProviderEnvironment() {
        return providerEnvironment;
    }

    public void setProviderEnvironment(String providerEnvironment) {
        this.providerEnvironment = providerEnvironment;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

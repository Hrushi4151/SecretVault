package com.secretvault.repository.entity;

import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.model.RepositoryStatus;
import com.secretvault.repository.model.RepositoryVisibility;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_repo_workspace_provider", columnNames = {"workspace_id", "provider", "owner", "name"})
        }
)
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 64)
    private RepositoryProvider provider;

    @Column(name = "external_repository_id", length = 128)
    private String externalRepositoryId;

    @Column(name = "owner", nullable = false, length = 128)
    private String owner;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "default_branch", nullable = false, length = 128)
    private String defaultBranch = "main";

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private RepositoryVisibility visibility = RepositoryVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RepositoryStatus status = RepositoryStatus.ACTIVE;

    @Column(name = "clone_url", length = 1024)
    private String cloneUrl;

    @Column(name = "auth_credential_encrypted", columnDefinition = "TEXT")
    private String authCredentialEncrypted;

    @Column(name = "last_scan_at")
    private Instant lastScanAt;

    @Column(name = "last_successful_scan_at")
    private Instant lastSuccessfulScanAt;

    @Column(name = "last_commit_sha", length = 64)
    private String lastCommitSha;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RepositoryEntity() {}

    public RepositoryEntity(UUID workspaceId, RepositoryProvider provider, String owner, String name, RepositoryVisibility visibility) {
        this.workspaceId = workspaceId;
        this.provider = provider;
        this.owner = owner;
        this.name = name;
        this.visibility = visibility != null ? visibility : RepositoryVisibility.PRIVATE;
    }

    @PreUpdate
    public void onUpdate() {
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

    public RepositoryProvider getProvider() {
        return provider;
    }

    public void setProvider(RepositoryProvider provider) {
        this.provider = provider;
    }

    public String getExternalRepositoryId() {
        return externalRepositoryId;
    }

    public void setExternalRepositoryId(String externalRepositoryId) {
        this.externalRepositoryId = externalRepositoryId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public RepositoryVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(RepositoryVisibility visibility) {
        this.visibility = visibility;
    }

    public RepositoryStatus getStatus() {
        return status;
    }

    public void setStatus(RepositoryStatus status) {
        this.status = status;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    public String getAuthCredentialEncrypted() {
        return authCredentialEncrypted;
    }

    public void setAuthCredentialEncrypted(String authCredentialEncrypted) {
        this.authCredentialEncrypted = authCredentialEncrypted;
    }

    public Instant getLastScanAt() {
        return lastScanAt;
    }

    public void setLastScanAt(Instant lastScanAt) {
        this.lastScanAt = lastScanAt;
    }

    public Instant getLastSuccessfulScanAt() {
        return lastSuccessfulScanAt;
    }

    public void setLastSuccessfulScanAt(Instant lastSuccessfulScanAt) {
        this.lastSuccessfulScanAt = lastSuccessfulScanAt;
    }

    public String getLastCommitSha() {
        return lastCommitSha;
    }

    public void setLastCommitSha(String lastCommitSha) {
        this.lastCommitSha = lastCommitSha;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
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

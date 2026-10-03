package com.secretvault.repository.entity;

import com.secretvault.repository.model.RepoFindingSeverity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repository_security_policies")
public class RepositorySecurityPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "repository_id")
    private UUID repositoryId;

    @Column(name = "scan_on_push", nullable = false)
    private boolean scanOnPush = true;

    @Column(name = "scan_pr", nullable = false)
    private boolean scanPr = true;

    @Column(name = "scan_history", nullable = false)
    private boolean scanHistory = true;

    @Column(name = "entropy_detection_enabled", nullable = false)
    private boolean entropyDetectionEnabled = true;

    @Column(name = "live_validation_enabled", nullable = false)
    private boolean liveValidationEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_ci_severity", nullable = false, length = 32)
    private RepoFindingSeverity failCiSeverity = RepoFindingSeverity.HIGH;

    @Column(name = "max_history_depth", nullable = false)
    private int maxHistoryDepth = 1000;

    @Column(name = "max_file_size_bytes", nullable = false)
    private long maxFileSizeBytes = 5242880L;

    @Column(name = "excluded_paths_json", columnDefinition = "TEXT")
    private String excludedPathsJson;

    @Column(name = "allowed_detectors_json", columnDefinition = "TEXT")
    private String allowedDetectorsJson;

    public boolean isEntropyDetection() {
        return entropyDetectionEnabled;
    }

    public void setEntropyDetection(boolean entropyDetection) {
        this.entropyDetectionEnabled = entropyDetection;
    }

    public boolean isProviderValidation() {
        return liveValidationEnabled;
    }

    public void setProviderValidation(boolean providerValidation) {
        this.liveValidationEnabled = providerValidation;
    }

    public String getFailCiThreshold() {
        return failCiSeverity != null ? failCiSeverity.name() : "HIGH";
    }

    public void setFailCiThreshold(String threshold) {
        try {
            this.failCiSeverity = RepoFindingSeverity.valueOf(threshold.toUpperCase());
        } catch (Exception e) {
            this.failCiSeverity = RepoFindingSeverity.HIGH;
        }
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String getExcludedPaths() {
        return excludedPathsJson;
    }

    public void setExcludedPaths(String excludedPaths) {
        this.excludedPathsJson = excludedPaths;
    }

    public String getAllowedDetectors() {
        return allowedDetectorsJson;
    }

    public void setAllowedDetectors(String allowedDetectors) {
        this.allowedDetectorsJson = allowedDetectors;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RepositorySecurityPolicy() {}

    public RepositorySecurityPolicy(UUID workspaceId, UUID repositoryId) {
        this.workspaceId = workspaceId;
        this.repositoryId = repositoryId;
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

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(UUID repositoryId) {
        this.repositoryId = repositoryId;
    }

    public boolean isScanOnPush() {
        return scanOnPush;
    }

    public void setScanOnPush(boolean scanOnPush) {
        this.scanOnPush = scanOnPush;
    }

    public boolean isScanPr() {
        return scanPr;
    }

    public void setScanPr(boolean scanPr) {
        this.scanPr = scanPr;
    }

    public boolean isScanHistory() {
        return scanHistory;
    }

    public void setScanHistory(boolean scanHistory) {
        this.scanHistory = scanHistory;
    }

    public boolean isEntropyDetectionEnabled() {
        return entropyDetectionEnabled;
    }

    public void setEntropyDetectionEnabled(boolean entropyDetectionEnabled) {
        this.entropyDetectionEnabled = entropyDetectionEnabled;
    }

    public boolean isLiveValidationEnabled() {
        return liveValidationEnabled;
    }

    public void setLiveValidationEnabled(boolean liveValidationEnabled) {
        this.liveValidationEnabled = liveValidationEnabled;
    }

    public RepoFindingSeverity getFailCiSeverity() {
        return failCiSeverity;
    }

    public void setFailCiSeverity(RepoFindingSeverity failCiSeverity) {
        this.failCiSeverity = failCiSeverity;
    }

    public int getMaxHistoryDepth() {
        return maxHistoryDepth;
    }

    public void setMaxHistoryDepth(int maxHistoryDepth) {
        this.maxHistoryDepth = maxHistoryDepth;
    }

    public String getExcludedPathsJson() {
        return excludedPathsJson;
    }

    public void setExcludedPathsJson(String excludedPathsJson) {
        this.excludedPathsJson = excludedPathsJson;
    }

    public String getAllowedDetectorsJson() {
        return allowedDetectorsJson;
    }

    public void setAllowedDetectorsJson(String allowedDetectorsJson) {
        this.allowedDetectorsJson = allowedDetectorsJson;
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

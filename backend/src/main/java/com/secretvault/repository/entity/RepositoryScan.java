package com.secretvault.repository.entity;

import com.secretvault.repository.model.ScanStatus;
import com.secretvault.repository.model.ScanType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repository_scans")
public class RepositoryScan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false, length = 32)
    private ScanType scanType = ScanType.FULL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ScanStatus status = ScanStatus.QUEUED;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(name = "base_sha", length = 64)
    private String baseSha;

    @Column(name = "head_sha", length = 64)
    private String headSha;

    @Column(name = "branch", length = 128)
    private String branch;

    @Column(name = "files_scanned", nullable = false)
    private int filesScanned = 0;

    @Column(name = "commits_scanned", nullable = false)
    private int commitsScanned = 0;

    @Column(name = "findings_count", nullable = false)
    private int findingsCount = 0;

    @Column(name = "high_risk_count", nullable = false)
    private int highRiskCount = 0;

    @Column(name = "skipped_files_count", nullable = false)
    private int skippedFilesCount = 0;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(UUID triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public RepositoryScan() {}

    public RepositoryScan(UUID workspaceId, UUID repositoryId, ScanType scanType) {
        this.workspaceId = workspaceId;
        this.repositoryId = repositoryId;
        this.scanType = scanType;
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

    public ScanType getScanType() {
        return scanType;
    }

    public void setScanType(ScanType scanType) {
        this.scanType = scanType;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getBaseSha() {
        return baseSha;
    }

    public void setBaseSha(String baseSha) {
        this.baseSha = baseSha;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
    }

    public int getCommitsScanned() {
        return commitsScanned;
    }

    public void setCommitsScanned(int commitsScanned) {
        this.commitsScanned = commitsScanned;
    }

    public int getFindingsCount() {
        return findingsCount;
    }

    public void setFindingsCount(int findingsCount) {
        this.findingsCount = findingsCount;
    }

    public int getHighRiskCount() {
        return highRiskCount;
    }

    public void setHighRiskCount(int highRiskCount) {
        this.highRiskCount = highRiskCount;
    }

    public int getSkippedFilesCount() {
        return skippedFilesCount;
    }

    public void setSkippedFilesCount(int skippedFilesCount) {
        this.skippedFilesCount = skippedFilesCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

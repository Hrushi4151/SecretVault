package com.secretvault.repository.entity;

import com.secretvault.repository.model.*;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "secret_findings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_finding_ws_repo_fp_file", columnNames = {"workspace_id", "repository_id", "fingerprint", "file_path"})
        }
)
public class SecretFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "scan_id")
    private UUID scanId;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "detector_type", nullable = false, length = 64)
    private String detectorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "secret_type", nullable = false, length = 64)
    private SecretType secretType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private RepoFindingSeverity severity = RepoFindingSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 32)
    private RepoFindingConfidence confidence = RepoFindingConfidence.HIGH;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RepoFindingStatus status = RepoFindingStatus.DETECTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private RepositoryVisibility visibility = RepositoryVisibility.PRIVATE;

    @Column(name = "branch", length = 128)
    private String branch;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(name = "author", length = 255)
    private String author;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "column_number")
    private Integer columnNumber;

    @Column(name = "masked_evidence", nullable = false, length = 255)
    private String maskedEvidence;

    @Column(name = "entropy")
    private Double entropy;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 32)
    private ValidationStatus validationStatus = ValidationStatus.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "remediation_status", nullable = false, length = 32)
    private RemediationStatus remediationStatus = RemediationStatus.NONE;

    @Column(name = "secret_id")
    private UUID secretId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SecretFinding() {}

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

    public UUID getScanId() {
        return scanId;
    }

    public void setScanId(UUID scanId) {
        this.scanId = scanId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getDetectorType() {
        return detectorType;
    }

    public void setDetectorType(String detectorType) {
        this.detectorType = detectorType;
    }

    public SecretType getSecretType() {
        return secretType;
    }

    public void setSecretType(SecretType secretType) {
        this.secretType = secretType;
    }

    public RepoFindingSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(RepoFindingSeverity severity) {
        this.severity = severity;
    }

    public RepoFindingConfidence getConfidence() {
        return confidence;
    }

    public void setConfidence(RepoFindingConfidence confidence) {
        this.confidence = confidence;
    }

    public RepoFindingStatus getStatus() {
        return status;
    }

    public void setStatus(RepoFindingStatus status) {
        this.status = status;
    }

    public RepositoryVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(RepositoryVisibility visibility) {
        this.visibility = visibility;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Integer getColumnNumber() {
        return columnNumber;
    }

    public void setColumnNumber(Integer columnNumber) {
        this.columnNumber = columnNumber;
    }

    public String getMaskedEvidence() {
        return maskedEvidence;
    }

    public void setMaskedEvidence(String maskedEvidence) {
        this.maskedEvidence = maskedEvidence;
    }

    public String getMaskedValue() {
        return maskedEvidence;
    }

    public void setMaskedValue(String maskedValue) {
        this.maskedEvidence = maskedValue;
    }

    public UUID getSecretId() {
        return secretId;
    }

    public void setSecretId(UUID secretId) {
        this.secretId = secretId;
    }

    public UUID getMatchedSecretId() {
        return secretId;
    }

    public void setMatchedSecretId(UUID matchedSecretId) {
        this.secretId = matchedSecretId;
    }

    public Double getEntropy() {
        return entropy;
    }

    public void setEntropy(Double entropy) {
        this.entropy = entropy;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public RemediationStatus getRemediationStatus() {
        return remediationStatus;
    }

    public void setRemediationStatus(RemediationStatus remediationStatus) {
        this.remediationStatus = remediationStatus;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
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

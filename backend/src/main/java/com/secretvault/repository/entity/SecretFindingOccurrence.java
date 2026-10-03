package com.secretvault.repository.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "secret_finding_occurrences")
public class SecretFindingOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "finding_id", nullable = false)
    private UUID findingId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(name = "branch", length = 128)
    private String branch;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "column_number")
    private Integer columnNumber;

    @Column(name = "detector", nullable = false, length = 64)
    private String detector;

    @Column(name = "scan_id")
    private UUID scanId;

    @Column(name = "fingerprint", length = 128)
    private String fingerprint;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

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

    public Instant getFirstSeen() {
        return firstSeenAt;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeenAt = firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeenAt;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeenAt = lastSeen;
    }

    public SecretFindingOccurrence() {}

    public SecretFindingOccurrence(UUID findingId, UUID workspaceId, UUID repositoryId, String commitSha, String branch, String filePath, Integer lineNumber, Integer columnNumber, String detector) {
        this.findingId = findingId;
        this.workspaceId = workspaceId;
        this.repositoryId = repositoryId;
        this.commitSha = commitSha;
        this.branch = branch;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.detector = detector;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFindingId() {
        return findingId;
    }

    public void setFindingId(UUID findingId) {
        this.findingId = findingId;
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

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
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

    public String getDetector() {
        return detector;
    }

    public void setDetector(String detector) {
        this.detector = detector;
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
}

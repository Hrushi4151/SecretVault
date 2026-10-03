package com.secretvault.rotation.entity;

import com.secretvault.rotation.model.LeaseStatus;
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
 * Ephemeral, cryptographically authorized lease governing runtime access to a secret version.
 */
@Entity
@Table(name = "secret_leases")
public class SecretLease {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    @Column(name = "secret_version_number", nullable = false)
    private Integer secretVersionNumber;

    @Column(name = "machine_identity_id")
    private UUID machineIdentityId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "consumer_id")
    private UUID consumerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LeaseStatus status = LeaseStatus.ACTIVE;

    @Column(name = "ttl_seconds", nullable = false)
    private long ttlSeconds = 900; // 15 minutes

    @Column(name = "max_lifetime_seconds", nullable = false)
    private long maxLifetimeSeconds = 14400; // 4 hours max

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_renewed_at")
    private Instant lastRenewedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revocation_reason", columnDefinition = "text")
    private String revocationReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SecretLease() {
    }

    public SecretLease(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer secretVersionNumber,
            UUID machineIdentityId,
            UUID userId,
            UUID consumerId,
            long ttlSeconds,
            long maxLifetimeSeconds,
            String ipAddress,
            String userAgent
    ) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.secretId = secretId;
        this.secretVersionNumber = secretVersionNumber;
        this.machineIdentityId = machineIdentityId;
        this.userId = userId;
        this.consumerId = consumerId;
        this.ttlSeconds = ttlSeconds;
        this.maxLifetimeSeconds = maxLifetimeSeconds;
        this.issuedAt = Instant.now();
        this.expiresAt = this.issuedAt.plusSeconds(ttlSeconds);
        this.status = LeaseStatus.ACTIVE;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(this.expiresAt) || now.isAfter(this.issuedAt.plusSeconds(this.maxLifetimeSeconds));
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

    public UUID getSecretId() {
        return secretId;
    }

    public void setSecretId(UUID secretId) {
        this.secretId = secretId;
    }

    public Integer getSecretVersionNumber() {
        return secretVersionNumber;
    }

    public void setSecretVersionNumber(Integer secretVersionNumber) {
        this.secretVersionNumber = secretVersionNumber;
    }

    public UUID getMachineIdentityId() {
        return machineIdentityId;
    }

    public void setMachineIdentityId(UUID machineIdentityId) {
        this.machineIdentityId = machineIdentityId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(UUID consumerId) {
        this.consumerId = consumerId;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public void setStatus(LeaseStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getMaxLifetimeSeconds() {
        return maxLifetimeSeconds;
    }

    public void setMaxLifetimeSeconds(long maxLifetimeSeconds) {
        this.maxLifetimeSeconds = maxLifetimeSeconds;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public Instant getLastRenewedAt() {
        return lastRenewedAt;
    }

    public void setLastRenewedAt(Instant lastRenewedAt) {
        this.lastRenewedAt = lastRenewedAt;
        this.updatedAt = Instant.now();
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(UUID revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
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

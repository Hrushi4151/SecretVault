package com.secretvault.oidc.entity;

import com.secretvault.oidc.model.OidcProviderStatus;
import com.secretvault.oidc.model.OidcProviderType;
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

@Entity
@Table(name = "oidc_providers")
public class OidcProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "issuer", nullable = false, length = 512)
    private String issuer;

    @Column(name = "discovery_url", length = 512)
    private String discoveryUrl;

    @Column(name = "jwks_url", length = 512)
    private String jwksUrl;

    @Column(name = "audience", nullable = false, length = 256)
    private String audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private OidcProviderType providerType = OidcProviderType.GENERIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OidcProviderStatus status = OidcProviderStatus.ACTIVE;

    @Column(name = "allowed_algorithms", nullable = false, length = 256)
    private String allowedAlgorithms = "RS256,ES256";

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_jwks_refresh_at")
    private Instant lastJwksRefreshAt;

    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

    @Column(name = "success_count", nullable = false)
    private long successCount = 0;

    @Column(name = "failure_count", nullable = false)
    private long failureCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public OidcProvider() {
    }

    public OidcProvider(UUID workspaceId, String name, String issuer, String discoveryUrl,
                        String jwksUrl, String audience, OidcProviderType providerType,
                        String allowedAlgorithms, UUID createdBy) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.issuer = issuer;
        this.discoveryUrl = discoveryUrl;
        this.jwksUrl = jwksUrl;
        this.audience = audience;
        this.providerType = providerType != null ? providerType : OidcProviderType.GENERIC;
        this.allowedAlgorithms = allowedAlgorithms != null ? allowedAlgorithms : "RS256,ES256";
        this.status = OidcProviderStatus.ACTIVE;
        this.createdBy = createdBy;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getDiscoveryUrl() {
        return discoveryUrl;
    }

    public void setDiscoveryUrl(String discoveryUrl) {
        this.discoveryUrl = discoveryUrl;
    }

    public String getJwksUrl() {
        return jwksUrl;
    }

    public void setJwksUrl(String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public OidcProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(OidcProviderType providerType) {
        this.providerType = providerType;
    }

    public OidcProviderStatus getStatus() {
        return status;
    }

    public void setStatus(OidcProviderStatus status) {
        this.status = status;
    }

    public String getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    public void setAllowedAlgorithms(String allowedAlgorithms) {
        this.allowedAlgorithms = allowedAlgorithms;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getLastJwksRefreshAt() {
        return lastJwksRefreshAt;
    }

    public void setLastJwksRefreshAt(Instant lastJwksRefreshAt) {
        this.lastJwksRefreshAt = lastJwksRefreshAt;
    }

    public Instant getLastAuthenticatedAt() {
        return lastAuthenticatedAt;
    }

    public void setLastAuthenticatedAt(Instant lastAuthenticatedAt) {
        this.lastAuthenticatedAt = lastAuthenticatedAt;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(long failureCount) {
        this.failureCount = failureCount;
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

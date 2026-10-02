package com.secretvault.provider.entity;

import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderType;
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
 * External platform provider integration entity storing envelope-encrypted credentials and config.
 */
@Entity
@Table(
        name = "provider_integrations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_provider_int_ws_name", columnNames = {"workspace_id", "display_name"})
        }
)
public class ProviderIntegration {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 64)
    private ProviderType providerType;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IntegrationStatus status = IntegrationStatus.VALIDATING;

    @Column(name = "configuration_json", nullable = false, columnDefinition = "TEXT")
    private String configurationJson = "{}";

    @Column(name = "encrypted_credential_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedCredentialToken;

    @Column(name = "encrypted_dek", nullable = false, columnDefinition = "TEXT")
    private String encryptedDek;

    @Column(name = "iv", nullable = false, columnDefinition = "TEXT")
    private String iv;

    @Column(name = "auth_tag", nullable = false, columnDefinition = "TEXT")
    private String authTag;

    @Column(name = "key_reference", nullable = false, length = 128)
    private String keyReference;

    @Column(name = "redacted_credential_hint", nullable = false, length = 64)
    private String redactedCredentialHint;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 255)
    private String lastErrorMessage;

    public ProviderIntegration() {
    }

    public ProviderIntegration(
            UUID workspaceId,
            ProviderType providerType,
            String displayName,
            IntegrationStatus status,
            String configurationJson,
            String encryptedCredentialToken,
            String encryptedDek,
            String iv,
            String authTag,
            String keyReference,
            String redactedCredentialHint,
            UUID createdBy
    ) {
        this.workspaceId = workspaceId;
        this.providerType = providerType;
        this.displayName = displayName;
        this.status = status != null ? status : IntegrationStatus.VALIDATING;
        this.configurationJson = configurationJson != null ? configurationJson : "{}";
        this.encryptedCredentialToken = encryptedCredentialToken;
        this.encryptedDek = encryptedDek;
        this.iv = iv;
        this.authTag = authTag;
        this.keyReference = keyReference;
        this.redactedCredentialHint = redactedCredentialHint;
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

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public IntegrationStatus getStatus() {
        return status;
    }

    public void setStatus(IntegrationStatus status) {
        this.status = status;
    }

    public String getConfigurationJson() {
        return configurationJson;
    }

    public void setConfigurationJson(String configurationJson) {
        this.configurationJson = configurationJson;
    }

    public String getEncryptedCredentialToken() {
        return encryptedCredentialToken;
    }

    public void setEncryptedCredentialToken(String encryptedCredentialToken) {
        this.encryptedCredentialToken = encryptedCredentialToken;
    }

    public String getEncryptedDek() {
        return encryptedDek;
    }

    public void setEncryptedDek(String encryptedDek) {
        this.encryptedDek = encryptedDek;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getAuthTag() {
        return authTag;
    }

    public void setAuthTag(String authTag) {
        this.authTag = authTag;
    }

    public String getKeyReference() {
        return keyReference;
    }

    public void setKeyReference(String keyReference) {
        this.keyReference = keyReference;
    }

    public String getRedactedCredentialHint() {
        return redactedCredentialHint;
    }

    public void setRedactedCredentialHint(String redactedCredentialHint) {
        this.redactedCredentialHint = redactedCredentialHint;
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

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Instant lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public Instant getLastErrorAt() {
        return lastErrorAt;
    }

    public void setLastErrorAt(Instant lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }
}

package com.secretvault.secret.reveal.entity;

import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity for custom Secret Reveal Policies.
 * Allows granular overrides at Workspace, Project, Environment, or Secret level.
 */
@Entity
@Table(name = "secret_reveal_policies")
public class SecretRevealPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private PrivilegedPolicyScope scopeType = PrivilegedPolicyScope.WORKSPACE;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "secret_id")
    private UUID secretId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_level", nullable = false, length = 32)
    private RevealPolicyLevel policyLevel = RevealPolicyLevel.DEFAULT;

    @Column(name = "require_step_up", nullable = false)
    private boolean requireStepUp = false;

    @Column(name = "allowed_step_up_factors", nullable = false)
    private String allowedStepUpFactors = "PASSWORD,TOTP,RECOVERY_CODE,WEBAUTHN";

    @Column(name = "require_webauthn_only", nullable = false)
    private boolean requireWebAuthnOnly = false;

    @Column(name = "require_reason", nullable = false)
    private boolean requireReason = false;

    @Column(name = "min_reason_length", nullable = false)
    private int minReasonLength = 10;

    @Column(name = "max_reason_length", nullable = false)
    private int maxReasonLength = 500;

    @Column(name = "require_privileged_or_jit", nullable = false)
    private boolean requirePrivilegedOrJit = false;

    @Column(name = "max_display_duration_seconds", nullable = false)
    private int maxDisplayDurationSeconds = 60;

    @Column(name = "copy_allowed", nullable = false)
    private boolean copyAllowed = true;

    @Column(name = "clipboard_timeout_seconds", nullable = false)
    private int clipboardTimeoutSeconds = 15;

    @Column(name = "bulk_reveal_allowed", nullable = false)
    private boolean bulkRevealAllowed = false;

    @Column(name = "max_bulk_count", nullable = false)
    private int maxBulkCount = 50;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute = 30;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version")
    private Long version = 0L;

    public SecretRevealPolicy() {
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

    public PrivilegedPolicyScope getScopeType() {
        return scopeType;
    }

    public void setScopeType(PrivilegedPolicyScope scopeType) {
        this.scopeType = scopeType;
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

    public RevealPolicyLevel getPolicyLevel() {
        return policyLevel;
    }

    public void setPolicyLevel(RevealPolicyLevel policyLevel) {
        this.policyLevel = policyLevel;
    }

    public boolean isRequireStepUp() {
        return requireStepUp;
    }

    public void setRequireStepUp(boolean requireStepUp) {
        this.requireStepUp = requireStepUp;
    }

    public String getAllowedStepUpFactors() {
        return allowedStepUpFactors;
    }

    public void setAllowedStepUpFactors(String allowedStepUpFactors) {
        this.allowedStepUpFactors = allowedStepUpFactors;
    }

    public boolean isRequireWebAuthnOnly() {
        return requireWebAuthnOnly;
    }

    public void setRequireWebAuthnOnly(boolean requireWebAuthnOnly) {
        this.requireWebAuthnOnly = requireWebAuthnOnly;
    }

    public boolean isRequireReason() {
        return requireReason;
    }

    public void setRequireReason(boolean requireReason) {
        this.requireReason = requireReason;
    }

    public int getMinReasonLength() {
        return minReasonLength;
    }

    public void setMinReasonLength(int minReasonLength) {
        this.minReasonLength = minReasonLength;
    }

    public int getMaxReasonLength() {
        return maxReasonLength;
    }

    public void setMaxReasonLength(int maxReasonLength) {
        this.maxReasonLength = maxReasonLength;
    }

    public boolean isRequirePrivilegedOrJit() {
        return requirePrivilegedOrJit;
    }

    public void setRequirePrivilegedOrJit(boolean requirePrivilegedOrJit) {
        this.requirePrivilegedOrJit = requirePrivilegedOrJit;
    }

    public int getMaxDisplayDurationSeconds() {
        return maxDisplayDurationSeconds;
    }

    public void setMaxDisplayDurationSeconds(int maxDisplayDurationSeconds) {
        this.maxDisplayDurationSeconds = maxDisplayDurationSeconds;
    }

    public boolean isCopyAllowed() {
        return copyAllowed;
    }

    public void setCopyAllowed(boolean copyAllowed) {
        this.copyAllowed = copyAllowed;
    }

    public int getClipboardTimeoutSeconds() {
        return clipboardTimeoutSeconds;
    }

    public void setClipboardTimeoutSeconds(int clipboardTimeoutSeconds) {
        this.clipboardTimeoutSeconds = clipboardTimeoutSeconds;
    }

    public boolean isBulkRevealAllowed() {
        return bulkRevealAllowed;
    }

    public void setBulkRevealAllowed(boolean bulkRevealAllowed) {
        this.bulkRevealAllowed = bulkRevealAllowed;
    }

    public int getMaxBulkCount() {
        return maxBulkCount;
    }

    public void setMaxBulkCount(int maxBulkCount) {
        this.maxBulkCount = maxBulkCount;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecretRevealPolicy that = (SecretRevealPolicy) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

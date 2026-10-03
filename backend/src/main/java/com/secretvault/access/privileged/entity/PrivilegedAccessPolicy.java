package com.secretvault.access.privileged.entity;

import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Privileged Access Policy Entity.
 * Defines governance, dual-approval quorum, justification, and step-up rules for sensitive operations.
 */
@Entity
@Table(name = "privileged_access_policies")
public class PrivilegedAccessPolicy {

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
    @Column(name = "action", length = 64)
    private PrivilegedAction action;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "require_step_up", nullable = false)
    private boolean requireStepUp = true;

    @Column(name = "allowed_step_up_factors", nullable = false)
    private String allowedStepUpFactors = "PASSWORD,TOTP,RECOVERY_CODE,WEBAUTHN";

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval = true;

    @Column(name = "approval_quorum", nullable = false)
    private int approvalQuorum = 1;

    @Column(name = "prevent_self_approval", nullable = false)
    private boolean preventSelfApproval = true;

    @Column(name = "require_justification", nullable = false)
    private boolean requireJustification = true;

    @Column(name = "max_duration_minutes", nullable = false)
    private int maxDurationMinutes = 60;

    @Column(name = "production_protected", nullable = false)
    private boolean productionProtected = false;

    @Column(name = "break_glass_allowed", nullable = false)
    private boolean breakGlassAllowed = true;

    @Column(name = "break_glass_requires_reason", nullable = false)
    private boolean breakGlassRequiresReason = true;

    @Column(name = "break_glass_requires_audit", nullable = false)
    private boolean breakGlassRequiresAudit = true;

    @Column(name = "emergency_duration_limit_minutes", nullable = false)
    private int emergencyDurationLimitMinutes = 30;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public PrivilegedAccessPolicy() {
    }

    public PrivilegedAccessPolicy(
            UUID workspaceId,
            PrivilegedPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            PrivilegedAction action,
            boolean requireApproval,
            int approvalQuorum,
            boolean preventSelfApproval,
            boolean requireStepUp,
            int maxDurationMinutes
    ) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.scopeType = scopeType != null ? scopeType : PrivilegedPolicyScope.WORKSPACE;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.secretId = secretId;
        this.action = action;
        this.requireApproval = requireApproval;
        this.approvalQuorum = Math.max(1, approvalQuorum);
        this.preventSelfApproval = preventSelfApproval;
        this.requireStepUp = requireStepUp;
        this.maxDurationMinutes = maxDurationMinutes > 0 ? maxDurationMinutes : 60;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public PrivilegedAction getAction() {
        return action;
    }

    public void setAction(PrivilegedAction action) {
        this.action = action;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public void setRequireApproval(boolean requireApproval) {
        this.requireApproval = requireApproval;
    }

    public int getApprovalQuorum() {
        return approvalQuorum;
    }

    public void setApprovalQuorum(int approvalQuorum) {
        this.approvalQuorum = approvalQuorum;
    }

    public boolean isPreventSelfApproval() {
        return preventSelfApproval;
    }

    public void setPreventSelfApproval(boolean preventSelfApproval) {
        this.preventSelfApproval = preventSelfApproval;
    }

    public boolean isRequireJustification() {
        return requireJustification;
    }

    public void setRequireJustification(boolean requireJustification) {
        this.requireJustification = requireJustification;
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public void setMaxDurationMinutes(int maxDurationMinutes) {
        this.maxDurationMinutes = maxDurationMinutes;
    }

    public boolean isProductionProtected() {
        return productionProtected;
    }

    public void setProductionProtected(boolean productionProtected) {
        this.productionProtected = productionProtected;
    }

    public boolean isBreakGlassAllowed() {
        return breakGlassAllowed;
    }

    public void setBreakGlassAllowed(boolean breakGlassAllowed) {
        this.breakGlassAllowed = breakGlassAllowed;
    }

    public boolean isBreakGlassRequiresReason() {
        return breakGlassRequiresReason;
    }

    public void setBreakGlassRequiresReason(boolean breakGlassRequiresReason) {
        this.breakGlassRequiresReason = breakGlassRequiresReason;
    }

    public boolean isBreakGlassRequiresAudit() {
        return breakGlassRequiresAudit;
    }

    public void setBreakGlassRequiresAudit(boolean breakGlassRequiresAudit) {
        this.breakGlassRequiresAudit = breakGlassRequiresAudit;
    }

    public int getEmergencyDurationLimitMinutes() {
        return emergencyDurationLimitMinutes;
    }

    public void setEmergencyDurationLimitMinutes(int emergencyDurationLimitMinutes) {
        this.emergencyDurationLimitMinutes = emergencyDurationLimitMinutes;
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
}

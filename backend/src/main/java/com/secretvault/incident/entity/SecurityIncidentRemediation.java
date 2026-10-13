package com.secretvault.incident.entity;

import com.secretvault.incident.dto.IncidentDtos.RemediationPlaybookAction;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_incident_remediations")
public class SecurityIncidentRemediation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private RemediationPlaybookAction action;

    @Column(name = "target_secret_id")
    private UUID targetSecretId;

    @Column(name = "target_machine_identity_id")
    private UUID targetMachineIdentityId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IncidentRemediationStatus status = IncidentRemediationStatus.QUEUED;

    @Column(name = "worker_id", length = 128)
    private String workerId;

    @Column(name = "claim_token", length = 128)
    private String claimToken;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "execution_details", columnDefinition = "TEXT")
    private String executionDetails;

    @Column(name = "requested_effects_json", columnDefinition = "TEXT")
    private String requestedEffectsJson;

    @Column(name = "verified_effects_json", columnDefinition = "TEXT")
    private String verifiedEffectsJson;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SecurityIncidentRemediation() {}

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }
    public UUID getApprovalId() { return approvalId; }
    public void setApprovalId(UUID approvalId) { this.approvalId = approvalId; }
    public RemediationPlaybookAction getAction() { return action; }
    public void setAction(RemediationPlaybookAction action) { this.action = action; }
    public UUID getTargetSecretId() { return targetSecretId; }
    public void setTargetSecretId(UUID targetSecretId) { this.targetSecretId = targetSecretId; }
    public UUID getTargetMachineIdentityId() { return targetMachineIdentityId; }
    public void setTargetMachineIdentityId(UUID targetMachineIdentityId) { this.targetMachineIdentityId = targetMachineIdentityId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public IncidentRemediationStatus getStatus() { return status; }
    public void setStatus(IncidentRemediationStatus status) { this.status = status; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public String getClaimToken() { return claimToken; }
    public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getExecutionDetails() { return executionDetails; }
    public void setExecutionDetails(String executionDetails) { this.executionDetails = executionDetails; }
    public String getRequestedEffectsJson() { return requestedEffectsJson; }
    public void setRequestedEffectsJson(String requestedEffectsJson) { this.requestedEffectsJson = requestedEffectsJson; }
    public String getVerifiedEffectsJson() { return verifiedEffectsJson; }
    public void setVerifiedEffectsJson(String verifiedEffectsJson) { this.verifiedEffectsJson = verifiedEffectsJson; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

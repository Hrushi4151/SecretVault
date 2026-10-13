package com.secretvault.incident.entity;

import com.secretvault.incident.dto.IncidentDtos.RemediationPlaybookAction;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_incident_approvals")
public class SecurityIncidentApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private RemediationPlaybookAction action;

    @Column(name = "target_secret_id")
    private UUID targetSecretId;

    @Column(name = "target_machine_identity_id")
    private UUID targetMachineIdentityId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "action_payload_hash", nullable = false, length = 64)
    private String actionPayloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IncidentApprovalStatus status = IncidentApprovalStatus.PENDING;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SecurityIncidentApproval() {}

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
    public RemediationPlaybookAction getAction() { return action; }
    public void setAction(RemediationPlaybookAction action) { this.action = action; }
    public UUID getTargetSecretId() { return targetSecretId; }
    public void setTargetSecretId(UUID targetSecretId) { this.targetSecretId = targetSecretId; }
    public UUID getTargetMachineIdentityId() { return targetMachineIdentityId; }
    public void setTargetMachineIdentityId(UUID targetMachineIdentityId) { this.targetMachineIdentityId = targetMachineIdentityId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getActionPayloadHash() { return actionPayloadHash; }
    public void setActionPayloadHash(String actionPayloadHash) { this.actionPayloadHash = actionPayloadHash; }
    public IncidentApprovalStatus getStatus() { return status; }
    public void setStatus(IncidentApprovalStatus status) { this.status = status; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

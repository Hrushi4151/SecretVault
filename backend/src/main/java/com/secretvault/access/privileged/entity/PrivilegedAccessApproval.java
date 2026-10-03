package com.secretvault.access.privileged.entity;

import com.secretvault.access.privileged.model.ApprovalDecision;
import com.secretvault.auth.stepup.model.StepUpFactor;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Privileged Access Approval Entity.
 * Records individual, immutable approver decisions in a four-eyes / quorum governance workflow.
 */
@Entity
@Table(
        name = "privileged_access_approvals",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_priv_approval_request_approver", columnNames = {"request_id", "approver_id"})
        }
)
public class PrivilegedAccessApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    private ApprovalDecision decision;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_up_factor", length = 64)
    private StepUpFactor stepUpFactor;

    @Column(name = "step_up_proof_token")
    private String stepUpProofToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PrivilegedAccessApproval() {
    }

    public PrivilegedAccessApproval(
            UUID requestId,
            UUID approverId,
            ApprovalDecision decision,
            String notes,
            StepUpFactor stepUpFactor,
            String stepUpProofToken
    ) {
        this.requestId = Objects.requireNonNull(requestId, "requestId must not be null");
        this.approverId = Objects.requireNonNull(approverId, "approverId must not be null");
        this.decision = Objects.requireNonNull(decision, "decision must not be null");
        this.notes = notes;
        this.stepUpFactor = stepUpFactor;
        this.stepUpProofToken = stepUpProofToken;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public void setApproverId(UUID approverId) {
        this.approverId = approverId;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public void setDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public StepUpFactor getStepUpFactor() {
        return stepUpFactor;
    }

    public void setStepUpFactor(StepUpFactor stepUpFactor) {
        this.stepUpFactor = stepUpFactor;
    }

    public String getStepUpProofToken() {
        return stepUpProofToken;
    }

    public void setStepUpProofToken(String stepUpProofToken) {
        this.stepUpProofToken = stepUpProofToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

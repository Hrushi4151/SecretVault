package com.secretvault.automation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_executions")
public class AutomationExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion = 1;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "trigger_event_type", nullable = false, length = 128)
    private String triggerEventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AutomationExecutionStatus status = AutomationExecutionStatus.COMPLETED;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = false;

    @Column(name = "evaluation_result_json", columnDefinition = "TEXT")
    private String evaluationResultJson;

    @Column(name = "actions_executed_json", columnDefinition = "TEXT")
    private String actionsExecutedJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "execution_depth", nullable = false)
    private int executionDepth = 0;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs = 0;

    @Column(name = "causation_id", length = 128)
    private String causationId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AutomationExecution() {}

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getTriggerEventType() { return triggerEventType; }
    public void setTriggerEventType(String triggerEventType) { this.triggerEventType = triggerEventType; }
    public AutomationExecutionStatus getStatus() { return status; }
    public void setStatus(AutomationExecutionStatus status) { this.status = status; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public String getEvaluationResultJson() { return evaluationResultJson; }
    public void setEvaluationResultJson(String evaluationResultJson) { this.evaluationResultJson = evaluationResultJson; }
    public String getActionsExecutedJson() { return actionsExecutedJson; }
    public void setActionsExecutedJson(String actionsExecutedJson) { this.actionsExecutedJson = actionsExecutedJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getExecutionDepth() { return executionDepth; }
    public void setExecutionDepth(int executionDepth) { this.executionDepth = executionDepth; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getCausationId() { return causationId; }
    public void setCausationId(String causationId) { this.causationId = causationId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public Instant getCreatedAt() { return createdAt; }
}

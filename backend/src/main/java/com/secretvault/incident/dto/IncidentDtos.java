package com.secretvault.incident.dto;

import com.secretvault.incident.entity.IncidentApprovalStatus;
import com.secretvault.incident.entity.IncidentRemediationStatus;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncidentApproval;
import com.secretvault.incident.entity.SecurityIncidentRemediation;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class IncidentDtos {

    public enum RemediationPlaybookAction {
        ROTATE_SECRET,
        REVOKE_SECRET_LEASES,
        DISABLE_MACHINE_IDENTITY,
        CONTAIN_INCIDENT,
        NOTIFY_RESPONDERS
    }

    public record IncidentRemediationRequest(
            @NotNull(message = "Remediation action is required")
            RemediationPlaybookAction action,
            UUID targetSecretId,
            UUID targetMachineIdentityId,
            String notes,
            boolean dryRun,
            boolean requireApproval,
            UUID approvalId,
            boolean async
    ) {
        public IncidentRemediationRequest(
                RemediationPlaybookAction action,
                UUID targetSecretId,
                UUID targetMachineIdentityId,
                String notes,
                boolean dryRun
        ) {
            this(action, targetSecretId, targetMachineIdentityId, notes, dryRun, false, null, false);
        }
    }

    public record IncidentRemediationResult(
            RemediationPlaybookAction action,
            boolean dryRun,
            boolean executed,
            String status,
            String details,
            IncidentStatus updatedIncidentStatus,
            Map<String, Object> metadata,
            UUID approvalId,
            UUID executionId
    ) {
        public IncidentRemediationResult(
                RemediationPlaybookAction action,
                boolean dryRun,
                boolean executed,
                String status,
                String details,
                IncidentStatus updatedIncidentStatus,
                Map<String, Object> metadata
        ) {
            this(action, dryRun, executed, status, details, updatedIncidentStatus, metadata, null, null);
        }
    }

    public record IncidentApprovalDecisionRequest(
            boolean approve,
            String reason
    ) {}

    public record IncidentApprovalResponse(
            UUID id,
            UUID workspaceId,
            UUID incidentId,
            RemediationPlaybookAction action,
            UUID targetSecretId,
            UUID targetMachineIdentityId,
            String notes,
            String actionPayloadHash,
            IncidentApprovalStatus status,
            UUID requestedBy,
            UUID decidedBy,
            Instant decidedAt,
            String decisionReason,
            Instant expiresAt,
            Instant createdAt
    ) {
        public static IncidentApprovalResponse fromEntity(SecurityIncidentApproval a) {
            return new IncidentApprovalResponse(
                    a.getId(),
                    a.getWorkspaceId(),
                    a.getIncidentId(),
                    a.getAction(),
                    a.getTargetSecretId(),
                    a.getTargetMachineIdentityId(),
                    a.getNotes(),
                    a.getActionPayloadHash(),
                    a.getStatus(),
                    a.getRequestedBy(),
                    a.getDecidedBy(),
                    a.getDecidedAt(),
                    a.getDecisionReason(),
                    a.getExpiresAt(),
                    a.getCreatedAt()
            );
        }
    }

    public record IncidentRemediationExecutionResponse(
            UUID id,
            UUID workspaceId,
            UUID incidentId,
            UUID approvalId,
            RemediationPlaybookAction action,
            UUID targetSecretId,
            UUID targetMachineIdentityId,
            String notes,
            UUID actorId,
            IncidentRemediationStatus status,
            String workerId,
            int attemptCount,
            int maxAttempts,
            String lastError,
            String executionDetails,
            String requestedEffectsJson,
            String verifiedEffectsJson,
            Instant completedAt,
            Instant createdAt
    ) {
        public static IncidentRemediationExecutionResponse fromEntity(SecurityIncidentRemediation r) {
            return new IncidentRemediationExecutionResponse(
                    r.getId(),
                    r.getWorkspaceId(),
                    r.getIncidentId(),
                    r.getApprovalId(),
                    r.getAction(),
                    r.getTargetSecretId(),
                    r.getTargetMachineIdentityId(),
                    r.getNotes(),
                    r.getActorId(),
                    r.getStatus(),
                    r.getWorkerId(),
                    r.getAttemptCount(),
                    r.getMaxAttempts(),
                    r.getLastError(),
                    r.getExecutionDetails(),
                    r.getRequestedEffectsJson(),
                    r.getVerifiedEffectsJson(),
                    r.getCompletedAt(),
                    r.getCreatedAt()
            );
        }
    }
}

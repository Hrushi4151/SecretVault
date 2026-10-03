package com.secretvault.access.privileged.dto;

import com.secretvault.access.privileged.entity.PrivilegedAccessApproval;
import com.secretvault.access.privileged.model.ApprovalDecision;
import com.secretvault.auth.stepup.model.StepUpFactor;

import java.time.Instant;
import java.util.UUID;

public record PrivilegedAccessApprovalResponse(
        UUID id,
        UUID requestId,
        UUID approverId,
        String approverEmail,
        String approverName,
        ApprovalDecision decision,
        String notes,
        StepUpFactor stepUpFactor,
        Instant createdAt
) {
    public static PrivilegedAccessApprovalResponse fromEntity(
            PrivilegedAccessApproval approval,
            String approverEmail,
            String approverName
    ) {
        return new PrivilegedAccessApprovalResponse(
                approval.getId(),
                approval.getRequestId(),
                approval.getApproverId(),
                approverEmail,
                approverName,
                approval.getDecision(),
                approval.getNotes(),
                approval.getStepUpFactor(),
                approval.getCreatedAt()
        );
    }
}

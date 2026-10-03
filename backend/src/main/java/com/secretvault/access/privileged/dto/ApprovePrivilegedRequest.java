package com.secretvault.access.privileged.dto;

import com.secretvault.access.privileged.model.ApprovalDecision;

public record ApprovePrivilegedRequest(
        ApprovalDecision decision,
        String notes,
        String stepUpProof
) {
    public ApprovePrivilegedRequest {
        if (decision == null) {
            decision = ApprovalDecision.APPROVED;
        }
    }
}

package com.secretvault.access.privileged.dto;

import com.secretvault.access.privileged.entity.PrivilegedAccessRequest;
import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.access.privileged.model.PrivilegedRequestStatus;
import com.secretvault.auth.stepup.model.StepUpFactor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrivilegedAccessRequestResponse(
        UUID id,
        UUID workspaceId,
        UUID requesterId,
        String requesterEmail,
        String requesterName,
        UUID targetUserId,
        String targetUserEmail,
        String targetUserName,
        PrivilegedAction action,
        PrivilegedPolicyScope scopeType,
        UUID projectId,
        String projectName,
        UUID environmentId,
        String environmentName,
        UUID secretId,
        String secretName,
        String requestedPermissions,
        String justification,
        int durationMinutes,
        PrivilegedRequestStatus status,
        boolean isBreakGlass,
        int requiredQuorum,
        int currentApprovalsCount,
        StepUpFactor stepUpFactor,
        Instant approvedAt,
        Instant expiresAt,
        Instant executedAt,
        Instant revokedAt,
        UUID revokedBy,
        String revocationReason,
        String rejectionReason,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        List<PrivilegedAccessApprovalResponse> approvals,
        boolean canApprove,
        boolean canCancel,
        boolean canExecute,
        boolean canRevoke
) {
    public static PrivilegedAccessRequestResponse of(
            PrivilegedAccessRequest request,
            String requesterEmail,
            String requesterName,
            String targetUserEmail,
            String targetUserName,
            String projectName,
            String environmentName,
            String secretName,
            List<PrivilegedAccessApprovalResponse> approvals,
            boolean canApprove,
            boolean canCancel,
            boolean canExecute,
            boolean canRevoke
    ) {
        return new PrivilegedAccessRequestResponse(
                request.getId(),
                request.getWorkspaceId(),
                request.getRequesterId(),
                requesterEmail,
                requesterName,
                request.getTargetUserId(),
                targetUserEmail,
                targetUserName,
                request.getAction(),
                request.getScopeType(),
                request.getProjectId(),
                projectName,
                request.getEnvironmentId(),
                environmentName,
                request.getSecretId(),
                secretName,
                request.getRequestedPermissions(),
                request.getJustification(),
                request.getDurationMinutes(),
                request.getStatus(),
                request.isBreakGlass(),
                request.getRequiredQuorum(),
                request.getCurrentApprovalsCount(),
                request.getStepUpFactor(),
                request.getApprovedAt(),
                request.getExpiresAt(),
                request.getExecutedAt(),
                request.getRevokedAt(),
                request.getRevokedBy(),
                request.getRevocationReason(),
                request.getRejectionReason(),
                request.getCancellationReason(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                approvals != null ? approvals : List.of(),
                canApprove,
                canCancel,
                canExecute,
                canRevoke
        );
    }
}

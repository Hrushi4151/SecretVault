package com.secretvault.access.jit.dto;

import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.model.AccessPermission;

import java.time.Instant;
import java.util.UUID;

public record JitAccessRequestResponse(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String userEmail,
        String userFullName,
        UUID projectId,
        String projectName,
        UUID environmentId,
        String environmentName,
        boolean isProtectedEnvironment,
        UUID secretId,
        String secretKey,
        AccessPermission requestedPermission,
        String permissionCode,
        int durationMinutes,
        String reason,
        JitStatus status,
        UUID approverId,
        String approverEmail,
        String reviewerNotes,
        Instant approvedAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static JitAccessRequestResponse fromEntity(
            JitAccessRequest request,
            String userEmail,
            String userFullName,
            String projectName,
            String environmentName,
            boolean isProtected,
            String secretKey,
            String approverEmail
    ) {
        boolean active = request.isCurrentlyActive();
        return new JitAccessRequestResponse(
                request.getId(),
                request.getWorkspaceId(),
                request.getUserId(),
                userEmail,
                userFullName,
                request.getProjectId(),
                projectName,
                request.getEnvironmentId(),
                environmentName,
                isProtected,
                request.getSecretId(),
                secretKey,
                request.getRequestedPermission(),
                request.getRequestedPermission().getCode(),
                request.getDurationMinutes(),
                request.getReason(),
                request.getStatus(),
                request.getApproverId(),
                approverEmail,
                request.getReviewerNotes(),
                request.getApprovedAt(),
                request.getExpiresAt(),
                request.getRevokedAt(),
                active,
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}

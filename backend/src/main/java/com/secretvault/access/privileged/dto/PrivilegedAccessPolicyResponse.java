package com.secretvault.access.privileged.dto;

import com.secretvault.access.privileged.entity.PrivilegedAccessPolicy;
import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;

import java.time.Instant;
import java.util.UUID;

public record PrivilegedAccessPolicyResponse(
        UUID id,
        UUID workspaceId,
        PrivilegedPolicyScope scopeType,
        UUID projectId,
        String projectName,
        UUID environmentId,
        String environmentName,
        UUID secretId,
        String secretName,
        PrivilegedAction action,
        boolean enabled,
        boolean requireStepUp,
        String allowedStepUpFactors,
        boolean requireApproval,
        int approvalQuorum,
        boolean preventSelfApproval,
        boolean requireJustification,
        int maxDurationMinutes,
        boolean productionProtected,
        boolean breakGlassAllowed,
        boolean breakGlassRequiresReason,
        boolean breakGlassRequiresAudit,
        int emergencyDurationLimitMinutes,
        Instant createdAt,
        Instant updatedAt
) {
    public static PrivilegedAccessPolicyResponse of(
            PrivilegedAccessPolicy policy,
            String projectName,
            String environmentName,
            String secretName
    ) {
        return new PrivilegedAccessPolicyResponse(
                policy.getId(),
                policy.getWorkspaceId(),
                policy.getScopeType(),
                policy.getProjectId(),
                projectName,
                policy.getEnvironmentId(),
                environmentName,
                policy.getSecretId(),
                secretName,
                policy.getAction(),
                policy.isEnabled(),
                policy.isRequireStepUp(),
                policy.getAllowedStepUpFactors(),
                policy.isRequireApproval(),
                policy.getApprovalQuorum(),
                policy.isPreventSelfApproval(),
                policy.isRequireJustification(),
                policy.getMaxDurationMinutes(),
                policy.isProductionProtected(),
                policy.isBreakGlassAllowed(),
                policy.isBreakGlassRequiresReason(),
                policy.isBreakGlassRequiresAudit(),
                policy.getEmergencyDurationLimitMinutes(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}

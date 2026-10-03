package com.secretvault.secret.reveal.dto;

import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.secret.reveal.entity.SecretRevealPolicy;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record SecretRevealPolicyResponse(
        UUID id,
        UUID workspaceId,
        PrivilegedPolicyScope scopeType,
        UUID projectId,
        UUID environmentId,
        UUID secretId,
        RevealPolicyLevel policyLevel,
        boolean requireStepUp,
        List<String> allowedStepUpFactors,
        boolean requireWebAuthnOnly,
        boolean requireReason,
        int minReasonLength,
        int maxReasonLength,
        boolean requirePrivilegedOrJit,
        int maxDisplayDurationSeconds,
        boolean copyAllowed,
        int clipboardTimeoutSeconds,
        boolean bulkRevealAllowed,
        int maxBulkCount,
        int rateLimitPerMinute,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static SecretRevealPolicyResponse fromEntity(SecretRevealPolicy p) {
        List<String> factors = p.getAllowedStepUpFactors() != null
                ? Arrays.stream(p.getAllowedStepUpFactors().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList()
                : List.of();

        return new SecretRevealPolicyResponse(
                p.getId(),
                p.getWorkspaceId(),
                p.getScopeType(),
                p.getProjectId(),
                p.getEnvironmentId(),
                p.getSecretId(),
                p.getPolicyLevel(),
                p.isRequireStepUp(),
                factors,
                p.isRequireWebAuthnOnly(),
                p.isRequireReason(),
                p.getMinReasonLength(),
                p.getMaxReasonLength(),
                p.isRequirePrivilegedOrJit(),
                p.getMaxDisplayDurationSeconds(),
                p.isCopyAllowed(),
                p.getClipboardTimeoutSeconds(),
                p.isBulkRevealAllowed(),
                p.getMaxBulkCount(),
                p.getRateLimitPerMinute(),
                p.isEnabled(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}

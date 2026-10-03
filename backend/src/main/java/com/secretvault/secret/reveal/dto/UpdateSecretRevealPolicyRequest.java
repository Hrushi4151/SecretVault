package com.secretvault.secret.reveal.dto;

import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateSecretRevealPolicyRequest(
        @NotNull PrivilegedPolicyScope scopeType,
        UUID projectId,
        UUID environmentId,
        UUID secretId,
        RevealPolicyLevel policyLevel,
        Boolean requireStepUp,
        String allowedStepUpFactors,
        Boolean requireWebAuthnOnly,
        Boolean requireReason,
        @Min(1) @Max(500) Integer minReasonLength,
        @Min(10) @Max(2000) Integer maxReasonLength,
        Boolean requirePrivilegedOrJit,
        @Min(5) @Max(300) Integer maxDisplayDurationSeconds,
        Boolean copyAllowed,
        @Min(1) @Max(120) Integer clipboardTimeoutSeconds,
        Boolean bulkRevealAllowed,
        @Min(1) @Max(200) Integer maxBulkCount,
        @Min(1) @Max(120) Integer rateLimitPerMinute,
        Boolean enabled
) {
}

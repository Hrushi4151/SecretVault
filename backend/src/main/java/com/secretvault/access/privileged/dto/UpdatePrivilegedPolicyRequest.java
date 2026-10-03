package com.secretvault.access.privileged.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePrivilegedPolicyRequest(
        Boolean enabled,
        Boolean requireStepUp,
        String allowedStepUpFactors,
        Boolean requireApproval,
        @Min(value = 1, message = "Approval quorum must be at least 1")
        @Max(value = 10, message = "Approval quorum cannot exceed 10")
        Integer approvalQuorum,
        Boolean preventSelfApproval,
        Boolean requireJustification,
        @Min(value = 5, message = "Max duration must be at least 5 minutes")
        @Max(value = 1440, message = "Max duration cannot exceed 1440 minutes")
        Integer maxDurationMinutes,
        Boolean productionProtected,
        Boolean breakGlassAllowed,
        Boolean breakGlassRequiresReason,
        Boolean breakGlassRequiresAudit,
        @Min(value = 5, message = "Emergency duration limit must be at least 5 minutes")
        @Max(value = 120, message = "Emergency duration limit cannot exceed 120 minutes")
        Integer emergencyDurationLimitMinutes,
        String stepUpProof
) {}

package com.secretvault.access.grant.dto;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAccessGrantRequest(
        @NotNull(message = "userId is required")
        UUID userId,

        @NotNull(message = "scopeType is required")
        AccessScope scopeType,

        UUID projectId,

        UUID environmentId,

        UUID secretId,

        @NotNull(message = "permission is required")
        AccessPermission permission
) {}

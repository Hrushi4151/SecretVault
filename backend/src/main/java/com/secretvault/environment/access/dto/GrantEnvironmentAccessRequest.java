package com.secretvault.environment.access.dto;

import com.secretvault.environment.access.entity.PermissionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload to grant scoped environment permission to a workspace member.
 */
@Schema(description = "Grant environment access payload")
public record GrantEnvironmentAccessRequest(
        @Schema(description = "Target user UUID (must already be a workspace member)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "User ID is required")
        UUID userId,

        @Schema(description = "Scoped environment permission level", example = "WRITE")
        @NotNull(message = "Permission level is required")
        PermissionLevel permissionLevel
) {
}

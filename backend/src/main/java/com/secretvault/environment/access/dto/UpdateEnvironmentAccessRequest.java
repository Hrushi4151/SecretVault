package com.secretvault.environment.access.dto;

import com.secretvault.environment.access.entity.PermissionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to update scoped environment permission level.
 */
@Schema(description = "Update environment access payload")
public record UpdateEnvironmentAccessRequest(
        @Schema(description = "Updated environment permission level", example = "READ")
        @NotNull(message = "Permission level is required")
        PermissionLevel permissionLevel
) {
}

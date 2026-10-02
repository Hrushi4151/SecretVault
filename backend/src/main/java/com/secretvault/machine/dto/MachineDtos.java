package com.secretvault.machine.dto;

import com.secretvault.access.model.AccessScope;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.model.MachineType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class MachineDtos {

    public record CreateMachineIdentityRequest(
            @NotBlank(message = "Machine identity name is required")
            @Size(min = 2, max = 128, message = "Name must be between 2 and 128 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Name must contain only alphanumeric characters, underscores, and hyphens")
            String name,

            String description,

            @NotNull(message = "Machine type is required")
            MachineType type,

            Instant expiresAt,

            Map<String, Object> metadata
    ) {}

    public record UpdateMachineIdentityRequest(
            @Size(min = 2, max = 128, message = "Name must be between 2 and 128 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Name must contain only alphanumeric characters, underscores, and hyphens")
            String name,

            String description,
            MachineType type,
            Instant expiresAt,
            Map<String, Object> metadata
    ) {}

    public record MachineIdentityResponse(
            UUID id,
            UUID workspaceId,
            String name,
            String description,
            MachineType type,
            MachineStatus status,
            Instant expiresAt,
            Instant lastAuthenticatedAt,
            Instant lastUsedAt,
            Instant disabledAt,
            Instant revokedAt,
            UUID createdBy,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static MachineIdentityResponse fromEntity(MachineIdentity entity) {
            return new MachineIdentityResponse(
                    entity.getId(),
                    entity.getWorkspaceId(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.getType(),
                    entity.getStatus(),
                    entity.getExpiresAt(),
                    entity.getLastAuthenticatedAt(),
                    entity.getLastUsedAt(),
                    entity.getDisabledAt(),
                    entity.getRevokedAt(),
                    entity.getCreatedBy(),
                    entity.getMetadata(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }

    public record CreateMachineGrantRequest(
            @NotNull(message = "Scope type is required")
            AccessScope scopeType,

            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String secretPattern,

            @NotBlank(message = "Permission is required")
            String permission,

            String effect // 'ALLOW' or 'DENY'
    ) {}

    public record MachineGrantResponse(
            UUID id,
            UUID workspaceId,
            UUID machineIdentityId,
            AccessScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String secretPattern,
            String permission,
            String effect,
            UUID grantedBy,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record MachineSessionResponse(
            UUID id,
            UUID workspaceId,
            UUID machineIdentityId,
            UUID oidcProviderId,
            String tokenPrefix,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt,
            Instant lastUsedAt,
            String sourceIp,
            String userAgent,
            boolean active
    ) {}
}

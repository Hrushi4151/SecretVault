package com.secretvault.auth.dto;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of an authenticated User profile.
 */
@Schema(description = "User profile response")
public record UserResponse(
        @Schema(description = "Unique User UUID")
        UUID id,

        @Schema(description = "Registered email address")
        String email,

        @Schema(description = "Full display name")
        String fullName,

        @Schema(description = "Whether MFA is configured and active")
        boolean isMfaEnabled,

        @Schema(description = "Account status")
        UserStatus status,

        @Schema(description = "Account creation timestamp")
        Instant createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.isMfaEnabled(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}

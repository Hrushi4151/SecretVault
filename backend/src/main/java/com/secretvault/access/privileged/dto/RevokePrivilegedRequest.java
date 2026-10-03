package com.secretvault.access.privileged.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokePrivilegedRequest(
        @NotBlank(message = "Revocation reason is required")
        String reason
) {}

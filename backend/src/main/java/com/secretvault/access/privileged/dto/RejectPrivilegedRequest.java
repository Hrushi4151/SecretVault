package com.secretvault.access.privileged.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectPrivilegedRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(min = 3, max = 1000, message = "Rejection reason must be between 3 and 1000 characters")
        String reason
) {}

package com.secretvault.secret.reveal.dto;

import jakarta.validation.constraints.Size;

public record CreateRevealIntentRequest(
        Integer versionNumber,
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason,
        String stepUpProof
) {
}

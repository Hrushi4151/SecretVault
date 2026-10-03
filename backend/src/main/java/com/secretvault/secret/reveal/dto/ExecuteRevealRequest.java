package com.secretvault.secret.reveal.dto;

public record ExecuteRevealRequest(
        String intentToken,
        Integer versionNumber,
        String reason,
        String stepUpProof
) {
}

package com.secretvault.secret.dto;

import java.util.UUID;

/**
 * Safe metadata-level secret version comparison response.
 * Never exposes plaintext values.
 */
public record SecretDiffResponse(
        UUID secretId,
        Integer fromVersion,
        Integer toVersion,
        boolean isEqual,
        String diffType, // "IDENTICAL", "MODIFIED", "EMPTY_TO_VALUE", "VALUE_TO_EMPTY"
        SecretVersionResponse fromMetadata,
        SecretVersionResponse toMetadata,
        Double entropyScoreA,
        Double entropyScoreB,
        String message
) {
}

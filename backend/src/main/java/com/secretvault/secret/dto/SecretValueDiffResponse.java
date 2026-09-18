package com.secretvault.secret.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Explicit value-level diff payload returned only upon authorized secure request.
 * Contains line diff segments for UI rendering.
 */
public record SecretValueDiffResponse(
        UUID secretId,
        Integer fromVersion,
        Integer toVersion,
        boolean isEqual,
        List<DiffLine> diffLines,
        Instant revealedAt
) {
    public record DiffLine(
            DiffLineType type, // EQUAL, ADDED, REMOVED
            String text
    ) {
    }

    public enum DiffLineType {
        EQUAL,
        ADDED,
        REMOVED
    }
}

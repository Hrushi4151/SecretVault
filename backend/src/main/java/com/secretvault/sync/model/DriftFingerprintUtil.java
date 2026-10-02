package com.secretvault.sync.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Utility for generating deterministic, concurrency-safe SHA-256 fingerprints
 * for drift detection records and deduplication.
 */
public final class DriftFingerprintUtil {

    private DriftFingerprintUtil() {}

    /**
     * Computes deterministic SHA-256 fingerprint for a drift occurrence.
     */
    public static String computeFingerprint(
            UUID workspaceId,
            UUID integrationId,
            UUID mappingId,
            String targetIdentifier,
            DriftType driftType
    ) {
        String raw = String.format("%s:%s:%s:%s:%s",
                workspaceId != null ? workspaceId.toString() : "null",
                integrationId != null ? integrationId.toString() : "null",
                mappingId != null ? mappingId.toString() : "null",
                targetIdentifier != null ? targetIdentifier.trim() : "unknown",
                driftType != null ? driftType.name() : "UNKNOWN"
        );
        return sha256(raw);
    }

    public static String sha256(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}

package com.secretvault.security.finding.util;

import com.secretvault.security.finding.model.FindingCategory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Generates deterministic SHA-256 deduplication fingerprints for security findings.
 */
public final class FindingFingerprintGenerator {

    private FindingFingerprintGenerator() {
    }

    public static String generate(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            FindingCategory category,
            String subjectKey
    ) {
        String normalizedSubject = subjectKey != null ? subjectKey.trim().toLowerCase() : "default";
        String raw = (workspaceId != null ? workspaceId.toString() : "null") + ":" +
                (projectId != null ? projectId.toString() : "global") + ":" +
                (environmentId != null ? environmentId.toString() : "global") + ":" +
                (category != null ? category.name() : "UNKNOWN") + ":" +
                normalizedSubject;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available", e);
        }
    }
}

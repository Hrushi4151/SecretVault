package com.secretvault.repository.engine;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Generates cryptographic fingerprints and masked representations of detected secrets.
 * The raw plaintext secret is NEVER stored or logged.
 */
@Component
public class SecretFingerprinter {

    public String computeFingerprint(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawSecret.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public String computeMaskedPreview(String rawSecret, String secretType) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return "********";
        }
        String trimmed = rawSecret.trim();

        // PEM private keys
        if (trimmed.startsWith("-----BEGIN") || trimmed.contains("PRIVATE KEY")) {
            int firstNewline = trimmed.indexOf('\n');
            String header = (firstNewline > 0) ? trimmed.substring(0, firstNewline) : "-----BEGIN PRIVATE KEY-----";
            return header + " [MASKED] -----END PRIVATE KEY-----";
        }

        // Known prefixed keys (e.g. AWS AKIA..., GitHub ghp_..., Slack xoxb-...)
        if (trimmed.startsWith("AKIA") && trimmed.length() >= 16) {
            return trimmed.substring(0, 4) + "************" + trimmed.substring(trimmed.length() - 4);
        }
        if (trimmed.startsWith("ghp_") || trimmed.startsWith("gho_") || trimmed.startsWith("ghu_") || trimmed.startsWith("ghs_")) {
            return trimmed.substring(0, 4) + "************" + (trimmed.length() > 4 ? trimmed.substring(trimmed.length() - Math.min(4, trimmed.length() - 4)) : "");
        }
        if (trimmed.startsWith("glpat-")) {
            return "glpat-************" + (trimmed.length() > 10 ? trimmed.substring(trimmed.length() - 4) : "");
        }
        if (trimmed.startsWith("xoxb-") || trimmed.startsWith("xoxp-")) {
            return trimmed.substring(0, 5) + "************" + trimmed.substring(trimmed.length() - 4);
        }
        if (trimmed.startsWith("sk_live_") || trimmed.startsWith("rk_live_")) {
            return trimmed.substring(0, 8) + "************" + (trimmed.length() > 12 ? trimmed.substring(trimmed.length() - 4) : "");
        }

        // General masking: show at most first 2 and last 2 characters, mask middle with 12 asterisks
        if (trimmed.length() <= 6) {
            return "********";
        }
        int prefixLen = Math.min(2, trimmed.length() / 4);
        int suffixLen = Math.min(2, trimmed.length() / 4);
        return trimmed.substring(0, prefixLen) + "************" + trimmed.substring(trimmed.length() - suffixLen);
    }
}

package com.secretvault.sync.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Calculates deterministic cryptographic hashes for secret values and metadata.
 * Plaintext values processed during comparison are strictly memory-resident
 * and immediately zeroized in finally blocks.
 */
public final class SecretFingerprintCalculator {

    private SecretFingerprintCalculator() {}

    /**
     * Computes SHA-256 fingerprint from secret metadata and ciphertext tokens.
     */
    public static String computeMetadataFingerprint(String secretName, int versionNumber, String encryptedDek) {
        String raw = String.format("%s:v%d:%s",
                secretName != null ? secretName.trim() : "",
                versionNumber,
                encryptedDek != null ? encryptedDek.trim() : ""
        );
        return sha256(raw);
    }

    public static String computeMetadataFingerprint(String secretName, int versionNumber, byte[] encryptedDek) {
        String dekHex = encryptedDek != null ? HexFormat.of().formatHex(encryptedDek) : "";
        return computeMetadataFingerprint(secretName, versionNumber, dekHex);
    }

    /**
     * Computes deterministic SHA-256 fingerprint for a plaintext secret value in RAM,
     * immediately zeroizing any intermediate arrays.
     */
    public static String computeValueFingerprint(String secretValue) {
        if (secretValue == null) {
            return sha256("");
        }
        byte[] bytes = secretValue.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}

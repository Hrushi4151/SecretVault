package com.secretvault.sync.model;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Calculates deterministic cryptographic hashes and HMAC digests for secret values and metadata.
 * Supports explicit domain separation, tenant isolation, and constant-time equality checks.
 * <p>
 * Memory security considerations:
 * Intermediate {@code byte[]} buffers allocated during digest computation are actively zeroized in finally blocks.
 * Note that {@code java.lang.String} instances in Java are immutable and cannot be zeroized in-place once instantiated.
 * Where zeroizable handling is required, callers should provide mutable {@code byte[]} arrays.
 */
public final class SecretFingerprintCalculator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DOMAIN_PREFIX = "SECRETVAULT:SYNC:v1:";

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
     * Computes deterministic keyed HMAC-SHA-256 fingerprint with domain separation
     * across tenants and target environments.
     */
    public static String computeKeyedMetadataFingerprint(
            UUID workspaceId,
            String secretName,
            int versionNumber,
            String encryptedDek,
            byte[] hmacKey
    ) {
        if (hmacKey == null || hmacKey.length == 0) {
            return computeMetadataFingerprint(secretName, versionNumber, encryptedDek);
        }
        String domain = DOMAIN_PREFIX + "META:" + (workspaceId != null ? workspaceId : "GLOBAL");
        String payload = String.format("%s:%s:v%d:%s",
                domain,
                secretName != null ? secretName.trim() : "",
                versionNumber,
                encryptedDek != null ? encryptedDek.trim() : ""
        );
        return computeHmac(payload.getBytes(StandardCharsets.UTF_8), hmacKey);
    }

    /**
     * Computes deterministic SHA-256 fingerprint for a plaintext secret value in RAM.
     * Intermediate byte arrays created from the String are immediately zeroized in a finally block.
     */
    public static String computeValueFingerprint(String secretValue) {
        if (secretValue == null) {
            return sha256("");
        }
        byte[] bytes = secretValue.getBytes(StandardCharsets.UTF_8);
        try {
            return computeValueFingerprint(bytes, false);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /**
     * Computes deterministic SHA-256 fingerprint for raw secret bytes, optionally zeroizing the input array.
     */
    public static String computeValueFingerprint(byte[] secretBytes, boolean zeroizeInput) {
        if (secretBytes == null || secretBytes.length == 0) {
            return sha256("");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secretBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        } finally {
            if (zeroizeInput) {
                Arrays.fill(secretBytes, (byte) 0);
            }
        }
    }

    /**
     * Computes keyed HMAC-SHA-256 fingerprint for secret values with domain separation.
     */
    public static String computeKeyedValueFingerprint(String secretValue, byte[] hmacKey, String domainScope) {
        if (hmacKey == null || hmacKey.length == 0) {
            return computeValueFingerprint(secretValue);
        }
        if (secretValue == null) {
            secretValue = "";
        }
        String domain = DOMAIN_PREFIX + "VAL:" + (domainScope != null ? domainScope.trim() : "DEFAULT") + ":";
        byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
        byte[] secretBytes = secretValue.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[domainBytes.length + secretBytes.length];

        try {
            System.arraycopy(domainBytes, 0, combined, 0, domainBytes.length);
            System.arraycopy(secretBytes, 0, combined, domainBytes.length, secretBytes.length);
            return computeHmac(combined, hmacKey);
        } finally {
            Arrays.fill(secretBytes, (byte) 0);
            Arrays.fill(combined, (byte) 0);
        }
    }

    /**
     * Constant-time comparison of two fingerprints to prevent timing side-channels.
     */
    public static boolean secureEquals(String fingerprintA, String fingerprintB) {
        if (fingerprintA == null || fingerprintB == null) {
            return false;
        }
        byte[] a = fingerprintA.getBytes(StandardCharsets.UTF_8);
        byte[] b = fingerprintB.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String computeHmac(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmac = mac.doFinal(data);
            return HexFormat.of().formatHex(hmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 calculation failed", e);
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

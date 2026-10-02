package com.secretvault.auth.mfa.totp;

/**
 * Cryptographic hash algorithms supported for RFC 6238 TOTP computation.
 */
public enum TotpAlgorithm {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    private final String hmacAlgorithm;

    TotpAlgorithm(String hmacAlgorithm) {
        this.hmacAlgorithm = hmacAlgorithm;
    }

    public String getHmacAlgorithm() {
        return hmacAlgorithm;
    }

    public static TotpAlgorithm fromString(String name) {
        if (name == null || name.isBlank()) {
            return SHA1;
        }
        String normalized = name.trim().toUpperCase().replace("-", "");
        return switch (normalized) {
            case "SHA1", "HMACSHA1" -> SHA1;
            case "SHA256", "HMACSHA256" -> SHA256;
            case "SHA512", "HMACSHA512" -> SHA512;
            default -> throw new IllegalArgumentException("Unsupported TOTP algorithm: " + name);
        };
    }
}

package com.secretvault.auth.mfa.totp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Server-side configuration properties for RFC 6238 TOTP engine.
 */
@Component
@ConfigurationProperties(prefix = "secretvault.mfa.totp")
public class TotpProperties {

    /**
     * Default HMAC algorithm: SHA1, SHA256, or SHA512.
     * RFC 6238 and standard authenticator apps default to SHA1.
     */
    private TotpAlgorithm algorithm = TotpAlgorithm.SHA1;

    /**
     * Number of digits in generated code (typically 6 or 8).
     */
    private int digits = 6;

    /**
     * Time step window duration in seconds (standard is 30).
     */
    private int periodSeconds = 30;

    /**
     * Number of past and future time steps allowed for clock drift tolerance.
     * Default 1 allows [t-1, t, t+1] (e.g. +/- 30s window).
     */
    private int allowedDriftSteps = 1;

    /**
     * Default issuer name embedded in provisioning URIs and displayed in authenticator apps.
     */
    private String issuer = "SecretVault";

    /**
     * Byte length for freshly generated TOTP secrets (20 bytes = 160 bits = 32 Base32 characters).
     */
    private int secretByteLength = 20;

    public TotpAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(TotpAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public int getDigits() {
        return digits;
    }

    public void setDigits(int digits) {
        if (digits != 6 && digits != 8) {
            throw new IllegalArgumentException("TOTP digits must be either 6 or 8");
        }
        this.digits = digits;
    }

    public int getPeriodSeconds() {
        return periodSeconds;
    }

    public void setPeriodSeconds(int periodSeconds) {
        if (periodSeconds <= 0 || periodSeconds > 300) {
            throw new IllegalArgumentException("TOTP period seconds must be between 1 and 300");
        }
        this.periodSeconds = periodSeconds;
    }

    public int getAllowedDriftSteps() {
        return allowedDriftSteps;
    }

    public void setAllowedDriftSteps(int allowedDriftSteps) {
        if (allowedDriftSteps < 0 || allowedDriftSteps > 5) {
            throw new IllegalArgumentException("TOTP allowed drift steps must be between 0 and 5");
        }
        this.allowedDriftSteps = allowedDriftSteps;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = (issuer != null && !issuer.isBlank()) ? issuer.trim() : "SecretVault";
    }

    public int getSecretByteLength() {
        return secretByteLength;
    }

    public void setSecretByteLength(int secretByteLength) {
        if (secretByteLength < 16 || secretByteLength > 64) {
            throw new IllegalArgumentException("TOTP secret byte length must be between 16 and 64 bytes (128 to 512 bits)");
        }
        this.secretByteLength = secretByteLength;
    }
}

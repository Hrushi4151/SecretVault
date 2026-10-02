package com.secretvault.auth.mfa.totp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * RFC 6238 compliant Time-Based One-Time Password (TOTP) cryptographic engine.
 * Provides cryptographically secure secret generation, standards-compatible code calculation,
 * constant-time verification with controlled clock drift tolerance, and Key URI generation.
 */
@Service
public class TotpService {

    private static final Logger log = LoggerFactory.getLogger(TotpService.class);
    private static final Pattern CODE_PATTERN_6 = Pattern.compile("^\\d{6}$");
    private static final Pattern CODE_PATTERN_8 = Pattern.compile("^\\d{8}$");

    private final TotpProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public TotpService() {
        this(new TotpProperties(), Clock.systemUTC());
    }

    public TotpService(TotpProperties properties) {
        this(properties, Clock.systemUTC());
    }

    @Autowired
    public TotpService(TotpProperties properties, @Autowired(required = false) Clock clock) {
        this.properties = Objects.requireNonNull(properties, "TotpProperties must not be null");
        this.clock = (clock != null) ? clock : Clock.systemUTC();
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a fresh, high-entropy Base32 encoded TOTP secret (default 160 bits / 20 bytes).
     *
     * @return Base32 encoded secret string (unpadded)
     */
    public String generateSecret() {
        return generateSecret(properties.getSecretByteLength());
    }

    /**
     * Generates a fresh, high-entropy Base32 encoded TOTP secret of specified byte length.
     *
     * @param byteLength Number of random bytes (16 to 64 bytes)
     * @return Base32 encoded secret string (unpadded)
     */
    public String generateSecret(int byteLength) {
        if (byteLength < 16 || byteLength > 64) {
            throw new IllegalArgumentException("Secret byte length must be between 16 and 64 bytes");
        }
        byte[] secretBytes = new byte[byteLength];
        secureRandom.nextBytes(secretBytes);
        try {
            return Base32.encode(secretBytes, false);
        } finally {
            java.util.Arrays.fill(secretBytes, (byte) 0);
        }
    }

    /**
     * Computes the TOTP code for the current instant using configured default settings.
     *
     * @param base32Secret Base32 encoded secret
     * @return Formatted numerical OTP string
     */
    public String generateCode(String base32Secret) {
        return generateCode(base32Secret, clock.instant());
    }

    /**
     * Computes the TOTP code for a specific instant using configured default settings.
     *
     * @param base32Secret Base32 encoded secret
     * @param instant      Target point in time
     * @return Formatted numerical OTP string
     */
    public String generateCode(String base32Secret, Instant instant) {
        return generateCode(base32Secret, instant, properties.getAlgorithm(), properties.getDigits(), properties.getPeriodSeconds());
    }

    /**
     * Computes the TOTP code with full parameterization.
     *
     * @param base32Secret  Base32 encoded secret
     * @param instant       Target point in time
     * @param algorithm     HMAC algorithm (SHA1, SHA256, SHA512)
     * @param digits        Number of digits (6 or 8)
     * @param periodSeconds Time step duration in seconds
     * @return Formatted numerical OTP string
     */
    public String generateCode(String base32Secret, Instant instant, TotpAlgorithm algorithm, int digits, int periodSeconds) {
        Objects.requireNonNull(base32Secret, "Base32 secret must not be null");
        Objects.requireNonNull(instant, "Instant must not be null");
        Objects.requireNonNull(algorithm, "Algorithm must not be null");
        validateParameters(digits, periodSeconds);

        byte[] keyBytes = Base32.decode(base32Secret);
        if (keyBytes.length < 10) {
            throw new IllegalArgumentException("TOTP secret contains insufficient key length");
        }

        long counter = instant.getEpochSecond() / periodSeconds;
        return computeOtp(keyBytes, counter, algorithm, digits);
    }

    /**
     * Verifies a candidate code against the current instant using default configured drift tolerance.
     *
     * @param base32Secret  Base32 encoded secret
     * @param candidateCode Code submitted by user
     * @return true if valid within allowable window, false otherwise
     */
    public boolean verifyCode(String base32Secret, String candidateCode) {
        return verifyCode(base32Secret, candidateCode, clock.instant());
    }

    /**
     * Verifies a candidate code against a specified instant using default configured drift tolerance.
     *
     * @param base32Secret  Base32 encoded secret
     * @param candidateCode Code submitted by user
     * @param instant       Verification point in time
     * @return true if valid within allowable window, false otherwise
     */
    public boolean verifyCode(String base32Secret, String candidateCode, Instant instant) {
        return verifyCode(base32Secret, candidateCode, instant, properties.getAllowedDriftSteps());
    }

    /**
     * Verifies a candidate code against a specified instant and custom drift window.
     *
     * @param base32Secret      Base32 encoded secret
     * @param candidateCode     Code submitted by user
     * @param instant           Verification point in time
     * @param allowedDriftSteps Number of past/future time windows to check
     * @return true if valid within allowable window, false otherwise
     */
    public boolean verifyCode(String base32Secret, String candidateCode, Instant instant, int allowedDriftSteps) {
        return verifyCode(base32Secret, candidateCode, instant, properties.getAlgorithm(), properties.getDigits(), properties.getPeriodSeconds(), allowedDriftSteps);
    }

    /**
     * Verifies a candidate code with full parameterization.
     */
    public boolean verifyCode(
            String base32Secret,
            String candidateCode,
            Instant instant,
            TotpAlgorithm algorithm,
            int digits,
            int periodSeconds,
            int allowedDriftSteps
    ) {
        if (base32Secret == null || candidateCode == null || instant == null || algorithm == null) {
            return false;
        }

        String sanitizedCode = candidateCode.trim();
        if (!isValidCodeFormat(sanitizedCode, digits)) {
            return false;
        }

        byte[] keyBytes;
        try {
            keyBytes = Base32.decode(base32Secret);
            if (keyBytes.length < 10) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (allowedDriftSteps < 0 || allowedDriftSteps > 5) {
            allowedDriftSteps = 1;
        }

        long currentCounter = instant.getEpochSecond() / periodSeconds;
        byte[] candidateBytes = sanitizedCode.getBytes(StandardCharsets.UTF_8);

        for (int i = -allowedDriftSteps; i <= allowedDriftSteps; i++) {
            long targetCounter = currentCounter + i;
            if (targetCounter < 0) {
                continue;
            }
            String expectedCode = computeOtp(keyBytes, targetCounter, algorithm, digits);
            byte[] expectedBytes = expectedCode.getBytes(StandardCharsets.UTF_8);

            if (MessageDigest.isEqual(candidateBytes, expectedBytes)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates standard Key URI for QR provisioning in authenticator applications.
     * Format: {@code otpauth://totp/{issuer}:{account}?secret={secret}&issuer={issuer}&algorithm={algo}&digits={digits}&period={period}}
     *
     * @param base32Secret Base32 encoded secret
     * @param accountName  Account name / user email
     * @return RFC compliant Key URI string
     */
    public String buildProvisioningUri(String base32Secret, String accountName) {
        return buildProvisioningUri(base32Secret, properties.getIssuer(), accountName, properties.getAlgorithm(), properties.getDigits(), properties.getPeriodSeconds());
    }

    /**
     * Generates parameterized Key URI for QR provisioning.
     */
    public String buildProvisioningUri(
            String base32Secret,
            String issuer,
            String accountName,
            TotpAlgorithm algorithm,
            int digits,
            int periodSeconds
    ) {
        Objects.requireNonNull(base32Secret, "Base32 secret must not be null");
        Objects.requireNonNull(accountName, "Account name must not be null");
        String safeIssuer = (issuer != null && !issuer.isBlank()) ? issuer.trim() : properties.getIssuer();
        String safeAccount = accountName.trim();

        String cleanSecret = base32Secret.trim().replace("=", "").toUpperCase(Locale.ROOT);
        if (!Base32.isValid(cleanSecret)) {
            throw new IllegalArgumentException("Invalid Base32 secret for provisioning URI");
        }

        String encodedIssuer = urlEncode(safeIssuer);
        String encodedAccount = urlEncode(safeAccount);

        return String.format(
                Locale.ROOT,
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=%s&digits=%d&period=%d",
                encodedIssuer,
                encodedAccount,
                cleanSecret,
                encodedIssuer,
                algorithm.name(),
                digits,
                periodSeconds
        );
    }

    /**
     * Direct raw-byte computation used by RFC test vectors and internal engine.
     */
    public String computeOtp(byte[] keyBytes, long counter, TotpAlgorithm algorithm, int digits) {
        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xFF);
            counter >>= 8;
        }

        try {
            Mac mac = Mac.getInstance(algorithm.getHmacAlgorithm());
            mac.init(new SecretKeySpec(keyBytes, algorithm.getHmacAlgorithm()));
            byte[] hash = mac.doFinal(counterBytes);

            // Dynamic truncation (RFC 4226 Section 5.4)
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int modulo = (int) Math.pow(10, digits);
            int otp = binary % modulo;

            String format = "%0" + digits + "d";
            return String.format(Locale.ROOT, format, otp);
        } catch (GeneralSecurityException e) {
            log.error("Cryptographic HMAC failure during OTP computation");
            throw new IllegalStateException("Failed to calculate TOTP HMAC", e);
        }
    }

    public TotpProperties getProperties() {
        return properties;
    }

    private static boolean isValidCodeFormat(String code, int digits) {
        if (code == null) {
            return false;
        }
        return (digits == 6) ? CODE_PATTERN_6.matcher(code).matches() : CODE_PATTERN_8.matcher(code).matches();
    }

    private static void validateParameters(int digits, int periodSeconds) {
        if (digits != 6 && digits != 8) {
            throw new IllegalArgumentException("TOTP digits must be either 6 or 8");
        }
        if (periodSeconds <= 0 || periodSeconds > 300) {
            throw new IllegalArgumentException("TOTP period seconds must be between 1 and 300");
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

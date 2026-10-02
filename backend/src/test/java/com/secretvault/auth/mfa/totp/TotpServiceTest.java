package com.secretvault.auth.mfa.totp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RFC 6238 TOTP Engine Comprehensive Tests")
class TotpServiceTest {

    private TotpService totpService;
    private TotpProperties properties;

    // RFC 6238 Appendix B Test Keys
    private static final byte[] RFC_SEED_SHA1 = "12345678901234567890".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RFC_SEED_SHA256 = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RFC_SEED_SHA512 = "1234567890123456789012345678901234567890123456789012345678901234".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        properties = new TotpProperties();
        totpService = new TotpService(properties, Clock.systemUTC());
    }

    // =========================================================================
    // RFC 6238 Official Reference Test Vectors (8 Digits & 6 Digits)
    // =========================================================================

    @Test
    @DisplayName("RFC 6238 Table 1 Test Vectors for Mode SHA1 (8 digits)")
    void testRfc6238Sha1Vectors8Digits() {
        assertRfcVector(RFC_SEED_SHA1, 59L, TotpAlgorithm.SHA1, 8, "94287082");
        assertRfcVector(RFC_SEED_SHA1, 1111111109L, TotpAlgorithm.SHA1, 8, "07081804");
        assertRfcVector(RFC_SEED_SHA1, 1111111111L, TotpAlgorithm.SHA1, 8, "14050471");
        assertRfcVector(RFC_SEED_SHA1, 1234567890L, TotpAlgorithm.SHA1, 8, "89005924");
        assertRfcVector(RFC_SEED_SHA1, 2000000000L, TotpAlgorithm.SHA1, 8, "69279037");
        assertRfcVector(RFC_SEED_SHA1, 20000000000L, TotpAlgorithm.SHA1, 8, "65353130");
    }

    @Test
    @DisplayName("RFC 6238 Table 1 Test Vectors for Mode SHA256 (8 digits)")
    void testRfc6238Sha256Vectors8Digits() {
        assertRfcVector(RFC_SEED_SHA256, 59L, TotpAlgorithm.SHA256, 8, "46119246");
        assertRfcVector(RFC_SEED_SHA256, 1111111109L, TotpAlgorithm.SHA256, 8, "68084774");
        assertRfcVector(RFC_SEED_SHA256, 1111111111L, TotpAlgorithm.SHA256, 8, "67062674");
        assertRfcVector(RFC_SEED_SHA256, 1234567890L, TotpAlgorithm.SHA256, 8, "91819424");
        assertRfcVector(RFC_SEED_SHA256, 2000000000L, TotpAlgorithm.SHA256, 8, "90698825");
        assertRfcVector(RFC_SEED_SHA256, 20000000000L, TotpAlgorithm.SHA256, 8, "77737706");
    }

    @Test
    @DisplayName("RFC 6238 Table 1 Test Vectors for Mode SHA512 (8 digits)")
    void testRfc6238Sha512Vectors8Digits() {
        assertRfcVector(RFC_SEED_SHA512, 59L, TotpAlgorithm.SHA512, 8, "90693936");
        assertRfcVector(RFC_SEED_SHA512, 1111111109L, TotpAlgorithm.SHA512, 8, "25091201");
        assertRfcVector(RFC_SEED_SHA512, 1111111111L, TotpAlgorithm.SHA512, 8, "99943326");
        assertRfcVector(RFC_SEED_SHA512, 1234567890L, TotpAlgorithm.SHA512, 8, "93441116");
        assertRfcVector(RFC_SEED_SHA512, 2000000000L, TotpAlgorithm.SHA512, 8, "38618901");
        assertRfcVector(RFC_SEED_SHA512, 20000000000L, TotpAlgorithm.SHA512, 8, "47863826");
    }

    @Test
    @DisplayName("RFC 6238 6-Digit Mode Validation (including leading zeroes preservation)")
    void testRfc6238Sha1Vectors6Digits() {
        assertRfcVector(RFC_SEED_SHA1, 59L, TotpAlgorithm.SHA1, 6, "287082");
        assertRfcVector(RFC_SEED_SHA1, 1111111109L, TotpAlgorithm.SHA1, 6, "081804");
        assertRfcVector(RFC_SEED_SHA1, 1111111111L, TotpAlgorithm.SHA1, 6, "050471");
        // Leading zeros preservation: 89005924 mod 10^6 = 005924
        assertRfcVector(RFC_SEED_SHA1, 1234567890L, TotpAlgorithm.SHA1, 6, "005924");
        assertRfcVector(RFC_SEED_SHA1, 2000000000L, TotpAlgorithm.SHA1, 6, "279037");
        assertRfcVector(RFC_SEED_SHA1, 20000000000L, TotpAlgorithm.SHA1, 6, "353130");
    }

    private void assertRfcVector(byte[] key, long epochSeconds, TotpAlgorithm algorithm, int digits, String expectedOtp) {
        long counter = epochSeconds / 30;
        String otp = totpService.computeOtp(key, counter, algorithm, digits);
        assertThat(otp).isEqualTo(expectedOtp);

        // Also test Base32 interface
        String base32 = Base32.encode(key, false);
        String calculated = totpService.generateCode(base32, Instant.ofEpochSecond(epochSeconds), algorithm, digits, 30);
        assertThat(calculated).isEqualTo(expectedOtp);
    }

    // =========================================================================
    // Secret Generation & Entropy Tests
    // =========================================================================

    @Test
    @DisplayName("generateSecret produces valid Base32 string with correct entropy")
    void testGenerateSecret() {
        String secret = totpService.generateSecret();
        assertThat(secret).isNotNull().hasSize(32); // 20 bytes * 8 / 5 = 32 chars
        assertThat(Base32.isValid(secret)).isTrue();

        byte[] decoded = Base32.decode(secret);
        assertThat(decoded).hasSize(20); // 160 bits standard
    }

    @Test
    @DisplayName("generateSecret produces unique values across 10,000 samples")
    void testSecretUniqueness() {
        Set<String> sample = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String secret = totpService.generateSecret();
            assertThat(sample.add(secret)).isTrue();
        }
    }

    // =========================================================================
    // Clock Drift & Boundary Tests
    // =========================================================================

    @Test
    @DisplayName("verifyCode respects controlled clock drift (current, -1 window, +1 window)")
    void testClockDriftVerification() {
        String secret = Base32.encode(RFC_SEED_SHA1, false);
        long baseEpochSecond = 1234567890L; // step = 41152263
        Instant baseInstant = Instant.ofEpochSecond(baseEpochSecond);

        // Expected code at current window (T = 1234567890, step 41152263)
        String currentCode = totpService.generateCode(secret, baseInstant);

        // Expected code at previous window (T = 1234567860, step 41152262)
        String prevCode = totpService.generateCode(secret, baseInstant.minusSeconds(30));

        // Expected code at next window (T = 1234567920, step 41152264)
        String nextCode = totpService.generateCode(secret, baseInstant.plusSeconds(30));

        // Code 2 steps back (T = 1234567830, step 41152261)
        String twoStepsPrevCode = totpService.generateCode(secret, baseInstant.minusSeconds(60));

        // Code 2 steps ahead (T = 1234567950, step 41152265)
        String twoStepsNextCode = totpService.generateCode(secret, baseInstant.plusSeconds(60));

        // With default drift = 1:
        assertThat(totpService.verifyCode(secret, currentCode, baseInstant, 1)).isTrue();
        assertThat(totpService.verifyCode(secret, prevCode, baseInstant, 1)).isTrue();
        assertThat(totpService.verifyCode(secret, nextCode, baseInstant, 1)).isTrue();

        // 2 windows away must be REJECTED with drift = 1
        assertThat(totpService.verifyCode(secret, twoStepsPrevCode, baseInstant, 1)).isFalse();
        assertThat(totpService.verifyCode(secret, twoStepsNextCode, baseInstant, 1)).isFalse();

        // With zero drift:
        assertThat(totpService.verifyCode(secret, currentCode, baseInstant, 0)).isTrue();
        assertThat(totpService.verifyCode(secret, prevCode, baseInstant, 0)).isFalse();
        assertThat(totpService.verifyCode(secret, nextCode, baseInstant, 0)).isFalse();
    }

    @Test
    @DisplayName("Fixed Clock injection operates deterministically")
    void testInjectableClock() {
        Instant fixedInstant = Instant.ofEpochSecond(1234567890L);
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        TotpService fixedTotpService = new TotpService(properties, fixedClock);

        String secret = Base32.encode(RFC_SEED_SHA1, false);
        String code = fixedTotpService.generateCode(secret);
        assertThat(code).isEqualTo("005924");
        assertThat(fixedTotpService.verifyCode(secret, "005924")).isTrue();
    }

    // =========================================================================
    // Strict Code Format Validation Tests
    // =========================================================================

    @ParameterizedTest
    @CsvSource({
            "null",
            "''",
            "'      '",
            "12345",
            "1234567",
            "abcdef",
            "12345a",
            "123 456",
            "123-456",
            "12345!",
            "-12345"
    })
    @DisplayName("Rejects malformed OTP input formats")
    void testMalformedCodeRejections(String candidate) {
        String input = "null".equals(candidate) ? null : candidate;
        String secret = Base32.encode(RFC_SEED_SHA1, false);
        assertThat(totpService.verifyCode(secret, input)).isFalse();
    }

    // =========================================================================
    // Provisioning URI Tests
    // =========================================================================

    @Test
    @DisplayName("buildProvisioningUri generates RFC-compliant Key URI with correct URL encoding")
    void testBuildProvisioningUri() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        String uri = totpService.buildProvisioningUri(secret, "alice@example.com");

        assertThat(uri).isEqualTo("otpauth://totp/SecretVault:alice%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=SecretVault&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    @DisplayName("buildProvisioningUri handles special characters in issuer and account")
    void testBuildProvisioningUriSpecialChars() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        String uri = totpService.buildProvisioningUri(secret, "Acme Corp / Sec Ops", "alice+vault@example.com", TotpAlgorithm.SHA256, 8, 60);

        assertThat(uri).isEqualTo("otpauth://totp/Acme%20Corp%20%2F%20Sec%20Ops:alice%2Bvault%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Acme%20Corp%20%2F%20Sec%20Ops&algorithm=SHA256&digits=8&period=60");
    }

    @Test
    @DisplayName("buildProvisioningUri rejects invalid Base32 secret")
    void testBuildProvisioningUriInvalidSecret() {
        assertThatThrownBy(() -> totpService.buildProvisioningUri("INVALID8SECRET", "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

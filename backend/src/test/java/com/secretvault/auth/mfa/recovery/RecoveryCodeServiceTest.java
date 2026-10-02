package com.secretvault.auth.mfa.recovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecoveryCodeService Unit & Cryptographic Tests")
class RecoveryCodeServiceTest {

    private RecoveryCodeService recoveryCodeService;

    @BeforeEach
    void setUp() {
        recoveryCodeService = new RecoveryCodeService(new BCryptPasswordEncoder(10));
    }

    @Test
    @DisplayName("generateCodes generates requested count of unique, well-formatted codes")
    void testGenerateCodes() {
        List<String> codes = recoveryCodeService.generateCodes(10);
        assertThat(codes).hasSize(10);

        Set<String> uniqueCodes = new HashSet<>(codes);
        assertThat(uniqueCodes).hasSize(10);

        for (String code : codes) {
            assertThat(code).matches("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}$");
            // Must NOT contain ambiguous characters 0, O, 1, I, L
            assertThat(code).doesNotContain("0", "O", "1", "I", "L", "o", "i", "l");
        }
    }

    @Test
    @DisplayName("generateCodes rejects invalid batch counts")
    void testGenerateCodesValidation() {
        assertThatThrownBy(() -> recoveryCodeService.generateCodes(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> recoveryCodeService.generateCodes(-5))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> recoveryCodeService.generateCodes(21))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("normalize handles spaces, lowercase, and hyphens accurately")
    void testNormalize() {
        assertThat(recoveryCodeService.normalize("2345-6789-ABCD")).isEqualTo("23456789ABCD");
        assertThat(recoveryCodeService.normalize("23456789abcd")).isEqualTo("23456789ABCD");
        assertThat(recoveryCodeService.normalize(" 2345 6789 abcd ")).isEqualTo("23456789ABCD");
        assertThat(recoveryCodeService.normalize("2345_6789_abcd")).isEqualTo("23456789ABCD");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2345-6789-ABC0", // contains 0
            "2345-6789-ABCO", // contains O
            "2345-6789-ABC1", // contains 1
            "2345-6789-ABCI", // contains I
            "2345-6789-ABCL", // contains L
            "2345-6789-ABC",  // too short (11 chars)
            "2345-6789-ABCDEF", // too long
            "2345-6789-ABC#", // special char
            ""
    })
    @DisplayName("normalize rejects invalid characters or malformed code formats")
    void testNormalizeRejects(String invalidCode) {
        assertThatThrownBy(() -> recoveryCodeService.normalize(invalidCode))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hash and matches accurately verify valid candidate recovery codes with BCrypt")
    void testHashAndMatches() {
        List<String> codes = recoveryCodeService.generateCodes(1);
        String rawCode = codes.getFirst();

        String hash = recoveryCodeService.hash(rawCode);
        assertThat(hash).isNotNull().startsWith("$2a$");

        // Exact match
        assertThat(recoveryCodeService.matches(rawCode, hash)).isTrue();

        // Lowercase candidate match
        assertThat(recoveryCodeService.matches(rawCode.toLowerCase(), hash)).isTrue();

        // Unformatted raw string match
        String unformatted = rawCode.replace("-", "");
        assertThat(recoveryCodeService.matches(unformatted, hash)).isTrue();

        // Wrong code fails
        assertThat(recoveryCodeService.matches("2345-6789-9999", hash)).isFalse();

        // Malformed code fails safely without exception
        assertThat(recoveryCodeService.matches("INVALID", hash)).isFalse();
        assertThat(recoveryCodeService.matches(null, hash)).isFalse();
        assertThat(recoveryCodeService.matches(rawCode, null)).isFalse();
    }
}

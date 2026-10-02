package com.secretvault.auth.mfa.totp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Base32 RFC 4648 Utility Tests")
class Base32Test {

    @Test
    @DisplayName("RFC 4648 test vectors - encoding and decoding with padding")
    void testRfcTestVectors() {
        // RFC 4648 Section 10 Test Vectors
        assertEncodeDecode("", "");
        assertEncodeDecode("f", "MY======");
        assertEncodeDecode("fo", "MZXQ====");
        assertEncodeDecode("foo", "MZXW6===");
        assertEncodeDecode("foob", "MZXW6YQ=");
        assertEncodeDecode("fooba", "MZXW6YTB");
        assertEncodeDecode("foobar", "MZXW6YTBOI======");
    }

    private void assertEncodeDecode(String plaintext, String expectedBase32Padded) {
        byte[] raw = plaintext.getBytes(StandardCharsets.UTF_8);
        String encodedPadded = Base32.encode(raw, true);
        assertThat(encodedPadded).isEqualTo(expectedBase32Padded);

        String unpaddedExpected = expectedBase32Padded.replace("=", "");
        String encodedUnpadded = Base32.encode(raw, false);
        assertThat(encodedUnpadded).isEqualTo(unpaddedExpected);

        // Decode padded
        byte[] decodedFromPadded = Base32.decode(expectedBase32Padded);
        assertThat(new String(decodedFromPadded, StandardCharsets.UTF_8)).isEqualTo(plaintext);

        // Decode unpadded
        byte[] decodedFromUnpadded = Base32.decode(unpaddedExpected);
        assertThat(new String(decodedFromUnpadded, StandardCharsets.UTF_8)).isEqualTo(plaintext);
    }

    @ParameterizedTest
    @CsvSource({
            "mzxw6ytb, fooba",
            "MZXW6YTB, fooba",
            "MZXW 6YTB, fooba",
            "mzxw-6ytb, fooba"
    })
    @DisplayName("Case-insensitivity and formatting tolerance in decode")
    void testCaseAndFormattingTolerance(String input, String expected) {
        byte[] decoded = Base32.decode(input);
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Rejects invalid Base32 characters")
    void testInvalidCharacters() {
        assertThatThrownBy(() -> Base32.decode("MZXW8YTB")) // '8' is invalid in Base32
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Base32 character");

        assertThatThrownBy(() -> Base32.decode("MZXW9YTB")) // '9' is invalid
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Base32.decode("MZXW0YTB")) // '0' is invalid
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Base32.decode("MZXW1YTB")) // '1' is invalid
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Base32.decode("MZXW#YTB")) // '#' is invalid
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isValid returns true for valid strings and false for malformed ones")
    void testIsValid() {
        assertThat(Base32.isValid("MZXW6YTB")).isTrue();
        assertThat(Base32.isValid("mzxw6ytb")).isTrue();
        assertThat(Base32.isValid("MZXW6===")).isTrue();
        assertThat(Base32.isValid("")).isTrue();
        assertThat(Base32.isValid(null)).isFalse();
        assertThat(Base32.isValid("INVALID8")).isFalse();
        assertThat(Base32.isValid("MZXW#")).isFalse();
    }
}

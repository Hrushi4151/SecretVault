package com.secretvault.auth.mfa.totp;

import java.util.Arrays;
import java.util.Objects;

/**
 * RFC 4648 compliant Base32 encoder and decoder for TOTP secret management.
 * Provides case-insensitive decoding, strict character validation, and safe error reporting.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final char[] ENCODE_TABLE = ALPHABET.toCharArray();
    private static final byte[] DECODE_TABLE = new byte[128];
    private static final char PAD = '=';

    static {
        Arrays.fill(DECODE_TABLE, (byte) -1);
        for (int i = 0; i < ENCODE_TABLE.length; i++) {
            char c = ENCODE_TABLE[i];
            DECODE_TABLE[c] = (byte) i;
            // Also register lowercase letters directly in lookup table for case-insensitivity
            if (c >= 'A' && c <= 'Z') {
                DECODE_TABLE[Character.toLowerCase(c)] = (byte) i;
            }
        }
    }

    private Base32() {
        // Utility class
    }

    /**
     * Encodes binary data into an unpadded RFC 4648 Base32 string.
     *
     * @param data Raw byte array
     * @return Base32 encoded string (no padding)
     */
    public static String encode(byte[] data) {
        return encode(data, false);
    }

    /**
     * Encodes binary data into an RFC 4648 Base32 string with optional padding.
     *
     * @param data Raw byte array
     * @param pad  Whether to pad output to a multiple of 8 characters with '='
     * @return Base32 encoded string
     */
    public static String encode(byte[] data, boolean pad) {
        Objects.requireNonNull(data, "Data to encode must not be null");
        if (data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(ENCODE_TABLE[index]);
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(ENCODE_TABLE[index]);
        }

        if (pad) {
            while (sb.length() % 8 != 0) {
                sb.append(PAD);
            }
        }

        return sb.toString();
    }

    /**
     * Decodes an RFC 4648 Base32 string (with or without '=' padding, case-insensitive) into raw bytes.
     *
     * @param base32String Base32 encoded string
     * @return Decoded byte array
     * @throws IllegalArgumentException if the input contains invalid characters or malformed bits
     */
    public static byte[] decode(String base32String) {
        Objects.requireNonNull(base32String, "Base32 string must not be null");
        String sanitized = base32String.trim().replace(" ", "").replace("-", "");
        if (sanitized.isEmpty()) {
            return new byte[0];
        }

        // Count valid characters (excluding padding)
        int length = sanitized.length();
        while (length > 0 && sanitized.charAt(length - 1) == PAD) {
            length--;
        }

        int buffer = 0;
        int bitsLeft = 0;
        byte[] result = new byte[(length * 5) / 8];
        int outIndex = 0;

        for (int i = 0; i < length; i++) {
            char c = sanitized.charAt(i);
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] == -1) {
                throw new IllegalArgumentException("Invalid Base32 character detected at index " + i);
            }
            int val = DECODE_TABLE[c];
            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                result[outIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }

        // Verify remaining bits do not contain non-zero unused bits (preventing bit stuffing / malleability)
        if (bitsLeft > 0) {
            int remainder = buffer & ((1 << bitsLeft) - 1);
            if (remainder != 0) {
                throw new IllegalArgumentException("Malformed Base32: non-zero padding bits detected");
            }
        }

        return result;
    }

    /**
     * Checks if a string is a valid Base32 representation without throwing an exception.
     *
     * @param base32String candidate string
     * @return true if valid Base32, false otherwise
     */
    public static boolean isValid(String base32String) {
        if (base32String == null) {
            return false;
        }
        try {
            decode(base32String);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

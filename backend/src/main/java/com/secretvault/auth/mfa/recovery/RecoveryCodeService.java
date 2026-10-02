package com.secretvault.auth.mfa.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for generating, formatting, normalizing, hashing, and verifying
 * high-entropy MFA backup recovery codes.
 * <p>
 * Uses a non-ambiguous 32-character alphabet (60 bits of cryptographic entropy per code)
 * to prevent user confusion between '0'/'O', '1'/'I'/'L'.
 */
@Service
public class RecoveryCodeService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryCodeService.class);

    // 32-character unambiguous alphanumeric alphabet (excludes 0, O, 1, I, L)
    private static final String UNAMBIGUOUS_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final char[] ALPHABET_CHARS = UNAMBIGUOUS_ALPHABET.toCharArray();
    private static final int CODE_LENGTH = 12; // 12 chars * 5 bits = 60 bits of entropy
    private static final int GROUP_SIZE = 4;
    private static final Pattern CANONICAL_PATTERN = Pattern.compile("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{12}$");

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public RecoveryCodeService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder must not be null");
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a batch of unique, cryptographically secure recovery codes.
     * Default batch size is typically 10 codes.
     *
     * @param count Number of unique codes to generate (between 1 and 20)
     * @return List of formatted recovery codes (e.g. "XXXX-XXXX-XXXX")
     */
    public List<String> generateCodes(int count) {
        if (count <= 0 || count > 20) {
            throw new IllegalArgumentException("Recovery code count must be between 1 and 20");
        }

        Set<String> uniqueCodes = new HashSet<>(count);
        List<String> result = new ArrayList<>(count);

        while (result.size() < count) {
            String code = generateSingleCode();
            if (uniqueCodes.add(code)) {
                result.add(code);
            }
        }

        return result;
    }

    /**
     * Formats a raw unseparated code into user-friendly grouped representation (e.g. "XXXX-XXXX-XXXX").
     */
    public String format(String rawNormalizedCode) {
        String canonical = normalize(rawNormalizedCode);
        StringBuilder sb = new StringBuilder(CODE_LENGTH + (CODE_LENGTH / GROUP_SIZE) - 1);
        for (int i = 0; i < canonical.length(); i++) {
            if (i > 0 && i % GROUP_SIZE == 0) {
                sb.append('-');
            }
            sb.append(canonical.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Normalizes a user-supplied recovery code for verification:
     * - Trims surrounding whitespace
     * - Converts to uppercase
     * - Strips grouping dashes, spaces, and underscores
     * - Validates character set and length
     *
     * @param input Candidate code from user
     * @return Canonical 12-character uppercase string
     * @throws IllegalArgumentException if the code format or character set is invalid
     */
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Recovery code must not be null or blank");
        }

        String cleaned = input.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-_]+", "");
        if (!CANONICAL_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("Invalid recovery code format or characters");
        }
        return cleaned;
    }

    /**
     * Hashes a recovery code for secure database storage using the configured BCrypt encoder.
     * Normalizes the code prior to hashing so formatted or unformatted submissions match.
     *
     * @param rawCode Raw or formatted recovery code
     * @return BCrypt hash string
     */
    public String hash(String rawCode) {
        String canonical = normalize(rawCode);
        return passwordEncoder.encode(canonical);
    }

    /**
     * Verifies whether a candidate recovery code matches a stored cryptographic hash in constant time.
     *
     * @param candidateCode Code submitted by user
     * @param storedHash    Stored BCrypt hash
     * @return true if the code matches the hash, false otherwise
     */
    public boolean matches(String candidateCode, String storedHash) {
        if (candidateCode == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        try {
            String canonical = normalize(candidateCode);
            return passwordEncoder.matches(canonical, storedHash);
        } catch (IllegalArgumentException e) {
            // Malformed candidate code format
            return false;
        }
    }

    private String generateSingleCode() {
        char[] chars = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            chars[i] = ALPHABET_CHARS[secureRandom.nextInt(ALPHABET_CHARS.length)];
        }

        StringBuilder formatted = new StringBuilder(CODE_LENGTH + 2);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % GROUP_SIZE == 0) {
                formatted.append('-');
            }
            formatted.append(chars[i]);
        }
        return formatted.toString();
    }
}

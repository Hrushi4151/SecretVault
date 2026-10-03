package com.secretvault.cli.security;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Centralized redaction utility ensuring sensitive tokens, passwords, and
 * authorization headers are never leaked into logs, diagnostics, or exception messages.
 */
public final class RedactionHelper {

    private static final Pattern BEARER_PATTERN = Pattern.compile("Bearer\\s+[A-Za-z0-9-_=.]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9-_]+\\.eyJ[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+");
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile("(?i)(authorization|proxy-authorization|x-auth-token|x-step-up-proof|x-reveal-intent-token|api-key|secret):\\s*[^\\r\\n]+");
    private static final Pattern PASSWORD_JSON_PATTERN = Pattern.compile("(?i)\"(password|secret|refreshToken|accessToken|value|totpCode|recoveryCode|stepUpProof|token)\"\\s*:\\s*\"[^\"]*\"");

    private RedactionHelper() {}

    /**
     * Sanitizes an arbitrary string by redacting JWTs, Bearer headers, sensitive JSON keys, and auth headers.
     */
    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = BEARER_PATTERN.matcher(input).replaceAll("Bearer [REDACTED]");
        result = JWT_PATTERN.matcher(result).replaceAll("[REDACTED_JWT]");
        result = AUTH_HEADER_PATTERN.matcher(result).replaceAll("$1: [REDACTED]");
        result = PASSWORD_JSON_PATTERN.matcher(result).replaceAll("\"$1\": \"[REDACTED]\"");
        return result;
    }

    /**
     * Safely clears a character array in memory.
     */
    public static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Safely clears a byte array in memory.
     */
    public static void wipe(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}

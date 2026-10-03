package io.secretvault.sdk.observability;

import java.util.regex.Pattern;

/**
 * Centralized utility for masking secrets, tokens, and authorization headers from all logs, metrics, and diagnostics.
 */
public final class RedactionUtil {

    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]+(\\.[a-zA-Z0-9_\\-\\.]+)*");
    private static final Pattern TOKEN_FIELD_PATTERN = Pattern.compile("(?i)(accessToken|refreshToken|token|machineToken|password)[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,]+)");

    private RedactionUtil() {}

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String sanitized = BEARER_PATTERN.matcher(input).replaceAll("Bearer [REDACTED]");
        sanitized = TOKEN_FIELD_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        return sanitized;
    }

    public static String redactSecretValue(String secret) {
        return "[REDACTED]";
    }
}

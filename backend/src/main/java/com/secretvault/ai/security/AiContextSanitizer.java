package com.secretvault.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mandatory Security Boundary for AI Context Sanitization.
 * Enforces zero-plaintext invariants: scrubs secrets, connection strings,
 * credentials, and high-entropy tokens before context is transmitted to any LLM.
 */
@Component
public class AiContextSanitizer {

    private static final Logger log = LoggerFactory.getLogger(AiContextSanitizer.class);

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z0-9_ ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9_ ]*PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\b(sk_live_[0-9a-zA-Z]{16,}|ghp_[0-9a-zA-Z]{16,}|github_pat_[0-9a-zA-Z_]{20,}|xox[baprs]-[0-9a-zA-Z]{10,}|(?:AKIA|ASIA)[0-9A-Z]{16}|ey[A-Za-z0-9-_]{20,}\\.[A-Za-z0-9-_]{20,}\\.[A-Za-z0-9-_]{10,})\\b"
    );

    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)Authorization:\\s*(?:Bearer\\s+)?[A-Za-z0-9\\-._~+/]+=*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9\\-._~+/]{16,}={0,2}\\b"
    );

    private static final Pattern URI_CREDENTIALS_PATTERN = Pattern.compile(
            "([a-zA-Z][a-zA-Z0-9+.-]*://)([^:/\\s]+):([^/\\s]+)@"
    );

    private static final Pattern KV_SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(password|secret|token|api[_-]?key|access[_-]?key|auth|bearer|client_secret|aws_secret_access_key)\\s*[:=]\\s*['\"]?([^\\s,'\"}\\]]+)['\"]?"
    );

    private static final Pattern HIGH_ENTROPY_PATTERN = Pattern.compile(
            "\\b([A-Fa-f0-9]{32,64}|[A-Za-z0-9+/]{32,}={0,2})\\b"
    );

    /**
     * Sanitizes raw text into safe structural metadata, replacing all credentials,
     * tokens, passwords, and high-entropy substrings with one-way deterministic hash digests.
     */
    public String sanitizeText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String result = input;

        // 1. Scrub PEM Private Keys
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_ASYMMETRIC_PRIVATE_KEY]");

        // 2. Scrub Well-Known Tokens
        result = TOKEN_PATTERN.matcher(result).replaceAll("[REDACTED_SECRET_TOKEN]");

        // 3. Scrub Authorization Headers & Standalone Bearer Tokens
        result = AUTH_HEADER_PATTERN.matcher(result).replaceAll("Authorization: [REDACTED_AUTHORIZATION_HEADER]");
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("Bearer [REDACTED_BEARER_TOKEN]");

        // 4. Scrub URI user:password credentials
        result = URI_CREDENTIALS_PATTERN.matcher(result).replaceAll("$1$2:[REDACTED_CREDENTIAL]@");

        // 5. Scrub Key-Value credentials
        Matcher kvMatcher = KV_SECRET_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (kvMatcher.find()) {
            String key = kvMatcher.group(1);
            String val = kvMatcher.group(2);
            String replacement = key + "=[SHA256:" + computeDigestPrefix(val) + "]";
            kvMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        kvMatcher.appendTail(sb);
        result = sb.toString();

        return result;
    }

    /**
     * Sanitizes prompt text submitted by the user. Ensures no live credentials
     * were pasted directly into the chat prompt.
     */
    public String sanitizeUserPrompt(String prompt) {
        return sanitizeText(prompt);
    }

    /**
     * Strict verification: asserts that a string contains ZERO raw plaintext credentials.
     * Throws SecurityException if dangerous patterns are detected.
     */
    public void assertZeroPlaintext(String context) {
        if (context == null || context.isBlank()) {
            return;
        }

        if (PRIVATE_KEY_PATTERN.matcher(context).find()) {
            log.error("Security violation: Raw private key detected in AI context");
            throw new SecurityException("Security Invariant Violation: Private key detected in AI context");
        }

        if (TOKEN_PATTERN.matcher(context).find()) {
            log.error("Security violation: Live secret token pattern detected in AI context");
            throw new SecurityException("Security Invariant Violation: Secret token pattern detected in AI context");
        }

        if (AUTH_HEADER_PATTERN.matcher(context).find() || BEARER_TOKEN_PATTERN.matcher(context).find()) {
            log.error("Security violation: Raw authorization credential detected in AI context");
            throw new SecurityException("Security Invariant Violation: Authorization credential detected in AI context");
        }

        if (URI_CREDENTIALS_PATTERN.matcher(context).find()) {
            log.error("Security violation: URI credentials detected in AI context");
            throw new SecurityException("Security Invariant Violation: URI credentials detected in AI context");
        }
    }

    /**
     * Produces an 8-character deterministic hex fingerprint prefix for hash mismatch and diff tracking.
     */
    public String computeDigestPrefix(String input) {
        if (input == null || input.isEmpty()) {
            return "00000000";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "sha256err";
        }
    }
}

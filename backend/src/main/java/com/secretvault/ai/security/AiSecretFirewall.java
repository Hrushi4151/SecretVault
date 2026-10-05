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
 * Absolute Secret Value Firewall for SecretVault AI Copilot.
 * Enforces zero-plaintext invariants across all AI processing layers:
 * 1. User Inquiries & Conversation Messages
 * 2. Tool Arguments & Invocations
 * 3. Tool Execution Results & Safe Context Assembly
 * 4. Assembled LLM Request Payloads
 * 5. Generated LLM Output Text
 *
 * Replaces live credentials with safe one-way cryptographic SHA-256 fingerprint prefixes.
 */
@Component
public class AiSecretFirewall {

    private static final Logger log = LoggerFactory.getLogger(AiSecretFirewall.class);

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z0-9_ ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9_ ]*PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AWS_KEY_PATTERN = Pattern.compile(
            "\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"
    );

    private static final Pattern TOKEN_PATTERNS = Pattern.compile(
            "\\b(sk_live_[0-9a-zA-Z]{16,}|ghp_[0-9a-zA-Z]{16,}|github_pat_[0-9a-zA-Z_]{20,}|xox[baprs]-[0-9a-zA-Z]{10,}|glpat-[0-9a-zA-Z\\-_]{20,})\\b"
    );

    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)Authorization:\\s*(?:Bearer\\s+|Basic\\s+)?[A-Za-z0-9\\-._~+/]+=*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9\\-._~+/]{16,}={0,2}\\b"
    );

    private static final Pattern URI_CREDENTIALS_PATTERN = Pattern.compile(
            "([a-zA-Z][a-zA-Z0-9+.-]*://)([^:/\\s]+):([^/\\s@]+)@"
    );

    private static final Pattern RAW_URI_CREDENTIALS_PATTERN = Pattern.compile(
            "([a-zA-Z][a-zA-Z0-9+.-]*://)([^:/\\s]+):(?!(\\[REDACTED))([^/\\s@]+)@"
    );

    private static final Pattern RAW_AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)Authorization:\\s*(?:Bearer\\s+|Basic\\s+)?(?!(\\[REDACTED))[A-Za-z0-9\\-._~+/]{10,}=*"
    );

    private static final Pattern RAW_BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+(?!(\\[REDACTED))[A-Za-z0-9\\-._~+/]{16,}={0,2}\\b"
    );

    private static final Pattern RAW_AWS_KEY_PATTERN = Pattern.compile(
            "\\b(?:AKIA|ASIA)(?!(\\[REDACTED))[0-9A-Z]{16}\\b"
    );

    private static final Pattern KV_SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(password|secret|token|api[_-]?key|access[_-]?key|auth|bearer|client_secret|aws_secret_access_key|private_key|database_url|connection_string)\\s*[:=]\\s*['\"]?([^\\s,'\"}\\]]+)['\"]?"
    );

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9-_]{10,}\\.eyJ[A-Za-z0-9-_]{10,}\\.[A-Za-z0-9-_]{10,}\\b"
    );

    /**
     * Sanitizes any arbitrary text input by redacting detected credentials and replacing
     * secret values with one-way SHA-256 fingerprint prefixes.
     */
    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String result = input;

        // 1. Asymmetric Private Keys
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_ASYMMETRIC_PRIVATE_KEY]");

        // 2. AWS Key IDs
        result = AWS_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_AWS_KEY_ID]");

        // 3. Known Tokens (GitHub, Stripe, Slack, GitLab)
        result = TOKEN_PATTERNS.matcher(result).replaceAll("[REDACTED_SECRET_TOKEN]");

        // 4. JWT Tokens
        result = JWT_PATTERN.matcher(result).replaceAll("[REDACTED_JWT_TOKEN]");

        // 5. Authorization Headers and Standalone Bearer Tokens
        result = AUTH_HEADER_PATTERN.matcher(result).replaceAll("Authorization: [REDACTED_AUTHORIZATION_HEADER]");
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("Bearer [REDACTED_BEARER_TOKEN]");

        // 6. URI user:password credentials
        result = URI_CREDENTIALS_PATTERN.matcher(result).replaceAll("$1$2:[REDACTED_CREDENTIAL]@");

        // 7. Key-Value credential pairs (e.g. password: xyz, api_key=abc)
        Matcher kvMatcher = KV_SECRET_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (kvMatcher.find()) {
            String key = kvMatcher.group(1);
            String val = kvMatcher.group(2);
            // Ignore trivial values or already redacted values
            if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false") || val.equalsIgnoreCase("null") || val.startsWith("[SHA256:") || val.startsWith("[REDACTED") || val.length() < 3) {
                kvMatcher.appendReplacement(sb, Matcher.quoteReplacement(kvMatcher.group(0)));
            } else {
                String replacement = key + "=[SHA256:" + computeDigestPrefix(val) + "]";
                kvMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        kvMatcher.appendTail(sb);
        result = sb.toString();

        return result;
    }

    /**
     * Sanitizes tool output JSON before passing into the AI context.
     */
    public String sanitizeToolOutput(String toolName, String outputJson) {
        if (outputJson == null || outputJson.isBlank()) {
            return "{}";
        }
        return sanitize(outputJson);
    }

    /**
     * Validates and scrubs LLM response text before it is returned to the user or caller.
     */
    public String sanitizeLlmResponse(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return "";
        }
        return sanitize(responseText);
    }

    /**
     * Strict verification: throws a SecurityException if dangerous raw plaintext patterns are detected.
     */
    public void assertZeroPlaintext(String context) {
        if (context == null || context.isBlank()) {
            return;
        }

        if (PRIVATE_KEY_PATTERN.matcher(context).find()) {
            log.error("Security violation: Raw private key detected in AI context");
            throw new SecurityException("Security Invariant Violation: Private key detected in AI context");
        }

        if (TOKEN_PATTERNS.matcher(context).find()) {
            log.error("Security violation: Live secret token pattern detected in AI context");
            throw new SecurityException("Security Invariant Violation: Secret token pattern detected in AI context");
        }

        if (RAW_AUTH_HEADER_PATTERN.matcher(context).find() || RAW_BEARER_TOKEN_PATTERN.matcher(context).find()) {
            log.error("Security violation: Raw authorization credential detected in AI context");
            throw new SecurityException("Security Invariant Violation: Authorization credential detected in AI context");
        }

        if (RAW_URI_CREDENTIALS_PATTERN.matcher(context).find()) {
            log.error("Security violation: URI credentials detected in AI context");
            throw new SecurityException("Security Invariant Violation: URI credentials detected in AI context");
        }
    }

    /**
     * Produces an 8-character deterministic hex fingerprint prefix for zero-knowledge tracking.
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

package com.secretvault.repository.engine;

import com.secretvault.repository.model.RepoFindingConfidence;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Context Analyzer inspects surrounding context, variable names, file paths,
 * and assignment structures to adjust detection confidence and identify sample/test tokens.
 */
@Component
public class ContextAnalyzer {

    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key", "access_key",
            "secret_key", "client_secret", "private_key", "auth_token", "jwt_secret",
            "db_pass", "database_password", "db_password", "connection_string", "webhook_secret",
            "master_key", "api_secret", "ai_gemini_api_key", "gemini_api_key", "openai_api_key",
            "access_token", "bearer_token", "session_token", "refresh_token", "oauth_token",
            "database_url", "db_url", "db_uri", "database_uri", "signing_secret", "encryption_key"
    );

    private static final Set<String> EXACT_PLACEHOLDER_VALUES = Set.of(
            "your_api_key_here", "your_secret_here", "your_password_here", "your_token_here",
            "your_key_here", "your_api_key", "your_secret", "your_token", "your_password",
            "insert_api_key_here", "insert_token_here", "insert_secret_here", "insert_password_here",
            "replace_me", "replace_me_with_real_secret", "replace_with_key", "replace_with_actual_secret",
            "changeme", "change_me", "change_this", "change_this_secret", "change_this_password",
            "dummy", "dummy-password", "dummy-password-sample", "dummy_password", "dummy_secret", "dummy_token", "dummy123",
            "sample", "sample_password", "sample_secret", "sample_token", "sample_api_key", "example", "example_secret",
            "placeholder", "fake", "fake_key", "fake_secret", "fake_token",
            "enter_password_here", "your-secret-here", "my_secret_key", "my_api_key", "my_password",
            "00000000", "0000000000000000", "11111111", "1111111111111111", "xxxxxxxx", "xxxxxxxxxxxxxxxx",
            "abcdef", "abcdefabcdefabcdef", "password", "secret", "admin", "root", "test", "test123",
            "123456", "12345678", "1234567890", "qwerty", "undefined", "null", "none", "nan", "true", "false"
    );

    private static final Set<String> SNIPPET_PLACEHOLDER_INDICATORS = Set.of(
            "your_api_key_here", "your_secret_here", "your_password_here", "your_token_here",
            "insert_api_key_here", "insert_token_here", "insert_secret_here", "insert_password_here",
            "replace_me", "replace_me_with_real_secret", "replace_with_key", "replace_with_actual_secret",
            "changeme", "change_me", "change_this",
            "dummy-password", "dummy-password-sample", "dummy_password", "dummy_secret",
            "sample_password", "sample_secret", "example_secret", "enter_password_here", "your-secret-here"
    );

    private static final Pattern TEST_PATH_PATTERN = Pattern.compile(
            "(?i)(/|^)(test|tests|__tests__|spec|specs|mock|mocks|fixtures|fixture|testdata|samples|examples)/|.*(test|spec)\\.[a-zA-Z0-9]+$"
    );

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)^(your_.*_here|insert_.*_here|replace_.*_here|replace_me.*|change_?me.*|<.*>|\\$\\{.*\\}|process\\.env\\..*|System\\.getenv\\(.*\\)|config\\.get\\(.*\\)|xxx+|000+|111+)$"
    );

    public boolean isPlaceholderValue(String matchedValue) {
        if (matchedValue == null || matchedValue.isBlank()) {
            return true;
        }
        String trimmed = matchedValue.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (EXACT_PLACEHOLDER_VALUES.contains(lower)) {
            return true;
        }

        if (PLACEHOLDER_PATTERN.matcher(trimmed).matches()) {
            return true;
        }

        // Check if expression reference rather than hardcoded value
        if (trimmed.startsWith("${") || trimmed.startsWith("<") || trimmed.startsWith("process.env.") || trimmed.startsWith("System.getenv")) {
            return true;
        }

        // Check local development / doc example connection strings
        if (trimmed.contains("://") && (trimmed.contains("@localhost") || trimmed.contains("@127.0.0.1") ||
                trimmed.contains("@example.com") || trimmed.contains(":password@") ||
                trimmed.contains(":dummy@") || trimmed.contains(":changeme@") || trimmed.contains(":test@"))) {
            return true;
        }

        return false;
    }

    public boolean isTestOrSampleContext(String filePath, String contextSnippet, String matchedValue) {
        if (isPlaceholderValue(matchedValue)) {
            return true;
        }
        if (contextSnippet != null) {
            String lowerSnippet = contextSnippet.toLowerCase(Locale.ROOT);
            for (String p : SNIPPET_PLACEHOLDER_INDICATORS) {
                if (lowerSnippet.contains(p)) {
                    return true;
                }
            }
        }
        return isTestFilePath(filePath);
    }

    public boolean isTestFilePath(String filePath) {
        if (filePath == null) return false;
        String normalized = filePath.replace('\\', '/');
        return TEST_PATH_PATTERN.matcher(normalized).find();
    }

    public boolean hasHighRiskKeywordContext(String lineContent) {
        if (lineContent == null) return false;
        String lower = lineContent.toLowerCase(Locale.ROOT);
        for (String kw : HIGH_RISK_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public RepoFindingConfidence evaluateConfidence(
            boolean isKnownPattern,
            boolean isHighEntropy,
            boolean hasKeywordContext,
            boolean isTestPath,
            boolean isSampleContent) {

        if (isSampleContent) {
            return RepoFindingConfidence.LOW;
        }

        if (isKnownPattern && hasKeywordContext) {
            return isTestPath ? RepoFindingConfidence.HIGH : RepoFindingConfidence.VERY_HIGH;
        }

        if (isKnownPattern) {
            return isTestPath ? RepoFindingConfidence.MEDIUM : RepoFindingConfidence.VERY_HIGH;
        }

        if (hasKeywordContext && isHighEntropy) {
            return isTestPath ? RepoFindingConfidence.MEDIUM : RepoFindingConfidence.HIGH;
        }

        if (hasKeywordContext || isHighEntropy) {
            return isTestPath ? RepoFindingConfidence.LOW : RepoFindingConfidence.MEDIUM;
        }

        return RepoFindingConfidence.LOW;
    }
}

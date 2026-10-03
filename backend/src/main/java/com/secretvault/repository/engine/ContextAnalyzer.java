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
            "db_pass", "database_password", "connection_string", "webhook_secret", "master_key"
    );

    private static final Set<String> SAMPLE_PATTERNS = Set.of(
            "dummy", "example", "sample", "fake", "placeholder", "changeme",
            "replace_me", "mock", "test123", "your_api_key_here", "insert_token_here",
            "xxxx", "00000000", "11111111", "abcdef"
    );

    private static final Pattern TEST_PATH_PATTERN = Pattern.compile(
            "(?i)(/|^)(test|tests|spec|specs|mock|fixtures|fixture|testdata|samples|examples)/|.*(test|spec)\\.[a-zA-Z0-9]+$"
    );

    public boolean isTestOrSampleContext(String filePath, String contextSnippet, String matchedValue) {
        String lowerSnippet = (contextSnippet != null ? contextSnippet : "").toLowerCase(Locale.ROOT);
        String lowerVal = (matchedValue != null ? matchedValue : "").toLowerCase(Locale.ROOT);

        for (String sample : SAMPLE_PATTERNS) {
            if (lowerSnippet.contains(sample) || lowerVal.contains(sample)) {
                return true;
            }
        }
        return false;
    }

    public boolean isTestFilePath(String filePath) {
        if (filePath == null) return false;
        return TEST_PATH_PATTERN.matcher(filePath).find();
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

        if (isKnownPattern) {
            if (isTestPath) {
                return RepoFindingConfidence.MEDIUM;
            }
            return RepoFindingConfidence.VERY_HIGH;
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

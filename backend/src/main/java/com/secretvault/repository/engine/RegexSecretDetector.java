package com.secretvault.repository.engine;

import com.secretvault.repository.model.RepoFindingConfidence;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.SecretType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based secret detection engine with bounded ReDoS-safe patterns,
 * Shannon entropy analysis, context scoring, and zero-plaintext leakage.
 */
@Component
public class RegexSecretDetector implements SecretDetector {

    private final SecretFingerprinter fingerprinter;
    private final EntropyEvaluator entropyEvaluator;
    private final ContextAnalyzer contextAnalyzer;

    public RegexSecretDetector(
            SecretFingerprinter fingerprinter,
            EntropyEvaluator entropyEvaluator,
            ContextAnalyzer contextAnalyzer) {
        this.fingerprinter = fingerprinter;
        this.entropyEvaluator = entropyEvaluator;
        this.contextAnalyzer = contextAnalyzer;
    }

    private static final class PatternDefinition {
        final String detectorName;
        final SecretType secretType;
        final RepoFindingSeverity severity;
        final Pattern pattern;
        final int secretGroup;
        final boolean requiresHighEntropy;

        PatternDefinition(String detectorName, SecretType secretType, RepoFindingSeverity severity, String regex, int secretGroup, boolean requiresHighEntropy) {
            this.detectorName = detectorName;
            this.secretType = secretType;
            this.severity = severity;
            this.pattern = Pattern.compile(regex);
            this.secretGroup = secretGroup;
            this.requiresHighEntropy = requiresHighEntropy;
        }
    }

    private final List<PatternDefinition> patterns = List.of(
            // AWS Access Key ID
            new PatternDefinition("AWS Access Key", SecretType.AWS_ACCESS_KEY, RepoFindingSeverity.HIGH,
                    "\\b(AKIA[0-9A-Z]{16})\\b", 1, false),

            // AWS Secret Key
            new PatternDefinition("AWS Secret Key", SecretType.AWS_SECRET_KEY, RepoFindingSeverity.CRITICAL,
                    "(?i)(?:aws_secret_access_key|aws_secret_key|secret_access_key)\\s*[=:]\\s*['\"]?([A-Za-z0-9/+=]{40})['\"]?", 1, true),

            // GitHub Classic PAT / Fine-Grained Token
            new PatternDefinition("GitHub Token", SecretType.GITHUB_TOKEN, RepoFindingSeverity.CRITICAL,
                    "\\b(gh[pous]_[0-9a-zA-Z]{36}|github_pat_[0-9a-zA-Z_]{22,82})\\b", 1, false),

            // GitLab Personal Access Token
            new PatternDefinition("GitLab Token", SecretType.GITLAB_TOKEN, RepoFindingSeverity.CRITICAL,
                    "\\b(glpat-[0-9a-zA-Z_\\-]{20,40})\\b", 1, false),

            // Google API Key
            new PatternDefinition("Google API Key", SecretType.GOOGLE_API_KEY, RepoFindingSeverity.HIGH,
                    "\\b(AIza[0-9A-Za-z_\\-]{35})\\b", 1, false),

            // Slack Bot / User Token
            new PatternDefinition("Slack Token", SecretType.SLACK_TOKEN, RepoFindingSeverity.HIGH,
                    "\\b(xox[baprs]-[0-9]{10,13}-[0-9]{10,13}-[a-zA-Z0-9]{24,34})\\b", 1, false),

            // Stripe Live Secret Key
            new PatternDefinition("Stripe Live Secret Key", SecretType.STRIPE_KEY, RepoFindingSeverity.CRITICAL,
                    "\\b([sr]k_live_[0-9a-zA-Z]{24,34})\\b", 1, false),

            // Twilio Account SID & Auth Token
            new PatternDefinition("Twilio Token", SecretType.GENERIC_TOKEN, RepoFindingSeverity.HIGH,
                    "\\b(AC[0-9a-fA-F]{32}|SK[0-9a-fA-F]{32})\\b", 1, false),

            // SendGrid API Key
            new PatternDefinition("SendGrid API Key", SecretType.GENERIC_API_KEY, RepoFindingSeverity.HIGH,
                    "\\b(SG\\.[0-9a-zA-Z_\\-]{22}\\.[0-9a-zA-Z_\\-]{43})\\b", 1, false),

            // RSA / OpenSSH Private Key Header
            new PatternDefinition("SSH/Private Key", SecretType.SSH_PRIVATE_KEY, RepoFindingSeverity.CRITICAL,
                    "(-----BEGIN (?:RSA|OPENSSH|DSA|EC|PGP)? PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA|OPENSSH|DSA|EC|PGP)? PRIVATE KEY-----)", 1, false),

            // Database Connection String with Credentials
            new PatternDefinition("Database Connection String", SecretType.DATABASE_CONNECTION_STRING, RepoFindingSeverity.CRITICAL,
                    "\\b(?:postgres|postgresql|mysql|mongodb(?:\\+srv)?|redis)://([^:]+):([^@\\s'\"]+)@([^/\\s'\"]+)/[^\\s'\"]+", 0, false),

            // JWT Token (3 base64url segments separated by dots)
            new PatternDefinition("JWT Token", SecretType.JWT, RepoFindingSeverity.MEDIUM,
                    "\\b(eyJ[A-Za-z0-9-_]{10,}\\.eyJ[A-Za-z0-9-_]{10,}\\.[A-Za-z0-9-_]{10,})\\b", 1, true),

            // Generic Password assignment
            new PatternDefinition("Hardcoded Password", SecretType.GENERIC_PASSWORD, RepoFindingSeverity.HIGH,
                    "(?i)(?:password|passwd|pwd|db_pass|database_password)\\s*[=:]\\s*['\"]([^'\"\\r\\n\\t\\f\\v]{8,64})['\"]", 1, false),

            // Generic API Key assignment
            new PatternDefinition("Hardcoded API Key", SecretType.GENERIC_API_KEY, RepoFindingSeverity.HIGH,
                    "(?i)(?:api_key|apikey|secret_key|client_secret)\\s*[=:]\\s*['\"]([^'\"\\r\\n\\t\\f\\v]{12,128})['\"]", 1, true)
    );

    @Override
    public String getName() {
        return "RegexSecretDetector";
    }

    @Override
    public List<SecretDetectionResult> scanContent(
            String content, String filePath, String commitSha, String branch, String author) {

        if (content == null || content.isEmpty()) {
            return List.of();
        }

        List<SecretDetectionResult> results = new ArrayList<>();
        boolean isTestPath = contextAnalyzer.isTestFilePath(filePath);

        // Precompute line offsets for accurate line and column tracking
        String[] lines = content.split("\r\n|\r|\n", -1);

        for (PatternDefinition def : patterns) {
            Matcher matcher = def.pattern.matcher(content);
            while (matcher.find()) {
                String rawSecret = matcher.group(def.secretGroup);
                if (rawSecret == null || rawSecret.isBlank()) {
                    continue;
                }

                // If detector requires high entropy, verify entropy before reporting
                double entropy = entropyEvaluator.calculateEntropy(rawSecret);
                if (def.requiresHighEntropy && !entropyEvaluator.isHighEntropy(rawSecret)) {
                    continue;
                }

                int matchStart = matcher.start(def.secretGroup);
                int[] lineAndCol = findLineAndColumn(lines, matchStart);
                int lineNumber = lineAndCol[0];
                int columnNumber = lineAndCol[1];

                String lineContent = (lineNumber <= lines.length) ? lines[lineNumber - 1] : "";
                boolean isSample = contextAnalyzer.isTestOrSampleContext(filePath, lineContent, rawSecret);
                boolean hasKeyword = contextAnalyzer.hasHighRiskKeywordContext(lineContent);

                RepoFindingConfidence confidence = contextAnalyzer.evaluateConfidence(
                        true, entropyEvaluator.isHighEntropy(rawSecret), hasKeyword, isTestPath, isSample);

                String fingerprint = fingerprinter.computeFingerprint(rawSecret);
                String maskedValue = fingerprinter.computeMaskedPreview(rawSecret, def.secretType.name());

                String summary = String.format("Detected %s via %s (entropy: %.2f)",
                        def.secretType.name(), def.detectorName, entropy);

                // If marked sample in test path, reduce severity to LOW
                RepoFindingSeverity severity = (isSample || isTestPath) && def.severity == RepoFindingSeverity.CRITICAL
                        ? RepoFindingSeverity.MEDIUM
                        : def.severity;

                results.add(new SecretDetectionResult(
                        def.secretType,
                        def.detectorName,
                        fingerprint,
                        maskedValue,
                        filePath,
                        lineNumber,
                        columnNumber,
                        severity,
                        confidence,
                        entropy,
                        commitSha,
                        branch,
                        author,
                        summary,
                        null
                ));
            }
        }

        return results;
    }

    private int[] findLineAndColumn(String[] lines, int charOffset) {
        int currentOffset = 0;
        for (int i = 0; i < lines.length; i++) {
            int lineLenWithNewline = lines[i].length() + 1; // approximate newline
            if (charOffset < currentOffset + lineLenWithNewline) {
                int col = charOffset - currentOffset + 1;
                return new int[]{i + 1, Math.max(1, col)};
            }
            currentOffset += lineLenWithNewline;
        }
        return new int[]{lines.length, 1};
    }
}

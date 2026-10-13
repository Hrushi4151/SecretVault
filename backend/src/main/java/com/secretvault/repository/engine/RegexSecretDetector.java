package com.secretvault.repository.engine;

import com.secretvault.repository.model.RepoFindingConfidence;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.SecretType;
import org.springframework.stereotype.Component;

import java.util.*;
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
        final boolean requiresSufficientEntropy;

        PatternDefinition(String detectorName, SecretType secretType, RepoFindingSeverity severity, String regex, int secretGroup, boolean requiresHighEntropy, boolean requiresSufficientEntropy) {
            this.detectorName = detectorName;
            this.secretType = secretType;
            this.severity = severity;
            this.pattern = Pattern.compile(regex);
            this.secretGroup = secretGroup;
            this.requiresHighEntropy = requiresHighEntropy;
            this.requiresSufficientEntropy = requiresSufficientEntropy;
        }

        PatternDefinition(String detectorName, SecretType secretType, RepoFindingSeverity severity, String regex, int secretGroup, boolean requiresHighEntropy) {
            this(detectorName, secretType, severity, regex, secretGroup, requiresHighEntropy, false);
        }
    }

    private final List<PatternDefinition> patterns = List.of(
            // AWS Access Key ID
            new PatternDefinition("AWS Access Key", SecretType.AWS_ACCESS_KEY, RepoFindingSeverity.HIGH,
                    "\\b((?:AKIA|ASIA)[0-9A-Z]{16})\\b", 1, false),

            // AWS Secret Key
            new PatternDefinition("AWS Secret Key", SecretType.AWS_SECRET_KEY, RepoFindingSeverity.CRITICAL,
                    "(?i)(?:aws_secret_access_key|aws_secret_key|secret_access_key)\\s*[:=]\\s*['\"`]?([A-Za-z0-9/+=]{40})['\"`]?", 1, true),

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

            // Database Connection String with embedded credentials
            new PatternDefinition("Database Connection String", SecretType.DATABASE_CONNECTION_STRING, RepoFindingSeverity.CRITICAL,
                    "\\b(?:postgres|postgresql|mysql|mongodb(?:\\+srv)?|redis|mariadb|sqlserver)://([^:]+):([^@\\s'\"`]+)@([^/\\s'\"`]+)/[^\\s'\"`]+", 0, false),

            // JWT Token (3 base64url segments separated by dots)
            new PatternDefinition("JWT Token", SecretType.JWT, RepoFindingSeverity.MEDIUM,
                    "\\b(eyJ[A-Za-z0-9-_]{10,}\\.eyJ[A-Za-z0-9-_]{10,}\\.[A-Za-z0-9-_]{10,})\\b", 1, true),

            // Generic API Key / Secret assignment (e.g. AI_GEMINI_API_KEY, API_SECRET, CLIENT_SECRET, SECRET_KEY, OPENAI_API_KEY)
            new PatternDefinition("Hardcoded API Key", SecretType.GENERIC_API_KEY, RepoFindingSeverity.HIGH,
                    "(?i)(?:const|let|var|export\\s+const|export\\s+let|export\\s+var)?\\s*(?:[a-zA-Z0-9_]*_)?(?:api_key|apikey|gemini_api_key|ai_gemini_api_key|openai_api_key|api_secret|client_secret|secret_key|secret_token|auth_key|app_secret)(?:_[a-zA-Z0-9_]+)?\\s*[:=]\\s*['\"`]([^'\"`\\r\\n\\t\\f\\v]{10,256})['\"`]", 1, false, true),

            // Generic Password assignment (e.g. DB_PASSWORD, DATABASE_PASSWORD, ADMIN_PASSWORD, PASSWORD, PASSWD, PWD)
            new PatternDefinition("Hardcoded Password", SecretType.DATABASE_PASSWORD, RepoFindingSeverity.HIGH,
                    "(?i)(?:const|let|var|export\\s+const|export\\s+let|export\\s+var)?\\s*(?:[a-zA-Z0-9_]*_)?(?:password|passwd|pwd|db_pass|database_password|db_password|admin_password|root_password|master_password|secret_password)(?:_[a-zA-Z0-9_]+)?\\s*[:=]\\s*['\"`]([^'\"`\\r\\n\\t\\f\\v]{6,128})['\"`]", 1, false, true),

            // Generic Access / Auth / Bearer Token assignment (e.g. ACCESS_TOKEN, AUTH_TOKEN, BEARER_TOKEN, REFRESH_TOKEN)
            new PatternDefinition("Hardcoded Access Token", SecretType.GENERIC_TOKEN, RepoFindingSeverity.HIGH,
                    "(?i)(?:const|let|var|export\\s+const|export\\s+let|export\\s+var)?\\s*(?:[a-zA-Z0-9_]*_)?(?:access_token|auth_token|bearer_token|session_token|refresh_token|api_token|private_token|oauth_token|webhook_secret|signing_secret|jwt_secret)(?:_[a-zA-Z0-9_]+)?\\s*[:=]\\s*['\"`]([^'\"`\\r\\n\\t\\f\\v]{10,256})['\"`]", 1, false, true),

            // Database URL / Connection String assignment (e.g. DATABASE_URL = "postgres://...")
            new PatternDefinition("Database URL Assignment", SecretType.DATABASE_CONNECTION_STRING, RepoFindingSeverity.CRITICAL,
                    "(?i)(?:const|let|var|export\\s+const|export\\s+let|export\\s+var)?\\s*(?:[a-zA-Z0-9_]*_)?(?:database_url|db_url|db_uri|database_uri|connection_string|db_connection|mongo_uri|mongodb_uri|redis_url|postgres_url)(?:_[a-zA-Z0-9_]+)?\\s*[:=]\\s*['\"`]?([^'\"`\\s\\r\\n]{10,512})['\"`]?", 1, false, false),

            // Unquoted Configuration Assignment (.env / yaml / properties) for API keys, secrets, passwords, and tokens
            new PatternDefinition("Configuration Credential Assignment", SecretType.GENERIC_API_KEY, RepoFindingSeverity.HIGH,
                    "(?i)^(?:export\\s+)?([A-Z0-9_]*(?:API_KEY|API_SECRET|CLIENT_SECRET|SECRET_KEY|DB_PASSWORD|DATABASE_PASSWORD|ACCESS_TOKEN|AUTH_TOKEN|DATABASE_URL)[A-Z0-9_]*)\\s*=\\s*['\"`]?([^'\"`\\s\\r\\n#]{8,256})['\"`]?$", 2, false, true)
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

        String normalizedPath = filePath != null ? filePath.replace('\\', '/') : "unknown";
        boolean isTestPath = contextAnalyzer.isTestFilePath(normalizedPath);

        // Precompute line offsets for accurate line and column tracking
        String[] lines = content.split("\r\n|\r|\n", -1);
        Map<String, SecretDetectionResult> deduplicatedResults = new LinkedHashMap<>();

        for (PatternDefinition def : patterns) {
            Matcher matcher = def.pattern.matcher(content);
            while (matcher.find()) {
                String rawSecret = matcher.group(def.secretGroup);
                if (rawSecret == null || rawSecret.isBlank()) {
                    continue;
                }

                String trimmedSecret = rawSecret.trim();

                // Skip code references / env interpolations
                if (trimmedSecret.startsWith("${") || trimmedSecret.startsWith("<") ||
                        trimmedSecret.startsWith("process.env.") || trimmedSecret.startsWith("System.getenv")) {
                    continue;
                }

                // Filter explicit placeholder strings
                if (contextAnalyzer.isPlaceholderValue(trimmedSecret)) {
                    continue;
                }

                // If detector requires high entropy, verify entropy before reporting
                double entropy = entropyEvaluator.calculateEntropy(trimmedSecret);
                if (def.requiresHighEntropy && !entropyEvaluator.isHighEntropy(trimmedSecret)) {
                    continue;
                }

                // If detector requires sufficient entropy, filter out trivial repeated strings
                if (def.requiresSufficientEntropy && (!entropyEvaluator.isSufficientEntropy(trimmedSecret) || entropyEvaluator.isTrivialString(trimmedSecret))) {
                    continue;
                }

                int matchStart = matcher.start(def.secretGroup);
                int[] lineAndCol = findLineAndColumn(lines, matchStart);
                int lineNumber = lineAndCol[0];
                int columnNumber = lineAndCol[1];

                String lineContent = (lineNumber <= lines.length) ? lines[lineNumber - 1] : "";
                boolean isSample = contextAnalyzer.isTestOrSampleContext(normalizedPath, lineContent, trimmedSecret);
                boolean hasKeyword = contextAnalyzer.hasHighRiskKeywordContext(lineContent);

                RepoFindingConfidence confidence = contextAnalyzer.evaluateConfidence(
                        true, entropyEvaluator.isHighEntropy(trimmedSecret), hasKeyword, isTestPath, isSample);

                String fingerprint = fingerprinter.computeFingerprint(trimmedSecret);
                String maskedValue = fingerprinter.computeMaskedPreview(trimmedSecret, def.secretType.name());

                // Resolve refined secret type based on identifier match context
                SecretType resolvedType = def.secretType;
                String lowerLine = lineContent.toLowerCase(Locale.ROOT);
                if (def.detectorName.equals("Hardcoded Password") && !lowerLine.contains("db") && !lowerLine.contains("database")) {
                    resolvedType = SecretType.GENERIC_PASSWORD;
                } else if (def.detectorName.equals("Configuration Credential Assignment")) {
                    if (lowerLine.contains("password") || lowerLine.contains("passwd")) {
                        resolvedType = SecretType.DATABASE_PASSWORD;
                    } else if (lowerLine.contains("token")) {
                        resolvedType = SecretType.GENERIC_TOKEN;
                    } else if (lowerLine.contains("database_url") || lowerLine.contains("db_url")) {
                        resolvedType = SecretType.DATABASE_CONNECTION_STRING;
                    } else {
                        resolvedType = SecretType.GENERIC_API_KEY;
                    }
                }

                String summary = String.format("Detected %s via %s (entropy: %.2f)",
                        resolvedType.name(), def.detectorName, entropy);

                RepoFindingSeverity severity = def.severity;

                SecretDetectionResult findingResult = new SecretDetectionResult(
                        resolvedType,
                        def.detectorName,
                        fingerprint,
                        maskedValue,
                        normalizedPath,
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
                );

                String dedupKey = normalizedPath + ":" + lineNumber + ":" + fingerprint;
                if (!deduplicatedResults.containsKey(dedupKey) ||
                        (severity == RepoFindingSeverity.CRITICAL && deduplicatedResults.get(dedupKey).severity() != RepoFindingSeverity.CRITICAL)) {
                    deduplicatedResults.put(dedupKey, findingResult);
                }
            }
        }

        return new ArrayList<>(deduplicatedResults.values());
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

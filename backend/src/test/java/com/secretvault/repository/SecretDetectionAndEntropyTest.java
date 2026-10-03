package com.secretvault.repository;

import com.secretvault.repository.engine.ContextAnalyzer;
import com.secretvault.repository.engine.EntropyEvaluator;
import com.secretvault.repository.engine.RegexSecretDetector;
import com.secretvault.repository.engine.SecretDetectionResult;
import com.secretvault.repository.engine.SecretFingerprinter;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.SecretType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 12: Secret Detection, Entropy & Fingerprinting Invariants")
class SecretDetectionAndEntropyTest {

    private final EntropyEvaluator entropyEvaluator = new EntropyEvaluator();
    private final SecretFingerprinter fingerprinter = new SecretFingerprinter();
    private final ContextAnalyzer contextAnalyzer = new ContextAnalyzer();
    private final RegexSecretDetector regexDetector = new RegexSecretDetector(fingerprinter, entropyEvaluator, contextAnalyzer);

    @Test
    @DisplayName("EntropyEvaluator correctly measures Shannon entropy and thresholds")
    void testEntropyEvaluation() {
        // High entropy: random 32 character hex / base64 string
        String highEntropy = "b4f8a91c7e2d3f6a8b1c4d9e0f3a5c7e";
        double entropy = entropyEvaluator.calculateEntropy(highEntropy);
        assertTrue(entropy >= 3.0, "High entropy hex string should exceed 3.0: " + entropy);
        assertTrue(entropyEvaluator.isHighEntropy(highEntropy));

        // Low entropy: repeated string
        String lowEntropy = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        double low = entropyEvaluator.calculateEntropy(lowEntropy);
        assertEquals(0.0, low, 0.001);
        assertFalse(entropyEvaluator.isHighEntropy(lowEntropy));

        // Empty / single character
        assertEquals(0.0, entropyEvaluator.calculateEntropy(""));
        assertEquals(0.0, entropyEvaluator.calculateEntropy(null));
    }

    @Test
    @DisplayName("SecretFingerprinter enforces zero plaintext leakage via masking and SHA-256")
    void testSecretFingerprinterInvariants() {
        String rawSecret = "AK" + "IAIOSFODNN7EXAMPLE";
        String fingerprint = fingerprinter.computeFingerprint(rawSecret);

        assertNotNull(fingerprint);
        assertEquals(64, fingerprint.length(), "SHA-256 hex must be exactly 64 characters");
        assertFalse(fingerprint.contains("AKIA"), "Fingerprint must not contain raw secret tokens");

        String masked = fingerprinter.computeMaskedPreview(rawSecret, "AWS_ACCESS_KEY");
        assertNotNull(masked);
        assertTrue(masked.startsWith("AKIA"));
        assertTrue(masked.endsWith("MPLE"));
        assertTrue(masked.contains("************"));
        assertFalse(masked.equals(rawSecret), "Masked value must never equal raw secret");

        // Short secret masking safety
        String shortSecret = "secret";
        String shortMasked = fingerprinter.computeMaskedPreview(shortSecret, "GENERIC_PASSWORD");
        assertTrue(shortMasked.contains("*"));
        assertFalse(shortMasked.equals(shortSecret));
    }

    @Test
    @DisplayName("ContextAnalyzer identifies keyword contexts and flags placeholders")
    void testContextAnalyzer() {
        assertTrue(contextAnalyzer.hasHighRiskKeywordContext("const apiKey = 'val';"));
        assertTrue(contextAnalyzer.hasHighRiskKeywordContext("export AWS_SECRET_KEY=xxx"));
        assertTrue(contextAnalyzer.hasHighRiskKeywordContext("password: secretpassword"));

        // Placeholders and examples should be flagged as likely false positives
        assertTrue(contextAnalyzer.isTestOrSampleContext("config.js", "your_api_key_here", "dummy"));
        assertTrue(contextAnalyzer.isTestFilePath("test/spec.js"));
        assertTrue(contextAnalyzer.isTestFilePath("src/test/java/TestApp.java"));
        assertFalse(contextAnalyzer.isTestFilePath("src/main/java/MainApp.java"));
    }

    @Test
    @DisplayName("RegexSecretDetector reliably detects AWS access keys")
    void testDetectAwsAccessKey() {
        String testKey = "AK" + "IAIOSFODNN7EXAMP12";
        String content = "aws_access_key_id = " + testKey + "\nregion = us-west-2";
        List<SecretDetectionResult> matches = regexDetector.scanContent(content, "config.py", "abc1234", "main", "alice");

        assertFalse(matches.isEmpty(), "Should detect AWS Access Key ID");
        SecretDetectionResult match = matches.stream()
                .filter(m -> m.secretType() == SecretType.AWS_ACCESS_KEY)
                .findFirst()
                .orElse(null);

        assertNotNull(match);
        assertEquals("AKIA************MP12", match.maskedValue());
        assertEquals(RepoFindingSeverity.HIGH, match.severity());
        assertEquals(1, match.lineNumber());
    }

    @Test
    @DisplayName("RegexSecretDetector reliably detects GitHub Personal Access Tokens")
    void testDetectGitHubToken() {
        String testToken = "gh" + "p_1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R";
        String content = "# Deployment file\nGITHUB_TOKEN = " + testToken;
        List<SecretDetectionResult> matches = regexDetector.scanContent(content, "deploy.sh", "commit1", "main", "bob");

        assertFalse(matches.isEmpty());
        SecretDetectionResult match = matches.stream()
                .filter(m -> m.secretType() == SecretType.GITHUB_TOKEN)
                .findFirst()
                .orElse(null);

        assertNotNull(match);
        assertTrue(match.maskedValue().startsWith("ghp_"));
        assertEquals(RepoFindingSeverity.CRITICAL, match.severity());
        assertEquals(2, match.lineNumber());
    }

    @Test
    @DisplayName("RegexSecretDetector reliably detects Stripe Live API keys")
    void testDetectStripeKey() {
        // Stripe regex requires [sr]k_live_[0-9a-zA-Z]{24,34}
        String testKey = "sk" + "_live_51Mzxyz123456789012345678";
        String content = "const stripe = require('stripe')('" + testKey + "');";
        List<SecretDetectionResult> matches = regexDetector.scanContent(content, "billing.js", "commit2", "feature", "charlie");

        assertFalse(matches.isEmpty());
        SecretDetectionResult match = matches.stream()
                .filter(m -> m.secretType() == SecretType.STRIPE_KEY)
                .findFirst()
                .orElse(null);

        assertNotNull(match);
        assertTrue(match.maskedValue().startsWith("sk_live_"));
        assertEquals(RepoFindingSeverity.CRITICAL, match.severity());
    }

    @Test
    @DisplayName("RegexSecretDetector reliably detects Database Connection URIs with embedded passwords")
    void testDetectDatabaseUri() {
        String testUri = "postgres://" + "admin:SuperSecretP%40ss123@db.prod.internal:5432/secrets";
        String content = "DATABASE_URL=" + testUri;
        List<SecretDetectionResult> matches = regexDetector.scanContent(content, ".env.production", "c3", "main", "dev");

        assertFalse(matches.isEmpty());
        SecretDetectionResult match = matches.stream()
                .filter(m -> m.secretType() == SecretType.DATABASE_CONNECTION_STRING)
                .findFirst()
                .orElse(null);

        assertNotNull(match);
        assertEquals(RepoFindingSeverity.CRITICAL, match.severity());
    }
}

package com.secretvault.repository.engine;

import com.secretvault.repository.model.RepoFindingConfidence;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.SecretType;

import java.util.UUID;

/**
 * Encapsulates a detected secret finding during scanning.
 * Contains only masked values and cryptographic fingerprints.
 */
public record SecretDetectionResult(
        SecretType secretType,
        String detectorType,
        String fingerprint,
        String maskedValue,
        String filePath,
        Integer lineNumber,
        Integer columnNumber,
        RepoFindingSeverity severity,
        RepoFindingConfidence confidence,
        Double entropy,
        String commitSha,
        String branch,
        String author,
        String evidenceSummary,
        UUID matchedSecretId
) {
}

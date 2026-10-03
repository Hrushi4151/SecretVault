package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RepositoryCliDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryDto(
            UUID id,
            UUID workspaceId,
            String provider,
            String externalRepositoryId,
            String owner,
            String name,
            String defaultBranch,
            String cloneUrl,
            String visibility,
            String status,
            Instant lastScanAt,
            Instant lastSuccessfulScanAt,
            String lastCommitSha,
            long totalFindings,
            long criticalFindings,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScanDto(
            UUID id,
            UUID workspaceId,
            UUID repositoryId,
            String scanType,
            String status,
            String commitSha,
            String branch,
            int filesScanned,
            int commitsScanned,
            int findingsCount,
            int highRiskCount,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecretFindingDto(
            UUID id,
            UUID workspaceId,
            UUID repositoryId,
            UUID scanId,
            String fingerprint,
            String detectorType,
            String secretType,
            String severity,
            String confidence,
            String status,
            String filePath,
            Integer lineNumber,
            Integer columnNumber,
            String maskedEvidence,
            String commitSha,
            String branch,
            String author,
            Double entropy,
            String validationStatus,
            String remediationStatus,
            UUID secretId,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WhyExposedDto(
            UUID findingId,
            String secretType,
            String severity,
            String confidence,
            String maskedValue,
            String filePath,
            Integer lineNumber,
            String commitSha,
            String branch,
            String repositoryName,
            String repositoryVisibility,
            boolean isMatchedWithSecretVault,
            UUID matchedSecretId,
            String validationStatus,
            List<String> riskFactors,
            String recommendedRemediation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemediationJobDto(
            UUID id,
            UUID workspaceId,
            UUID findingId,
            String action,
            String status,
            UUID initiatedBy,
            String notes,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {}
}

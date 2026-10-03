package io.secretvault.sdk.model.repo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RepoSecurityModels {

    public record RepositoryInfo(
            UUID id,
            UUID workspaceId,
            String provider,
            String owner,
            String name,
            String defaultBranch,
            String visibility,
            String status,
            long totalFindings,
            long criticalFindings,
            Instant lastScanAt
    ) {}

    public record ScanStatusInfo(
            UUID id,
            UUID workspaceId,
            UUID repositoryId,
            String scanType,
            String status,
            int filesScanned,
            int commitsScanned,
            int findingsCount,
            int highRiskCount,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record SecretFindingInfo(
            UUID id,
            UUID workspaceId,
            UUID repositoryId,
            String secretType,
            String severity,
            String confidence,
            String status,
            String filePath,
            Integer lineNumber,
            String maskedEvidence,
            String fingerprint,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {}

    public record WhyExposedInfo(
            UUID findingId,
            String secretType,
            String severity,
            String maskedValue,
            String filePath,
            Integer lineNumber,
            String repositoryName,
            List<String> riskFactors,
            String recommendedRemediation
    ) {}
}

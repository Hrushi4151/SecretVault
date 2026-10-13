package com.secretvault.repository.dto;

import com.secretvault.repository.model.RemediationAction;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.model.RepositoryVisibility;
import com.secretvault.repository.model.ScanType;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public class RepositoryDtos {

    public record ConnectRepositoryRequest(
            RepositoryProvider provider,
            String externalId,
            @NotBlank String owner,
            @NotBlank String name,
            String defaultBranch,
            String cloneUrl,
            RepositoryVisibility visibility
    ) {}

    public record TriggerScanRequest(
            ScanType scanType,
            String branch
    ) {}

    public record LocalScanRequest(
            @NotBlank String path,
            boolean scanHistory
    ) {}

    public record UpdateFindingStatusRequest(
            RepoFindingStatus status,
            String reason
    ) {}

    public record RemediateFindingRequest(
            RemediationAction action,
            String notes,
            String filePattern,
            Instant expiresAt,
            UUID targetSecretId
    ) {
        public RemediateFindingRequest(RemediationAction action, String notes) {
            this(action, notes, null, null, null);
        }
    }

    public record AllowlistFindingRequest(
            UUID repositoryId,
            @NotBlank String fingerprint,
            String detector,
            String path,
            String reason,
            Instant expiresAt
    ) {}

    public record RemediationTargetInfo(
            UUID findingId,
            String secretType,
            String severity,
            String maskedEvidence,
            String filePath,
            Integer lineNumber,
            String commitSha,
            String branch,
            boolean isManagedInSecretVault,
            UUID matchedSecretId,
            String matchedSecretName,
            String matchedEnvironmentName,
            String matchedProjectName,
            Integer currentVersionNumber,
            String providerName,
            boolean rotationSupported,
            boolean revocationSupported,
            String rotationStrategy,
            String impactWarning,
            java.util.List<String> manualInstructions
    ) {}

    public record FindingAllowlistDto(
            UUID id,
            UUID workspaceId,
            UUID repositoryId,
            String repositoryName,
            String fingerprint,
            String detectorType,
            String filePattern,
            String reason,
            UUID createdBy,
            Instant expiresAt,
            boolean active,
            Instant createdAt
    ) {}

    public record GitHubIntegrationStatusResponse(
            boolean connected,
            String username,
            String name,
            String avatarUrl,
            java.util.List<String> scopes,
            String status,
            long monitoredRepoCount,
            Instant connectedAt,
            Instant lastValidatedAt
    ) {}

    public record ConnectGitHubIntegrationRequest(
            String token,
            String code,
            String redirectUri
    ) {}

    public record ToggleMonitoringRequest(
            boolean enabled
    ) {}
}

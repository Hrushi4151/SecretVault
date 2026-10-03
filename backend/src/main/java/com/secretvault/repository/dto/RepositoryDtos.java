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
            String notes
    ) {}

    public record AllowlistFindingRequest(
            UUID repositoryId,
            @NotBlank String fingerprint,
            String detector,
            String path,
            String reason,
            Instant expiresAt
    ) {}
}

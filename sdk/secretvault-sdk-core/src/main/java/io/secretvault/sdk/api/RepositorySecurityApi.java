package io.secretvault.sdk.api;

import io.secretvault.sdk.model.repo.RepoSecurityModels.RepositoryInfo;
import io.secretvault.sdk.model.repo.RepoSecurityModels.ScanStatusInfo;
import io.secretvault.sdk.model.repo.RepoSecurityModels.SecretFindingInfo;
import io.secretvault.sdk.model.repo.RepoSecurityModels.WhyExposedInfo;

import java.util.List;
import java.util.UUID;

/**
 * High-level SDK client API for interacting with SecretVault Repository Security
 * and Secret Leak Detection services.
 */
public interface RepositorySecurityApi {

    List<RepositoryInfo> listRepositories();

    RepositoryInfo getRepository(UUID repositoryId);

    ScanStatusInfo triggerScan(UUID repositoryId, String scanType);

    ScanStatusInfo getScanStatus(UUID scanId);

    List<SecretFindingInfo> getFindings(UUID repositoryId);

    SecretFindingInfo getFinding(UUID findingId);

    WhyExposedInfo explainFinding(UUID findingId);
}

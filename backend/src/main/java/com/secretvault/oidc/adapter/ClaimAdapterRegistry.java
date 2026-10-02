package com.secretvault.oidc.adapter;

import com.secretvault.oidc.model.OidcProviderType;
import org.springframework.stereotype.Component;

@Component
public class ClaimAdapterRegistry {

    private final GitHubActionsClaimAdapter gitHubAdapter;
    private final GitLabCiClaimAdapter gitLabAdapter;
    private final GenericOidcClaimAdapter genericAdapter;

    public ClaimAdapterRegistry(
            GitHubActionsClaimAdapter gitHubAdapter,
            GitLabCiClaimAdapter gitLabAdapter,
            GenericOidcClaimAdapter genericAdapter) {
        this.gitHubAdapter = gitHubAdapter;
        this.gitLabAdapter = gitLabAdapter;
        this.genericAdapter = genericAdapter;
    }

    public ClaimAdapter getAdapter(OidcProviderType type) {
        if (type == null) {
            return genericAdapter;
        }
        return switch (type) {
            case GITHUB_ACTIONS -> gitHubAdapter;
            case GITLAB_CI -> gitLabAdapter;
            case GENERIC -> genericAdapter;
        };
    }
}

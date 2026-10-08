package com.secretvault.repository.provider;

import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.model.RepositoryVisibility;

import java.util.List;

/**
 * Adapter interface defining standard repository provider operations
 * across GitHub, GitLab, and other source code management providers.
 */
public interface RepositoryProviderAdapter {

    record RemoteRepositoryDto(
            String id,
            String owner,
            String name,
            String defaultBranch,
            String cloneUrl,
            RepositoryVisibility visibility,
            String description,
            boolean isPrivate
    ) {}

    RepositoryProvider getProvider();

    boolean isAvailable();

    boolean validateConnection(String token);

    List<RemoteRepositoryDto> listRepositories(String token);

    RemoteRepositoryDto getRepository(String owner, String name, String token);

    String buildAuthenticatedCloneUrl(String cloneUrl, String token);
}

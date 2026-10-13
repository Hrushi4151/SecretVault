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
            boolean isPrivate,
            boolean canRead,
            boolean canAdminHooks,
            boolean isAlreadyMonitored,
            String lastScanStatus,
            java.time.Instant lastScanAt,
            String permissionWarning,
            String role
    ) {
        public RemoteRepositoryDto(
                String id,
                String owner,
                String name,
                String defaultBranch,
                String cloneUrl,
                RepositoryVisibility visibility,
                String description,
                boolean isPrivate
        ) {
            this(id, owner, name, defaultBranch, cloneUrl, visibility, description, isPrivate,
                    true, true, false, null, null, null, "OWNER");
        }
    }

    record UserProfileDto(
            String login,
            String name,
            String avatarUrl,
            String email,
            List<String> scopes,
            int publicRepos,
            int totalPrivateRepos
    ) {}

    RepositoryProvider getProvider();

    boolean isAvailable();

    boolean validateConnection(String token);

    default UserProfileDto getUserProfile(String token) {
        return null;
    }

    List<RemoteRepositoryDto> listRepositories(String token);

    default List<RemoteRepositoryDto> listRepositories(String token, String username) {
        return listRepositories(token);
    }

    RemoteRepositoryDto getRepository(String owner, String name, String token);

    String buildAuthenticatedCloneUrl(String cloneUrl, String token);
}

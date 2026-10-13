package com.secretvault.repository.provider;

import com.secretvault.repository.model.RepositoryProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of repository provider adapters (GitHub, GitLab, Bitbucket).
 */
@Component
public class RepositoryProviderRegistry {

    private final Map<RepositoryProvider, RepositoryProviderAdapter> adapters = new ConcurrentHashMap<>();

    public RepositoryProviderRegistry(List<RepositoryProviderAdapter> adapterList) {
        for (RepositoryProviderAdapter adapter : adapterList) {
            adapters.put(adapter.getProvider(), adapter);
        }
    }

    public Optional<RepositoryProviderAdapter> getAdapter(RepositoryProvider provider) {
        return Optional.ofNullable(adapters.get(provider));
    }

    public RepositoryProviderAdapter requireAdapter(RepositoryProvider provider) {
        return getAdapter(provider).orElseThrow(() ->
                new IllegalArgumentException("Repository provider [" + provider + "] is coming later and not yet enabled for live sync. Supported: GITHUB."));
    }

    public List<RepositoryProvider> getSupportedProviders() {
        return List.of(RepositoryProvider.GITHUB);
    }
}

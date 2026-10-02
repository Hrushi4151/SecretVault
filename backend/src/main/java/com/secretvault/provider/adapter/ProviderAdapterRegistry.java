package com.secretvault.provider.adapter;

import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.model.ProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry holding all available ProviderAdapter implementations.
 */
@Component
public class ProviderAdapterRegistry {

    private final Map<ProviderType, ProviderAdapter> adapters = new EnumMap<>(ProviderType.class);

    public ProviderAdapterRegistry(List<ProviderAdapter> adapterList) {
        if (adapterList != null) {
            for (ProviderAdapter adapter : adapterList) {
                adapters.put(adapter.getProviderType(), adapter);
            }
        }
    }

    /**
     * Retrieves the adapter for a given ProviderType, or throws an ApiException.
     */
    public ProviderAdapter getAdapter(ProviderType providerType) {
        ProviderAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw ApiException.badRequest("Unsupported platform provider type: " + providerType);
        }
        return adapter;
    }

    /**
     * Optionally finds an adapter for a given ProviderType.
     */
    public Optional<ProviderAdapter> findAdapter(ProviderType providerType) {
        return Optional.ofNullable(adapters.get(providerType));
    }

    /**
     * Checks if an adapter is registered for a given ProviderType.
     */
    public boolean supports(ProviderType providerType) {
        return adapters.containsKey(providerType);
    }

    /**
     * Returns all registered adapters.
     */
    public List<ProviderAdapter> getAllAdapters() {
        return List.copyOf(adapters.values());
    }
}

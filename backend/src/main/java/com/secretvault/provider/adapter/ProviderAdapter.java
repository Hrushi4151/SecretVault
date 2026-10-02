package com.secretvault.provider.adapter;

import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderDiscoveredEnvironment;
import com.secretvault.provider.model.ProviderDiscoveredResource;
import com.secretvault.provider.model.ProviderSecretMetadata;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.model.ProviderValidationResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service Provider Interface (SPI) for external platform provider adapters.
 * Encapsulates all third-party API communication, contract normalization, and capability declarations.
 */
public interface ProviderAdapter {

    /**
     * The unique provider type handled by this adapter.
     */
    ProviderType getProviderType();

    /**
     * Explicit capabilities supported by this adapter.
     */
    Set<ProviderCapability> getCapabilities();

    /**
     * Authenticates and validates connection credentials with the external provider.
     */
    ProviderValidationResult validateConnection(Map<String, Object> configuration, String credential);

    /**
     * Discovers projects, services, or apps accessible with the given credentials.
     */
    List<ProviderDiscoveredResource> discoverResources(Map<String, Object> configuration, String credential);

    /**
     * Discovers deployment environments or target tiers for a specific provider resource.
     */
    List<ProviderDiscoveredEnvironment> discoverEnvironments(Map<String, Object> configuration, String credential, String providerResourceId);

    /**
     * Lists secret/environment variable metadata on the external provider for a specific mapping (zero plaintext).
     */
    List<ProviderSecretMetadata> listSecrets(Map<String, Object> configuration, String credential, ProviderResourceMapping mapping);

    /**
     * Pushes or updates a secret/environment variable on the external provider.
     */
    ProviderSecretOperationResult pushSecret(
            Map<String, Object> configuration,
            String credential,
            ProviderResourceMapping mapping,
            String secretName,
            String secretValue
    );

    /**
     * Deletes a secret/environment variable from the external provider.
     */
    ProviderSecretOperationResult deleteSecret(
            Map<String, Object> configuration,
            String credential,
            ProviderResourceMapping mapping,
            String secretName
    );
}

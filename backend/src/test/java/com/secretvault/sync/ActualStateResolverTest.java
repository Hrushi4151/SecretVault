package com.secretvault.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.*;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.sync.model.ActualStateResult;
import com.secretvault.sync.model.DriftType;
import com.secretvault.sync.service.ActualStateResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActualStateResolverTest {

    @Mock
    private ProviderIntegrationRepository integrationRepository;

    @Mock
    private ProviderCredentialService credentialService;

    @Mock
    private ProviderAdapterRegistry adapterRegistry;

    @Mock
    private ProviderAdapter adapter;

    private ActualStateResolver resolver;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID integrationId;
    private ProviderResourceMapping mapping;
    private ProviderIntegration integration;

    @BeforeEach
    void setUp() {
        resolver = new ActualStateResolver(
                integrationRepository,
                credentialService,
                adapterRegistry,
                new ObjectMapper()
        );

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        integrationId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_123", "prj_123", "production", "{}", true
        );
        mapping.setId(UUID.randomUUID());

        integration = new ProviderIntegration(
                workspaceId, ProviderType.VERCEL, "Vercel", IntegrationStatus.ACTIVE,
                "{}", "enc_token", "enc_dek", "iv", "tag", "kms", "hint", UUID.randomUUID()
        );
        integration.setId(integrationId);
    }

    @Test
    @DisplayName("Resolves actual provider secrets successfully")
    void resolveActualStateSuccess() {
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId))
                .thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(adapter);
        when(adapter.getCapabilities()).thenReturn(Set.of(ProviderCapability.READ_SECRET_METADATA));
        when(credentialService.decryptCredential(integration)).thenReturn("plain_token");
        when(adapter.validateConnection(any(), eq("plain_token")))
                .thenReturn(ProviderValidationResult.success("Team", "team_1", Set.of()));

        ProviderSecretMetadata metadata = new ProviderSecretMetadata("API_KEY", "production", Instant.now(), "env_1");
        when(adapter.listSecrets(any(), eq("plain_token"), eq(mapping)))
                .thenReturn(List.of(metadata));

        ActualStateResult result = resolver.resolveActualState(workspaceId, mapping);

        assertThat(result.success()).isTrue();
        assertThat(result.states()).hasSize(1);
        assertThat(result.states().get(0).providerSecretName()).isEqualTo("API_KEY");
    }

    @Test
    @DisplayName("Returns PERMISSION_DENIED on auth failure during validation")
    void resolveActualStateAuthFailure() {
        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId))
                .thenReturn(Optional.of(integration));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(adapter);
        when(adapter.getCapabilities()).thenReturn(Set.of(ProviderCapability.READ_SECRET_METADATA));
        when(credentialService.decryptCredential(integration)).thenReturn("invalid_token");
        when(adapter.validateConnection(any(), eq("invalid_token")))
                .thenReturn(ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Token invalid"));

        ActualStateResult result = resolver.resolveActualState(workspaceId, mapping);

        assertThat(result.success()).isFalse();
        assertThat(result.errorDriftType()).isEqualTo(DriftType.PERMISSION_DENIED);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
    }
}

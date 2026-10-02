package com.secretvault.sync;

import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.sync.model.DesiredSecretState;
import com.secretvault.sync.model.SyncScope;
import com.secretvault.sync.service.DesiredStateResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesiredStateResolverTest {

    @Mock
    private ProviderResourceMappingRepository mappingRepository;

    @Mock
    private ProviderIntegrationRepository integrationRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository versionRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    private DesiredStateResolver resolver;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID integrationId;

    @BeforeEach
    void setUp() {
        resolver = new DesiredStateResolver(
                mappingRepository,
                integrationRepository,
                secretRepository,
                versionRepository,
                environmentRepository,
                projectRepository
        );

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Resolves desired state for active secrets across workspace mappings")
    void resolveDesiredStateSuccess() {
        ProviderIntegration integration = new ProviderIntegration(
                workspaceId, ProviderType.VERCEL, "Vercel Prod", IntegrationStatus.ACTIVE,
                "{}", "enc_token", "enc_dek", "iv", "tag", "kms", "hint", UUID.randomUUID()
        );
        integration.setId(integrationId);

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_123", "prj_123", "production", "{}", true
        );
        mapping.setId(UUID.randomUUID());

        Secret secret = new Secret(environmentId, "APP_SECRET", "desc", UUID.randomUUID());
        secret.setId(UUID.randomUUID());
        secret.setStatus(SecretStatus.ACTIVE);

        SecretVersion version = new SecretVersion(
                secret.getId(), 1, "cipher".getBytes(), "dek".getBytes(),
                "iv".getBytes(), "tag".getBytes(), "kms-1", UUID.randomUUID(), "Initial"
        );

        when(mappingRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(mapping));
        when(integrationRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(integration));
        when(secretRepository.findByEnvironmentIdAndStatus(environmentId, SecretStatus.ACTIVE))
                .thenReturn(List.of(secret));
        when(versionRepository.findTopBySecretIdOrderByVersionNumberDesc(secret.getId()))
                .thenReturn(Optional.of(version));

        Map<ProviderResourceMapping, List<DesiredSecretState>> result =
                resolver.resolveDesiredState(workspaceId, SyncScope.WORKSPACE, null);

        assertThat(result).containsKey(mapping);
        List<DesiredSecretState> states = result.get(mapping);
        assertThat(states).hasSize(1);
        DesiredSecretState state = states.get(0);
        assertThat(state.secretName()).isEqualTo("APP_SECRET");
        assertThat(state.versionNumber()).isEqualTo(1);
        assertThat(state.desiredFingerprint()).isNotBlank();
    }
}

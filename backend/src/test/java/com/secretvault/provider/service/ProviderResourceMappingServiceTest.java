package com.secretvault.provider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.provider.dto.CreateResourceMappingRequest;
import com.secretvault.provider.dto.ProviderResourceMappingResponse;
import com.secretvault.provider.dto.UpdateResourceMappingRequest;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.security.event.service.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderResourceMappingServiceTest {

    @Mock
    private ProviderResourceMappingRepository mappingRepository;
    @Mock
    private ProviderIntegrationRepository integrationRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private EffectiveAccessService effectiveAccessService;
    @Mock
    private AuditService auditService;
    @Mock
    private SecurityEventService securityEventService;

    private ProviderResourceMappingService mappingService;
    private ObjectMapper objectMapper;
    private UUID workspaceId;
    private UUID integrationId;
    private UUID projectId;
    private UUID environmentId;
    private UUID callerUserId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mappingService = new ProviderResourceMappingService(
                mappingRepository,
                integrationRepository,
                projectRepository,
                environmentRepository,
                effectiveAccessService,
                auditService,
                securityEventService,
                objectMapper
        );

        workspaceId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        callerUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Create Mapping: Successfully creates tenant-isolated mapping")
    void testCreateMappingSuccess() {
        CreateResourceMappingRequest req = new CreateResourceMappingRequest(
                projectId, environmentId, ProviderResourceType.PROJECT,
                "prj_vercel_123", "my-app", "production", Map.of("region", "iad1"), true
        );

        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(integrationId);
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(ProviderType.VERCEL);

        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(new Project()));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(new Environment()));
        when(mappingRepository.existsByWorkspaceIdAndIntegrationIdAndProjectIdAndEnvironmentId(workspaceId, integrationId, projectId, environmentId)).thenReturn(false);
        when(mappingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderResourceMappingResponse res = mappingService.createMapping(workspaceId, integrationId, req, callerUserId);

        assertThat(res).isNotNull();
        assertThat(res.providerResourceId()).isEqualTo("prj_vercel_123");
        assertThat(res.providerEnvironment()).isEqualTo("production");
        assertThat(res.syncEnabled()).isTrue();
        verify(effectiveAccessService).checkPermission(workspaceId, null, null, null, AccessPermission.INTEGRATION_MANAGE, callerUserId);
    }

    @Test
    @DisplayName("Create Mapping: Fails with Conflict if duplicate mapping exists")
    void testCreateMappingDuplicateConflict() {
        CreateResourceMappingRequest req = new CreateResourceMappingRequest(
                projectId, environmentId, ProviderResourceType.PROJECT,
                "prj_vercel_123", "my-app", "production", null, true
        );

        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(integrationId);
        integration.setWorkspaceId(workspaceId);

        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(new Project()));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(new Environment()));
        when(mappingRepository.existsByWorkspaceIdAndIntegrationIdAndProjectIdAndEnvironmentId(workspaceId, integrationId, projectId, environmentId)).thenReturn(true);

        assertThatThrownBy(() -> mappingService.createMapping(workspaceId, integrationId, req, callerUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("List Mappings: Returns mappings for given integration")
    void testListMappings() {
        ProviderIntegration integration = new ProviderIntegration();
        integration.setId(integrationId);
        integration.setWorkspaceId(workspaceId);

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_1", "App", "production", "{}", true
        );

        when(integrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)).thenReturn(Optional.of(integration));
        when(mappingRepository.findByWorkspaceIdAndIntegrationId(workspaceId, integrationId)).thenReturn(List.of(mapping));

        List<ProviderResourceMappingResponse> list = mappingService.listMappings(workspaceId, integrationId, callerUserId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).providerResourceId()).isEqualTo("prj_1");
    }

    @Test
    @DisplayName("Delete Mapping: Successfully removes mapping")
    void testDeleteMapping() {
        UUID mappingId = UUID.randomUUID();
        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setId(mappingId);
        mapping.setWorkspaceId(workspaceId);
        mapping.setIntegrationId(integrationId);

        when(mappingRepository.findByIdAndWorkspaceId(mappingId, workspaceId)).thenReturn(Optional.of(mapping));

        mappingService.deleteMapping(workspaceId, integrationId, mappingId, callerUserId);

        verify(mappingRepository).delete(mapping);
    }
}

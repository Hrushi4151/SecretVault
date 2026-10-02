package com.secretvault.sync;

import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.sync.model.ActualStateResult;
import com.secretvault.sync.scheduler.SyncDriftScheduler;
import com.secretvault.sync.service.ActualStateResolver;
import com.secretvault.sync.service.DesiredStateResolver;
import com.secretvault.sync.service.DriftDetectionEngine;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncDriftSchedulerTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ProviderIntegrationRepository integrationRepository;

    @Mock
    private ProviderResourceMappingRepository mappingRepository;

    @Mock
    private DesiredStateResolver desiredStateResolver;

    @Mock
    private ActualStateResolver actualStateResolver;

    @Mock
    private DriftDetectionEngine driftDetectionEngine;

    private SyncDriftScheduler scheduler;

    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        scheduler = new SyncDriftScheduler(
                workspaceRepository,
                integrationRepository,
                mappingRepository,
                desiredStateResolver,
                actualStateResolver,
                driftDetectionEngine
        );

        workspaceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Executes periodic drift detection across active workspaces when enabled")
    void runsScheduledDriftScanSuccessfully() {
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);

        Workspace workspace = new Workspace(UUID.randomUUID(), "Acme Corp", "acme", true);
        workspace.setId(workspaceId);

        UUID integrationId = UUID.randomUUID();
        ProviderIntegration integration = new ProviderIntegration(
                workspaceId, ProviderType.VERCEL, "Vercel", IntegrationStatus.ACTIVE,
                "{}", "enc_token", "enc_dek", "iv", "tag", "kms", "hint", UUID.randomUUID()
        );
        integration.setId(integrationId);

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integration.getId(), UUID.randomUUID(), UUID.randomUUID(),
                ProviderResourceType.PROJECT, "prj", "prj", "production", "{}", true
        );
        mapping.setId(UUID.randomUUID());

        when(workspaceRepository.findAll()).thenReturn(List.of(workspace));
        when(integrationRepository.findByWorkspaceIdAndStatus(workspaceId, IntegrationStatus.ACTIVE))
                .thenReturn(List.of(integration));
        when(mappingRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(mapping));
        when(desiredStateResolver.resolveDesiredStateForMapping(workspaceId, mapping)).thenReturn(Collections.emptyList());
        when(actualStateResolver.resolveActualState(workspaceId, mapping)).thenReturn(ActualStateResult.success(Collections.emptyList()));

        scheduler.runScheduledDriftScan();

        verify(driftDetectionEngine).detectDriftForMapping(eq(workspaceId), eq(mapping), any(), any(), isNull());
    }

    @Test
    @DisplayName("Skips execution when scheduler is disabled via configuration")
    void skipsExecutionWhenDisabled() {
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);

        scheduler.runScheduledDriftScan();

        verifyNoInteractions(workspaceRepository);
        verifyNoInteractions(driftDetectionEngine);
    }
}

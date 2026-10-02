package com.secretvault.sync;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.DriftRecordRepository;
import com.secretvault.sync.repository.SyncJobRepository;
import com.secretvault.sync.repository.SyncOperationRepository;
import com.secretvault.sync.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Phase8IsolationTest {

    @Mock
    private DriftRecordRepository driftRecordRepository;

    @Mock
    private SyncJobRepository jobRepository;

    @Mock
    private SyncOperationRepository operationRepository;

    @Mock
    private ProviderResourceMappingRepository mappingRepository;

    @Mock
    private ProviderSecretSyncService providerSecretSyncService;

    @Mock
    private DesiredStateResolver desiredStateResolver;

    @Mock
    private ActualStateResolver actualStateResolver;

    @Mock
    private DriftDetectionEngine driftDetectionEngine;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityEventService securityEventService;

    private DriftRecordService driftRecordService;
    private SyncExecutionEngine syncExecutionEngine;
    private SyncJobService syncJobService;

    private UUID tenantAWorkspaceId;
    private UUID tenantBWorkspaceId;
    private UUID tenantAUserId;
    private UUID tenantBUserId;

    @BeforeEach
    void setUp() {
        driftRecordService = new DriftRecordService(
                driftRecordRepository,
                effectiveAccessService,
                auditService,
                securityEventService,
                desiredStateResolver,
                actualStateResolver,
                driftDetectionEngine
        );

        SyncPlanningEngine syncPlanningEngine = new SyncPlanningEngine();

        syncExecutionEngine = new SyncExecutionEngine(
                jobRepository,
                operationRepository,
                mappingRepository,
                providerSecretSyncService,
                desiredStateResolver,
                actualStateResolver,
                driftDetectionEngine,
                syncPlanningEngine,
                effectiveAccessService,
                auditService,
                securityEventService
        );

        syncJobService = new SyncJobService(
                jobRepository,
                operationRepository,
                syncExecutionEngine,
                effectiveAccessService
        );

        tenantAWorkspaceId = UUID.randomUUID();
        tenantBWorkspaceId = UUID.randomUUID();
        tenantAUserId = UUID.randomUUID();
        tenantBUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant A cannot view Tenant B drift records")
    void testTenantCannotViewOtherTenantDrift() {
        doThrow(ApiException.forbidden("User is not an active member of this workspace"))
                .when(effectiveAccessService).checkPermission(
                        eq(tenantBWorkspaceId), any(), any(), any(), eq(AccessPermission.DRIFT_VIEW), eq(tenantAUserId)
                );

        assertThatThrownBy(() -> driftRecordService.getDriftRecords(
                tenantBWorkspaceId, null, null, null, null, null, null, null,
                PageRequest.of(0, 10), tenantAUserId
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("not an active member of this workspace");
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant A cannot execute sync on Tenant B workspace")
    void testTenantCannotExecuteSyncOnOtherTenantWorkspace() {
        doThrow(ApiException.forbidden("User lacks SYNC_EXECUTE permission in target workspace"))
                .when(effectiveAccessService).checkPermission(
                        eq(tenantBWorkspaceId), any(), any(), any(), eq(AccessPermission.SYNC_EXECUTE), eq(tenantAUserId)
                );

        assertThatThrownBy(() -> syncJobService.triggerSync(
                tenantBWorkspaceId, SyncScope.WORKSPACE, null, ReconciliationPolicy.SAFE_RECONCILIATION, tenantAUserId
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("SYNC_EXECUTE");
    }

    @Test
    @DisplayName("IDOR Protection: Accessing drift record from mismatched workspace is rejected")
    void testDriftRecordIdorProtection() {
        UUID driftId = UUID.randomUUID();
        when(driftRecordRepository.findByIdAndWorkspaceId(eq(driftId), eq(tenantAWorkspaceId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> driftRecordService.getDriftRecordById(tenantAWorkspaceId, driftId, tenantAUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found in this workspace");
    }
}

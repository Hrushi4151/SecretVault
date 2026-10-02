package com.secretvault.sync;

import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.sync.model.*;
import com.secretvault.sync.service.SyncPlanningEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SyncPlanningEngineTest {

    private SyncPlanningEngine planningEngine;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID integrationId;
    private UUID mappingId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        planningEngine = new SyncPlanningEngine();

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        mappingId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, projectId, environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_123", "prj_vercel_123", "production", "{}", true
        );
        mapping.setId(mappingId);
    }

    @Test
    @DisplayName("Plans CREATE operation when secret is missing from provider in SAFE_RECONCILIATION")
    void planCreateForMissingSecret() {
        UUID secretId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, secretId,
                "API_KEY", 1, "fp_desired", true, mappingId, Instant.now()
        );

        Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap = Map.of(mapping, List.of(desired));
        Map<UUID, ActualStateResult> actualMap = Map.of(mappingId, ActualStateResult.success(Collections.emptyList()));

        SyncPlan plan = planningEngine.planSync(
                workspaceId, SyncScope.WORKSPACE, null,
                ReconciliationPolicy.SAFE_RECONCILIATION, desiredMap, actualMap, Collections.emptyList()
        );

        assertThat(plan.totalOperations()).isEqualTo(1);
        SyncOperationPlan op = plan.operations().get(0);
        assertThat(op.operationType()).isEqualTo(SyncOperationType.CREATE);
        assertThat(op.initialStatus()).isEqualTo(SyncOperationStatus.PENDING);
        assertThat(op.secretName()).isEqualTo("API_KEY");
    }

    @Test
    @DisplayName("Blocks mutation and plans BLOCKED when policy is DETECT_ONLY")
    void planBlockedForDetectOnlyPolicy() {
        UUID secretId = UUID.randomUUID();
        DesiredSecretState desired = new DesiredSecretState(
                workspaceId, projectId, environmentId, secretId,
                "API_KEY", 1, "fp_desired", true, mappingId, Instant.now()
        );

        Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap = Map.of(mapping, List.of(desired));
        Map<UUID, ActualStateResult> actualMap = Map.of(mappingId, ActualStateResult.success(Collections.emptyList()));

        SyncPlan plan = planningEngine.planSync(
                workspaceId, SyncScope.WORKSPACE, null,
                ReconciliationPolicy.DETECT_ONLY, desiredMap, actualMap, Collections.emptyList()
        );

        assertThat(plan.totalOperations()).isEqualTo(1);
        SyncOperationPlan op = plan.operations().get(0);
        assertThat(op.operationType()).isEqualTo(SyncOperationType.CREATE);
        assertThat(op.initialStatus()).isEqualTo(SyncOperationStatus.BLOCKED);
        assertThat(op.reason()).contains("DETECT_ONLY");
    }

    @Test
    @DisplayName("Conservative deletion: unmanaged remote secrets are planned as BLOCKED and warning added")
    void conservativeDeletionBlocksProviderDelete() {
        ProviderSecretState unmanagedRemote = new ProviderSecretState(
                integrationId, mappingId, "prj_vercel_123", "production",
                "OLD_KEY", "env_old_1", null, true, "production", Instant.now()
        );

        Map<ProviderResourceMapping, List<DesiredSecretState>> desiredMap = Map.of(mapping, Collections.emptyList());
        Map<UUID, ActualStateResult> actualMap = Map.of(mappingId, ActualStateResult.success(List.of(unmanagedRemote)));

        SyncPlan plan = planningEngine.planSync(
                workspaceId, SyncScope.WORKSPACE, null,
                ReconciliationPolicy.SAFE_RECONCILIATION, desiredMap, actualMap, Collections.emptyList()
        );

        assertThat(plan.totalOperations()).isEqualTo(1);
        SyncOperationPlan op = plan.operations().get(0);
        assertThat(op.operationType()).isEqualTo(SyncOperationType.BLOCKED);
        assertThat(op.initialStatus()).isEqualTo(SyncOperationStatus.BLOCKED);
        assertThat(op.reason()).contains("Conservative deletion");
        assertThat(plan.warnings()).isNotEmpty();
    }
}

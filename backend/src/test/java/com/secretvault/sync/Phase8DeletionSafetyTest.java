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

class Phase8DeletionSafetyTest {

    private SyncPlanningEngine syncPlanningEngine;
    private UUID workspaceId;
    private UUID integrationId;
    private ProviderResourceMapping mapping;

    @BeforeEach
    void setUp() {
        syncPlanningEngine = new SyncPlanningEngine();
        workspaceId = UUID.randomUUID();
        integrationId = UUID.randomUUID();

        mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), UUID.randomUUID(),
                ProviderResourceType.PROJECT, "prj_del_safe", "App", "production", "{}", true
        );
    }

    @Test
    @DisplayName("Conservative Deletion: Unmanaged provider secret is BLOCKED with UNMANAGED_PROVIDER_SECRET code")
    void testUnmanagedProviderSecretIsBlockedFromDeletion() {
        // Desired: empty (no secrets in SecretVault)
        // Actual: provider has an unexpected secret UNTRACKED_KEY
        ProviderSecretState unmanagedSecret = new ProviderSecretState(
                integrationId, mapping.getId(), "prj_del_safe", "production",
                "UNTRACKED_KEY", "env_unmanaged_1", "sha256:unmanaged", true, "{}", Instant.now()
        );
        ActualStateResult actualResult = ActualStateResult.success(List.of(unmanagedSecret));

        SyncPlan plan = syncPlanningEngine.planSync(
                workspaceId,
                SyncScope.WORKSPACE,
                null,
                ReconciliationPolicy.SAFE_RECONCILIATION,
                Map.of(mapping, Collections.emptyList()),
                Map.of(mapping.getId(), actualResult),
                Collections.emptyList()
        );

        assertThat(plan.operations()).hasSize(1);
        SyncOperationPlan op = plan.operations().get(0);

        assertThat(op.operationType()).isEqualTo(SyncOperationType.BLOCKED);
        assertThat(op.initialStatus()).isEqualTo(SyncOperationStatus.BLOCKED);
        assertThat(op.errorCode()).isEqualTo("UNMANAGED_PROVIDER_SECRET");
        assertThat(op.reason()).contains("Conservative deletion");
        assertThat(plan.warnings()).anyMatch(w -> w.contains("UNTRACKED_KEY"));
    }
}

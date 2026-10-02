package com.secretvault.sync;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.sync.dto.DriftRecordResponse;
import com.secretvault.sync.dto.UpdateDriftStatusRequest;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.DriftSeverity;
import com.secretvault.sync.model.DriftStatus;
import com.secretvault.sync.model.DriftType;
import com.secretvault.sync.repository.DriftRecordRepository;
import com.secretvault.sync.service.DriftRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriftRecordServiceTest {

    @Mock
    private DriftRecordRepository driftRecordRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityEventService securityEventService;

    @Mock
    private com.secretvault.sync.service.DesiredStateResolver desiredStateResolver;

    @Mock
    private com.secretvault.sync.service.ActualStateResolver actualStateResolver;

    @Mock
    private com.secretvault.sync.service.DriftDetectionEngine driftDetectionEngine;

    private DriftRecordService driftRecordService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID callerUserId;

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

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        callerUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Lists drift records with permission check and allowlisted pagination")
    void getDriftRecordsSuccess() {
        DriftRecord record = new DriftRecord(
                workspaceId, projectId, environmentId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "DATABASE_URL", null, DriftType.MISSING_FROM_PROVIDER,
                DriftSeverity.HIGH, "fp1", null, "fp_rec_1"
        );
        record.setId(UUID.randomUUID());

        when(driftRecordRepository.findWithFilters(
                eq(workspaceId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(record)));

        Page<DriftRecordResponse> page = driftRecordService.getDriftRecords(
                workspaceId, null, null, null, null, null, null, null,
                PageRequest.of(0, 10), callerUserId
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).secretName()).isEqualTo("DATABASE_URL");
        verify(effectiveAccessService).checkPermission(
                eq(workspaceId), isNull(), isNull(), isNull(), eq(AccessPermission.DRIFT_VIEW), eq(callerUserId)
        );
    }

    @Test
    @DisplayName("Updates drift triage status to ACKNOWLEDGED and RESOLVED with audit logging")
    void updateDriftStatusSuccess() {
        UUID driftId = UUID.randomUUID();
        DriftRecord record = new DriftRecord(
                workspaceId, projectId, environmentId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "DATABASE_URL", null, DriftType.MISSING_FROM_PROVIDER,
                DriftSeverity.HIGH, "fp1", null, "fp_rec_1"
        );
        record.setId(driftId);

        when(driftRecordRepository.findByIdAndWorkspaceId(driftId, workspaceId))
                .thenReturn(Optional.of(record));
        when(driftRecordRepository.save(any(DriftRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateDriftStatusRequest request = new UpdateDriftStatusRequest(DriftStatus.RESOLVED, "Fixed manually in provider dashboard");
        DriftRecordResponse response = driftRecordService.updateDriftStatus(workspaceId, driftId, request, callerUserId);

        assertThat(response.status()).isEqualTo(DriftStatus.RESOLVED);
        assertThat(response.resolvedBy()).isEqualTo(callerUserId);
        assertThat(response.resolutionReason()).contains("Fixed manually");

        verify(effectiveAccessService).checkPermission(
                eq(workspaceId), eq(projectId), eq(environmentId), any(), eq(AccessPermission.DRIFT_MANAGE), eq(callerUserId)
        );
        verify(auditService).recordAudit(any(), eq(workspaceId), eq(callerUserId), eq("USER"), any(), any(), eq(driftId), any(), any(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("Throws 404 when drift record not found in workspace")
    void getDriftRecordNotFound() {
        UUID driftId = UUID.randomUUID();
        when(driftRecordRepository.findByIdAndWorkspaceId(driftId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> driftRecordService.getDriftRecordById(workspaceId, driftId, callerUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}

package com.secretvault.sync;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.sync.dto.DryRunResponse;
import com.secretvault.sync.dto.SyncJobResponse;
import com.secretvault.sync.dto.SyncOperationResponse;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.*;
import com.secretvault.sync.repository.SyncJobRepository;
import com.secretvault.sync.repository.SyncOperationRepository;
import com.secretvault.sync.service.SyncExecutionEngine;
import com.secretvault.sync.service.SyncJobService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncJobServiceTest {

    @Mock
    private SyncJobRepository syncJobRepository;

    @Mock
    private SyncOperationRepository syncOperationRepository;

    @Mock
    private SyncExecutionEngine syncExecutionEngine;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    private SyncJobService syncJobService;

    private UUID workspaceId;
    private UUID callerUserId;

    @BeforeEach
    void setUp() {
        syncJobService = new SyncJobService(
                syncJobRepository,
                syncOperationRepository,
                syncExecutionEngine,
                effectiveAccessService
        );

        workspaceId = UUID.randomUUID();
        callerUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Lists sync jobs with permission check and pagination")
    void getSyncJobsSuccess() {
        SyncJob job = new SyncJob(
                workspaceId, SyncScope.WORKSPACE, null, false,
                ReconciliationPolicy.SAFE_RECONCILIATION, callerUserId
        );
        job.setId(UUID.randomUUID());
        job.setStatus(SyncJobStatus.COMPLETED);

        when(syncJobRepository.findWithFilters(eq(workspaceId), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));

        Page<SyncJobResponse> page = syncJobService.getSyncJobs(
                workspaceId, null, null, PageRequest.of(0, 10), callerUserId
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).status()).isEqualTo(SyncJobStatus.COMPLETED);
        verify(effectiveAccessService).checkPermission(
                eq(workspaceId), isNull(), isNull(), isNull(), eq(AccessPermission.SYNC_VIEW), eq(callerUserId)
        );
    }

    @Test
    @DisplayName("Retrieves operations for a sync job")
    void getSyncJobOperationsSuccess() {
        UUID jobId = UUID.randomUUID();
        SyncJob job = new SyncJob(
                workspaceId, SyncScope.WORKSPACE, null, false,
                ReconciliationPolicy.SAFE_RECONCILIATION, callerUserId
        );
        job.setId(jobId);

        SyncOperation op = new SyncOperation(
                jobId, UUID.randomUUID(), "API_KEY", UUID.randomUUID(), UUID.randomUUID(),
                SyncOperationType.CREATE, "fp1", null, "Missing from provider"
        );
        op.setId(UUID.randomUUID());
        op.setStatus(SyncOperationStatus.SUCCESS);

        when(syncJobRepository.findByIdAndWorkspaceId(jobId, workspaceId))
                .thenReturn(Optional.of(job));
        when(syncOperationRepository.findByJobId(eq(jobId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(op)));

        Page<SyncOperationResponse> page = syncJobService.getSyncJobOperations(
                workspaceId, jobId, PageRequest.of(0, 20), callerUserId
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).secretName()).isEqualTo("API_KEY");
    }

    @Test
    @DisplayName("Throws 404 when querying sync job belonging to another workspace")
    void getSyncJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(syncJobRepository.findByIdAndWorkspaceId(jobId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> syncJobService.getSyncJobById(workspaceId, jobId, callerUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}

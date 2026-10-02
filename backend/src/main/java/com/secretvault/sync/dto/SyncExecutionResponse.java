package com.secretvault.sync.dto;

import com.secretvault.sync.entity.SyncJob;

import java.util.List;

/**
 * Result returned upon live synchronization execution.
 */
public record SyncExecutionResponse(
        SyncJobResponse job,
        List<SyncOperationResponse> operations,
        int totalOperations,
        int successfulOperations,
        int failedOperations,
        int blockedOperations,
        int driftCount,
        boolean success,
        String message
) {
    public static SyncExecutionResponse of(SyncJob job, List<SyncOperationResponse> operations, String message) {
        SyncJobResponse jobResponse = SyncJobResponse.fromEntity(job);
        boolean isSuccess = job.getFailedOperations() == 0;
        return new SyncExecutionResponse(
                jobResponse,
                operations,
                job.getTotalOperations(),
                job.getSuccessfulOperations(),
                job.getFailedOperations(),
                job.getBlockedOperations(),
                job.getDriftCount(),
                isSuccess,
                message
        );
    }
}

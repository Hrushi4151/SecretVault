package com.secretvault.rotation.repository;

import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.model.RotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RotationJobRepository extends JpaRepository<RotationJob, UUID> {

    List<RotationJob> findBySecretIdOrderByCreatedAtDesc(UUID secretId);

    Page<RotationJob> findBySecretIdOrderByCreatedAtDesc(UUID secretId, Pageable pageable);

    Optional<RotationJob> findTopBySecretIdOrderByCreatedAtDesc(UUID secretId);

    List<RotationJob> findByWorkspaceId(UUID workspaceId);

    Page<RotationJob> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Optional<RotationJob> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);

    List<RotationJob> findByStatusAndGracePeriodEndsAtLessThanEqual(RotationStatus status, Instant now);

    @Query("SELECT j FROM RotationJob j WHERE j.secretId = :secretId AND j.status NOT IN ('COMPLETED', 'ROLLED_BACK', 'FAILED', 'CANCELLED', 'EXPIRED')")
    List<RotationJob> findActiveJobsBySecretId(@Param("secretId") UUID secretId);

    @Query("SELECT j FROM RotationJob j WHERE j.status = 'GRACE_PERIOD' AND j.gracePeriodEndsAt <= :now")
    List<RotationJob> findJobsWithExpiredGracePeriod(@Param("now") Instant now);

    @Query("SELECT j FROM RotationJob j WHERE j.status IN ('QUEUED', 'VALIDATION_FAILED', 'ACTIVATION_FAILED') AND j.retryCount < j.maxRetries AND j.nextRetryAt <= :now")
    List<RotationJob> findRetryableJobs(@Param("now") Instant now);

    @Query("SELECT j FROM RotationJob j WHERE j.workspaceId = :workspaceId AND j.status IN :statuses")
    List<RotationJob> findByWorkspaceIdAndStatusIn(@Param("workspaceId") UUID workspaceId, @Param("statuses") Set<RotationStatus> statuses);

    @Query("SELECT COUNT(j) FROM RotationJob j WHERE j.workspaceId = :workspaceId AND j.status NOT IN ('COMPLETED', 'ROLLED_BACK', 'FAILED', 'CANCELLED', 'EXPIRED')")
    long countActiveByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT COUNT(j) FROM RotationJob j WHERE j.workspaceId = :workspaceId AND j.status IN ('FAILED', 'VALIDATION_FAILED', 'ACTIVATION_FAILED')")
    long countFailedByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}

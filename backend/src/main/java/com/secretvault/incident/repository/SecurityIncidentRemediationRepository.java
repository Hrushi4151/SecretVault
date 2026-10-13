package com.secretvault.incident.repository;

import com.secretvault.incident.entity.IncidentRemediationStatus;
import com.secretvault.incident.entity.SecurityIncidentRemediation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityIncidentRemediationRepository extends JpaRepository<SecurityIncidentRemediation, UUID> {

    Optional<SecurityIncidentRemediation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<SecurityIncidentRemediation> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    List<SecurityIncidentRemediation> findByStatusOrderByCreatedAtAsc(IncidentRemediationStatus status, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE SecurityIncidentRemediation r SET r.status = :claimedStatus, r.workerId = :workerId, r.claimToken = :claimToken, " +
            "r.lockedAt = :now, r.attemptCount = r.attemptCount + 1, r.updatedAt = :now " +
            "WHERE r.id = :id AND (r.status = :queuedStatus OR (r.status = :claimedStatus AND r.lockedAt < :staleCutoff)) " +
            "AND r.attemptCount < r.maxAttempts")
    int claimRemediationAtomic(
            @Param("id") UUID id,
            @Param("claimedStatus") IncidentRemediationStatus claimedStatus,
            @Param("queuedStatus") IncidentRemediationStatus queuedStatus,
            @Param("staleCutoff") Instant staleCutoff,
            @Param("workerId") String workerId,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying
    @Query("UPDATE SecurityIncidentRemediation r SET r.status = :status, r.lastError = :error, " +
            "r.workerId = NULL, r.claimToken = NULL, r.lockedAt = NULL, r.updatedAt = :now " +
            "WHERE r.id = :id AND r.claimToken = :claimToken")
    int releaseClaimForRetry(
            @Param("id") UUID id,
            @Param("status") IncidentRemediationStatus status,
            @Param("error") String error,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying
    @Query("UPDATE SecurityIncidentRemediation r SET r.status = :status, r.executionDetails = :details, r.verifiedEffectsJson = :verifiedEffects, " +
            "r.completedAt = :now, r.workerId = NULL, r.claimToken = NULL, r.lockedAt = NULL, r.updatedAt = :now " +
            "WHERE r.id = :id AND r.claimToken = :claimToken")
    int completeRemediationGuarded(
            @Param("id") UUID id,
            @Param("status") IncidentRemediationStatus status,
            @Param("details") String details,
            @Param("verifiedEffects") String verifiedEffects,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying
    @Query("UPDATE SecurityIncidentRemediation r SET r.status = :status, r.lastError = :error, r.verifiedEffectsJson = :verifiedEffects, " +
            "r.workerId = NULL, r.claimToken = NULL, r.lockedAt = NULL, r.updatedAt = :now " +
            "WHERE r.id = :id AND r.claimToken = :claimToken")
    int failRemediationGuarded(
            @Param("id") UUID id,
            @Param("status") IncidentRemediationStatus status,
            @Param("error") String error,
            @Param("verifiedEffects") String verifiedEffects,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying
    @Query("UPDATE SecurityIncidentRemediation r SET r.status = :queuedStatus, r.workerId = NULL, r.claimToken = NULL, r.lockedAt = NULL, r.updatedAt = :now " +
            "WHERE r.status = :claimedStatus AND r.lockedAt < :staleCutoff AND r.attemptCount < r.maxAttempts")
    int recoverStaleRemediationsAtomic(
            @Param("queuedStatus") IncidentRemediationStatus queuedStatus,
            @Param("claimedStatus") IncidentRemediationStatus claimedStatus,
            @Param("staleCutoff") Instant staleCutoff,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying
    @Query("UPDATE SecurityIncidentRemediation r SET r.status = :failedStatus, r.lastError = 'Exhausted maximum retry attempts without completion', " +
            "r.workerId = NULL, r.claimToken = NULL, r.lockedAt = NULL, r.updatedAt = :now " +
            "WHERE r.status = :claimedStatus AND r.lockedAt < :staleCutoff AND r.attemptCount >= r.maxAttempts")
    int failExhaustedStaleRemediationsAtomic(
            @Param("failedStatus") IncidentRemediationStatus failedStatus,
            @Param("claimedStatus") IncidentRemediationStatus claimedStatus,
            @Param("staleCutoff") Instant staleCutoff,
            @Param("now") Instant now
    );
}

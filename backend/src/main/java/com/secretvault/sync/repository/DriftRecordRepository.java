package com.secretvault.sync.repository;

import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.model.DriftSeverity;
import com.secretvault.sync.model.DriftStatus;
import com.secretvault.sync.model.DriftType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for DriftRecord entities.
 */
@Repository
public interface DriftRecordRepository extends JpaRepository<DriftRecord, UUID> {

    Optional<DriftRecord> findByWorkspaceIdAndFingerprint(UUID workspaceId, String fingerprint);

    Optional<DriftRecord> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<DriftRecord> findByWorkspaceIdAndStatus(UUID workspaceId, DriftStatus status);

    List<DriftRecord> findByMappingIdAndStatus(UUID mappingId, DriftStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, DriftStatus status);

    @Query("SELECT d FROM DriftRecord d WHERE d.workspaceId = :workspaceId " +
           "AND (:status IS NULL OR d.status = :status) " +
           "AND (:driftType IS NULL OR d.driftType = :driftType) " +
           "AND (:severity IS NULL OR d.severity = :severity) " +
           "AND (:projectId IS NULL OR d.projectId = :projectId) " +
           "AND (:environmentId IS NULL OR d.environmentId = :environmentId) " +
           "AND (:integrationId IS NULL OR d.integrationId = :integrationId) " +
           "AND (:mappingId IS NULL OR d.mappingId = :mappingId)")
    Page<DriftRecord> findWithFilters(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") DriftStatus status,
            @Param("driftType") DriftType driftType,
            @Param("severity") DriftSeverity severity,
            @Param("projectId") UUID projectId,
            @Param("environmentId") UUID environmentId,
            @Param("integrationId") UUID integrationId,
            @Param("mappingId") UUID mappingId,
            Pageable pageable
    );
}

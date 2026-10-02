package com.secretvault.sync.repository;

import com.secretvault.sync.entity.SyncOperation;
import com.secretvault.sync.model.SyncOperationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for SyncOperation entities.
 */
@Repository
public interface SyncOperationRepository extends JpaRepository<SyncOperation, UUID> {

    List<SyncOperation> findByJobId(UUID jobId);

    Page<SyncOperation> findByJobId(UUID jobId, Pageable pageable);

    List<SyncOperation> findByJobIdAndStatus(UUID jobId, SyncOperationStatus status);

    long countByJobIdAndStatus(UUID jobId, SyncOperationStatus status);

    List<SyncOperation> findBySecretId(UUID secretId);

    List<SyncOperation> findByMappingId(UUID mappingId);
}

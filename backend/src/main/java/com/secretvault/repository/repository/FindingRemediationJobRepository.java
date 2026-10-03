package com.secretvault.repository.repository;

import com.secretvault.repository.entity.FindingRemediationJob;
import com.secretvault.repository.model.RemediationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FindingRemediationJobRepository extends JpaRepository<FindingRemediationJob, UUID> {

    Optional<FindingRemediationJob> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<FindingRemediationJob> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<FindingRemediationJob> findByFindingId(UUID findingId, Pageable pageable);

    List<FindingRemediationJob> findByWorkspaceIdAndStatus(UUID workspaceId, RemediationStatus status);

    boolean existsByFindingIdAndStatusIn(UUID findingId, List<RemediationStatus> statuses);
}

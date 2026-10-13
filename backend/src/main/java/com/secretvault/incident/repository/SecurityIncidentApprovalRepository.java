package com.secretvault.incident.repository;

import com.secretvault.incident.entity.IncidentApprovalStatus;
import com.secretvault.incident.entity.SecurityIncidentApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityIncidentApprovalRepository extends JpaRepository<SecurityIncidentApproval, UUID> {

    List<SecurityIncidentApproval> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    Optional<SecurityIncidentApproval> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<SecurityIncidentApproval> findByIdAndIncidentIdAndWorkspaceId(UUID id, UUID incidentId, UUID workspaceId);

    List<SecurityIncidentApproval> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, IncidentApprovalStatus status);
}

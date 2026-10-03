package com.secretvault.incident.repository;

import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, UUID> {

    Optional<SecurityIncident> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<SecurityIncident> findByWorkspaceIdAndIncidentNumber(UUID workspaceId, String incidentNumber);

    Page<SecurityIncident> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<SecurityIncident> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, IncidentStatus status, Pageable pageable);

    Page<SecurityIncident> findByWorkspaceIdAndSeverityOrderByCreatedAtDesc(UUID workspaceId, IncidentSeverity severity, Pageable pageable);

    long countByWorkspaceIdAndStatus(UUID workspaceId, IncidentStatus status);

    long countByWorkspaceIdAndSeverity(UUID workspaceId, IncidentSeverity severity);

    @Query("SELECT COUNT(i) FROM SecurityIncident i WHERE i.workspaceId = :workspaceId")
    long countByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT i FROM SecurityIncident i WHERE i.workspaceId = :workspaceId AND i.status IN ('OPEN', 'INVESTIGATING', 'CONTAINED', 'REMEDIATION') ORDER BY i.createdAt DESC")
    List<SecurityIncident> findActiveIncidents(@Param("workspaceId") UUID workspaceId);
}

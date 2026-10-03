package com.secretvault.incident.repository;

import com.secretvault.incident.entity.SecurityIncidentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityIncidentEventRepository extends JpaRepository<SecurityIncidentEvent, UUID> {

    List<SecurityIncidentEvent> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    Optional<SecurityIncidentEvent> findByIncidentIdAndEventId(UUID incidentId, UUID eventId);

    boolean existsByIncidentIdAndEventId(UUID incidentId, UUID eventId);
}

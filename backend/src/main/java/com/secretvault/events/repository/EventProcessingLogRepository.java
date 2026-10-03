package com.secretvault.events.repository;

import com.secretvault.events.entity.EventProcessingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventProcessingLogRepository extends JpaRepository<EventProcessingLog, UUID> {

    Optional<EventProcessingLog> findByEventIdAndConsumerName(UUID eventId, String consumerName);

    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);

    List<EventProcessingLog> findByEventId(UUID eventId);

    List<EventProcessingLog> findByWorkspaceIdOrderByProcessedAtDesc(UUID workspaceId);
}

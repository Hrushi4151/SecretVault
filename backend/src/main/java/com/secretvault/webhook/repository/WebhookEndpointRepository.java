package com.secretvault.webhook.repository;

import com.secretvault.webhook.entity.WebhookEndpoint;
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
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    Optional<WebhookEndpoint> findByWorkspaceIdAndName(UUID workspaceId, String name);

    Page<WebhookEndpoint> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    List<WebhookEndpoint> findByWorkspaceIdAndEnabledTrue(UUID workspaceId);

    @Query("SELECT w FROM WebhookEndpoint w WHERE w.workspaceId = :workspaceId AND w.enabled = true " +
            "AND (w.subscribedEventsJson LIKE %:eventType% OR w.subscribedEventsJson LIKE '%*%' OR w.subscribedEventsJson LIKE '%ALL%')")
    List<WebhookEndpoint> findSubscribedEndpoints(
            @Param("workspaceId") UUID workspaceId,
            @Param("eventType") String eventType
    );

    long countByWorkspaceId(UUID workspaceId);
}

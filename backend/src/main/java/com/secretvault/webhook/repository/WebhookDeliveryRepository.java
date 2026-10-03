package com.secretvault.webhook.repository;

import com.secretvault.webhook.entity.WebhookDelivery;
import com.secretvault.webhook.entity.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    Page<WebhookDelivery> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<WebhookDelivery> findByWorkspaceIdAndWebhookIdOrderByCreatedAtDesc(UUID workspaceId, UUID webhookId, Pageable pageable);

    List<WebhookDelivery> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(WebhookDeliveryStatus status, Instant cutoff, Pageable pageable);

    long countByWorkspaceIdAndStatus(UUID workspaceId, WebhookDeliveryStatus status);
}

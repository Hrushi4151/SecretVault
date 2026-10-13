package com.secretvault.webhook.repository;

import com.secretvault.webhook.entity.WebhookDelivery;
import com.secretvault.webhook.entity.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    boolean existsByWebhookIdAndEventId(UUID webhookId, UUID eventId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("UPDATE WebhookDelivery d SET d.status = :newStatus, d.workerId = :workerId, d.leasedUntil = :leasedUntil " +
            "WHERE d.id = :id AND d.status = :expectedStatus")
    int claimDelivery(
            @Param("id") UUID id,
            @Param("expectedStatus") WebhookDeliveryStatus expectedStatus,
            @Param("newStatus") WebhookDeliveryStatus newStatus,
            @Param("workerId") String workerId,
            @Param("leasedUntil") Instant leasedUntil
    );

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("UPDATE WebhookDelivery d SET d.status = :resetStatus, d.workerId = null, d.leasedUntil = null, d.nextRetryAt = :now " +
            "WHERE d.status = :staleStatus AND d.leasedUntil < :now")
    int recoverStaleDeliveries(
            @Param("staleStatus") WebhookDeliveryStatus staleStatus,
            @Param("resetStatus") WebhookDeliveryStatus resetStatus,
            @Param("now") Instant now
    );

    @Query("SELECT d FROM WebhookDelivery d WHERE d.status IN (:statuses) AND d.nextRetryAt <= :cutoff ORDER BY d.nextRetryAt ASC")
    List<WebhookDelivery> findDueDeliveries(
            @Param("statuses") List<WebhookDeliveryStatus> statuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    long countByWorkspaceIdAndStatus(UUID workspaceId, WebhookDeliveryStatus status);
}

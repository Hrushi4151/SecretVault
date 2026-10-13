package com.secretvault.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.webhook.entity.WebhookDelivery;
import com.secretvault.webhook.entity.WebhookDeliveryStatus;
import com.secretvault.webhook.entity.WebhookEndpoint;
import com.secretvault.webhook.repository.WebhookDeliveryRepository;
import com.secretvault.webhook.repository.WebhookEndpointRepository;
import com.secretvault.webhook.security.HardenedWebhookTransport;
import com.secretvault.webhook.security.SsrfProtectionValidator;
import com.secretvault.webhook.security.WebhookSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_DELIVERY_ATTEMPTS = 5;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookService webhookService;
    private final WebhookSigner webhookSigner;
    private final SsrfProtectionValidator ssrfValidator;
    private final HardenedWebhookTransport hardenedTransport;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WebhookDeliveryService(
            WebhookDeliveryRepository deliveryRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookService webhookService,
            WebhookSigner webhookSigner,
            SsrfProtectionValidator ssrfValidator,
            HardenedWebhookTransport hardenedTransport,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.webhookService = webhookService;
        this.webhookSigner = webhookSigner;
        this.ssrfValidator = ssrfValidator;
        this.hardenedTransport = hardenedTransport;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void dispatchDomainEvent(DomainEvent event) {
        enqueueDeliveriesForEvent(event);
    }

    @Transactional
    public void enqueueDeliveriesForEvent(DomainEvent event) {
        if (event == null || event.getWorkspaceId() == null || event.getEventType() == null) {
            return;
        }

        List<WebhookEndpoint> endpoints = endpointRepository.findSubscribedEndpoints(
                event.getWorkspaceId(), event.getEventType().name()
        );

        String rawPayload;
        String payloadDigest;
        try {
            rawPayload = objectMapper.writeValueAsString(event);
            payloadDigest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawPayload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            log.error("Failed to serialize domain event [{}] for webhooks: {}", event.getEventId(), e.getMessage());
            return;
        }

        for (WebhookEndpoint ep : endpoints) {
            // Deduplication: prevent duplicate domain events from enqueuing duplicate delivery records
            if (deliveryRepository.existsByWebhookIdAndEventId(ep.getId(), event.getEventId())) {
                log.info("Duplicate domain event [{}] delivery skipped for endpoint [{}]", event.getEventId(), ep.getId());
                continue;
            }

            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setWorkspaceId(event.getWorkspaceId());
            delivery.setWebhookId(ep.getId());
            delivery.setEventId(event.getEventId());
            delivery.setAttempt(1);
            delivery.setStatus(WebhookDeliveryStatus.PENDING);
            delivery.setNextRetryAt(Instant.now());
            delivery.setPayloadDigest(payloadDigest);
            delivery.setPayloadJson(rawPayload);

            WebhookDelivery saved = deliveryRepository.save(delivery);
            deliverAsync(ep, rawPayload, saved.getId());
        }
    }

    @Async
    public CompletableFuture<WebhookDelivery> deliverAsync(WebhookEndpoint endpoint, String payload, UUID deliveryId) {
        return CompletableFuture.supplyAsync(() -> executeDelivery(endpoint, payload, deliveryId));
    }

    @Async
    public CompletableFuture<WebhookDelivery> deliverAsync(WebhookEndpoint endpoint, DomainEvent event, UUID deliveryId) {
        String payload = null;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception ignored) {}
        return deliverAsync(endpoint, payload, deliveryId);
    }

    @Transactional
    public WebhookDelivery executeDelivery(WebhookEndpoint endpoint, String payload, UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivery not found: " + deliveryId));

        // Atomic worker claim: ensures only one worker or thread processes this delivery
        String workerId = UUID.randomUUID().toString().substring(0, 8) + "@worker";
        Instant leasedUntil = Instant.now().plus(LEASE_DURATION);

        int claimed = deliveryRepository.claimDelivery(
                deliveryId,
                WebhookDeliveryStatus.PENDING,
                WebhookDeliveryStatus.PROCESSING,
                workerId,
                leasedUntil
        );

        if (claimed == 0) {
            claimed = deliveryRepository.claimDelivery(
                    deliveryId,
                    WebhookDeliveryStatus.FAILED,
                    WebhookDeliveryStatus.PROCESSING,
                    workerId,
                    leasedUntil
            );
        }

        if (claimed == 0 && delivery.getStatus() != WebhookDeliveryStatus.PROCESSING) {
            log.info("Delivery [{}] already claimed by another worker; skipping execution", deliveryId);
            return delivery;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Re-validate SSRF prior to every dispatch
            ssrfValidator.validateDestinationUrl(endpoint.getDestinationUrl());

            String rawPayload = (payload != null && !payload.isBlank()) ? payload : delivery.getPayloadJson();
            if (rawPayload == null) rawPayload = "{}";

            String payloadDigest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawPayload.getBytes(StandardCharsets.UTF_8))
            );
            delivery.setPayloadDigest(payloadDigest);

            long timestampSeconds = Instant.now().getEpochSecond();
            String signingSecret = webhookService.revealSigningSecret(endpoint);
            if (signingSecret == null || signingSecret.isBlank()) {
                throw new IllegalStateException("Signing secret is missing or empty (fail closed)");
            }

            String signatureHeader = webhookSigner.computeSignature(signingSecret, timestampSeconds, rawPayload);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("User-Agent", "SecretVault-Webhook/1.0");
            headers.put("X-SecretVault-Event-Id", delivery.getEventId().toString());
            headers.put("X-SecretVault-Timestamp", String.valueOf(timestampSeconds));
            headers.put("X-SecretVault-Signature", signatureHeader);
            headers.put("X-SecretVault-Delivery-Id", delivery.getId().toString());

            // Execute outbound request via DNS-rebinding-protected transport
            HardenedWebhookTransport.WebhookHttpResponse response = hardenedTransport.executePost(
                    endpoint.getDestinationUrl(),
                    headers,
                    rawPayload,
                    CONNECT_TIMEOUT,
                    REQUEST_TIMEOUT
            );

            long duration = System.currentTimeMillis() - startTime;
            delivery.setDurationMs(duration);
            delivery.setHttpStatus(response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus(WebhookDeliveryStatus.SUCCESS);
                delivery.setDeliveredAt(Instant.now());
                delivery.setErrorMessage(null);
                delivery.setWorkerId(null);
                delivery.setLeasedUntil(null);
                endpoint.setLastSuccessAt(Instant.now());
                log.info("Webhook delivery [{}] to [{}] succeeded with HTTP {}", deliveryId, endpoint.getName(), response.statusCode());
            } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                handleRetryableFailure(delivery, "HTTP " + response.statusCode(), response.getRetryAfterSeconds());
                endpoint.setLastFailureAt(Instant.now());
            } else {
                // Permanent 4xx client failure
                delivery.setStatus(WebhookDeliveryStatus.FAILED);
                delivery.setErrorMessage("HTTP " + response.statusCode() + " client error (non-retryable)");
                delivery.setWorkerId(null);
                delivery.setLeasedUntil(null);
                endpoint.setLastFailureAt(Instant.now());
            }

        } catch (ApiException ssrfEx) {
            delivery.setStatus(WebhookDeliveryStatus.BLOCKED_SSRF);
            delivery.setErrorMessage(ssrfEx.getMessage());
            delivery.setDurationMs(System.currentTimeMillis() - startTime);
            delivery.setWorkerId(null);
            delivery.setLeasedUntil(null);
            endpoint.setLastFailureAt(Instant.now());
            log.warn("Blocked webhook dispatch [{}] due to SSRF: {}", deliveryId, ssrfEx.getMessage());
        } catch (Exception ex) {
            handleRetryableFailure(delivery, ex.getMessage(), null);
            endpoint.setLastFailureAt(Instant.now());
        }

        endpointRepository.save(endpoint);
        return deliveryRepository.save(delivery);
    }

    @Transactional
    public WebhookDelivery executeDelivery(WebhookEndpoint endpoint, DomainEvent event, UUID deliveryId) {
        String payload = null;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception ignored) {}
        return executeDelivery(endpoint, payload, deliveryId);
    }

    private void handleRetryableFailure(WebhookDelivery delivery, String error, Long retryAfterSeconds) {
        delivery.setErrorMessage(error);
        delivery.setWorkerId(null);
        delivery.setLeasedUntil(null);

        if (delivery.getAttempt() >= MAX_DELIVERY_ATTEMPTS) {
            delivery.setStatus(WebhookDeliveryStatus.DEAD_LETTER);
            delivery.setNextRetryAt(null);
            log.error("Webhook delivery [{}] reached max attempts ({}), marked DEAD_LETTER", delivery.getId(), MAX_DELIVERY_ATTEMPTS);
        } else {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setAttempt(delivery.getAttempt() + 1);

            long delaySec;
            if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                delaySec = retryAfterSeconds;
            } else {
                // Exponential backoff with bounded random jitter
                long baseDelay = (long) Math.pow(2, delivery.getAttempt()) * 5; // 10s, 20s, 40s
                long jitter = ThreadLocalRandom.current().nextLong(1, 4); // 1-3s jitter
                delaySec = baseDelay + jitter;
            }

            delivery.setNextRetryAt(Instant.now().plusSeconds(delaySec));
            log.info("Webhook delivery [{}] scheduled for retry {} in {}s", delivery.getId(), delivery.getAttempt(), delaySec);
        }
    }

    /**
     * Periodic background worker:
     * 1. Recovers stale worker leases stuck in PROCESSING > 5 minutes.
     * 2. Polls due deliveries (FAILED/PENDING with nextRetryAt <= now) and dispatches them.
     */
    @Scheduled(fixedDelayString = "${secretvault.webhook.retry-interval-ms:5000}")
    @Transactional
    public void processScheduledRetriesAndStaleLeases() {
        Instant now = Instant.now();

        // 1. Stale worker lease recovery
        int recovered = deliveryRepository.recoverStaleDeliveries(
                WebhookDeliveryStatus.PROCESSING,
                WebhookDeliveryStatus.FAILED,
                now
        );
        if (recovered > 0) {
            log.warn("Recovered {} stale webhook delivery worker leases", recovered);
        }

        // 2. Poll due deliveries
        List<WebhookDelivery> dueDeliveries = deliveryRepository.findDueDeliveries(
                List.of(WebhookDeliveryStatus.FAILED, WebhookDeliveryStatus.PENDING),
                now,
                PageRequest.of(0, 20)
        );

        for (WebhookDelivery d : dueDeliveries) {
            Optional<WebhookEndpoint> epOpt = endpointRepository.findById(d.getWebhookId());
            if (epOpt.isPresent() && epOpt.get().isEnabled()) {
                deliverAsync(epOpt.get(), d.getPayloadJson(), d.getId());
            } else {
                d.setStatus(WebhookDeliveryStatus.DEAD_LETTER);
                d.setErrorMessage("Endpoint disabled or deleted");
                deliveryRepository.save(d);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<WebhookDelivery> getDeliveries(UUID workspaceId, UUID webhookId, UUID actorId, Pageable pageable) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_VIEW);
        if (webhookId != null) {
            return deliveryRepository.findByWorkspaceIdAndWebhookIdOrderByCreatedAtDesc(workspaceId, webhookId, pageable);
        }
        return deliveryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDelivery> getDeliveries(UUID workspaceId, UUID webhookId, Pageable pageable) {
        return getDeliveries(workspaceId, webhookId, null, pageable);
    }

    @Transactional
    public WebhookDelivery replayDelivery(UUID workspaceId, UUID deliveryId, UUID actorId) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_MANAGE);

        WebhookDelivery existing = deliveryRepository.findByWorkspaceIdAndId(workspaceId, deliveryId)
                .orElseThrow(() -> ApiException.notFound("Webhook delivery not found: " + deliveryId));

        WebhookEndpoint ep = endpointRepository.findByWorkspaceIdAndId(workspaceId, existing.getWebhookId())
                .orElseThrow(() -> ApiException.badRequest("Target webhook endpoint does not exist"));

        if (!ep.isEnabled()) {
            throw ApiException.badRequest("Cannot replay delivery for disabled webhook endpoint");
        }

        existing.setStatus(WebhookDeliveryStatus.PENDING);
        existing.setAttempt(1);
        existing.setErrorMessage(null);
        existing.setHttpStatus(null);
        existing.setWorkerId(null);
        existing.setLeasedUntil(null);
        existing.setNextRetryAt(Instant.now());

        WebhookDelivery saved = deliveryRepository.save(existing);
        deliverAsync(ep, saved.getPayloadJson(), saved.getId());

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.WEBHOOK_DELIVERY_REPLAYED,
                "WEBHOOK_DELIVERY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return saved;
    }

    @Transactional
    public WebhookDelivery replayDelivery(UUID workspaceId, UUID deliveryId) {
        return replayDelivery(workspaceId, deliveryId, null);
    }

    @Transactional
    public WebhookDelivery testEndpoint(UUID workspaceId, UUID endpointId, UUID actorId) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_MANAGE);

        WebhookEndpoint endpoint = endpointRepository.findByWorkspaceIdAndId(workspaceId, endpointId)
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint not found: " + endpointId));

        UUID pingEventId = UUID.randomUUID();
        String pingPayload;
        try {
            pingPayload = objectMapper.writeValueAsString(Map.of(
                    "eventId", pingEventId.toString(),
                    "eventType", "WEBHOOK_PING",
                    "workspaceId", workspaceId.toString(),
                    "occurredAt", Instant.now().toString(),
                    "data", Map.of(
                            "message", "SecretVault Webhook Ping Test",
                            "endpointId", endpointId.toString(),
                            "endpointName", endpoint.getName()
                    )
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct test ping payload", e);
        }

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWorkspaceId(workspaceId);
        delivery.setWebhookId(endpointId);
        delivery.setEventId(pingEventId);
        delivery.setAttempt(1);
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setNextRetryAt(Instant.now());
        delivery.setPayloadJson(pingPayload);

        WebhookDelivery saved = deliveryRepository.save(delivery);
        deliverAsync(endpoint, pingPayload, saved.getId());

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.WEBHOOK_DELIVERY_DISPATCHED,
                "WEBHOOK_DELIVERY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return saved;
    }
}

package com.secretvault.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.webhook.entity.WebhookDelivery;
import com.secretvault.webhook.entity.WebhookDeliveryStatus;
import com.secretvault.webhook.entity.WebhookEndpoint;
import com.secretvault.webhook.repository.WebhookDeliveryRepository;
import com.secretvault.webhook.repository.WebhookEndpointRepository;
import com.secretvault.webhook.security.SsrfProtectionValidator;
import com.secretvault.webhook.security.WebhookSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_DELIVERY_ATTEMPTS = 5;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookService webhookService;
    private final WebhookSigner webhookSigner;
    private final SsrfProtectionValidator ssrfValidator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookDeliveryService(
            WebhookDeliveryRepository deliveryRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookService webhookService,
            WebhookSigner webhookSigner,
            SsrfProtectionValidator ssrfValidator,
            ObjectMapper objectMapper
    ) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.webhookService = webhookService;
        this.webhookSigner = webhookSigner;
        this.ssrfValidator = ssrfValidator;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Transactional
    public void dispatchDomainEvent(DomainEvent event) {
        enqueueDeliveriesForEvent(event);
    }

    @Transactional
    public void enqueueDeliveriesForEvent(DomainEvent event) {
        List<WebhookEndpoint> endpoints = endpointRepository.findSubscribedEndpoints(
                event.getWorkspaceId(), event.getEventType().name()
        );

        for (WebhookEndpoint ep : endpoints) {
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setWorkspaceId(event.getWorkspaceId());
            delivery.setWebhookId(ep.getId());
            delivery.setEventId(event.getEventId());
            delivery.setAttempt(1);
            delivery.setStatus(WebhookDeliveryStatus.PENDING);
            delivery.setNextRetryAt(Instant.now());

            WebhookDelivery saved = deliveryRepository.save(delivery);
            deliverAsync(ep, event, saved.getId());
        }
    }

    @Async
    public CompletableFuture<WebhookDelivery> deliverAsync(WebhookEndpoint endpoint, DomainEvent event, UUID deliveryId) {
        return CompletableFuture.supplyAsync(() -> executeDelivery(endpoint, event, deliveryId));
    }

    @Transactional
    public WebhookDelivery executeDelivery(WebhookEndpoint endpoint, DomainEvent event, UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivery not found: " + deliveryId));

        long startTime = System.currentTimeMillis();

        try {
            // Re-validate SSRF prior to every dispatch
            ssrfValidator.validateDestinationUrl(endpoint.getDestinationUrl());

            String rawPayload = objectMapper.writeValueAsString(event);
            String payloadDigest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawPayload.getBytes())
            );
            delivery.setPayloadDigest(payloadDigest);

            long timestampSeconds = Instant.now().getEpochSecond();
            String signingSecret = webhookService.revealSigningSecret(endpoint);
            String signatureHeader = webhookSigner.computeSignature(signingSecret, timestampSeconds, rawPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getDestinationUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SecretVault-Webhook/1.0")
                    .header("X-SecretVault-Event-Id", event.getEventId().toString())
                    .header("X-SecretVault-Event-Type", event.getEventType().name())
                    .header("X-SecretVault-Timestamp", String.valueOf(timestampSeconds))
                    .header("X-SecretVault-Signature", signatureHeader)
                    .header("X-SecretVault-Delivery-Id", delivery.getId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(rawPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;
            delivery.setDurationMs(duration);
            delivery.setHttpStatus(response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus(WebhookDeliveryStatus.SUCCESS);
                delivery.setDeliveredAt(Instant.now());
                delivery.setErrorMessage(null);
                endpoint.setLastSuccessAt(Instant.now());
                log.info("Webhook delivery [{}] to [{}] succeeded with HTTP {}", deliveryId, endpoint.getName(), response.statusCode());
            } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                handleRetryableFailure(delivery, "HTTP " + response.statusCode());
                endpoint.setLastFailureAt(Instant.now());
            } else {
                // Permanent 4xx client failure
                delivery.setStatus(WebhookDeliveryStatus.FAILED);
                delivery.setErrorMessage("HTTP " + response.statusCode() + " client error (non-retryable)");
                endpoint.setLastFailureAt(Instant.now());
            }

        } catch (ApiException ssrfEx) {
            delivery.setStatus(WebhookDeliveryStatus.BLOCKED_SSRF);
            delivery.setErrorMessage(ssrfEx.getMessage());
            delivery.setDurationMs(System.currentTimeMillis() - startTime);
            endpoint.setLastFailureAt(Instant.now());
            log.warn("Blocked webhook dispatch [{}] due to SSRF: {}", deliveryId, ssrfEx.getMessage());
        } catch (Exception ex) {
            handleRetryableFailure(delivery, ex.getMessage());
            endpoint.setLastFailureAt(Instant.now());
        }

        endpointRepository.save(endpoint);
        return deliveryRepository.save(delivery);
    }

    private void handleRetryableFailure(WebhookDelivery delivery, String error) {
        delivery.setErrorMessage(error);
        if (delivery.getAttempt() >= MAX_DELIVERY_ATTEMPTS) {
            delivery.setStatus(WebhookDeliveryStatus.DEAD_LETTER);
            log.error("Webhook delivery [{}] reached max attempts, marked DEAD_LETTER", delivery.getId());
        } else {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setAttempt(delivery.getAttempt() + 1);
            long delaySec = (long) Math.pow(2, delivery.getAttempt()) * 5; // 10s, 20s, 40s
            delivery.setNextRetryAt(Instant.now().plusSeconds(delaySec));
        }
    }

    @Transactional(readOnly = true)
    public Page<WebhookDelivery> getDeliveries(UUID workspaceId, UUID webhookId, Pageable pageable) {
        if (webhookId != null) {
            return deliveryRepository.findByWorkspaceIdAndWebhookIdOrderByCreatedAtDesc(workspaceId, webhookId, pageable);
        }
        return deliveryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional
    public WebhookDelivery replayDelivery(UUID workspaceId, UUID deliveryId) {
        WebhookDelivery existing = deliveryRepository.findByWorkspaceIdAndId(workspaceId, deliveryId)
                .orElseThrow(() -> ApiException.notFound("Webhook delivery not found"));

        WebhookDelivery retry = new WebhookDelivery();
        retry.setWorkspaceId(workspaceId);
        retry.setWebhookId(existing.getWebhookId());
        retry.setEventId(existing.getEventId());
        retry.setAttempt(existing.getAttempt() + 1);
        retry.setStatus(WebhookDeliveryStatus.PENDING);
        retry.setNextRetryAt(Instant.now());
        return deliveryRepository.save(retry);
    }
}

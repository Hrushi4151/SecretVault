package com.secretvault.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.webhook.entity.WebhookEndpoint;
import com.secretvault.webhook.repository.WebhookEndpointRepository;
import com.secretvault.webhook.security.SsrfProtectionValidator;
import com.secretvault.webhook.security.WebhookSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEndpointRepository endpointRepository;
    private final SsrfProtectionValidator ssrfValidator;
    private final WebhookSigner webhookSigner;
    private final EncryptionService encryptionService;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WebhookService(
            WebhookEndpointRepository endpointRepository,
            SsrfProtectionValidator ssrfValidator,
            WebhookSigner webhookSigner,
            EncryptionService encryptionService,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.endpointRepository = endpointRepository;
        this.ssrfValidator = ssrfValidator;
        this.webhookSigner = webhookSigner;
        this.encryptionService = encryptionService;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookEndpoint createEndpoint(
            UUID workspaceId,
            String name,
            String destinationUrl,
            List<String> subscribedEvents,
            UUID creatorId
    ) {
        if (workspaceId == null) throw ApiException.badRequest("workspaceId is required");
        if (name == null || name.isBlank()) throw ApiException.badRequest("Webhook name is required");
        if (destinationUrl == null || destinationUrl.isBlank()) throw ApiException.badRequest("Destination URL is required");

        effectiveAccessService.requireWorkspacePermission(creatorId, workspaceId, AccessPermission.WEBHOOK_MANAGE);

        // Validate SSRF rules and DNS resolution
        ssrfValidator.validateDestinationUrl(destinationUrl);

        if (endpointRepository.findByWorkspaceIdAndName(workspaceId, name.trim()).isPresent()) {
            throw ApiException.conflict("Webhook endpoint with name '" + name + "' already exists");
        }

        String rawSecret = webhookSigner.generateSigningSecret();
        String secretPrefix = rawSecret.substring(0, Math.min(12, rawSecret.length())) + "...";

        EncryptedPayload payload = encryptionService.encrypt(rawSecret.getBytes(StandardCharsets.UTF_8), "system-webhook");
        String encryptedSecret;
        try {
            encryptedSecret = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize webhook signing secret", e);
        }

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setWorkspaceId(workspaceId);
        endpoint.setName(name.trim());
        endpoint.setDestinationUrl(destinationUrl.trim());
        endpoint.setEnabled(true);
        endpoint.setSecretPrefix(secretPrefix);
        endpoint.setSigningSecretEncrypted(encryptedSecret);
        endpoint.setCreatedBy(creatorId);

        if (subscribedEvents != null && !subscribedEvents.isEmpty()) {
            endpoint.setSubscribedEventsJson(String.join(",", subscribedEvents));
        } else {
            endpoint.setSubscribedEventsJson("*");
        }

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Created webhook endpoint [{}] for workspace [{}] -> [{}]", saved.getId(), workspaceId, destinationUrl);

        auditService.recordAudit(
                null,
                workspaceId,
                creatorId,
                "USER",
                AuditAction.WEBHOOK_ENDPOINT_CREATED,
                "WEBHOOK_ENDPOINT",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<WebhookEndpoint> getEndpoints(UUID workspaceId, UUID actorId, Pageable pageable) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_VIEW);
        return endpointRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WebhookEndpoint> getEndpoints(UUID workspaceId, Pageable pageable) {
        return getEndpoints(workspaceId, null, pageable);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint getEndpoint(UUID workspaceId, UUID endpointId, UUID actorId) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_VIEW);
        return endpointRepository.findByWorkspaceIdAndId(workspaceId, endpointId)
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint not found: " + endpointId));
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint getEndpoint(UUID workspaceId, UUID endpointId) {
        return getEndpoint(workspaceId, endpointId, null);
    }

    @Transactional
    public WebhookEndpoint updateEndpoint(
            UUID workspaceId,
            UUID endpointId,
            String name,
            String destinationUrl,
            Boolean enabled,
            List<String> subscribedEvents,
            UUID actorId
    ) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_MANAGE);

        WebhookEndpoint endpoint = getEndpoint(workspaceId, endpointId, actorId);

        if (destinationUrl != null && !destinationUrl.isBlank() && !destinationUrl.equals(endpoint.getDestinationUrl())) {
            ssrfValidator.validateDestinationUrl(destinationUrl);
            endpoint.setDestinationUrl(destinationUrl.trim());
        }

        if (name != null && !name.isBlank() && !name.equals(endpoint.getName())) {
            var existing = endpointRepository.findByWorkspaceIdAndName(workspaceId, name.trim());
            if (existing.isPresent() && !existing.get().getId().equals(endpointId)) {
                throw ApiException.conflict("Webhook endpoint with name '" + name + "' already exists");
            }
            endpoint.setName(name.trim());
        }

        if (enabled != null) {
            endpoint.setEnabled(enabled);
        }

        if (subscribedEvents != null) {
            endpoint.setSubscribedEventsJson(String.join(",", subscribedEvents));
        }

        WebhookEndpoint saved = endpointRepository.save(endpoint);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.WEBHOOK_ENDPOINT_UPDATED,
                "WEBHOOK_ENDPOINT",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return saved;
    }

    @Transactional
    public WebhookEndpoint updateEndpoint(
            UUID workspaceId,
            UUID endpointId,
            String name,
            String destinationUrl,
            Boolean enabled,
            List<String> subscribedEvents
    ) {
        return updateEndpoint(workspaceId, endpointId, name, destinationUrl, enabled, subscribedEvents, null);
    }

    @Transactional
    public void deleteEndpoint(UUID workspaceId, UUID endpointId, UUID actorId) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_MANAGE);

        WebhookEndpoint endpoint = getEndpoint(workspaceId, endpointId, actorId);
        endpointRepository.delete(endpoint);
        log.info("Deleted webhook endpoint [{}] for workspace [{}]", endpointId, workspaceId);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.WEBHOOK_ENDPOINT_DELETED,
                "WEBHOOK_ENDPOINT",
                endpointId,
                null,
                null,
                "SUCCESS"
        );
    }

    @Transactional
    public void deleteEndpoint(UUID workspaceId, UUID endpointId) {
        deleteEndpoint(workspaceId, endpointId, null);
    }

    public String revealSigningSecret(WebhookEndpoint endpoint) {
        if (endpoint == null || endpoint.getSigningSecretEncrypted() == null || endpoint.getSigningSecretEncrypted().isBlank()) {
            throw new IllegalStateException("Webhook endpoint missing signing secret (fail closed)");
        }
        try {
            EncryptedPayload payload = objectMapper.readValue(endpoint.getSigningSecretEncrypted(), EncryptedPayload.class);
            return new String(encryptionService.decrypt(payload, "system-webhook"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt webhook signing secret: {}", e.getMessage());
            throw new IllegalStateException("Failed to decrypt webhook signing secret: " + e.getMessage(), e);
        }
    }

    public String revealPreviousSigningSecret(WebhookEndpoint endpoint) {
        if (endpoint == null || endpoint.getPreviousSigningSecretEncrypted() == null || endpoint.getPreviousSigningSecretEncrypted().isBlank()) {
            return null;
        }
        try {
            EncryptedPayload payload = objectMapper.readValue(endpoint.getPreviousSigningSecretEncrypted(), EncryptedPayload.class);
            return new String(encryptionService.decrypt(payload, "system-webhook"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decrypt previous webhook signing secret: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public String revealSigningSecretForOperator(UUID workspaceId, UUID endpointId, UUID actorId) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_MANAGE);
        WebhookEndpoint endpoint = getEndpoint(workspaceId, endpointId, actorId);
        String secret = revealSigningSecret(endpoint);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.WEBHOOK_SECRET_REVEALED,
                "WEBHOOK_ENDPOINT",
                endpointId,
                null,
                null,
                "SUCCESS"
        );

        return secret;
    }

    @Transactional
    public String rotateSigningSecret(UUID workspaceId, UUID endpointId, UUID actorId) {
        effectiveAccessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.WEBHOOK_MANAGE);
        WebhookEndpoint endpoint = getEndpoint(workspaceId, endpointId, actorId);

        // Transition: current secret becomes previous secret
        endpoint.setPreviousSigningSecretEncrypted(endpoint.getSigningSecretEncrypted());
        endpoint.setSecretRotatedAt(Instant.now());

        // Generate and encrypt new secret
        String newRawSecret = webhookSigner.generateSigningSecret();
        String secretPrefix = newRawSecret.substring(0, Math.min(12, newRawSecret.length())) + "...";

        EncryptedPayload payload = encryptionService.encrypt(newRawSecret.getBytes(StandardCharsets.UTF_8), "system-webhook");
        String encryptedSecret;
        try {
            encryptedSecret = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize rotated webhook signing secret", e);
        }

        endpoint.setSigningSecretEncrypted(encryptedSecret);
        endpoint.setSecretPrefix(secretPrefix);
        endpointRepository.save(endpoint);

        log.info("Rotated signing secret for webhook endpoint [{}] in workspace [{}]", endpointId, workspaceId);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.WEBHOOK_SECRET_ROTATED,
                "WEBHOOK_ENDPOINT",
                endpointId,
                null,
                null,
                "SUCCESS"
        );

        return newRawSecret;
    }
}

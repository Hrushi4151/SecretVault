package com.secretvault.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEndpointRepository endpointRepository;
    private final SsrfProtectionValidator ssrfValidator;
    private final WebhookSigner webhookSigner;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public WebhookService(
            WebhookEndpointRepository endpointRepository,
            SsrfProtectionValidator ssrfValidator,
            WebhookSigner webhookSigner,
            EncryptionService encryptionService,
            ObjectMapper objectMapper
    ) {
        this.endpointRepository = endpointRepository;
        this.ssrfValidator = ssrfValidator;
        this.webhookSigner = webhookSigner;
        this.encryptionService = encryptionService;
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

        // Validate SSRF rules
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
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<WebhookEndpoint> getEndpoints(UUID workspaceId, Pageable pageable) {
        return endpointRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint getEndpoint(UUID workspaceId, UUID endpointId) {
        return endpointRepository.findByWorkspaceIdAndId(workspaceId, endpointId)
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint not found"));
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
        WebhookEndpoint endpoint = getEndpoint(workspaceId, endpointId);

        if (destinationUrl != null && !destinationUrl.isBlank() && !destinationUrl.equals(endpoint.getDestinationUrl())) {
            ssrfValidator.validateDestinationUrl(destinationUrl);
            endpoint.setDestinationUrl(destinationUrl.trim());
        }

        if (name != null && !name.isBlank() && !name.equals(endpoint.getName())) {
            if (endpointRepository.findByWorkspaceIdAndName(workspaceId, name.trim()).isPresent()) {
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

        return endpointRepository.save(endpoint);
    }

    @Transactional
    public void deleteEndpoint(UUID workspaceId, UUID endpointId) {
        WebhookEndpoint endpoint = getEndpoint(workspaceId, endpointId);
        endpointRepository.delete(endpoint);
        log.info("Deleted webhook endpoint [{}] for workspace [{}]", endpointId, workspaceId);
    }

    public String revealSigningSecret(WebhookEndpoint endpoint) {
        try {
            EncryptedPayload payload = objectMapper.readValue(endpoint.getSigningSecretEncrypted(), EncryptedPayload.class);
            return new String(encryptionService.decrypt(payload, "system-webhook"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt webhook signing secret: {}", e.getMessage());
            return "";
        }
    }
}

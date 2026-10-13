package com.secretvault.events.handler;

import com.secretvault.events.dispatcher.DomainEventHandler;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventType;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Event-driven domain event handler that triggers synchronization of updated or rotated secrets
 * to external hosting/cloud platform integrations (Vercel, Render) having active resource mappings.
 */
@Component
public class ProviderSyncDomainEventHandler implements DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ProviderSyncDomainEventHandler.class);
    private static final String CONSUMER_NAME = "ProviderSyncDomainEventHandler";

    private final ProviderResourceMappingRepository mappingRepository;
    private final ProviderSecretSyncService providerSecretSyncService;

    public ProviderSyncDomainEventHandler(
            ProviderResourceMappingRepository mappingRepository,
            ProviderSecretSyncService providerSecretSyncService
    ) {
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.providerSecretSyncService = Objects.requireNonNull(providerSecretSyncService, "providerSecretSyncService must not be null");
    }

    @Override
    public String getConsumerName() {
        return CONSUMER_NAME;
    }

    @Override
    public boolean supports(DomainEvent event) {
        if (event == null || event.getEventType() == null) {
            return false;
        }
        EventType type = event.getEventType();
        return type == EventType.SECRET_UPDATED
                || type == EventType.SECRET_VERSION_CREATED
                || type == EventType.ROTATION_COMPLETED;
    }

    @Override
    public void handle(DomainEvent event) {
        if (!supports(event)) {
            return;
        }

        UUID environmentId = event.getEnvironmentId();
        UUID secretId = event.getSecretId();
        UUID workspaceId = event.getWorkspaceId();

        if (environmentId == null || secretId == null || workspaceId == null) {
            log.debug("Skipping provider sync for event [{}]: missing environmentId, secretId, or workspaceId", event.getEventId());
            return;
        }

        UUID actorUserId = resolveActor(event);

        List<ProviderResourceMapping> mappings = mappingRepository.findByEnvironmentId(environmentId);
        if (mappings.isEmpty()) {
            return;
        }

        for (ProviderResourceMapping mapping : mappings) {
            if (!mapping.isSyncEnabled()) {
                continue;
            }

            try {
                log.info("Triggering event-driven secret sync for secret [{}] to mapping [{}] (integration: [{}])",
                        secretId, mapping.getId(), mapping.getIntegrationId());

                providerSecretSyncService.pushSecretToProvider(
                        workspaceId,
                        mapping.getIntegrationId(),
                        mapping.getId(),
                        secretId,
                        actorUserId
                );
            } catch (Exception e) {
                log.warn("Event-driven sync failed for secret [{}] on mapping [{}]: {}",
                        secretId, mapping.getId(), e.getMessage());
            }
        }
    }

    private UUID resolveActor(DomainEvent event) {
        if (event.getActorId() != null) {
            try {
                return UUID.fromString(event.getActorId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}

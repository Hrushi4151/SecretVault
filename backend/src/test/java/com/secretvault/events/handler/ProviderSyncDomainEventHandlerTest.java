package com.secretvault.events.handler;

import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventType;
import com.secretvault.provider.dto.PushSecretToProviderResponse;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderResourceMappingRepository;
import com.secretvault.provider.service.ProviderSecretSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderSyncDomainEventHandlerTest {

    @Mock
    private ProviderResourceMappingRepository mappingRepository;

    @Mock
    private ProviderSecretSyncService providerSecretSyncService;

    @Mock
    private DomainEvent domainEvent;

    private ProviderSyncDomainEventHandler handler;

    private UUID workspaceId;
    private UUID environmentId;
    private UUID secretId;
    private UUID integrationId;
    private UUID mappingId;
    private UUID actorUserId;

    @BeforeEach
    void setUp() {
        handler = new ProviderSyncDomainEventHandler(mappingRepository, providerSecretSyncService);

        workspaceId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        integrationId = UUID.randomUUID();
        mappingId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Consumer Name matches contract")
    void testConsumerName() {
        assertThat(handler.getConsumerName()).isEqualTo("ProviderSyncDomainEventHandler");
    }

    @Test
    @DisplayName("Supports only secret update, version creation, and rotation completed events")
    void testSupportsEventTypes() {
        when(domainEvent.getEventType()).thenReturn(EventType.SECRET_UPDATED);
        assertThat(handler.supports(domainEvent)).isTrue();

        when(domainEvent.getEventType()).thenReturn(EventType.SECRET_VERSION_CREATED);
        assertThat(handler.supports(domainEvent)).isTrue();

        when(domainEvent.getEventType()).thenReturn(EventType.ROTATION_COMPLETED);
        assertThat(handler.supports(domainEvent)).isTrue();

        when(domainEvent.getEventType()).thenReturn(EventType.SECRET_DELETED);
        assertThat(handler.supports(domainEvent)).isFalse();

        when(domainEvent.getEventType()).thenReturn(EventType.LEASE_CREATED);
        assertThat(handler.supports(domainEvent)).isFalse();

        assertThat(handler.supports(null)).isFalse();
    }

    @Test
    @DisplayName("Pushes secret to external provider for all active mappings when event arrives")
    void testHandleSyncPushesSecret() {
        when(domainEvent.getEventType()).thenReturn(EventType.SECRET_UPDATED);
        when(domainEvent.getWorkspaceId()).thenReturn(workspaceId);
        when(domainEvent.getEnvironmentId()).thenReturn(environmentId);
        when(domainEvent.getSecretId()).thenReturn(secretId);
        when(domainEvent.getActorId()).thenReturn(actorUserId.toString());

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_1", "app", "production", "{}", true
        );
        mapping.setId(mappingId);

        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(providerSecretSyncService.pushSecretToProvider(workspaceId, integrationId, mappingId, secretId, actorUserId))
                .thenReturn(PushSecretToProviderResponse.success(
                        secretId, "DB_PASS", ProviderType.VERCEL, "prj_vercel_1", "production", "env_123", "UPDATE"
                ));

        handler.handle(domainEvent);

        verify(providerSecretSyncService).pushSecretToProvider(workspaceId, integrationId, mappingId, secretId, actorUserId);
    }

    @Test
    @DisplayName("Skips mappings where sync is disabled")
    void testHandleSkipsSyncDisabledMappings() {
        when(domainEvent.getEventType()).thenReturn(EventType.ROTATION_COMPLETED);
        when(domainEvent.getWorkspaceId()).thenReturn(workspaceId);
        when(domainEvent.getEnvironmentId()).thenReturn(environmentId);
        when(domainEvent.getSecretId()).thenReturn(secretId);
        when(domainEvent.getActorId()).thenReturn(actorUserId.toString());

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_1", "app", "production", "{}", false
        );
        mapping.setId(mappingId);

        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));

        handler.handle(domainEvent);

        verifyNoInteractions(providerSecretSyncService);
    }

    @Test
    @DisplayName("Handles provider push failure gracefully without throwing exception")
    void testHandleCatchesPushException() {
        when(domainEvent.getEventType()).thenReturn(EventType.SECRET_VERSION_CREATED);
        when(domainEvent.getWorkspaceId()).thenReturn(workspaceId);
        when(domainEvent.getEnvironmentId()).thenReturn(environmentId);
        when(domainEvent.getSecretId()).thenReturn(secretId);
        when(domainEvent.getActorId()).thenReturn("invalid-uuid");

        ProviderResourceMapping mapping = new ProviderResourceMapping(
                workspaceId, integrationId, UUID.randomUUID(), environmentId,
                ProviderResourceType.PROJECT, "prj_vercel_1", "app", "production", "{}", true
        );
        mapping.setId(mappingId);

        when(mappingRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(mapping));
        when(providerSecretSyncService.pushSecretToProvider(eq(workspaceId), eq(integrationId), eq(mappingId), eq(secretId), any(UUID.class)))
                .thenThrow(new RuntimeException("Network timeout to provider"));

        // Must not throw
        handler.handle(domainEvent);

        verify(providerSecretSyncService).pushSecretToProvider(eq(workspaceId), eq(integrationId), eq(mappingId), eq(secretId), any(UUID.class));
    }
}

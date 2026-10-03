package com.secretvault.events.publisher;

import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for publishing domain events to the transactional outbox.
 */
public interface EventPublisher {
    void publish(DomainEvent event);

    default void publish(EventType eventType, UUID workspaceId, String aggregateType, String aggregateId, EventSeverity severity, Map<String, ?> metadata) {
        BaseDomainEvent.Builder builder = BaseDomainEvent.builder()
                .eventType(eventType)
                .workspaceId(workspaceId)
                .aggregateType(aggregateType != null ? aggregateType : "UNKNOWN")
                .aggregateId(aggregateId != null ? aggregateId : "UNKNOWN")
                .severity(severity != null ? severity : EventSeverity.INFO);
        if (metadata != null) {
            metadata.forEach(builder::addMetadata);
        }
        publish(builder.build());
    }
}


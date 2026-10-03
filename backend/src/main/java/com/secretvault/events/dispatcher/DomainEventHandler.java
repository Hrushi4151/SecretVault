package com.secretvault.events.dispatcher;

import com.secretvault.events.model.DomainEvent;

/**
 * Pluggable domain event consumer interface.
 * Each handler is executed independently with idempotent processing guarantees.
 */
public interface DomainEventHandler {

    /**
     * Unique consumer name for deduplication in event_processing_log.
     */
    String getConsumerName();

    /**
     * Handles the domain event. Must be idempotent.
     */
    void handle(DomainEvent event);

    /**
     * Whether this handler is interested in the given event type.
     */
    default boolean supports(DomainEvent event) {
        return true;
    }
}

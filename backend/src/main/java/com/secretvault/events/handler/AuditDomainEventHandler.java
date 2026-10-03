package com.secretvault.events.handler;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.events.dispatcher.DomainEventHandler;
import com.secretvault.events.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Domain event consumer that correlates events with the immutable Audit Ledger.
 */
@Component
public class AuditDomainEventHandler implements DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditDomainEventHandler.class);
    private static final String CONSUMER_NAME = "AuditDomainEventHandler";

    private final AuditService auditService;

    public AuditDomainEventHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public String getConsumerName() {
        return CONSUMER_NAME;
    }

    @Override
    public void handle(DomainEvent event) {
        log.debug("Audit handler processing event [{}] type [{}]", event.getEventId(), event.getEventType());
        try {
            UUID actorUuid = null;
            if (event.getActorId() != null) {
                try {
                    actorUuid = UUID.fromString(event.getActorId());
                } catch (IllegalArgumentException ignored) {}
            }

            UUID resourceUuid = null;
            if (event.getAggregateId() != null) {
                try {
                    resourceUuid = UUID.fromString(event.getAggregateId());
                } catch (IllegalArgumentException ignored) {}
            }

            AuditAction action = mapToAuditAction(event.getEventType().name());
            if (action != null) {
                auditService.recordAudit(
                        null,
                        event.getWorkspaceId(),
                        actorUuid,
                        event.getActorType() != null ? event.getActorType() : "SYSTEM",
                        action,
                        event.getAggregateType() != null ? event.getAggregateType() : "DOMAIN_EVENT",
                        resourceUuid,
                        event.getRequestId(),
                        null,
                        "SUCCESS"
                );
            }
        } catch (Exception ex) {
            log.warn("Non-fatal: Failed to log audit record for event [{}]: {}", event.getEventId(), ex.getMessage());
        }
    }

    private AuditAction mapToAuditAction(String eventType) {
        try {
            return AuditAction.valueOf(eventType);
        } catch (Exception e) {
            return null;
        }
    }
}

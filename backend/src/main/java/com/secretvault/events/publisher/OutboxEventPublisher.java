package com.secretvault.events.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.entity.DomainEventEntity;
import com.secretvault.events.entity.OutboxEvent;
import com.secretvault.events.entity.OutboxStatus;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.repository.DomainEventRepository;
import com.secretvault.events.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final DomainEventRepository domainEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
            DomainEventRepository domainEventRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.domainEventRepository = domainEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        if (event == null) {
            throw ApiException.badRequest("Domain event cannot be null");
        }
        if (event.getWorkspaceId() == null) {
            throw ApiException.badRequest("Domain event workspaceId is mandatory");
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(event);

            // 1. Persist to authoritative, immutable Domain Event Store
            DomainEventEntity domainEntity = new DomainEventEntity();
            domainEntity.setEventId(event.getEventId());
            domainEntity.setEventType(event.getEventType().name());
            domainEntity.setEventVersion(event.getEventVersion());
            domainEntity.setWorkspaceId(event.getWorkspaceId());
            domainEntity.setProjectId(event.getProjectId());
            domainEntity.setEnvironmentId(event.getEnvironmentId());
            domainEntity.setSecretId(event.getSecretId());
            domainEntity.setAggregateType(event.getAggregateType() != null ? event.getAggregateType() : "UNKNOWN");
            domainEntity.setAggregateId(event.getAggregateId() != null ? event.getAggregateId() : event.getEventId().toString());
            domainEntity.setActorType(event.getActorType() != null ? event.getActorType() : "USER");
            domainEntity.setActorId(event.getActorId());
            domainEntity.setCorrelationId(event.getCorrelationId());
            domainEntity.setCausationId(event.getCausationId());
            domainEntity.setRequestId(event.getRequestId());
            domainEntity.setSource(event.getSource() != null ? event.getSource() : "secretvault-core");
            domainEntity.setSeverity(event.getSeverity() != null ? event.getSeverity().name() : "INFO");
            domainEntity.setPayloadJson(jsonPayload);
            domainEntity.setMetadataJson(event.getMetadata() != null ? objectMapper.writeValueAsString(event.getMetadata()) : "{}");
            domainEntity.setOccurredAt(event.getOccurredAt());
            domainEntity.setRecordedAt(Instant.now());
            domainEventRepository.save(domainEntity);

            // 2. Persist to operational Transactional Outbox
            OutboxEvent outbox = new OutboxEvent();
            outbox.setEventId(event.getEventId());
            outbox.setEventType(event.getEventType().name());
            outbox.setEventVersion(event.getEventVersion());
            outbox.setWorkspaceId(event.getWorkspaceId());
            outbox.setAggregateType(event.getAggregateType() != null ? event.getAggregateType() : "UNKNOWN");
            outbox.setAggregateId(event.getAggregateId() != null ? event.getAggregateId() : event.getEventId().toString());
            outbox.setPayload(jsonPayload);
            outbox.setOccurredAt(event.getOccurredAt());
            outbox.setAvailableAt(Instant.now());
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setAttemptCount(0);
            outbox.setMaxAttempts(5);
            outbox.setCorrelationId(event.getCorrelationId());
            outbox.setCausationId(event.getCausationId());
            outbox.setRequestId(event.getRequestId());

            outboxEventRepository.save(outbox);
            log.debug("Enqueued outbox event [{}] type [{}] for workspace [{}]",
                    event.getEventId(), event.getEventType(), event.getWorkspaceId());
        } catch (Exception ex) {
            log.error("Failed to serialize and enqueue outbox event [{}]: {}", event.getEventId(), ex.getMessage(), ex);
            throw ApiException.internal("EVENT_PUBLISH_ERROR", "Failed to persist domain event to transactional outbox", ex);
        }
    }

    /**
     * Publishes event ensuring an active transaction (creates one if not currently present).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishInTransaction(DomainEvent event) {
        publish(event);
    }
}

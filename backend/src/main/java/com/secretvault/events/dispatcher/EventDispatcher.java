package com.secretvault.events.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.events.entity.EventProcessingLog;
import com.secretvault.events.entity.OutboxEvent;
import com.secretvault.events.entity.OutboxStatus;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.repository.EventProcessingLogRepository;
import com.secretvault.events.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);
    private static final String WORKER_ID = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private static final int BATCH_SIZE = 50;
    private static final Duration STALE_LOCK_THRESHOLD = Duration.ofMinutes(5);

    private final OutboxEventRepository outboxRepository;
    private final EventProcessingLogRepository processingLogRepository;
    private final List<DomainEventHandler> handlers;
    private final ObjectMapper objectMapper;

    public EventDispatcher(
            OutboxEventRepository outboxRepository,
            EventProcessingLogRepository processingLogRepository,
            List<DomainEventHandler> handlers,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.processingLogRepository = processingLogRepository;
        this.handlers = handlers != null ? handlers : List.of();
        this.objectMapper = objectMapper;
    }

    /**
     * Periodic scheduled poller for processing pending outbox events.
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void processOutboxBatch() {
        try {
            recoverStaleLocks();
            dispatchPendingEvents();
        } catch (Exception ex) {
            log.error("Error in scheduled outbox dispatcher run: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Claims and dispatches pending events. Can also be invoked directly by tests.
     */
    @Transactional
    public int dispatchPendingEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> pending = outboxRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                OutboxStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE)
        );

        if (pending.isEmpty()) {
            // Also check for FAILED events ready for retry
            pending = outboxRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                    OutboxStatus.FAILED, now, PageRequest.of(0, BATCH_SIZE)
            );
        }

        int processedCount = 0;
        for (OutboxEvent event : pending) {
            if (claimAndDispatch(event)) {
                processedCount++;
            }
        }
        return processedCount;
    }

    @Transactional
    public boolean claimAndDispatch(OutboxEvent outbox) {
        // Lock event
        outbox.setStatus(OutboxStatus.PROCESSING);
        outbox.setLockedAt(Instant.now());
        outbox.setLockedBy(WORKER_ID);
        outbox.setAttemptCount(outbox.getAttemptCount() + 1);
        outboxRepository.saveAndFlush(outbox);

        try {
            BaseDomainEvent domainEvent = objectMapper.readValue(outbox.getPayload(), BaseDomainEvent.class);

            for (DomainEventHandler handler : handlers) {
                if (!handler.supports(domainEvent)) {
                    continue;
                }

                String consumerName = handler.getConsumerName();
                boolean alreadyProcessed = processingLogRepository.existsByEventIdAndConsumerName(
                        domainEvent.getEventId(), consumerName
                );

                if (alreadyProcessed) {
                    log.debug("Event [{}] already processed by consumer [{}], skipping",
                            domainEvent.getEventId(), consumerName);
                    continue;
                }

                try {
                    handler.handle(domainEvent);
                    processingLogRepository.save(new EventProcessingLog(
                            domainEvent.getEventId(),
                            consumerName,
                            domainEvent.getWorkspaceId(),
                            "PROCESSED",
                            domainEvent.getCorrelationId()
                    ));
                } catch (Exception handlerEx) {
                    log.error("Consumer [{}] failed on event [{}]: {}",
                            consumerName, domainEvent.getEventId(), handlerEx.getMessage(), handlerEx);
                    EventProcessingLog errorLog = new EventProcessingLog(
                            domainEvent.getEventId(),
                            consumerName,
                            domainEvent.getWorkspaceId(),
                            "FAILED",
                            domainEvent.getCorrelationId()
                    );
                    errorLog.setError(handlerEx.getMessage());
                    processingLogRepository.save(errorLog);
                    throw handlerEx;
                }
            }

            outbox.setStatus(OutboxStatus.PROCESSED);
            outbox.setProcessedAt(Instant.now());
            outbox.setLastError(null);
            outbox.setLockedAt(null);
            outbox.setLockedBy(null);
            outboxRepository.save(outbox);
            return true;

        } catch (Exception ex) {
            log.warn("Failed dispatching outbox event [{}]: {}", outbox.getEventId(), ex.getMessage());
            outbox.setLastError(ex.getMessage());
            outbox.setLockedAt(null);
            outbox.setLockedBy(null);

            if (outbox.getAttemptCount() >= outbox.getMaxAttempts()) {
                outbox.setStatus(OutboxStatus.DEAD_LETTER);
                log.error("Outbox event [{}] reached max attempts ({}), marked DEAD_LETTER",
                        outbox.getEventId(), outbox.getMaxAttempts());
            } else {
                outbox.setStatus(OutboxStatus.FAILED);
                long delaySeconds = calculateExponentialBackoff(outbox.getAttemptCount());
                outbox.setAvailableAt(Instant.now().plusSeconds(delaySeconds));
            }
            outboxRepository.save(outbox);
            return false;
        }
    }

    /**
     * Recovers abandoned PROCESSING events where the worker lock timed out.
     */
    @Transactional
    public void recoverStaleLocks() {
        Instant cutoff = Instant.now().minus(STALE_LOCK_THRESHOLD);
        List<OutboxEvent> stale = outboxRepository.findStaleLockedEvents(
                OutboxStatus.PROCESSING, cutoff, PageRequest.of(0, 50)
        );

        for (OutboxEvent event : stale) {
            log.warn("Recovering stale locked event [{}] locked by [{}] at [{}]",
                    event.getEventId(), event.getLockedBy(), event.getLockedAt());
            event.setStatus(OutboxStatus.PENDING);
            event.setLockedAt(null);
            event.setLockedBy(null);
            outboxRepository.save(event);
        }
    }

    /**
     * Replays/dispatches a DomainEvent directly through registered handlers.
     */
    public void dispatchDomainEvent(com.secretvault.events.model.DomainEvent domainEvent, boolean reexecuteSideEffects) {
        if (domainEvent == null) return;
        for (DomainEventHandler handler : handlers) {
            if (!handler.supports(domainEvent)) continue;
            // If side-effects are not authorized during replay and handler is automation/action, skip destructive side effects unless allowed
            if (!reexecuteSideEffects && handler.getConsumerName().contains("Automation")) {
                log.info("Skipping automation handler during dry replay of event {}", domainEvent.eventId());
                continue;
            }
            try {
                handler.handle(domainEvent);
            } catch (Exception e) {
                log.error("Handler [{}] failed during event replay {}: {}", handler.getConsumerName(), domainEvent.eventId(), e.getMessage(), e);
            }
        }
    }

    private long calculateExponentialBackoff(int attempt) {
        long base = (long) Math.pow(2, Math.min(attempt, 6)); // 2, 4, 8, 16, 32, 64s
        long jitter = ThreadLocalRandom.current().nextLong(1, 4);
        return base + jitter;
    }
}


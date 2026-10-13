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
        return claimAndDispatch(outbox, WORKER_ID, UUID.randomUUID().toString());
    }

    @Transactional
    public boolean claimAndDispatch(OutboxEvent outbox, String workerId, String claimToken) {
        Instant now = Instant.now();
        Instant staleCutoff = now.minus(STALE_LOCK_THRESHOLD);
        int claimed = outboxRepository.claimEventAtomic(
                outbox.getId(),
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING,
                OutboxStatus.FAILED,
                OutboxStatus.PROCESSING,
                staleCutoff,
                workerId,
                claimToken,
                now
        );

        if (claimed == 0) {
            log.debug("Event [{}] was already claimed by another worker; skipping", outbox.getEventId());
            return false;
        }

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
                    String sanitizedHandlerError = sanitizeErrorMessage(handlerEx.getMessage());
                    log.error("Consumer [{}] failed on event [{}]: {}",
                            consumerName, domainEvent.getEventId(), sanitizedHandlerError);
                    EventProcessingLog errorLog = new EventProcessingLog(
                            domainEvent.getEventId(),
                            consumerName,
                            domainEvent.getWorkspaceId(),
                            "FAILED",
                            domainEvent.getCorrelationId()
                    );
                    errorLog.setError(sanitizedHandlerError);
                    processingLogRepository.save(errorLog);
                    throw handlerEx;
                }
            }

            // Guarded completion: Ensure lock was not lost/stolen before marking PROCESSED
            int completed = outboxRepository.completeEventGuarded(
                    outbox.getId(),
                    OutboxStatus.PROCESSED,
                    workerId,
                    claimToken,
                    Instant.now()
            );
            if (completed == 0) {
                log.warn("Stale worker [{}] failed to complete event [{}] - lock was stolen or expired",
                        workerId, outbox.getEventId());
                return false;
            }
            return true;

        } catch (Exception ex) {
            String sanitizedError = sanitizeErrorMessage(ex.getMessage());
            log.warn("Failed dispatching outbox event [{}]: {}", outbox.getEventId(), sanitizedError);

            int nextAttempt = outbox.getAttemptCount() + 1;
            if (nextAttempt >= outbox.getMaxAttempts()) {
                outboxRepository.failEventGuarded(
                        outbox.getId(),
                        OutboxStatus.DEAD_LETTER,
                        Instant.now(),
                        sanitizedError,
                        workerId,
                        claimToken
                );
                log.error("Outbox event [{}] reached max attempts ({}), marked DEAD_LETTER",
                        outbox.getEventId(), outbox.getMaxAttempts());
            } else {
                long delaySeconds = calculateExponentialBackoff(nextAttempt);
                outboxRepository.failEventGuarded(
                        outbox.getId(),
                        OutboxStatus.FAILED,
                        Instant.now().plusSeconds(delaySeconds),
                        sanitizedError,
                        workerId,
                        claimToken
                );
            }
            return false;
        }
    }

    /**
     * Atomically recovers abandoned PROCESSING events where the worker lock timed out.
     * Guaranteed not to reset or overwrite newer claims.
     */
    @Transactional
    public void recoverStaleLocks() {
        Instant cutoff = Instant.now().minus(STALE_LOCK_THRESHOLD);
        int recovered = outboxRepository.recoverStaleLockedEventsAtomic(
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING,
                cutoff
        );
        if (recovered > 0) {
            log.warn("Safely recovered {} stale locked outbox events back to PENDING", recovered);
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

    /**
     * Resets a DEAD_LETTER or FAILED outbox event back to PENDING for immediate redelivery.
     */
    @Transactional
    public boolean retryDeadLetterEvent(UUID workspaceId, UUID eventId) {
        OutboxEvent event = outboxRepository.findByWorkspaceIdAndEventId(workspaceId, eventId)
                .orElseThrow(() -> com.secretvault.common.exception.ApiException.notFound("Event not found in outbox"));

        if (event.getStatus() != OutboxStatus.DEAD_LETTER && event.getStatus() != OutboxStatus.FAILED) {
            throw com.secretvault.common.exception.ApiException.badRequest("Event is not in DEAD_LETTER or FAILED status: " + event.getStatus());
        }

        event.setStatus(OutboxStatus.PENDING);
        event.setAvailableAt(Instant.now());
        event.setAttemptCount(0);
        event.setLastError(null);
        event.setLockedAt(null);
        event.setLockedBy(null);
        outboxRepository.save(event);

        log.info("Dead-letter event [{}] in workspace [{}] reset to PENDING for redelivery", eventId, workspaceId);
        return true;
    }

    /**
     * Gathers operational backlog and health metrics for the outbox.
     */
    @Transactional(readOnly = true)
    public com.secretvault.events.dto.OutboxStatsDto getOutboxStats(UUID workspaceId) {
        long pending = outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.PENDING);
        long processing = outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.PROCESSING);
        long failed = outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.FAILED);
        long deadLetter = outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.DEAD_LETTER);
        long processed = outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.PROCESSED);

        List<OutboxEvent> oldestPending = outboxRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                OutboxStatus.PENDING, Instant.now().plus(Duration.ofDays(1)), PageRequest.of(0, 1)
        );
        Long oldestPendingAgeSeconds = null;
        if (!oldestPending.isEmpty()) {
            oldestPendingAgeSeconds = Duration.between(oldestPending.get(0).getCreatedAt(), Instant.now()).toSeconds();
        }

        return new com.secretvault.events.dto.OutboxStatsDto(
                pending, processing, failed, deadLetter, processed, oldestPendingAgeSeconds
        );
    }

    private long calculateExponentialBackoff(int attempt) {
        long base = (long) Math.pow(2, Math.min(attempt, 6)); // 2, 4, 8, 16, 32, 64s
        long jitter = ThreadLocalRandom.current().nextLong(1, 4);
        return base + jitter;
    }

    public static String sanitizeErrorMessage(String error) {
        if (error == null) return null;
        String sanitized = error;
        // 1. Canary tokens (sec_canary..., secret_canary...)
        sanitized = sanitized.replaceAll("(?i)(sec[a-z]*[_-]?canary[a-zA-Z0-9_\\-]*)", "[REDACTED_CANARY]");
        // 2. Known token prefixes (GitHub, AWS, Slack, GitLab, Stripe, Google)
        sanitized = sanitized.replaceAll("(?i)(gh[pours]_[a-zA-Z0-9]{20,})", "[REDACTED_TOKEN]");
        sanitized = sanitized.replaceAll("(?i)(A[SK]IA[0-9A-Z]{16})", "[REDACTED_TOKEN]");
        sanitized = sanitized.replaceAll("(?i)(xox[baprs]-[a-zA-Z0-9\\-]{10,})", "[REDACTED_TOKEN]");
        sanitized = sanitized.replaceAll("(?i)(glpat-[a-zA-Z0-9\\-_]{20,})", "[REDACTED_TOKEN]");
        sanitized = sanitized.replaceAll("(?i)([rs]k_live_[a-zA-Z0-9]{24,})", "[REDACTED_TOKEN]");
        sanitized = sanitized.replaceAll("(?i)(AIza[0-9A-Za-z\\-_]{35})", "[REDACTED_TOKEN]");
        // 3. PEM Private Keys (multiline matching)
        sanitized = sanitized.replaceAll("-----BEGIN[ A-Z_-]+KEY-----[\\s\\S]*?-----END[ A-Z_-]+KEY-----", "[REDACTED_KEY]");
        // 4. Authorization / Bearer tokens: strip token value without swallowing whole line
        sanitized = sanitized.replaceAll("(?i)(bearer\\s+)[a-zA-Z0-9_\\-\\.~+/]+=*", "$1[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)(authorization:\\s*(?:bearer\\s+)?)[^\\s,;]+", "$1[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s,;]+", "$1[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)(password\\s*[=:]\\s*)[^\\s,;]+", "$1[REDACTED]");
        // 5. High-entropy hex tokens (32+ hex chars with entropy >= 3.0)
        sanitized = com.secretvault.events.model.BaseDomainEvent.sanitizeHexTokensInText(sanitized);
        return sanitized;
    }
}


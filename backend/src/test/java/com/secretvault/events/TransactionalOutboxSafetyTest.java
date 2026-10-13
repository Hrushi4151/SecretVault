package com.secretvault.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.dispatcher.DomainEventHandler;
import com.secretvault.events.dispatcher.EventDispatcher;
import com.secretvault.events.dto.OutboxStatsDto;
import com.secretvault.events.entity.DomainEventEntity;
import com.secretvault.events.entity.EventProcessingLog;
import com.secretvault.events.entity.OutboxEvent;
import com.secretvault.events.entity.OutboxStatus;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.OutboxEventPublisher;
import com.secretvault.events.repository.DomainEventRepository;
import com.secretvault.events.repository.EventProcessingLogRepository;
import com.secretvault.events.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: Transactional Outbox Guarantees & Concurrency Safety Tests")
class TransactionalOutboxSafetyTest {

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private EventProcessingLogRepository processingLogRepository;

    private ObjectMapper objectMapper;
    private OutboxEventPublisher publisher;
    private EventDispatcher dispatcher;

    private UUID workspaceId;
    private UUID secretId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        workspaceId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        publisher = new OutboxEventPublisher(domainEventRepository, outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Publisher persists both authoritative DomainEventEntity and OutboxEvent in transaction")
    void testDualPersistenceInTransaction() {
        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_CREATED)
                .workspaceId(workspaceId)
                .secretId(secretId)
                .aggregateType("SECRET")
                .aggregateId(secretId.toString())
                .addMetadata("version", 1)
                .build();

        publisher.publish(event);

        ArgumentCaptor<DomainEventEntity> domainCaptor = ArgumentCaptor.forClass(DomainEventEntity.class);
        verify(domainEventRepository, times(1)).save(domainCaptor.capture());
        DomainEventEntity savedDomain = domainCaptor.getValue();
        assertThat(savedDomain.getEventId()).isEqualTo(event.getEventId());
        assertThat(savedDomain.getEventType()).isEqualTo("SECRET_CREATED");
        assertThat(savedDomain.getWorkspaceId()).isEqualTo(workspaceId);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(outboxCaptor.capture());
        OutboxEvent savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getEventId()).isEqualTo(event.getEventId());
        assertThat(savedOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(0);
        assertThat(savedOutbox.getMaxAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("Atomic claiming: Second concurrent worker receives 0 claimed rows and skips processing")
    void testConcurrentWorkerClaimAtomic() {
        UUID eventPk = UUID.randomUUID();
        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(eventPk);
        outbox.setEventId(UUID.randomUUID());
        outbox.setWorkspaceId(workspaceId);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setPayload("{}");

        // Worker 1 claims successfully (1 row updated)
        when(outboxRepository.claimEventAtomic(eq(eventPk), eq(OutboxStatus.PROCESSING), eq(OutboxStatus.PENDING), eq(OutboxStatus.FAILED), eq(OutboxStatus.PROCESSING), any(Instant.class), anyString(), anyString(), any(Instant.class)))
                .thenReturn(0); // Simulate Worker 2 attempting after Worker 1 already claimed

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(), objectMapper);

        boolean claimedAndDispatched = dispatcher.claimAndDispatch(outbox);

        assertThat(claimedAndDispatched).isFalse();
        // Verify no handler or completion was attempted
        verify(outboxRepository, never()).completeEventGuarded(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Stale worker defense: Guarded completion fails if worker lock was lost or expired")
    void testStaleWorkerGuardedCompletion() throws Exception {
        UUID eventPk = UUID.randomUUID();
        UUID domainEventId = UUID.randomUUID();

        BaseDomainEvent domainEvent = BaseDomainEvent.builder()
                .eventId(domainEventId)
                .eventType(EventType.SECRET_UPDATED)
                .workspaceId(workspaceId)
                .build();
        String payloadJson = objectMapper.writeValueAsString(domainEvent);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(eventPk);
        outbox.setEventId(domainEventId);
        outbox.setWorkspaceId(workspaceId);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setPayload(payloadJson);

        when(outboxRepository.claimEventAtomic(eq(eventPk), any(), any(), any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(1);

        // Guarded completion returns 0 because lock was stolen by another worker
        when(outboxRepository.completeEventGuarded(eq(eventPk), eq(OutboxStatus.PROCESSED), anyString(), anyString(), any()))
                .thenReturn(0);

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(), objectMapper);

        boolean result = dispatcher.claimAndDispatch(outbox);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Claim Token Fencing: Worker A losing claim to Worker B cannot complete or fail the event")
    void testWorkerALosingClaimToWorkerB() throws Exception {
        UUID eventPk = UUID.randomUUID();
        UUID domainEventId = UUID.randomUUID();
        BaseDomainEvent domainEvent = BaseDomainEvent.builder()
                .eventId(domainEventId)
                .eventType(EventType.SECRET_UPDATED)
                .workspaceId(workspaceId)
                .build();
        String payloadJson = objectMapper.writeValueAsString(domainEvent);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(eventPk);
        outbox.setEventId(domainEventId);
        outbox.setWorkspaceId(workspaceId);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setPayload(payloadJson);

        String tokenA = "token-worker-a-1111";
        String tokenB = "token-worker-b-2222";

        // Worker A claims with tokenA
        when(outboxRepository.claimEventAtomic(eq(eventPk), any(), any(), any(), any(), any(), eq("worker-A"), eq(tokenA), any()))
                .thenReturn(1);

        // Worker B later reclaims with tokenB after lease expiry
        when(outboxRepository.claimEventAtomic(eq(eventPk), any(), any(), any(), any(), any(), eq("worker-B"), eq(tokenB), any()))
                .thenReturn(1);

        // When Worker A attempts to complete with tokenA, database rejects because claimToken in DB is tokenB
        when(outboxRepository.completeEventGuarded(eq(eventPk), eq(OutboxStatus.PROCESSED), eq("worker-A"), eq(tokenA), any()))
                .thenReturn(0);

        // Worker B succeeds
        when(outboxRepository.completeEventGuarded(eq(eventPk), eq(OutboxStatus.PROCESSED), eq("worker-B"), eq(tokenB), any()))
                .thenReturn(1);

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(), objectMapper);

        // Worker A attempt fails
        boolean resultA = dispatcher.claimAndDispatch(outbox, "worker-A", tokenA);
        assertThat(resultA).isFalse();

        // Worker B attempt succeeds
        boolean resultB = dispatcher.claimAndDispatch(outbox, "worker-B", tokenB);
        assertThat(resultB).isTrue();
    }

    @Test
    @DisplayName("Claim Token Fencing: Same worker process identity claiming twice generates distinct tokens; older token rejected")
    void testSameWorkerProcessIdentityDistinctTokens() throws Exception {
        UUID eventPk = UUID.randomUUID();
        UUID domainEventId = UUID.randomUUID();
        BaseDomainEvent domainEvent = BaseDomainEvent.builder()
                .eventId(domainEventId)
                .eventType(EventType.SECRET_UPDATED)
                .workspaceId(workspaceId)
                .build();
        String payloadJson = objectMapper.writeValueAsString(domainEvent);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(eventPk);
        outbox.setEventId(domainEventId);
        outbox.setWorkspaceId(workspaceId);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setPayload(payloadJson);

        String worker1 = "worker-1";
        String token1FirstRun = "token-run-1";
        String token1SecondRun = "token-run-2";

        // First run acquires token1FirstRun
        when(outboxRepository.claimEventAtomic(eq(eventPk), any(), any(), any(), any(), any(), eq(worker1), eq(token1FirstRun), any()))
                .thenReturn(1);

        // Reclaim after timeout acquires token1SecondRun
        when(outboxRepository.claimEventAtomic(eq(eventPk), any(), any(), any(), any(), any(), eq(worker1), eq(token1SecondRun), any()))
                .thenReturn(1);

        // First run's completion is rejected because token in DB was overwritten by token1SecondRun
        when(outboxRepository.completeEventGuarded(eq(eventPk), eq(OutboxStatus.PROCESSED), eq(worker1), eq(token1FirstRun), any()))
                .thenReturn(0);

        // Second run's completion succeeds
        when(outboxRepository.completeEventGuarded(eq(eventPk), eq(OutboxStatus.PROCESSED), eq(worker1), eq(token1SecondRun), any()))
                .thenReturn(1);

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(), objectMapper);

        // Execution with old token is rejected!
        boolean resultOld = dispatcher.claimAndDispatch(outbox, worker1, token1FirstRun);
        assertThat(resultOld).isFalse();

        // Execution with active token succeeds!
        boolean resultNew = dispatcher.claimAndDispatch(outbox, worker1, token1SecondRun);
        assertThat(resultNew).isTrue();
    }

    @Test
    @DisplayName("Atomic recovery safely resets stale locks without overwriting newer claims")
    void testAtomicRecoveryProtectsNewerClaims() {
        when(outboxRepository.recoverStaleLockedEventsAtomic(eq(OutboxStatus.PENDING), eq(OutboxStatus.PROCESSING), any(Instant.class)))
                .thenReturn(3);

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(), objectMapper);
        dispatcher.recoverStaleLocks();

        verify(outboxRepository, times(1)).recoverStaleLockedEventsAtomic(
                eq(OutboxStatus.PENDING), eq(OutboxStatus.PROCESSING), any(Instant.class)
        );
    }

    @Test
    @DisplayName("Poison events transition to DEAD_LETTER when attemptCount exceeds maxAttempts")
    void testPoisonEventDeadLetterEscalation() throws Exception {
        UUID eventPk = UUID.randomUUID();
        UUID domainEventId = UUID.randomUUID();

        BaseDomainEvent domainEvent = BaseDomainEvent.builder()
                .eventId(domainEventId)
                .eventType(EventType.SECRET_UPDATED)
                .workspaceId(workspaceId)
                .build();
        String payloadJson = objectMapper.writeValueAsString(domainEvent);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(eventPk);
        outbox.setEventId(domainEventId);
        outbox.setWorkspaceId(workspaceId);
        outbox.setStatus(OutboxStatus.FAILED);
        outbox.setAttemptCount(4); // 4 + 1 = 5 (reaches maxAttempts 5)
        outbox.setMaxAttempts(5);
        outbox.setPayload(payloadJson);

        DomainEventHandler failingHandler = mock(DomainEventHandler.class);
        when(failingHandler.supports(any())).thenReturn(true);
        when(failingHandler.getConsumerName()).thenReturn("FailingConsumer");
        doThrow(new RuntimeException("Simulated persistent downstream outage")).when(failingHandler).handle(any());

        when(outboxRepository.claimEventAtomic(eq(eventPk), any(), any(), any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(1);

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(failingHandler), objectMapper);

        boolean dispatched = dispatcher.claimAndDispatch(outbox);

        assertThat(dispatched).isFalse();
        // Verifies event is marked DEAD_LETTER with sanitized error message
        verify(outboxRepository, times(1)).failEventGuarded(
                eq(eventPk),
                eq(OutboxStatus.DEAD_LETTER),
                any(Instant.class),
                contains("Simulated persistent downstream outage"),
                anyString(),
                anyString()
        );
    }

    @Test
    @DisplayName("Idempotent consumer processing: Same event dispatched twice is processed only once")
    void testConsumerIdempotencyInbox() throws Exception {
        UUID domainEventId = UUID.randomUUID();
        BaseDomainEvent domainEvent = BaseDomainEvent.builder()
                .eventId(domainEventId)
                .eventType(EventType.SECRET_CREATED)
                .workspaceId(workspaceId)
                .build();
        String payloadJson = objectMapper.writeValueAsString(domainEvent);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(UUID.randomUUID());
        outbox.setEventId(domainEventId);
        outbox.setWorkspaceId(workspaceId);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setPayload(payloadJson);

        DomainEventHandler handler = mock(DomainEventHandler.class);
        when(handler.supports(any())).thenReturn(true);
        when(handler.getConsumerName()).thenReturn("NotificationConsumer");

        // First execution: not yet processed
        when(processingLogRepository.existsByEventIdAndConsumerName(domainEventId, "NotificationConsumer"))
                .thenReturn(false)
                .thenReturn(true); // Second execution: already in processing inbox

        when(outboxRepository.claimEventAtomic(any(), any(), any(), any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(outboxRepository.completeEventGuarded(any(), any(), anyString(), anyString(), any()))
                .thenReturn(1);

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(handler), objectMapper);

        // Run 1: processes successfully
        boolean run1 = dispatcher.claimAndDispatch(outbox);
        assertThat(run1).isTrue();
        verify(handler, times(1)).handle(any());

        // Run 2: idempotent deduplication triggers, handler is NOT called again
        boolean run2 = dispatcher.claimAndDispatch(outbox);
        assertThat(run2).isTrue();
        verify(handler, times(1)).handle(any()); // Still only 1 invocation!
    }

    @Test
    @DisplayName("Operator dead-letter recovery: retryDeadLetterEvent resets to PENDING with 0 attempts")
    void testDeadLetterRetryRecovery() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent deadEvent = new OutboxEvent();
        deadEvent.setId(UUID.randomUUID());
        deadEvent.setEventId(eventId);
        deadEvent.setWorkspaceId(workspaceId);
        deadEvent.setStatus(OutboxStatus.DEAD_LETTER);
        deadEvent.setAttemptCount(5);
        deadEvent.setMaxAttempts(5);
        deadEvent.setLastError("Downstream timeout");
        deadEvent.setLockedBy("worker-stale");
        deadEvent.setLockedAt(Instant.now().minusSeconds(300));

        when(outboxRepository.findByWorkspaceIdAndEventId(workspaceId, eventId))
                .thenReturn(Optional.of(deadEvent));

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(), objectMapper);

        boolean retried = dispatcher.retryDeadLetterEvent(workspaceId, eventId);

        assertThat(retried).isTrue();
        assertThat(deadEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(deadEvent.getAttemptCount()).isEqualTo(0);
        assertThat(deadEvent.getLastError()).isNull();
        assertThat(deadEvent.getLockedBy()).isNull();
        assertThat(deadEvent.getLockedAt()).isNull();
        assertThat(deadEvent.getAvailableAt()).isNotNull();

        verify(outboxRepository, times(1)).save(deadEvent);
    }

    @Test
    @DisplayName("Outbox backlog stats accurately report queue depth and oldest pending age")
    void testOutboxBacklogStats() {
        when(outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.PENDING)).thenReturn(12L);
        when(outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.PROCESSING)).thenReturn(3L);
        when(outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.FAILED)).thenReturn(2L);
        when(outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.DEAD_LETTER)).thenReturn(1L);
        when(outboxRepository.countByWorkspaceIdAndStatus(workspaceId, OutboxStatus.PROCESSED)).thenReturn(145L);

        OutboxEvent oldest = new OutboxEvent();
        oldest.setCreatedAt(Instant.now().minusSeconds(42));
        when(outboxRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(eq(OutboxStatus.PENDING), any(), any()))
                .thenReturn(List.of(oldest));

        dispatcher = new EventDispatcher(outboxRepository, processingLogRepository, List.of(), objectMapper);

        OutboxStatsDto stats = dispatcher.getOutboxStats(workspaceId);

        assertThat(stats.pendingCount()).isEqualTo(12L);
        assertThat(stats.processingCount()).isEqualTo(3L);
        assertThat(stats.failedCount()).isEqualTo(2L);
        assertThat(stats.deadLetterCount()).isEqualTo(1L);
        assertThat(stats.processedCount()).isEqualTo(145L);
        assertThat(stats.oldestPendingAgeSeconds()).isNotNull().isGreaterThanOrEqualTo(40L);
    }

    @Test
    @DisplayName("DomainEventEntity @PreUpdate prevents any modification to persisted event facts")
    void testDomainEventEntityImmutabilityBarrier() {
        DomainEventEntity entity = new DomainEventEntity();
        entity.setEventId(UUID.randomUUID());
        entity.setEventType("SECRET_CREATED");
        entity.setWorkspaceId(workspaceId);

        assertThatThrownBy(entity::onUpdate)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("strictly immutable");
    }
}

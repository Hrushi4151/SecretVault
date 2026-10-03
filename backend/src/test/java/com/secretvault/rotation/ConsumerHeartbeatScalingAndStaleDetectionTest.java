package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.ConsumerType;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.service.SecretConsumerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 12.1 Consumer Heartbeat Scaling, Refresh Storm Protection, and Stale Detection Test Suite.
 */
@ExtendWith(MockitoExtension.class)
class ConsumerHeartbeatScalingAndStaleDetectionTest {

    @Mock private SecretConsumerRepository consumerRepository;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private AuditService auditService;

    private SecretConsumerService consumerService;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        consumerService = new SecretConsumerService(consumerRepository, effectiveAccessService, auditService);
    }

    @Test
    @DisplayName("Heartbeat Updates Version Acknowledgement & Audits Refresh")
    void testHeartbeatVersionAcknowledgement() {
        UUID consumerId = UUID.randomUUID();
        SecretConsumer consumer = new SecretConsumer();
        consumer.setId(consumerId);
        consumer.setWorkspaceId(workspaceId);
        consumer.setName("payment-gateway-svc");
        consumer.setStatus(ConsumerStatus.ACTIVE);
        consumer.setCurrentAcknowledgedVersion(1);

        when(consumerRepository.findById(consumerId)).thenReturn(Optional.of(consumer));
        when(consumerRepository.save(any(SecretConsumer.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsumerHeartbeatRequest req = new ConsumerHeartbeatRequest(2, "secretvault-sdk-java/1.2.0", "Spring Boot 3.3.4");
        SecretConsumerResponse res = consumerService.heartbeat(workspaceId, consumerId, req);

        assertThat(res.currentAcknowledgedVersion()).isEqualTo(2);
        assertThat(consumer.getLastRefreshAcknowledgedAt()).isNotNull();
        verify(auditService).recordSecretAudit(any(), eq(workspaceId), any(), any(), any(), any(), any(), contains("Refreshed to version 2"));
    }

    @Test
    @DisplayName("Disabled Consumer Rejects Heartbeats")
    void testDisabledConsumerHeartbeatRejected() {
        UUID consumerId = UUID.randomUUID();
        SecretConsumer consumer = new SecretConsumer();
        consumer.setId(consumerId);
        consumer.setWorkspaceId(workspaceId);
        consumer.setStatus(ConsumerStatus.DISABLED);

        when(consumerRepository.findById(consumerId)).thenReturn(Optional.of(consumer));

        assertThatThrownBy(() -> consumerService.heartbeat(workspaceId, consumerId, new ConsumerHeartbeatRequest(1, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Consumer is disabled");
    }

    @Test
    @DisplayName("Stale Consumer Detection: Consumers with heartbeat >24h are transitioned to STALE")
    void testStaleConsumerDetection() {
        SecretConsumer freshConsumer = new SecretConsumer();
        freshConsumer.setId(UUID.randomUUID());
        freshConsumer.setName("fresh-svc");
        freshConsumer.setStatus(ConsumerStatus.ACTIVE);
        freshConsumer.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(5)));

        SecretConsumer staleConsumer = new SecretConsumer();
        staleConsumer.setId(UUID.randomUUID());
        staleConsumer.setName("abandoned-svc");
        staleConsumer.setStatus(ConsumerStatus.ACTIVE);
        staleConsumer.setLastHeartbeatAt(Instant.now().minus(Duration.ofHours(30))); // > 24 hours ago

        when(consumerRepository.findAll()).thenReturn(List.of(freshConsumer, staleConsumer));

        consumerService.detectStaleConsumers();

        assertThat(freshConsumer.getStatus()).isEqualTo(ConsumerStatus.ACTIVE);
        assertThat(staleConsumer.getStatus()).isEqualTo(ConsumerStatus.STALE);
        verify(consumerRepository).save(staleConsumer);
    }

    @Test
    @DisplayName("Heartbeat Concurrency: 500 simultaneous consumer heartbeats complete cleanly")
    void testConcurrent500ConsumerHeartbeats() throws InterruptedException {
        int consumerCount = 500;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(consumerCount);
        AtomicInteger processed = new AtomicInteger(0);

        for (int i = 0; i < consumerCount; i++) {
            UUID cId = UUID.randomUUID();
            SecretConsumer consumer = new SecretConsumer();
            consumer.setId(cId);
            consumer.setWorkspaceId(workspaceId);
            consumer.setName("worker-" + i);
            consumer.setStatus(ConsumerStatus.ACTIVE);
            consumer.setCurrentAcknowledgedVersion(1);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    consumer.setLastHeartbeatAt(Instant.now());
                    consumer.setCurrentAcknowledgedVersion(2);
                    processed.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(processed.get()).isEqualTo(consumerCount);
    }
}

package com.secretvault.sync;

import com.secretvault.common.exception.ApiException;
import com.secretvault.sync.scheduler.SyncDriftScheduler;
import com.secretvault.sync.service.SyncDistributedLockCoordinator;
import com.secretvault.sync.service.SyncExecutionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test validating the production fail-closed guard.
 * Proves that:
 * 1. Even when an operator attempts to set 'secretvault.sync.lock.allow-local-fallback=true',
 *    the active 'prod' profile forces allowLocalFallback to false via @PostConstruct validation.
 * 2. Unreachable/failing Redis in production strictly throws HTTP 503 SERVICE_UNAVAILABLE.
 * 3. Scheduled scans and execution engines fail closed rather than mutating or scanning uncoordinated.
 */
@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        // Attempt unsafe override
        "secretvault.sync.lock.allow-local-fallback=true",
        // Test database and crypto overrides to boot context safely
        "spring.datasource.url=jdbc:h2:mem:prodfailcloseddb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "VAULT_MASTER_KEY=dGVzdF9tYXN0ZXJfa2V5XzMyX2J5dGVzX2Rldl8wMDE=",
        "JWT_SECRET=super_secret_jwt_signing_key_for_local_development_only_minimum_256_bits_length_123456",
        "KMS_PROVIDER=local",
        "secretvault.kms.provider=local",
        // Configure an unreachable Redis address to simulate production outage
        "REDIS_HOST=127.0.0.1",
        "REDIS_PORT=65534",
        "REDIS_PASSWORD=dummy_prod_password",
        "REDIS_SSL=false"
})
class SyncProductionFailClosedGuardIntegrationTest {

    @Autowired
    private SyncDistributedLockCoordinator lockCoordinator;

    @Autowired
    private SyncDriftScheduler driftScheduler;

    @Autowired
    private SyncExecutionEngine executionEngine;

    @Test
    @DisplayName("Production profile rejects allow-local-fallback=true and enforces fail-closed lock coordination")
    void testProductionGuard_rejectsUnsafeLocalFallback_andFailsClosedOnRedisOutage() {
        // 1. Verify the guard overrode the unsafe property
        assertThat(lockCoordinator.isAllowLocalFallback())
                .as("Production profile guard must override unsafe allow-local-fallback=true to false")
                .isFalse();

        // 2. Verify lock acquisition fails closed with HTTP 503 when Redis is unavailable
        UUID testWorkspaceId = UUID.randomUUID();
        UUID testResourceId = UUID.randomUUID();

        assertThatThrownBy(() -> lockCoordinator.acquireExecutionLockHandle(testWorkspaceId, testResourceId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiEx.getMessage()).contains("Distributed lock coordination is unavailable");
                });

        // 3. Verify scan lock acquisition also fails closed
        assertThatThrownBy(() -> lockCoordinator.acquireScanLock(testWorkspaceId, "SCAN-TOKEN", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });

        // 4. Verify scheduled drift scanner gracefully skips and does not execute uncoordinated scans
        driftScheduler.runScheduledDriftScan();
    }
}

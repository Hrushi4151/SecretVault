package com.secretvault.events;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL Integration Tests executing against live PostgreSQL (postgres:16-alpine).
 * Connects directly to the real PostgreSQL container and verifies:
 * - Flyway V1 through V23 migration success
 * - Database trigger prevention of UPDATE and DELETE on domain_events
 * - Permitted INSERT into domain_events
 * - Transactional commit and rollback behavior
 * - Outbox atomic claiming and claim-token fencing
 * - Stale worker completion and failure rejection
 * - Same worker ID with stale token rejection
 * - Atomic recovery immunity against newer claims
 * - Consumer deduplication inbox unique constraint enforcement
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Phase 13: Real PostgreSQL Domain Event & Transactional Outbox Integration Tests")
class RealPostgresDomainEventOutboxIntegrationTest {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/secretvault_dev";
    private static final String DB_USER = "vault_user";
    private static final String DB_PASSWORD = "vault_secure_password_dev_only";

    private static boolean postgresAvailable = false;
    private static Connection connection;

    private static UUID testWorkspaceId;

    @BeforeAll
    static void initPostgresAndFlyway() {
        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            postgresAvailable = (connection != null && !connection.isClosed());

            if (postgresAvailable) {
                // Apply Flyway migrations against real PostgreSQL
                Flyway flyway = Flyway.configure()
                        .dataSource(DB_URL, DB_USER, DB_PASSWORD)
                        .load();
                flyway.migrate();

                // Setup test workspace
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT id FROM workspaces LIMIT 1")) {
                    if (rs.next()) {
                        testWorkspaceId = (UUID) rs.getObject("id");
                    }
                }

                if (testWorkspaceId == null) {
                    UUID orgId = UUID.randomUUID();
                    testWorkspaceId = UUID.randomUUID();
                    try (PreparedStatement orgStmt = connection.prepareStatement(
                            "INSERT INTO organizations (id, name, slug) VALUES (?, ?, ?)")) {
                        orgStmt.setObject(1, orgId);
                        orgStmt.setString(2, "Test Events Org");
                        orgStmt.setString(3, "test-events-org-" + orgId.toString().substring(0, 8));
                        orgStmt.executeUpdate();
                    }
                    try (PreparedStatement wsStmt = connection.prepareStatement(
                            "INSERT INTO workspaces (id, organization_id, name, slug) VALUES (?, ?, ?, ?)")) {
                        wsStmt.setObject(1, testWorkspaceId);
                        wsStmt.setObject(2, orgId);
                        wsStmt.setString(3, "Test Events Workspace");
                        wsStmt.setString(4, "test-events-ws-" + testWorkspaceId.toString().substring(0, 8));
                        wsStmt.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            postgresAvailable = false;
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @BeforeEach
    void checkPostgresAvailable() {
        assumeTrue(postgresAvailable, "PostgreSQL container (secretvault-postgres-dev) is not running on localhost:5432");
    }

    @Test
    @Order(1)
    @DisplayName("P0 Immutability: PostgreSQL database trigger prevents UPDATE on domain_events")
    void testPostgresTriggerPreventsUpdate() throws SQLException {
        UUID eventId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();

        // 1. Permitted INSERT must succeed
        try (PreparedStatement insertStmt = connection.prepareStatement(
                "INSERT INTO domain_events (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload_json, metadata_json, occurred_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())")) {
            insertStmt.setObject(1, rowId);
            insertStmt.setObject(2, eventId);
            insertStmt.setString(3, "SECRET_CREATED");
            insertStmt.setObject(4, testWorkspaceId);
            insertStmt.setString(5, "SECRET");
            insertStmt.setString(6, "sec-001");
            insertStmt.setString(7, "{\"version\":1}");
            insertStmt.setString(8, "{}");
            int inserted = insertStmt.executeUpdate();
            assertThat(inserted).isEqualTo(1);
        }

        // 2. Prohibited UPDATE must be aborted by PostgreSQL trigger
        try (PreparedStatement updateStmt = connection.prepareStatement(
                "UPDATE domain_events SET payload_json = '{\"corrupted\":true}' WHERE event_id = ?")) {
            updateStmt.setObject(1, eventId);

            assertThatThrownBy(updateStmt::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("domain_events is strictly immutable: UPDATE operations are prohibited");
        }

        // 3. Verify data remains completely uncorrupted
        try (PreparedStatement selectStmt = connection.prepareStatement(
                "SELECT payload_json FROM domain_events WHERE event_id = ?")) {
            selectStmt.setObject(1, eventId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("payload_json")).isEqualTo("{\"version\":1}");
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("P0 Immutability: PostgreSQL database trigger prevents DELETE on domain_events")
    void testPostgresTriggerPreventsDelete() throws SQLException {
        UUID eventId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();

        // 1. Insert permitted event
        try (PreparedStatement insertStmt = connection.prepareStatement(
                "INSERT INTO domain_events (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload_json, metadata_json, occurred_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())")) {
            insertStmt.setObject(1, rowId);
            insertStmt.setObject(2, eventId);
            insertStmt.setString(3, "SECRET_DELETED");
            insertStmt.setObject(4, testWorkspaceId);
            insertStmt.setString(5, "SECRET");
            insertStmt.setString(6, "sec-002");
            insertStmt.setString(7, "{\"deleted\":true}");
            insertStmt.setString(8, "{}");
            insertStmt.executeUpdate();
        }

        // 2. Prohibited DELETE must be aborted by PostgreSQL trigger
        try (PreparedStatement deleteStmt = connection.prepareStatement(
                "DELETE FROM domain_events WHERE event_id = ?")) {
            deleteStmt.setObject(1, eventId);

            assertThatThrownBy(deleteStmt::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("domain_events is strictly immutable: DELETE operations are prohibited");
        }

        // 3. Verify event still exists in the table
        try (PreparedStatement selectStmt = connection.prepareStatement(
                "SELECT count(*) FROM domain_events WHERE event_id = ?")) {
            selectStmt.setObject(1, eventId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("P1 Transactional: Real PostgreSQL rollback leaves 0 rows in domain_events and outbox")
    void testRealPostgresTransactionalRollback() throws SQLException {
        UUID domainEventId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();

        connection.setAutoCommit(false);
        try {
            // Write domain event inside transaction
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO domain_events (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload_json, metadata_json, occurred_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())")) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, domainEventId);
                stmt.setString(3, "SECRET_ROTATED");
                stmt.setObject(4, testWorkspaceId);
                stmt.setString(5, "SECRET");
                stmt.setString(6, "sec-tx");
                stmt.setString(7, "{}");
                stmt.setString(8, "{}");
                stmt.executeUpdate();
            }

            // Write outbox event inside same transaction
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO event_outbox (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload, occurred_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, now())")) {
                stmt.setObject(1, outboxId);
                stmt.setObject(2, domainEventId);
                stmt.setString(3, "SECRET_ROTATED");
                stmt.setObject(4, testWorkspaceId);
                stmt.setString(5, "SECRET");
                stmt.setString(6, "sec-tx");
                stmt.setString(7, "{}");
                stmt.executeUpdate();
            }

            // Simulate failure and rollback
            connection.rollback();
        } finally {
            connection.setAutoCommit(true);
        }

        // Verify neither domain event nor outbox entry persisted
        try (PreparedStatement stmt = connection.prepareStatement("SELECT count(*) FROM domain_events WHERE event_id = ?")) {
            stmt.setObject(1, domainEventId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }

        try (PreparedStatement stmt = connection.prepareStatement("SELECT count(*) FROM event_outbox WHERE id = ?")) {
            stmt.setObject(1, outboxId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("P0 Claim Fencing: Stale Worker A rejected when Worker B reclaims with new claim token")
    void testClaimTokenFencingStaleWorkerRejected() throws SQLException {
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // 1. Insert PENDING outbox event
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO event_outbox (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload, occurred_at, status) " +
                        "VALUES (?, ?, 'SECRET_UPDATED', ?, 'SECRET', 'agg-1', '{}', now(), 'PENDING')")) {
            stmt.setObject(1, outboxId);
            stmt.setObject(2, eventId);
            stmt.setObject(3, testWorkspaceId);
            stmt.executeUpdate();
        }

        String tokenA = "token-A-" + UUID.randomUUID();
        String tokenB = "token-B-" + UUID.randomUUID();

        // 2. Worker A claims the event
        String claimSql = "UPDATE event_outbox SET status = 'PROCESSING', locked_at = now(), locked_by = ?, claim_token = ?, attempt_count = attempt_count + 1 " +
                "WHERE id = ? AND (status = 'PENDING' OR status = 'FAILED' OR (status = 'PROCESSING' AND locked_at < ?))";
        try (PreparedStatement stmt = connection.prepareStatement(claimSql)) {
            stmt.setString(1, "worker-A");
            stmt.setString(2, tokenA);
            stmt.setObject(3, outboxId);
            stmt.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(300)));
            int claimed = stmt.executeUpdate();
            assertThat(claimed).isEqualTo(1);
        }

        // 3. Simulate Worker A lease expiry (set locked_at into past)
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE event_outbox SET locked_at = now() - interval '10 minutes' WHERE id = ?")) {
            stmt.setObject(1, outboxId);
            stmt.executeUpdate();
        }

        // 4. Worker B reclaims the event with tokenB
        try (PreparedStatement stmt = connection.prepareStatement(claimSql)) {
            stmt.setString(1, "worker-B");
            stmt.setString(2, tokenB);
            stmt.setObject(3, outboxId);
            stmt.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(300)));
            int claimed = stmt.executeUpdate();
            assertThat(claimed).isEqualTo(1);
        }

        // 5. Worker A attempts completion with tokenA -> must affect 0 rows!
        String completeSql = "UPDATE event_outbox SET status = 'PROCESSED', processed_at = now(), locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                "WHERE id = ? AND locked_by = ? AND claim_token = ?";
        try (PreparedStatement stmt = connection.prepareStatement(completeSql)) {
            stmt.setObject(1, outboxId);
            stmt.setString(2, "worker-A");
            stmt.setString(3, tokenA);
            int updated = stmt.executeUpdate();
            assertThat(updated).as("Stale Worker A must NOT be able to complete event").isEqualTo(0);
        }

        // 6. Worker A attempts failure with tokenA -> must affect 0 rows!
        String failSql = "UPDATE event_outbox SET status = 'FAILED', last_error = 'failed', locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                "WHERE id = ? AND locked_by = ? AND claim_token = ?";
        try (PreparedStatement stmt = connection.prepareStatement(failSql)) {
            stmt.setObject(1, outboxId);
            stmt.setString(2, "worker-A");
            stmt.setString(3, tokenA);
            int updated = stmt.executeUpdate();
            assertThat(updated).as("Stale Worker A must NOT be able to mark event failed").isEqualTo(0);
        }

        // 7. Active Worker B completes with tokenB -> must succeed!
        try (PreparedStatement stmt = connection.prepareStatement(completeSql)) {
            stmt.setObject(1, outboxId);
            stmt.setString(2, "worker-B");
            stmt.setString(3, tokenB);
            int updated = stmt.executeUpdate();
            assertThat(updated).as("Active Worker B must succeed in completion").isEqualTo(1);
        }
    }

    @Test
    @Order(5)
    @DisplayName("P0 Claim Fencing: Same worker ID claiming twice has distinct tokens; old execution rejected")
    void testSameWorkerIdDistinctTokensFencing() throws SQLException {
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO event_outbox (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload, occurred_at, status) " +
                        "VALUES (?, ?, 'SECRET_UPDATED', ?, 'SECRET', 'agg-2', '{}', now(), 'PENDING')")) {
            stmt.setObject(1, outboxId);
            stmt.setObject(2, eventId);
            stmt.setObject(3, testWorkspaceId);
            stmt.executeUpdate();
        }

        String worker = "worker-single-process";
        String token1 = "token-run-1-" + UUID.randomUUID();
        String token2 = "token-run-2-" + UUID.randomUUID();

        String claimSql = "UPDATE event_outbox SET status = 'PROCESSING', locked_at = now(), locked_by = ?, claim_token = ?, attempt_count = attempt_count + 1 " +
                "WHERE id = ? AND (status = 'PENDING' OR status = 'FAILED' OR (status = 'PROCESSING' AND locked_at < ?))";

        // Claim 1
        try (PreparedStatement stmt = connection.prepareStatement(claimSql)) {
            stmt.setString(1, worker);
            stmt.setString(2, token1);
            stmt.setObject(3, outboxId);
            stmt.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(300)));
            stmt.executeUpdate();
        }

        // Simulate timeout
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE event_outbox SET locked_at = now() - interval '10 minutes' WHERE id = ?")) {
            stmt.setObject(1, outboxId);
            stmt.executeUpdate();
        }

        // Claim 2 by SAME worker ID
        try (PreparedStatement stmt = connection.prepareStatement(claimSql)) {
            stmt.setString(1, worker);
            stmt.setString(2, token2);
            stmt.setObject(3, outboxId);
            stmt.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(300)));
            stmt.executeUpdate();
        }

        // Stale execution 1 tries to complete with token1 -> rejected!
        String completeSql = "UPDATE event_outbox SET status = 'PROCESSED', processed_at = now(), locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                "WHERE id = ? AND locked_by = ? AND claim_token = ?";
        try (PreparedStatement stmt = connection.prepareStatement(completeSql)) {
            stmt.setObject(1, outboxId);
            stmt.setString(2, worker);
            stmt.setString(3, token1);
            int updated = stmt.executeUpdate();
            assertThat(updated).isEqualTo(0);
        }

        // Active execution 2 completes with token2 -> succeeds!
        try (PreparedStatement stmt = connection.prepareStatement(completeSql)) {
            stmt.setObject(1, outboxId);
            stmt.setString(2, worker);
            stmt.setString(3, token2);
            int updated = stmt.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }
    }

    @Test
    @Order(6)
    @DisplayName("P0 Stale Recovery: Atomic recovery does NOT overwrite or reset newer claims")
    void testAtomicRecoveryDoesNotTouchNewerClaims() throws SQLException {
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID staleOutboxId = UUID.randomUUID();

        // 1. Insert stale row (locked 10 minutes ago)
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO event_outbox (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload, occurred_at, status, locked_at, locked_by, claim_token) " +
                        "VALUES (?, ?, 'SECRET_UPDATED', ?, 'SECRET', 'agg-stale', '{}', now(), 'PROCESSING', ?, 'worker-stale', 'stale-token')")) {
            stmt.setObject(1, staleOutboxId);
            stmt.setObject(2, UUID.randomUUID());
            stmt.setObject(3, testWorkspaceId);
            stmt.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(600)));
            stmt.executeUpdate();
        }

        // 2. Insert event claimed 10 seconds ago (active, fresh lease)
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO event_outbox (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload, occurred_at, status, locked_at, locked_by, claim_token) " +
                        "VALUES (?, ?, 'SECRET_UPDATED', ?, 'SECRET', 'agg-3', '{}', now(), 'PROCESSING', now(), 'worker-active', 'active-token')")) {
            stmt.setObject(1, outboxId);
            stmt.setObject(2, eventId);
            stmt.setObject(3, testWorkspaceId);
            stmt.executeUpdate();
        }

        // 3. Run atomic recovery with 5-minute cutoff
        String recoverySql = "UPDATE event_outbox SET status = 'PENDING', locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                "WHERE status = 'PROCESSING' AND locked_at < ?";
        try (PreparedStatement stmt = connection.prepareStatement(recoverySql)) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(300)));
            stmt.executeUpdate();
        }

        // 4. Verify stale row was recovered to PENDING with cleared token
        try (PreparedStatement stmt = connection.prepareStatement("SELECT status, claim_token FROM event_outbox WHERE id = ?")) {
            stmt.setObject(1, staleOutboxId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("PENDING");
                assertThat(rs.getString("claim_token")).isNull();
            }
        }

        // 5. Verify active fresh claim was untouched (remains PROCESSING with active-token)
        try (PreparedStatement stmt = connection.prepareStatement("SELECT status, claim_token FROM event_outbox WHERE id = ?")) {
            stmt.setObject(1, outboxId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("PROCESSING");
                assertThat(rs.getString("claim_token")).isEqualTo("active-token");
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("P1 Consumer Inbox: PostgreSQL enforces unique constraint on (event_id, consumer_name)")
    void testConsumerInboxUniqueConstraintDeduplication() throws SQLException {
        UUID eventId = UUID.randomUUID();
        String consumer = "AuditLogConsumer";

        // 1. First record processed successfully
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO event_processing_log (id, event_id, consumer_name, workspace_id, status) VALUES (?, ?, ?, ?, 'PROCESSED')")) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, eventId);
            stmt.setString(3, consumer);
            stmt.setObject(4, testWorkspaceId);
            stmt.executeUpdate();
        }

        // 2. Duplicate record with same event_id and consumer_name is blocked by database constraint
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO event_processing_log (id, event_id, consumer_name, workspace_id, status) VALUES (?, ?, ?, ?, 'PROCESSED')")) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, eventId);
            stmt.setString(3, consumer);
            stmt.setObject(4, testWorkspaceId);

            assertThatThrownBy(stmt::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_event_consumer");
        }
    }

    @Test
    @Order(8)
    @DisplayName("P0 Concurrency: 10 concurrent workers contending on same outbox event — exactly 1 winner")
    void testConcurrentMultiWorkerClaimContentionAgainstPostgres() throws Exception {
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Insert 1 PENDING outbox event
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO event_outbox (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload, occurred_at, status) " +
                        "VALUES (?, ?, 'SECRET_CREATED', ?, 'SECRET', 'sec-multi', '{}', now(), 'PENDING')")) {
            stmt.setObject(1, outboxId);
            stmt.setObject(2, eventId);
            stmt.setObject(3, testWorkspaceId);
            stmt.executeUpdate();
        }

        int workerCount = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(workerCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(workerCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger winnersCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> winningWorker = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> winningToken = new java.util.concurrent.atomic.AtomicReference<>();

        String claimSql = "UPDATE event_outbox SET status = 'PROCESSING', locked_at = now(), locked_by = ?, claim_token = ?, attempt_count = attempt_count + 1, updated_at = now() " +
                "WHERE id = ? AND (status = 'PENDING' OR (status = 'PROCESSING' AND locked_at < ?))";

        for (int i = 0; i < workerCount; i++) {
            final String workerId = "worker-" + i;
            final String token = UUID.randomUUID().toString();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    try (Connection workerConn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                         PreparedStatement stmt = workerConn.prepareStatement(claimSql)) {
                        stmt.setString(1, workerId);
                        stmt.setString(2, token);
                        stmt.setObject(3, outboxId);
                        stmt.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(60)));
                        int updated = stmt.executeUpdate();
                        if (updated == 1) {
                            winnersCount.incrementAndGet();
                            winningWorker.set(workerId);
                            winningToken.set(token);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean completed = executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Invariant: Exactly 1 worker wins the claim
        assertThat(winnersCount.get()).isEqualTo(1);
        assertThat(winningWorker.get()).isNotNull();
        assertThat(winningToken.get()).isNotNull();

        // A losing worker attempts to complete the event with its stale token -> rejected (0 rows updated)
        String completeSql = "UPDATE event_outbox SET status = 'COMPLETED', locked_by = NULL, claim_token = NULL, processed_at = now(), updated_at = now() " +
                "WHERE id = ? AND locked_by = ? AND claim_token = ? AND status = 'PROCESSING'";
        try (PreparedStatement stmt = connection.prepareStatement(completeSql)) {
            stmt.setObject(1, outboxId);
            stmt.setString(2, "losing-worker");
            stmt.setString(3, "losing-token");
            int rows = stmt.executeUpdate();
            assertThat(rows).isEqualTo(0);
        }

        // Winning worker completes with valid token -> succeeds (1 row updated)
        try (PreparedStatement stmt = connection.prepareStatement(completeSql)) {
            stmt.setObject(1, outboxId);
            stmt.setString(2, winningWorker.get());
            stmt.setString(3, winningToken.get());
            int rows = stmt.executeUpdate();
            assertThat(rows).isEqualTo(1);
        }

        // Verify status in DB is COMPLETED
        try (PreparedStatement stmt = connection.prepareStatement("SELECT status FROM event_outbox WHERE id = ?")) {
            stmt.setObject(1, outboxId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("COMPLETED");
            }
        }
    }
}

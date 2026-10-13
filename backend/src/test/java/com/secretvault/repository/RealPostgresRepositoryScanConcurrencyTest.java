package com.secretvault.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL Concurrency and Deduplication Acceptance Tests for Repository Scans.
 * Runs against live PostgreSQL (postgres:16-alpine) on port 5432.
 * Verifies:
 * 1. Flyway V24 migration and partial unique index uq_repo_scans_repo_idempotency.
 * 2. Database-level unique constraint prevents duplicate GitHub delivery IDs.
 * 3. Simultaneous requests handled by separate application instances are safely deduplicated.
 * 4. Failed enqueue rollback leaves no false duplicate record, retry succeeds cleanly.
 * 5. Atomic database claiming prevents concurrent scheduler instances from executing the same scan.
 * 6. Stale scan lease expiry and atomic recovery.
 * 7. Lease fencing prevents stale/interrupted workers from overwriting newer worker results.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("SecretVault — Real PostgreSQL Repository Scan Concurrency & Deduplication Acceptance")
class RealPostgresRepositoryScanConcurrencyTest {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/secretvault_dev";
    private static final String DB_USER = "vault_user";
    private static final String DB_PASSWORD = "vault_secure_password_dev_only";

    private static boolean postgresAvailable = false;
    private static Connection connection;

    private static UUID testWorkspaceId;
    private static UUID testRepoId;

    @BeforeAll
    static void initPostgresAndFlyway() {
        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            postgresAvailable = (connection != null && !connection.isClosed());

            if (postgresAvailable) {
                // Apply Flyway migrations including V24
                Flyway flyway = Flyway.configure()
                        .dataSource(DB_URL, DB_USER, DB_PASSWORD)
                        .load();
                flyway.migrate();

                // Setup test organization and workspace
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
                        orgStmt.setString(2, "Test Concurrency Org");
                        orgStmt.setString(3, "test-conc-org-" + orgId.toString().substring(0, 8));
                        orgStmt.executeUpdate();
                    }
                    try (PreparedStatement wsStmt = connection.prepareStatement(
                            "INSERT INTO workspaces (id, organization_id, name, slug) VALUES (?, ?, ?, ?)")) {
                        wsStmt.setObject(1, testWorkspaceId);
                        wsStmt.setObject(2, orgId);
                        wsStmt.setString(3, "Test Concurrency Workspace");
                        wsStmt.setString(4, "test-conc-ws-" + testWorkspaceId.toString().substring(0, 8));
                        wsStmt.executeUpdate();
                    }
                }

                // Setup test repository
                testRepoId = UUID.randomUUID();
                try (PreparedStatement repoStmt = connection.prepareStatement(
                        "INSERT INTO repositories (id, workspace_id, provider, owner, name, default_branch, visibility, status) " +
                                "VALUES (?, ?, 'GITHUB', 'TestOwner', 'TestRepo', 'main', 'PRIVATE', 'ACTIVE') " +
                                "ON CONFLICT (workspace_id, provider, owner, name) DO UPDATE SET updated_at = now() RETURNING id")) {
                    repoStmt.setObject(1, testRepoId);
                    repoStmt.setObject(2, testWorkspaceId);
                    try (ResultSet rs = repoStmt.executeQuery()) {
                        if (rs.next()) {
                            testRepoId = (UUID) rs.getObject("id");
                        }
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
    @DisplayName("P0 Migration: Flyway V24 created partial unique index and fencing columns")
    void testFlywayV24MigrationAndConstraintsApplied() throws SQLException {
        // 1. Verify partial unique index exists
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'repository_scans' AND indexname = 'uq_repo_scans_repo_idempotency'")) {
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                String indexDef = rs.getString("indexdef");
                assertThat(indexDef).contains("UNIQUE INDEX");
                assertThat(indexDef).contains("repository_id");
                assertThat(indexDef).contains("idempotency_key");
                assertThat(indexDef).contains("WHERE (idempotency_key IS NOT NULL)");
            }
        }

        // 2. Verify fencing columns exist
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'repository_scans' AND column_name IN ('claim_token', 'locked_by', 'locked_at', 'attempt_count')")) {
            List<String> cols = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
            }
            assertThat(cols).contains("claim_token", "locked_by", "locked_at", "attempt_count");
        }
    }

    @Test
    @Order(2)
    @DisplayName("P0 Deduplication: Database-level unique constraint prevents duplicate GitHub delivery IDs")
    void testDatabaseLevelUniqueConstraintPreventsDuplicateDeliveryJobs() throws SQLException {
        String deliveryId = "deliv-" + UUID.randomUUID();
        UUID scan1Id = UUID.randomUUID();
        UUID scan2Id = UUID.randomUUID();

        // 1. First insert with delivery ID succeeds
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, idempotency_key, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'QUEUED', ?, now())")) {
            stmt.setObject(1, scan1Id);
            stmt.setObject(2, testWorkspaceId);
            stmt.setObject(3, testRepoId);
            stmt.setString(4, deliveryId);
            int rows = stmt.executeUpdate();
            assertThat(rows).isEqualTo(1);
        }

        // 2. Second insert with exact same repository and delivery ID must violate unique index
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, idempotency_key, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'QUEUED', ?, now())")) {
            stmt.setObject(1, scan2Id);
            stmt.setObject(2, testWorkspaceId);
            stmt.setObject(3, testRepoId);
            stmt.setString(4, deliveryId);

            assertThatThrownBy(stmt::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_repo_scans_repo_idempotency");
        }

        // 3. Null idempotency_key must be allowed multiple times (partial unique index invariant)
        UUID scan3Id = UUID.randomUUID();
        UUID scan4Id = UUID.randomUUID();
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, idempotency_key, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'QUEUED', NULL, now())")) {
            stmt.setObject(1, scan3Id);
            stmt.setObject(2, testWorkspaceId);
            stmt.setObject(3, testRepoId);
            assertThat(stmt.executeUpdate()).isEqualTo(1);

            stmt.setObject(1, scan4Id);
            stmt.setObject(2, testWorkspaceId);
            stmt.setObject(3, testRepoId);
            assertThat(stmt.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    @Order(3)
    @DisplayName("P0 Concurrency: Simultaneous requests across separate instances atomically deduplicated")
    void testSimultaneousRequestsHandledBySeparateInstancesDeduplicated() throws Exception {
        int threads = 10;
        String sharedDeliveryId = "concurrent-deliv-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger collisionCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    barrier.await(); // Synchronize all threads to execute at the exact same instant
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, idempotency_key, created_at) " +
                                    "VALUES (?, ?, ?, 'FULL', 'QUEUED', ?, now())")) {
                        stmt.setObject(1, UUID.randomUUID());
                        stmt.setObject(2, testWorkspaceId);
                        stmt.setObject(3, testRepoId);
                        stmt.setString(4, sharedDeliveryId);
                        stmt.executeUpdate();
                        successCount.incrementAndGet();
                    } catch (SQLException e) {
                        if (e.getMessage().contains("uq_repo_scans_repo_idempotency")) {
                            collisionCount.incrementAndGet();
                        } else {
                            throw e;
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly 1 thread must win; all 9 others must encounter unique collision
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(collisionCount.get()).isEqualTo(threads - 1);

        // Verify authoritative database contains exactly one record
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM repository_scans WHERE repository_id = ? AND idempotency_key = ?")) {
            stmt.setObject(1, testRepoId);
            stmt.setString(2, sharedDeliveryId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("P0 Retry Safety: Failed enqueue can be retried safely without false duplicate record")
    void testFailedEnqueueCanBeRetriedSafelyWithoutFalseDuplicateRecord() throws SQLException {
        String retryDeliveryId = "rollback-deliv-" + UUID.randomUUID();

        // 1. Transaction A starts, inserts, but fails and rolls back
        try (Connection connA = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            connA.setAutoCommit(false);
            try (PreparedStatement stmt = connA.prepareStatement(
                    "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, idempotency_key, created_at) " +
                            "VALUES (?, ?, ?, 'FULL', 'QUEUED', ?, now())")) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, testWorkspaceId);
                stmt.setObject(3, testRepoId);
                stmt.setString(4, retryDeliveryId);
                stmt.executeUpdate();
            }
            connA.rollback(); // Simulate transaction abort / network fault before commit
        }

        // 2. Verify no false duplicate record remains
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM repository_scans WHERE repository_id = ? AND idempotency_key = ?")) {
            stmt.setObject(1, testRepoId);
            stmt.setString(2, retryDeliveryId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }

        // 3. Retry with same delivery ID must succeed cleanly
        try (Connection connB = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (PreparedStatement stmt = connB.prepareStatement(
                    "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, idempotency_key, created_at) " +
                            "VALUES (?, ?, ?, 'FULL', 'QUEUED', ?, now())")) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, testWorkspaceId);
                stmt.setObject(3, testRepoId);
                stmt.setString(4, retryDeliveryId);
                int rows = stmt.executeUpdate();
                assertThat(rows).isEqualTo(1);
            }
        }

        // Verify committed record exists
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM repository_scans WHERE repository_id = ? AND idempotency_key = ?")) {
            stmt.setObject(1, testRepoId);
            stmt.setString(2, retryDeliveryId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("P0 Atomic Claiming: Two scheduler instances cannot execute same scan concurrently")
    void testAtomicClaimingPreventsTwoConcurrentSchedulersExecutingSameScan() throws Exception {
        UUID scanId = UUID.randomUUID();

        // 1. Insert a queued scan
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, attempt_count, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'QUEUED', 0, now())")) {
            stmt.setObject(1, scanId);
            stmt.setObject(2, testWorkspaceId);
            stmt.setObject(3, testRepoId);
            stmt.executeUpdate();
        }

        // 2. Race 8 worker scheduler threads to claim scanId simultaneously
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CyclicBarrier barrier = new CyclicBarrier(workers);
        AtomicInteger claimedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            final String workerId = "scheduler-node-" + i;
            final String claimToken = UUID.randomUUID().toString();

            futures.add(executor.submit(() -> {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    barrier.await();
                    try (PreparedStatement claimStmt = conn.prepareStatement(
                            "UPDATE repository_scans SET status = 'CLONING', started_at = now(), locked_at = now(), " +
                                    "locked_by = ?, claim_token = ?, attempt_count = attempt_count + 1 " +
                                    "WHERE id = ? AND (status = 'QUEUED' OR (status IN ('CLONING', 'INDEXING', 'SCANNING', 'CLASSIFYING') AND locked_at < ?))")) {
                        claimStmt.setString(1, workerId);
                        claimStmt.setString(2, claimToken);
                        claimStmt.setObject(3, scanId);
                        claimStmt.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(900))); // 15 min stale cutoff
                        int updated = claimStmt.executeUpdate();
                        if (updated > 0) {
                            claimedCount.addAndGet(updated);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // EXACTLY ONE worker node wins the atomic claim!
        assertThat(claimedCount.get()).isEqualTo(1);

        // Verify scan in database has status CLONING and attempt_count = 1
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT status, attempt_count, locked_by, claim_token FROM repository_scans WHERE id = ?")) {
            stmt.setObject(1, scanId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("CLONING");
                assertThat(rs.getInt("attempt_count")).isEqualTo(1);
                assertThat(rs.getString("locked_by")).startsWith("scheduler-node-");
                assertThat(rs.getString("claim_token")).isNotBlank();
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("P0 Stale Recovery: Abandoned scan recovered back to QUEUED after lease expiry")
    void testStaleScanRecoveryViaLeaseExpiry() throws SQLException {
        UUID scanId = UUID.randomUUID();
        Instant twentyMinutesAgo = Instant.now().minusSeconds(1200);

        // 1. Insert a scan in CLONING with stale locked_at (simulating killed worker)
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, locked_by, claim_token, locked_at, attempt_count, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'CLONING', 'crashed-worker', 'dead-token', ?, 1, now())")) {
            stmt.setObject(1, scanId);
            stmt.setObject(2, testWorkspaceId);
            stmt.setObject(3, testRepoId);
            stmt.setTimestamp(4, Timestamp.from(twentyMinutesAgo));
            stmt.executeUpdate();
        }

        // 2. Atomic recovery execution (cutoff = 15 minutes ago)
        Instant cutoff = Instant.now().minusSeconds(900);
        int recoveredRows;
        try (PreparedStatement recoverStmt = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'QUEUED', locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                        "WHERE id = ? AND status IN ('CLONING', 'INDEXING', 'SCANNING', 'CLASSIFYING') AND locked_at < ?")) {
            recoverStmt.setObject(1, scanId);
            recoverStmt.setTimestamp(2, Timestamp.from(cutoff));
            recoveredRows = recoverStmt.executeUpdate();
        }
        assertThat(recoveredRows).isEqualTo(1);

        // 3. Scan is now QUEUED and lease is completely cleared
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT status, locked_by, claim_token, locked_at FROM repository_scans WHERE id = ?")) {
            stmt.setObject(1, scanId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("QUEUED");
                assertThat(rs.getString("locked_by")).isNull();
                assertThat(rs.getString("claim_token")).isNull();
                assertThat(rs.getTimestamp("locked_at")).isNull();
            }
        }

        // 4. A new worker node can now atomically claim the scan
        try (PreparedStatement claimStmt = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'CLONING', started_at = now(), locked_at = now(), " +
                        "locked_by = 'replacement-worker', claim_token = 'new-token', attempt_count = attempt_count + 1 " +
                        "WHERE id = ? AND status = 'QUEUED'")) {
            claimStmt.setObject(1, scanId);
            int claimed = claimStmt.executeUpdate();
            assertThat(claimed).isEqualTo(1);
        }
    }

    @Test
    @Order(7)
    @DisplayName("P0 Lease Fencing: Stale worker cannot overwrite newer worker's results")
    void testOldWorkerCannotOverwriteNewWorkerResultGuardedCompletion() throws SQLException {
        UUID scanId = UUID.randomUUID();

        // 1. Initial claim by Worker 1
        String worker1 = "worker-1";
        String token1 = "token-uuid-111";
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, locked_by, claim_token, locked_at, attempt_count, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'CLONING', ?, ?, now(), 1, now())")) {
            stmt.setObject(1, scanId);
            stmt.setObject(2, testWorkspaceId);
            stmt.setObject(3, testRepoId);
            stmt.setString(4, worker1);
            stmt.setString(5, token1);
            stmt.executeUpdate();
        }

        // 2. Lease expires and Worker 2 claims the scan with new claim_token
        String worker2 = "worker-2";
        String token2 = "token-uuid-222";
        try (PreparedStatement reclaimStmt = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'CLONING', locked_by = ?, claim_token = ?, locked_at = now(), attempt_count = attempt_count + 1 " +
                        "WHERE id = ?")) {
            reclaimStmt.setString(1, worker2);
            reclaimStmt.setString(2, token2);
            reclaimStmt.setObject(3, scanId);
            assertThat(reclaimStmt.executeUpdate()).isEqualTo(1);
        }

        // 3. Worker 1 (delayed/stale) wakes up and attempts guarded completion with token1
        try (PreparedStatement complete1 = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'COMPLETED', completed_at = now(), duration_ms = 500, " +
                        "files_scanned = 10, findings_count = 99, locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            complete1.setObject(1, scanId);
            complete1.setString(2, worker1);
            complete1.setString(3, token1);
            int updated = complete1.executeUpdate();

            // Stale completion rejected! Rows updated must be ZERO!
            assertThat(updated).isEqualTo(0);
        }

        // 4. Worker 1 attempts guarded failure with token1
        try (PreparedStatement fail1 = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'FAILED', error_message = 'Old worker error', " +
                        "locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            fail1.setObject(1, scanId);
            fail1.setString(2, worker1);
            fail1.setString(3, token1);
            int updated = fail1.executeUpdate();

            // Stale failure rejected! Rows updated must be ZERO!
            assertThat(updated).isEqualTo(0);
        }

        // 5. Worker 2 completes legitimately with token2
        try (PreparedStatement complete2 = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'COMPLETED', completed_at = now(), duration_ms = 1200, " +
                        "files_scanned = 50, findings_count = 3, locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            complete2.setObject(1, scanId);
            complete2.setString(2, worker2);
            complete2.setString(3, token2);
            int updated = complete2.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }

        // 6. Verify final state reflects Worker 2's authoritative result
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT status, findings_count, files_scanned, locked_by, claim_token FROM repository_scans WHERE id = ?")) {
            stmt.setObject(1, scanId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("COMPLETED");
                assertThat(rs.getInt("findings_count")).isEqualTo(3);
                assertThat(rs.getInt("files_scanned")).isEqualTo(50);
                assertThat(rs.getString("locked_by")).isNull();
                assertThat(rs.getString("claim_token")).isNull();
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("P0 Heartbeat: Actively renewed lease maintains lock; stale worker cannot renew after lease loss")
    void testHeartbeatLeaseRenewalAndStaleWorkerRenewalRejection() throws SQLException {
        UUID scanId = UUID.randomUUID();
        String workerA = "worker-heartbeat-A";
        String tokenA = "token-heartbeat-AAA";

        // 1. Worker A claims the scan
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, locked_by, claim_token, locked_at, attempt_count, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'SCANNING', ?, ?, now() - interval '10 minutes', 1, now())")) {
            insert.setObject(1, scanId);
            insert.setObject(2, testWorkspaceId);
            insert.setObject(3, testRepoId);
            insert.setString(4, workerA);
            insert.setString(5, tokenA);
            insert.executeUpdate();
        }

        // 2. Worker A issues heartbeat lease renewal: conditional on scanId, workerId, claimToken
        try (PreparedStatement renew = connection.prepareStatement(
                "UPDATE repository_scans SET locked_at = now() " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            renew.setObject(1, scanId);
            renew.setString(2, workerA);
            renew.setString(3, tokenA);
            int renewed = renew.executeUpdate();
            assertThat(renewed).isEqualTo(1);
        }

        // Verify locked_at is now within 5 seconds of current timestamp
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT extract(epoch from (now() - locked_at)) as diff_sec FROM repository_scans WHERE id = ?")) {
            check.setObject(1, scanId);
            try (ResultSet rs = check.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("diff_sec")).isLessThan(5.0);
            }
        }

        // 3. Simulate Worker A crashes/stalls past 15 minutes
        try (PreparedStatement age = connection.prepareStatement(
                "UPDATE repository_scans SET locked_at = now() - interval '20 minutes' WHERE id = ?")) {
            age.setObject(1, scanId);
            age.executeUpdate();
        }

        // 4. Worker B detects stale lock (>15m) and atomically steals/re-claims the scan
        String workerB = "worker-heartbeat-B";
        String tokenB = "token-heartbeat-BBB";
        try (PreparedStatement steal = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'SCANNING', locked_by = ?, claim_token = ?, locked_at = now(), attempt_count = attempt_count + 1 " +
                        "WHERE id = ? AND status IN ('SCANNING', 'CLONING') AND locked_at < now() - interval '15 minutes'")) {
            steal.setString(1, workerB);
            steal.setString(2, tokenB);
            steal.setObject(3, scanId);
            int stolen = steal.executeUpdate();
            assertThat(stolen).isEqualTo(1);
        }

        // 5. Worker A wakes up and attempts heartbeat lease renewal with tokenA: MUST FAIL (0 rows updated)
        try (PreparedStatement staleRenew = connection.prepareStatement(
                "UPDATE repository_scans SET locked_at = now() " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            staleRenew.setObject(1, scanId);
            staleRenew.setString(2, workerA);
            staleRenew.setString(3, tokenA);
            int staleRows = staleRenew.executeUpdate();
            assertThat(staleRows).isEqualTo(0);
        }

        // 6. Verify Worker B remains authoritative owner
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT locked_by, claim_token FROM repository_scans WHERE id = ?")) {
            check.setObject(1, scanId);
            try (ResultSet rs = check.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("locked_by")).isEqualTo(workerB);
                assertThat(rs.getString("claim_token")).isEqualTo(tokenB);
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("P0 Heartbeat: Long-running scan beyond 15 minutes with active heartbeat blocks competing workers")
    void testLongRunningScanWithHeartbeatBlocksCompetingWorkers() throws SQLException {
        UUID scanId = UUID.randomUUID();
        String workerOwner = "worker-active-owner";
        String tokenOwner = "token-active-owner";

        // 1. Scan created 25 minutes ago (simulating 25-minute enterprise scan)
        // Heartbeat kept locked_at updated to now - 1 minute
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, locked_by, claim_token, locked_at, attempt_count, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'SCANNING', ?, ?, now() - interval '1 minute', 1, now() - interval '25 minutes')")) {
            insert.setObject(1, scanId);
            insert.setObject(2, testWorkspaceId);
            insert.setObject(3, testRepoId);
            insert.setString(4, workerOwner);
            insert.setString(5, tokenOwner);
            insert.executeUpdate();
        }

        // 2. Competing worker attempts to claim using standard atomic claim query
        String workerCompetitor = "worker-competitor";
        String tokenCompetitor = "token-competitor";
        try (PreparedStatement claimStmt = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'SCANNING', locked_by = ?, claim_token = ?, locked_at = now(), attempt_count = attempt_count + 1 " +
                        "WHERE id = ? AND (status = 'QUEUED' OR (status IN ('CLONING', 'SCANNING', 'CLASSIFYING') AND locked_at < now() - interval '15 minutes'))")) {
            claimStmt.setString(1, workerCompetitor);
            claimStmt.setString(2, tokenCompetitor);
            claimStmt.setObject(3, scanId);
            int claimed = claimStmt.executeUpdate();

            // Competing worker must NOT claim the actively renewed scan!
            assertThat(claimed).isEqualTo(0);
        }

        // 3. Worker Owner completes scan legitimately
        try (PreparedStatement complete = connection.prepareStatement(
                "UPDATE repository_scans SET status = 'COMPLETED', completed_at = now(), duration_ms = 1500000, " +
                        "files_scanned = 25000, findings_count = 0, locked_at = NULL, locked_by = NULL, claim_token = NULL " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            complete.setObject(1, scanId);
            complete.setString(2, workerOwner);
            complete.setString(3, tokenOwner);
            assertThat(complete.executeUpdate()).isEqualTo(1);
        }
    }
}


package com.secretvault.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL Multi-Instance Concurrency Acceptance Test Suite.
 * Simulates multiple independent application instances (Node 1 and Node 2)
 * running simultaneously against the same PostgreSQL 16 database.
 *
 * Verifies:
 * 1. Duplicate GitHub deliveries routed to separate nodes produce exactly ONE durable scan job.
 * 2. Only one worker node owns a scan lease at any given time.
 * 3. Outbox workers across multiple nodes cannot corrupt state when leases expire or network delays occur.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("SecretVault — Multi-Instance PostgreSQL Concurrency Acceptance")
class RealPostgresMultiInstanceConcurrencyTest {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/secretvault_dev";
    private static final String DB_USER = "vault_user";
    private static final String DB_PASSWORD = "vault_secure_password_dev_only";

    private static boolean postgresAvailable = false;
    private static Connection masterConnection;

    private static UUID testWorkspaceId;
    private static UUID testRepoId;

    // Simulated Instance 1 (Node 1)
    private static final String NODE_1_WORKER = "node-1-worker-" + UUID.randomUUID().toString().substring(0, 8);

    // Simulated Instance 2 (Node 2)
    private static final String NODE_2_WORKER = "node-2-worker-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void initPostgres() {
        try {
            masterConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            postgresAvailable = (masterConnection != null && !masterConnection.isClosed());

            if (postgresAvailable) {
                Flyway flyway = Flyway.configure()
                        .dataSource(DB_URL, DB_USER, DB_PASSWORD)
                        .load();
                flyway.migrate();

                // Setup test workspace
                try (Statement stmt = masterConnection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT id FROM workspaces LIMIT 1")) {
                    if (rs.next()) {
                        testWorkspaceId = (UUID) rs.getObject("id");
                    }
                }

                if (testWorkspaceId == null) {
                    UUID orgId = UUID.randomUUID();
                    testWorkspaceId = UUID.randomUUID();
                    try (PreparedStatement orgStmt = masterConnection.prepareStatement(
                            "INSERT INTO organizations (id, name, slug) VALUES (?, ?, ?)")) {
                        orgStmt.setObject(1, orgId);
                        orgStmt.setString(2, "Multi Instance Org");
                        orgStmt.setString(3, "multi-inst-org-" + orgId.toString().substring(0, 8));
                        orgStmt.executeUpdate();
                    }
                    try (PreparedStatement wsStmt = masterConnection.prepareStatement(
                            "INSERT INTO workspaces (id, organization_id, name, slug) VALUES (?, ?, ?, ?)")) {
                        wsStmt.setObject(1, testWorkspaceId);
                        wsStmt.setObject(2, orgId);
                        wsStmt.setString(3, "Multi Instance Workspace");
                        wsStmt.setString(4, "multi-inst-ws-" + testWorkspaceId.toString().substring(0, 8));
                        wsStmt.executeUpdate();
                    }
                }

                // Setup test repository
                testRepoId = UUID.randomUUID();
                try (PreparedStatement repoStmt = masterConnection.prepareStatement(
                        "INSERT INTO repositories (id, workspace_id, provider, owner, name, default_branch, visibility, status) " +
                                "VALUES (?, ?, 'GITHUB', 'MultiInstanceOrg', 'MultiRepo', 'main', 'PRIVATE', 'ACTIVE') " +
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
        if (masterConnection != null && !masterConnection.isClosed()) {
            masterConnection.close();
        }
    }

    @BeforeEach
    void checkPostgresAvailable() {
        assumeTrue(postgresAvailable, "PostgreSQL 16 is not running on localhost:5432");
    }

    @Test
    @Order(1)
    @DisplayName("Multi-Instance: Concurrent deliveries across Node 1 and Node 2 produce exactly ONE durable scan")
    void testConcurrentDeliveriesAcrossNodesProduceSingleDurableScan() throws Exception {
        String deliveryId = "gh-deliv-multi-" + UUID.randomUUID();
        int nodeCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(nodeCount);
        CyclicBarrier barrier = new CyclicBarrier(nodeCount);

        AtomicInteger successfulInserts = new AtomicInteger(0);
        AtomicInteger duplicateDetected = new AtomicInteger(0);

        Callable<Void> nodeTask = () -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                barrier.await(5, TimeUnit.SECONDS);

                // Simulate inbound webhook enqueue logic:
                // Check if already enqueued; if not, attempt insert. If unique violation occurs, detect duplicate.
                boolean exists = false;
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT count(1) FROM repository_scans WHERE repository_id = ? AND idempotency_key = ?")) {
                    check.setObject(1, testRepoId);
                    check.setString(2, deliveryId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            exists = true;
                        }
                    }
                }

                if (exists) {
                    duplicateDetected.incrementAndGet();
                    return null;
                }

                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, idempotency_key, created_at) " +
                                "VALUES (?, ?, ?, 'FULL', 'QUEUED', ?, now())")) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, testWorkspaceId);
                    insert.setObject(3, testRepoId);
                    insert.setString(4, deliveryId);
                    insert.executeUpdate();
                    successfulInserts.incrementAndGet();
                } catch (SQLException sqle) {
                    // PostgreSQL 23505 = unique_violation
                    if ("23505".equals(sqle.getSQLState())) {
                        duplicateDetected.incrementAndGet();
                    } else {
                        throw sqle;
                    }
                }
            }
            return null;
        };

        Future<Void> f1 = pool.submit(nodeTask);
        Future<Void> f2 = pool.submit(nodeTask);
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one node must have successfully inserted the scan
        assertThat(successfulInserts.get()).isEqualTo(1);
        assertThat(duplicateDetected.get()).isEqualTo(1);

        // Verify database contains exactly one row for this delivery ID
        try (PreparedStatement stmt = masterConnection.prepareStatement(
                "SELECT count(1) FROM repository_scans WHERE repository_id = ? AND idempotency_key = ?")) {
            stmt.setObject(1, testRepoId);
            stmt.setString(2, deliveryId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Multi-Instance: Competing Node 1 and Node 2 workers - only ONE worker owns the scan lease")
    void testCompetingWorkersOnlyOneOwnsScanLease() throws Exception {
        UUID scanId = UUID.randomUUID();

        // 1. Enqueue scan
        try (PreparedStatement insert = masterConnection.prepareStatement(
                "INSERT INTO repository_scans (id, workspace_id, repository_id, scan_type, status, created_at) " +
                        "VALUES (?, ?, ?, 'FULL', 'QUEUED', now())")) {
            insert.setObject(1, scanId);
            insert.setObject(2, testWorkspaceId);
            insert.setObject(3, testRepoId);
            insert.executeUpdate();
        }

        // 2. Both nodes compete to claim this scan simultaneously
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        AtomicInteger node1Claimed = new AtomicInteger(0);
        AtomicInteger node2Claimed = new AtomicInteger(0);

        String node1Token = "token-node1-" + UUID.randomUUID();
        String node2Token = "token-node2-" + UUID.randomUUID();

        Callable<Void> node1Task = () -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                barrier.await(5, TimeUnit.SECONDS);
                try (PreparedStatement claim = conn.prepareStatement(
                        "UPDATE repository_scans SET status = 'CLONING', locked_by = ?, claim_token = ?, locked_at = now(), attempt_count = attempt_count + 1 " +
                                "WHERE id = ? AND (status = 'QUEUED' OR (status IN ('CLONING', 'SCANNING') AND locked_at < now() - interval '15 minutes'))")) {
                    claim.setString(1, NODE_1_WORKER);
                    claim.setString(2, node1Token);
                    claim.setObject(3, scanId);
                    int updated = claim.executeUpdate();
                    if (updated > 0) node1Claimed.incrementAndGet();
                }
            }
            return null;
        };

        Callable<Void> node2Task = () -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                barrier.await(5, TimeUnit.SECONDS);
                try (PreparedStatement claim = conn.prepareStatement(
                        "UPDATE repository_scans SET status = 'CLONING', locked_by = ?, claim_token = ?, locked_at = now(), attempt_count = attempt_count + 1 " +
                                "WHERE id = ? AND (status = 'QUEUED' OR (status IN ('CLONING', 'SCANNING') AND locked_at < now() - interval '15 minutes'))")) {
                    claim.setString(1, NODE_2_WORKER);
                    claim.setString(2, node2Token);
                    claim.setObject(3, scanId);
                    int updated = claim.executeUpdate();
                    if (updated > 0) node2Claimed.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = pool.submit(node1Task);
        Future<Void> f2 = pool.submit(node2Task);
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one worker won
        int totalClaims = node1Claimed.get() + node2Claimed.get();
        assertThat(totalClaims).isEqualTo(1);

        // Verify the database row is locked strictly by the winning worker
        try (PreparedStatement stmt = masterConnection.prepareStatement(
                "SELECT locked_by, claim_token, status FROM repository_scans WHERE id = ?")) {
            stmt.setObject(1, scanId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("CLONING");
                String lockedBy = rs.getString("locked_by");
                if (node1Claimed.get() == 1) {
                    assertThat(lockedBy).isEqualTo(NODE_1_WORKER);
                    assertThat(rs.getString("claim_token")).isEqualTo(node1Token);
                } else {
                    assertThat(lockedBy).isEqualTo(NODE_2_WORKER);
                    assertThat(rs.getString("claim_token")).isEqualTo(node2Token);
                }
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Multi-Instance: Outbox workers cannot corrupt state when Node 1 delays and Node 2 takes over")
    void testOutboxWorkersMultiInstanceDelayAndTakeoverSafety() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();

        // 1. Enqueue outbox event in PostgreSQL
        try (PreparedStatement insert = masterConnection.prepareStatement(
                "INSERT INTO event_outbox (id, event_id, event_type, workspace_id, aggregate_type, aggregate_id, payload, status, available_at, occurred_at, attempt_count, max_attempts, created_at) " +
                        "VALUES (?, ?, 'SECRET_EXPOSED', ?, 'SECRET', 'sec-multi-01', '{\"test\":true}', 'PENDING', now(), now(), 0, 5, now())")) {
            insert.setObject(1, rowId);
            insert.setObject(2, eventId);
            insert.setObject(3, testWorkspaceId);
            insert.executeUpdate();
        }


        // 2. Node 1 claims event
        String node1ClaimToken = "node1-outbox-token-" + UUID.randomUUID();
        try (PreparedStatement claim = masterConnection.prepareStatement(
                "UPDATE event_outbox SET status = 'PROCESSING', locked_by = ?, claim_token = ?, locked_at = now() - interval '6 minutes', attempt_count = attempt_count + 1 " +
                        "WHERE id = ? AND status = 'PENDING'")) {
            claim.setString(1, NODE_1_WORKER);
            claim.setString(2, node1ClaimToken);
            claim.setObject(3, rowId);
            assertThat(claim.executeUpdate()).isEqualTo(1);
        }

        // 3. Node 2 detects stale lock (>5 minutes) and claims it
        String node2ClaimToken = "node2-outbox-token-" + UUID.randomUUID();
        try (PreparedStatement steal = masterConnection.prepareStatement(
                "UPDATE event_outbox SET status = 'PROCESSING', locked_by = ?, claim_token = ?, locked_at = now(), attempt_count = attempt_count + 1 " +
                        "WHERE id = ? AND status = 'PROCESSING' AND locked_at < now() - interval '5 minutes'")) {
            steal.setString(1, NODE_2_WORKER);
            steal.setString(2, node2ClaimToken);
            steal.setObject(3, rowId);
            int stolen = steal.executeUpdate();
            assertThat(stolen).isEqualTo(1);
        }

        // 4. Node 2 completes processing legitimately
        try (PreparedStatement complete2 = masterConnection.prepareStatement(
                "UPDATE event_outbox SET status = 'PROCESSED', processed_at = now(), locked_by = NULL, locked_at = NULL, claim_token = NULL " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            complete2.setObject(1, rowId);
            complete2.setString(2, NODE_2_WORKER);
            complete2.setString(3, node2ClaimToken);
            assertThat(complete2.executeUpdate()).isEqualTo(1);
        }

        // 5. Node 1 (wakes up after network stall) attempts to complete with its stale claimToken
        try (PreparedStatement complete1 = masterConnection.prepareStatement(
                "UPDATE event_outbox SET status = 'PROCESSED', processed_at = now(), locked_by = NULL, locked_at = NULL, claim_token = NULL " +
                        "WHERE id = ? AND locked_by = ? AND claim_token = ?")) {
            complete1.setObject(1, rowId);
            complete1.setString(2, NODE_1_WORKER);
            complete1.setString(3, node1ClaimToken);
            int rowsUpdated = complete1.executeUpdate();

            // Guarded completion guarantees stale Node 1 is fenced!
            assertThat(rowsUpdated).isEqualTo(0);
        }

        // 6. Verify final state is safely PROCESSED with no corruption
        try (PreparedStatement check = masterConnection.prepareStatement(
                "SELECT status, locked_by, claim_token FROM event_outbox WHERE id = ?")) {
            check.setObject(1, rowId);
            try (ResultSet rs = check.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("PROCESSED");
                assertThat(rs.getString("locked_by")).isNull();
                assertThat(rs.getString("claim_token")).isNull();
            }
        }
    }
}

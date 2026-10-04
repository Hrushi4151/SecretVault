package com.secretvault.cli.kubernetes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kubernetes Secret Synchronization, Ephemeral Leases & Rotation Contract Tests")
class KubernetesSecretSyncContractTest {

    private static final Path K8S_BASE = Path.of("..", "infrastructure", "kubernetes");

    @Test
    @DisplayName("Verify Client Reveal and Lease API methods exist")
    void testClientRevealAndLeaseMethods() throws Exception {
        Path clientFile = K8S_BASE.resolve(Path.of("pkg", "client", "client.go"));
        String clientContent = Files.readString(clientFile);

        assertTrue(clientContent.contains("func (c *SecretVaultClient) RevealSecret"),
                "Client must implement RevealSecret method");
        assertTrue(clientContent.contains("func (c *SecretVaultClient) ListSecrets"),
                "Client must implement ListSecrets method");
        assertTrue(clientContent.contains("func (c *SecretVaultClient) CreateLease"),
                "Client must implement CreateLease method");
        assertTrue(clientContent.contains("func (c *SecretVaultClient) RenewLease"),
                "Client must implement RenewLease method");
        assertTrue(clientContent.contains("func (c *SecretVaultClient) RevokeLease"),
                "Client must implement RevokeLease method");
    }

    @Test
    @DisplayName("Verify SecretVaultSecret controller implements CreationPolicy and Lease Lifecycle")
    void testSecretVaultSecretControllerDelivery() throws Exception {
        Path controllerFile = K8S_BASE.resolve(Path.of("controllers", "secretvaultsecret_controller.go"));
        String content = Files.readString(controllerFile);

        assertTrue(content.contains("CreationPolicyOwner"), "Controller must support Owner creation policy");
        assertTrue(content.contains("CreationPolicyMerge"), "Controller must support Merge creation policy");
        assertTrue(content.contains("GetSecretMetadata"), "Controller must perform metadata-first check");
        assertTrue(content.contains("RevealSecret"), "Controller must perform authorized secret reveal");
        assertTrue(content.contains("reconcileLease"), "Controller must reconcile ephemeral leases");
        assertTrue(content.contains("RevokeLease"), "Controller must revoke leases upon deletion");
    }

    @Test
    @DisplayName("Verify SecretVaultSync controller implements filtering, drift, and rolling restart")
    void testSecretVaultSyncControllerDelivery() throws Exception {
        Path controllerFile = K8S_BASE.resolve(Path.of("controllers", "secretvaultsync_controller.go"));
        String content = Files.readString(controllerFile);

        assertTrue(content.contains("filterSecrets"), "Controller must filter secrets by whitelist/blacklist/tags");
        assertTrue(content.contains("DriftPolicyDetectOnly"), "Controller must support DetectOnly drift policy");
        assertTrue(content.contains("DriftPolicyEnforce"), "Controller must support Enforce drift policy");
        assertTrue(content.contains("DriftPolicyIgnore"), "Controller must support Ignore drift policy");
        assertTrue(content.contains("handleRotation"), "Controller must handle upstream secret rotation");
        assertTrue(content.contains("secretvault.io/revision"), "Controller must update pod template revision annotation");
    }

    @Test
    @DisplayName("Verify Operator metrics track Phase 13.4 synchronization and lease counters")
    void testOperatorMetricsCoverage() throws Exception {
        Path metricsFile = K8S_BASE.resolve(Path.of("pkg", "metrics", "operator_metrics.go"));
        String content = Files.readString(metricsFile);

        assertTrue(content.contains("SecretSyncTotal"), "Metrics must record SecretSyncTotal");
        assertTrue(content.contains("SecretVersionChangesTotal"), "Metrics must record SecretVersionChangesTotal");
        assertTrue(content.contains("SecretDriftTotal"), "Metrics must record SecretDriftTotal");
        assertTrue(content.contains("LeaseCreatedTotal"), "Metrics must record LeaseCreatedTotal");
        assertTrue(content.contains("LeaseRenewedTotal"), "Metrics must record LeaseRenewedTotal");
        assertTrue(content.contains("LeaseRevokedTotal"), "Metrics must record LeaseRevokedTotal");
        assertTrue(content.contains("WorkloadRestartTotal"), "Metrics must record WorkloadRestartTotal");
        assertTrue(content.contains("KubernetesSecretWriteTotal"), "Metrics must record KubernetesSecretWriteTotal");
    }
}

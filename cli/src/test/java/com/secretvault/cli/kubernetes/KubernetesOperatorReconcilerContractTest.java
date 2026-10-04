package com.secretvault.cli.kubernetes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kubernetes Operator Reconciler Core Contract Tests")
class KubernetesOperatorReconcilerContractTest {

    private static final Path K8S_BASE = Path.of("..", "infrastructure", "kubernetes");

    @Test
    @DisplayName("Verify Operator Controller and Manager source files exist")
    void testOperatorSourceFilesExist() {
        Path controllersPkg = K8S_BASE.resolve("controllers");
        Path cmdPkg = K8S_BASE.resolve(Path.of("cmd", "manager"));
        Path rbacDir = K8S_BASE.resolve(Path.of("config", "rbac"));

        assertTrue(Files.exists(controllersPkg.resolve("secretvaultsecret_controller.go")),
                "secretvaultsecret_controller.go must exist");
        assertTrue(Files.exists(controllersPkg.resolve("secretvaultsync_controller.go")),
                "secretvaultsync_controller.go must exist");
        assertTrue(Files.exists(controllersPkg.resolve("secretvaultsecret_controller_test.go")),
                "secretvaultsecret_controller_test.go must exist");
        assertTrue(Files.exists(controllersPkg.resolve("secretvaultsync_controller_test.go")),
                "secretvaultsync_controller_test.go must exist");
        assertTrue(Files.exists(cmdPkg.resolve("main.go")), "cmd/manager/main.go must exist");

        assertTrue(Files.exists(rbacDir.resolve("role.yaml")), "rbac/role.yaml must exist");
        assertTrue(Files.exists(rbacDir.resolve("service_account.yaml")), "rbac/service_account.yaml must exist");
        assertTrue(Files.exists(rbacDir.resolve("leader_election_role.yaml")), "rbac/leader_election_role.yaml must exist");
    }

    @Test
    @DisplayName("Verify RBAC least-privilege security invariants in Phase 13.4")
    void testRbacLeastPrivilege() throws Exception {
        Path roleFile = K8S_BASE.resolve(Path.of("config", "rbac", "role.yaml"));
        String roleContent = Files.readString(roleFile);

        // RBAC must cover secretvault.io, core secrets, apps, and events
        assertTrue(roleContent.contains("secretvault.io"), "Role must contain secretvault.io apiGroup");
        assertTrue(roleContent.contains("secretvaultsecrets"), "Role must permit secretvaultsecrets");
        assertTrue(roleContent.contains("secretvaultsyncs"), "Role must permit secretvaultsyncs");
        assertTrue(roleContent.contains("secrets"), "Role must permit core/v1 secret delivery");
        assertTrue(roleContent.contains("deployments"), "Role must permit apps/v1 deployments restart");
        assertTrue(roleContent.contains("events"), "Role must permit event creation");

        // RBAC must NOT contain cluster-admin or wildcard verbs
        assertFalse(roleContent.contains("cluster-admin"), "Role must not grant cluster-admin");
        assertFalse(roleContent.contains("verbs:\n      - \"*\""), "Role must not contain wildcard verbs");
    }

    @Test
    @DisplayName("Verify Reconciler status conditions and observedGeneration rules")
    void testReconcilerStatusAndGenerationContracts() throws Exception {
        Path secretController = K8S_BASE.resolve(Path.of("controllers", "secretvaultsecret_controller.go"));
        String content = Files.readString(secretController);

        assertTrue(content.contains("ObservedGeneration: svs.Generation"),
                "Controller must set ObservedGeneration to match resource Generation");
        assertTrue(content.contains("ConditionReady"), "Controller must manage Ready condition");
        assertTrue(content.contains("ConditionSynced"), "Controller must manage Synced condition");
        assertTrue(content.contains("ConditionError"), "Controller must manage Error condition");
        assertTrue(content.contains("ReasonConfigInvalid"), "Controller must classify ConfigurationInvalid");
        assertTrue(content.contains("ReasonAuthFailed"), "Controller must classify AuthenticationFailed");
        assertTrue(content.contains("ReasonAuthDenied"), "Controller must classify AuthorizationDenied");
    }

    @Test
    @DisplayName("Verify Manager health probes and leader election configuration")
    void testManagerConfiguration() throws Exception {
        Path mainFile = K8S_BASE.resolve(Path.of("cmd", "manager", "main.go"));
        String content = Files.readString(mainFile);

        assertTrue(content.contains("LeaderElection:         enableLeaderElection"),
                "Manager must configure LeaderElection");
        assertTrue(content.contains("AddHealthzCheck"), "Manager must register healthz probe");
        assertTrue(content.contains("AddReadyzCheck"), "Manager must register readyz probe");
        assertTrue(content.contains("SetupSignalHandler"), "Manager must handle graceful shutdown signals");
    }
}

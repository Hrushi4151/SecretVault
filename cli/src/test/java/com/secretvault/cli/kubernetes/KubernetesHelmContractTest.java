package com.secretvault.cli.kubernetes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kubernetes Helm Chart Production Hardening Contract Tests")
class KubernetesHelmContractTest {

    private static final Path HELM_BASE = Path.of("..", "infrastructure", "kubernetes", "helm", "secretvault-operator");

    @Test
    @DisplayName("Verify Helm Chart structure and metadata files exist")
    void testHelmChartStructure() {
        assertTrue(Files.exists(HELM_BASE.resolve("Chart.yaml")), "Chart.yaml must exist");
        assertTrue(Files.exists(HELM_BASE.resolve("values.yaml")), "values.yaml must exist");
        assertTrue(Files.exists(HELM_BASE.resolve("values.schema.json")), "values.schema.json must exist");
        assertTrue(Files.exists(HELM_BASE.resolve(Path.of("crds", "secretvault.io_secretvaultsecrets.yaml"))),
                "crds/secretvault.io_secretvaultsecrets.yaml must exist");
        assertTrue(Files.exists(HELM_BASE.resolve(Path.of("crds", "secretvault.io_secretvaultsyncs.yaml"))),
                "crds/secretvault.io_secretvaultsyncs.yaml must exist");
    }

    @Test
    @DisplayName("Verify Helm templates exist for deployment, RBAC, network policy, and observability")
    void testHelmTemplatesExist() {
        Path templatesDir = HELM_BASE.resolve("templates");
        assertTrue(Files.exists(templatesDir.resolve("_helpers.tpl")), "_helpers.tpl must exist");
        assertTrue(Files.exists(templatesDir.resolve("deployment.yaml")), "deployment.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("serviceaccount.yaml")), "serviceaccount.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("role.yaml")), "role.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("rolebinding.yaml")), "rolebinding.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("clusterrole.yaml")), "clusterrole.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("clusterrolebinding.yaml")), "clusterrolebinding.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("leader-election-role.yaml")), "leader-election-role.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("leader-election-rolebinding.yaml")), "leader-election-rolebinding.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("service.yaml")), "service.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("servicemonitor.yaml")), "servicemonitor.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("networkpolicy.yaml")), "networkpolicy.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("poddisruptionbudget.yaml")), "poddisruptionbudget.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("configmap.yaml")), "configmap.yaml must exist");
        assertTrue(Files.exists(templatesDir.resolve("NOTES.txt")), "NOTES.txt must exist");
    }

    @Test
    @DisplayName("Verify Pod Security Standards and Zero Plaintext in Helm values")
    void testHelmValuesSecurity() throws Exception {
        String valuesContent = Files.readString(HELM_BASE.resolve("values.yaml")).replace("\r\n", "\n");

        assertTrue(valuesContent.contains("runAsNonRoot: true"), "Values must enforce runAsNonRoot");
        assertTrue(valuesContent.contains("allowPrivilegeEscalation: false"), "Values must disable allowPrivilegeEscalation");
        assertTrue(valuesContent.contains("readOnlyRootFilesystem: true"), "Values must enforce readOnlyRootFilesystem");
        assertTrue(valuesContent.contains("drop:\n      - ALL"), "Values must drop ALL capabilities");

        // Verify zero credentials
        assertFalse(valuesContent.contains("password: \""), "Values must not contain hardcoded passwords");
        assertFalse(valuesContent.contains("token: \""), "Values must not contain hardcoded tokens");
    }

    @Test
    @DisplayName("Verify RBAC least privilege in Helm templates")
    void testHelmRbacLeastPrivilege() throws Exception {
        String clusterRole = Files.readString(HELM_BASE.resolve(Path.of("templates", "clusterrole.yaml"))).replace("\r\n", "\n");

        assertTrue(clusterRole.contains("secretvault.io"), "RBAC must cover secretvault.io");
        assertTrue(clusterRole.contains("secrets"), "RBAC must cover core/v1 secrets");
        assertTrue(clusterRole.contains("deployments"), "RBAC must cover apps/v1 deployments");
        assertFalse(clusterRole.contains("cluster-admin"), "RBAC must not grant cluster-admin");
        assertFalse(clusterRole.contains("pods/exec"), "RBAC must not grant pods/exec");
        assertFalse(clusterRole.contains("verbs:\n      - \"*\""), "RBAC must not contain wildcard verbs");
    }
}

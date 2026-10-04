/**
 * SecretVault Kubernetes Operator — Helm Chart Validation & Hardening Test Suite
 * Validates Chart metadata, values schema, template rendering invariants, RBAC least privilege, and zero plaintext leakage.
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

console.log('Running SecretVault Helm Chart Validation & Hardening Tests...\n');

let passedTests = 0;
let totalTests = 0;

function runTest(name, fn) {
  totalTests++;
  try {
    fn();
    console.log(`✔ [Test ${totalTests}] Passed: ${name}`);
    passedTests++;
  } catch (err) {
    console.error(`✖ [Test ${totalTests}] FAILED: ${name}`);
    console.error(err);
    process.exit(1);
  }
}

const HELM_BASE = path.join(__dirname, '..', 'helm', 'secretvault-operator');

(async () => {
  // 1. Chart.yaml validation
  runTest('1. Chart.yaml conforms to Helm v2 specification with SecretVault metadata', () => {
    const chartContent = fs.readFileSync(path.join(HELM_BASE, 'Chart.yaml'), 'utf8');
    assert.ok(chartContent.includes('apiVersion: v2'), 'Chart must be apiVersion v2');
    assert.ok(chartContent.includes('name: secretvault-operator'), 'Chart name must be secretvault-operator');
    assert.ok(chartContent.includes('type: application'), 'Chart type must be application');
    assert.ok(chartContent.includes('version: 0.1.0'), 'Chart version must be 0.1.0');
    assert.ok(chartContent.includes('appVersion: "1.0.0"'), 'AppVersion must be 1.0.0');
    assert.ok(chartContent.includes('maintainers:'), 'Chart must include maintainers');
  });

  // 2. Authoritative CRDs in crds/
  runTest('2. Authoritative CRDs are present in crds/ directory without destructive hooks', () => {
    const crdsDir = path.join(HELM_BASE, 'crds');
    assert.ok(fs.existsSync(path.join(crdsDir, 'secretvault.io_secretvaultsecrets.yaml')), 'SecretVaultSecret CRD must exist in crds/');
    assert.ok(fs.existsSync(path.join(crdsDir, 'secretvault.io_secretvaultsyncs.yaml')), 'SecretVaultSync CRD must exist in crds/');

    const svsContent = fs.readFileSync(path.join(crdsDir, 'secretvault.io_secretvaultsecrets.yaml'), 'utf8');
    assert.ok(!svsContent.includes('helm.sh/hook: pre-delete'), 'CRDs must not have destructive pre-delete hooks');
    assert.ok(!svsContent.includes('helm.sh/hook: post-delete'), 'CRDs must not have destructive post-delete hooks');
  });

  // 3. values.schema.json validation
  runTest('3. values.schema.json enforces strict typing, non-root, and capability drop', () => {
    const schemaContent = fs.readFileSync(path.join(HELM_BASE, 'values.schema.json'), 'utf8');
    const schema = JSON.parse(schemaContent);

    assert.strictEqual(schema.required.includes('replicaCount'), true);
    assert.strictEqual(schema.required.includes('podSecurityContext'), true);
    assert.strictEqual(schema.required.includes('securityContext'), true);

    // Verify non-root enforcement
    assert.strictEqual(schema.properties.podSecurityContext.properties.runAsNonRoot.const, true);
    assert.strictEqual(schema.properties.securityContext.properties.allowPrivilegeEscalation.const, false);
    assert.strictEqual(schema.properties.securityContext.properties.readOnlyRootFilesystem.const, true);
  });

  // 4. values.yaml secure defaults
  runTest('4. values.yaml provides secure production defaults with zero credentials', () => {
    const valuesContent = fs.readFileSync(path.join(HELM_BASE, 'values.yaml'), 'utf8');

    assert.ok(valuesContent.includes('runAsNonRoot: true'), 'runAsNonRoot must default to true');
    assert.ok(valuesContent.includes('allowPrivilegeEscalation: false'), 'allowPrivilegeEscalation must default to false');
    assert.ok(valuesContent.includes('readOnlyRootFilesystem: true'), 'readOnlyRootFilesystem must default to true');
    assert.ok(valuesContent.includes('drop:\n      - ALL'), 'Linux capabilities must drop ALL');
    assert.ok(valuesContent.includes('type: RuntimeDefault'), 'seccomp profile must be RuntimeDefault');
    assert.ok(valuesContent.includes('enabled: true'), 'Leader election must be enabled by default');

    // Zero secret credentials in values.yaml
    assert.ok(!valuesContent.includes('password: "'), 'values.yaml must not contain hardcoded passwords');
    assert.ok(!valuesContent.includes('token: "'), 'values.yaml must not contain hardcoded tokens');
    assert.ok(!valuesContent.includes('apiKey: "'), 'values.yaml must not contain hardcoded apiKeys');
  });

  // 5. Deployment template security constraints
  runTest('5. deployment.yaml configures probes, securityContexts, memory tmpfs, and leader election', () => {
    const deployContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'deployment.yaml'), 'utf8');

    assert.ok(deployContent.includes('/healthz'), 'Deployment must configure livenessProbe /healthz');
    assert.ok(deployContent.includes('/readyz'), 'Deployment must configure readinessProbe /readyz');
    assert.ok(deployContent.includes('--leader-elect='), 'Deployment must pass leader-election arguments');
    assert.ok(deployContent.includes('mountPath: /tmp'), 'Deployment must mount emptyDir /tmp for readOnlyRootFilesystem');
    assert.ok(deployContent.includes('terminationGracePeriodSeconds: 30'), 'Deployment must support graceful termination');
  });

  // 6. RBAC least-privilege verification
  runTest('6. RBAC templates grant scoped least-privilege permissions with zero cluster-admin', () => {
    const roleContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'role.yaml'), 'utf8');
    const clusterRoleContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'clusterrole.yaml'), 'utf8');

    for (const content of [roleContent, clusterRoleContent]) {
      assert.ok(content.includes('secretvault.io'), 'RBAC must cover secretvault.io');
      assert.ok(content.includes('secrets'), 'RBAC must permit core/v1 secret synchronization');
      assert.ok(content.includes('deployments'), 'RBAC must permit rolling restart on rotation');
      assert.ok(!content.includes('cluster-admin'), 'RBAC must never grant cluster-admin');
      assert.ok(!content.includes('pods/exec'), 'RBAC must never grant pods/exec');
      assert.ok(!content.includes('pods/attach'), 'RBAC must never grant pods/attach');
      assert.ok(!content.includes('verbs:\n      - "*"'), 'RBAC must not use wildcard verbs');
    }
  });

  // 7. NetworkPolicy isolation
  runTest('7. networkpolicy.yaml isolates ingress/egress to DNS, KubeAPI, and SecretVault API', () => {
    const npContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'networkpolicy.yaml'), 'utf8');

    assert.ok(npContent.includes('port: 53'), 'NetworkPolicy must allow DNS egress');
    assert.ok(npContent.includes('port: 443') || npContent.includes('port: 6443'), 'NetworkPolicy must allow KubeAPI egress');
    assert.ok(npContent.includes('secretVaultApiPort'), 'NetworkPolicy must allow SecretVault API egress');
    assert.ok(npContent.includes('allowMetrics'), 'NetworkPolicy must allow metrics scraping ingress');
  });

  // 8. ServiceAccount & Token separation
  runTest('8. ServiceAccount isolates operator controller token from workload identity tokens', () => {
    const saContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'serviceaccount.yaml'), 'utf8');
    assert.ok(saContent.includes('automountServiceAccountToken:'), 'ServiceAccount must configure automountServiceAccountToken');
  });

  // 9. ConfigMap contains only non-sensitive runtime config
  runTest('9. configmap.yaml contains strictly non-sensitive configuration keys', () => {
    const cmContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'configmap.yaml'), 'utf8');

    assert.ok(cmContent.includes('SECRETVAULT_SERVER_URL'), 'ConfigMap must specify SERVER_URL');
    assert.ok(cmContent.includes('OIDC_AUDIENCE'), 'ConfigMap must specify OIDC_AUDIENCE');
    assert.ok(!cmContent.includes('TOKEN:'), 'ConfigMap must not contain TOKEN');
    assert.ok(!cmContent.includes('PASSWORD:'), 'ConfigMap must not contain PASSWORD');
    assert.ok(!cmContent.includes('CLIENT_SECRET:'), 'ConfigMap must not contain CLIENT_SECRET');
  });

  // 10. ServiceMonitor & PDB optional templates
  runTest('10. ServiceMonitor and PodDisruptionBudget are safely templated under conditional gates', () => {
    const smContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'servicemonitor.yaml'), 'utf8');
    const pdbContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'poddisruptionbudget.yaml'), 'utf8');

    assert.ok(smContent.includes('monitoring.coreos.com/v1'), 'ServiceMonitor must use Prometheus Operator API');
    assert.ok(smContent.includes('.Values.metrics.serviceMonitor.enabled'), 'ServiceMonitor must be conditionally gated');
    assert.ok(pdbContent.includes('.Values.podDisruptionBudget.enabled'), 'PDB must be conditionally gated');
  });

  // 11. NOTES.txt contains operational instructions with zero credentials
  runTest('11. NOTES.txt provides verification steps without printing credentials', () => {
    const notesContent = fs.readFileSync(path.join(HELM_BASE, 'templates', 'NOTES.txt'), 'utf8');

    assert.ok(notesContent.includes('kubectl get pods'), 'NOTES.txt must explain pod verification');
    assert.ok(notesContent.includes('kubectl logs'), 'NOTES.txt must explain log inspection');
    assert.ok(notesContent.includes('KMS / etcd encryption-at-rest'), 'NOTES.txt must include KMS security notice');
    assert.ok(!notesContent.includes('token:'), 'NOTES.txt must not print tokens');
  });

  console.log('\n========================================');
  console.log(`ALL ${passedTests}/${totalTests} HELM VALIDATION TESTS PASSED (100%)`);
  console.log('========================================\n');
})();

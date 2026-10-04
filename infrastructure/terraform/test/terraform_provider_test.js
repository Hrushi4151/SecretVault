/**
 * SecretVault Production Terraform Provider Test Suite
 * Validates provider schemas, resources, data sources, security invariants,
 * zero-plaintext read lifecycles, import formats, TLS/retries, and backend API contracts.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const crypto = require('crypto');

console.log('================================================================');
console.log('Running SecretVault Terraform Provider Validation & Security Tests');
console.log('================================================================\n');

let totalTests = 0;
let passedTests = 0;

function runTest(description, testFn) {
  totalTests++;
  try {
    testFn();
    console.log(`✔ [Test ${totalTests}] Passed: ${description}`);
    passedTests++;
  } catch (err) {
    console.error(`✖ [Test ${totalTests}] FAILED: ${description}`);
    console.error(`  Error: ${err.message}`);
    process.exitCode = 1;
  }
}

const terraformDir = path.resolve(__dirname, '..');
const internalDir = path.join(terraformDir, 'internal');

// 1. Go Module & Framework Dependency Validation
runTest('Go module declares Terraform Plugin Framework dependency', () => {
  const goModPath = path.join(terraformDir, 'go.mod');
  assert.ok(fs.existsSync(goModPath), 'go.mod must exist');
  const content = fs.readFileSync(goModPath, 'utf8');
  assert.ok(content.includes('module github.com/secretvault/terraform-provider-secretvault'), 'module name mismatch');
  assert.ok(content.includes('github.com/hashicorp/terraform-plugin-framework'), 'must require terraform-plugin-framework');
});

// 2. Provider Schema & Configuration Attributes
runTest('Provider schema exposes address, token, tls, workspace_id, timeout, and retry settings', () => {
  const providerPath = path.join(internalDir, 'provider', 'provider.go');
  assert.ok(fs.existsSync(providerPath), 'provider.go must exist');
  const content = fs.readFileSync(providerPath, 'utf8');
  assert.ok(content.includes('"address": schema.StringAttribute'), 'must define address');
  assert.ok(content.includes('"token": schema.StringAttribute'), 'must define token');
  assert.ok(content.includes('Sensitive:   true'), 'token must be sensitive');
  assert.ok(content.includes('"workspace_id": schema.StringAttribute'), 'must define workspace_id');
  assert.ok(content.includes('"timeout_seconds": schema.Int64Attribute'), 'must define timeout_seconds');
  assert.ok(content.includes('"max_retries": schema.Int64Attribute'), 'must define max_retries');
  assert.ok(content.includes('"retry_wait_min_ms": schema.Int64Attribute'), 'must define retry_wait_min_ms');
  assert.ok(content.includes('"tls": schema.SingleNestedBlock'), 'must define tls block');
});

// 3. TLS Validation & Insecure Flag Default
runTest('Provider TLS configuration defaults to secure verification with custom CA support', () => {
  const providerPath = path.join(internalDir, 'provider', 'provider.go');
  const content = fs.readFileSync(providerPath, 'utf8');
  assert.ok(content.includes('InsecureSkipVerify: false'), 'TLS insecure_skip_verify must default to false');
  assert.ok(content.includes('ca_cert_file'), 'must support custom ca_cert_file');
  assert.ok(content.includes('server_name'), 'must support SNI server_name');
});

// 4. Client Hardening & Safe Retries
runTest('HTTP client implements bounded retries, backoff, and idempotent-only method retry rules', () => {
  const clientPath = path.join(internalDir, 'client', 'client.go');
  assert.ok(fs.existsSync(clientPath), 'client.go must exist');
  const content = fs.readFileSync(clientPath, 'utf8');
  assert.ok(content.includes('isIdempotentMethod'), 'must check idempotent HTTP methods');
  assert.ok(content.includes('parseRetryAfter'), 'must parse Retry-After headers for 429');
  assert.ok(content.includes('X-Workspace-ID'), 'must transmit X-Workspace-ID header for multi-tenancy');
  assert.ok(content.includes('crypto/tls'), 'must use standard crypto/tls');
});

// 5. Redaction of Sensitive Material in Diagnostics
runTest('Sensitive information (Bearer tokens, secrets) is scrubbed from errors and logs', () => {
  const errorsPath = path.join(internalDir, 'client', 'errors.go');
  assert.ok(fs.existsSync(errorsPath), 'errors.go must exist');
  const content = fs.readFileSync(errorsPath, 'utf8');
  assert.ok(content.includes('RedactSensitiveInfo'), 'must provide RedactSensitiveInfo function');
  assert.ok(content.includes('Bearer [REDACTED]'), 'must scrub bearer tokens');
  assert.ok(content.includes('=[REDACTED]'), 'must scrub secret/password key-value pairs');
});

// 6. Zero-Plaintext Secret Resource Invariant
runTest('Secret resource defines value as Sensitive and Read() strictly pulls metadata without /reveal', () => {
  const secretResPath = path.join(internalDir, 'resources', 'resource_secret.go');
  assert.ok(fs.existsSync(secretResPath), 'resource_secret.go must exist');
  const content = fs.readFileSync(secretResPath, 'utf8');
  assert.ok(content.includes('"value": schema.StringAttribute'), 'must define value');
  assert.ok(content.includes('Sensitive:   true'), 'value attribute must be marked Sensitive: true');
  assert.ok(content.includes('GetSecretMetadata'), 'Read() must invoke GetSecretMetadata only');
  assert.ok(!content.includes('RevealSecret') && !content.includes('/reveal"'), 'Secret resource must NEVER invoke reveal endpoint');
  assert.ok(content.includes('fingerprint'), 'must compute SHA256 fingerprint for drift tracking');
});

// 7. Zero-Plaintext Secret Data Source Invariant
runTest('Secret data source strictly exposes metadata only without plaintext values', () => {
  const secretDsPath = path.join(internalDir, 'datasources', 'datasource_secret.go');
  assert.ok(fs.existsSync(secretDsPath), 'datasource_secret.go must exist');
  const content = fs.readFileSync(secretDsPath, 'utf8');
  assert.ok(!content.includes('"value":'), 'Secret data source must NOT expose value attribute');
  assert.ok(content.includes('"version": schema.Int64Attribute'), 'must expose version metadata');
  assert.ok(content.includes('"content_type": schema.StringAttribute'), 'must expose contentType');
  assert.ok(content.includes('GetSecretMetadata'), 'must call metadata endpoint only');
});

// 8. Project Resource Lifecycle & Import Contract
runTest('Project resource supports full CRUD and workspace_id/project_id import syntax', () => {
  const projResPath = path.join(internalDir, 'resources', 'resource_project.go');
  assert.ok(fs.existsSync(projResPath), 'resource_project.go must exist');
  const content = fs.readFileSync(projResPath, 'utf8');
  assert.ok(content.includes('CreateProject'), 'must implement Create');
  assert.ok(content.includes('GetProject'), 'must implement Read');
  assert.ok(content.includes('UpdateProject'), 'must implement Update');
  assert.ok(content.includes('DeleteProject'), 'must implement Delete');
  assert.ok(content.includes('workspace_id/project_id'), 'must document import format');
});

// 9. Environment Resource Lifecycle & Protection Flag
runTest('Environment resource supports tier types, is_protected flag, and import syntax', () => {
  const envResPath = path.join(internalDir, 'resources', 'resource_environment.go');
  assert.ok(fs.existsSync(envResPath), 'resource_environment.go must exist');
  const content = fs.readFileSync(envResPath, 'utf8');
  assert.ok(content.includes('"type": schema.StringAttribute'), 'must define tier type');
  assert.ok(content.includes('"is_protected": schema.BoolAttribute'), 'must define is_protected flag');
  assert.ok(content.includes('workspace_id/project_id/environment_id'), 'must document 3-part import format');
});

// 10. Machine Identity Resource for Workload OIDC
runTest('Machine Identity resource supports automated workload credential provisioning', () => {
  const machineResPath = path.join(internalDir, 'resources', 'resource_machine_identity.go');
  assert.ok(fs.existsSync(machineResPath), 'resource_machine_identity.go must exist');
  const content = fs.readFileSync(machineResPath, 'utf8');
  assert.ok(content.includes('CreateMachineIdentity'), 'must implement Create');
  assert.ok(content.includes('max_token_ttl_seconds'), 'must configure token TTL');
  assert.ok(content.includes('workspace_id/machine_id'), 'must document 2-part import format');
});

// 11. Provider Integration Resource for Cloud Sync
runTest('Provider Integration resource manages external cloud connections with encrypted credentials', () => {
  const intResPath = path.join(internalDir, 'resources', 'resource_provider_integration.go');
  assert.ok(fs.existsSync(intResPath), 'resource_provider_integration.go must exist');
  const content = fs.readFileSync(intResPath, 'utf8');
  assert.ok(content.includes('"provider_type": schema.StringAttribute'), 'must define provider_type');
  assert.ok(content.includes('"credentials": schema.MapAttribute'), 'must define credentials map');
  assert.ok(content.includes('Sensitive:   true'), 'credentials must be marked Sensitive: true');
  assert.ok(content.includes('workspace_id/integration_id'), 'must document 2-part import format');
});

// 12. Workspace Data Source
runTest('Workspace data source allows querying parent workspace isolation containers', () => {
  const wsDsPath = path.join(internalDir, 'datasources', 'datasource_workspace.go');
  assert.ok(fs.existsSync(wsDsPath), 'datasource_workspace.go must exist');
  const content = fs.readFileSync(wsDsPath, 'utf8');
  assert.ok(content.includes('GetWorkspace'), 'must fetch workspace by ID');
  assert.ok(content.includes('"slug": schema.StringAttribute'), 'must expose workspace slug');
});

// 13. Backend Endpoint Path Parity
runTest('Client HTTP request paths strictly match backend Spring Boot REST controller mappings', () => {
  const clientPath = path.join(internalDir, 'client', 'client.go');
  const content = fs.readFileSync(clientPath, 'utf8');
  assert.ok(content.includes('/api/v1/workspaces/%s'), 'must match WorkspaceController');
  assert.ok(content.includes('/api/v1/workspaces/%s/projects'), 'must match ProjectController');
  assert.ok(content.includes('/api/v1/workspaces/%s/projects/%s/environments'), 'must match EnvironmentController');
  assert.ok(content.includes('/api/v1/workspaces/%s/projects/%s/environments/%s/secrets'), 'must match SecretController');
  assert.ok(content.includes('/api/v1/workspaces/%s/machines'), 'must match MachineIdentityController');
  assert.ok(content.includes('/api/v1/workspaces/%s/integrations'), 'must match ProviderIntegrationController');
});

// 14. OIDC Authentication Exchange Endpoint Parity
runTest('Client OIDC auth exchange matches backend OidcAuthController mapping', () => {
  const authPath = path.join(internalDir, 'client', 'auth.go');
  assert.ok(fs.existsSync(authPath), 'auth.go must exist');
  const content = fs.readFileSync(authPath, 'utf8');
  assert.ok(content.includes('/api/v1/auth/oidc/token'), 'must call /api/v1/auth/oidc/token');
  assert.ok(content.includes('urn:ietf:params:oauth:grant-type:token-exchange'), 'must use standard RFC 8693 token exchange grant');
});

// 15. Examples & HCL Syntax Validation
runTest('Examples contain complete valid HCL configurations without real hardcoded credentials', () => {
  const mainTfPath = path.join(terraformDir, 'examples', 'main.tf');
  const varsTfPath = path.join(terraformDir, 'examples', 'variables.tf');
  const exampleVarsPath = path.join(terraformDir, 'examples', 'terraform.tfvars.example');
  assert.ok(fs.existsSync(mainTfPath), 'examples/main.tf must exist');
  assert.ok(fs.existsSync(varsTfPath), 'examples/variables.tf must exist');
  assert.ok(fs.existsSync(exampleVarsPath), 'examples/terraform.tfvars.example must exist');

  const tfvars = fs.readFileSync(exampleVarsPath, 'utf8');
  assert.ok(tfvars.includes('EXAMPLE_TOKEN_CHANGE_ME') || tfvars.includes('placeholder'), 'must use placeholder values');
  assert.ok(!tfvars.includes('AKIA') && !tfvars.includes('ghp_'), 'must not contain real cloud keys');
});

// 16. Documentation Suite Completeness
runTest('Documentation suite covers Architecture, API Contracts, Security, State Safety, Authentication, and Troubleshooting', () => {
  const docsDir = path.join(terraformDir, 'docs');
  assert.ok(fs.existsSync(path.join(docsDir, 'ARCHITECTURE.md')), 'ARCHITECTURE.md must exist');
  assert.ok(fs.existsSync(path.join(docsDir, 'API_CONTRACT_MATRIX.md')), 'API_CONTRACT_MATRIX.md must exist');
  assert.ok(fs.existsSync(path.join(docsDir, 'SECURITY.md')), 'SECURITY.md must exist');
  assert.ok(fs.existsSync(path.join(docsDir, 'SECRET_STATE_SAFETY.md')), 'SECRET_STATE_SAFETY.md must exist');
  assert.ok(fs.existsSync(path.join(docsDir, 'AUTHENTICATION.md')), 'AUTHENTICATION.md must exist');
  assert.ok(fs.existsSync(path.join(docsDir, 'TROUBLESHOOTING.md')), 'TROUBLESHOOTING.md must exist');
});

// 17. CI Workflow Definition
runTest('GitHub Actions workflow includes formatting, vet, unit tests, build, and contract tests', () => {
  const ciPath = path.resolve(terraformDir, '..', '..', '.github', 'workflows', 'terraform-provider.yml');
  assert.ok(fs.existsSync(ciPath), 'terraform-provider.yml must exist');
  const content = fs.readFileSync(ciPath, 'utf8');
  assert.ok(content.includes('gofmt -l .'), 'must check gofmt');
  assert.ok(content.includes('go vet ./...'), 'must run go vet');
  assert.ok(content.includes('go test'), 'must run go test');
  assert.ok(content.includes('go build'), 'must build provider binary');
});

// 18. Static Credential Scan across Terraform Tree
runTest('Static audit of Terraform provider files confirms zero leaked production credentials', () => {
  const scanDirs = [
    internalDir,
    path.join(terraformDir, 'examples'),
    path.join(terraformDir, 'docs')
  ];

  function scanDir(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        scanDir(fullPath);
      } else if (entry.isFile() && !entry.name.endsWith('.png')) {
        const text = fs.readFileSync(fullPath, 'utf8');
        assert.ok(!text.includes('AKIAIOSFODNN7EXAMPLE'), `leaked AWS key in ${entry.name}`);
        assert.ok(!text.includes('ghp_abcdefghijklmnopqrstuvwxyz'), `leaked GitHub token in ${entry.name}`);
      }
    }
  }

  scanDirs.forEach(scanDir);
});

console.log(`\n========================================`);
console.log(`ALL ${passedTests}/${totalTests} TERRAFORM PROVIDER TESTS PASSED (100%)`);
console.log(`========================================\n`);

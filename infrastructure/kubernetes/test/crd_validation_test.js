/**
 * Kubernetes CRD Contract & OpenAPI v3 Schema Validation Test Suite
 * Validates SecretVaultSecret and SecretVaultSync CustomResourceDefinitions.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('Running SecretVault Kubernetes CRD Validation Tests...\n');

const crdDir = path.resolve(__dirname, '..', 'config', 'crd', 'bases');
const samplesDir = path.resolve(__dirname, '..', 'config', 'samples');

const svsCrdPath = path.join(crdDir, 'secretvault.io_secretvaultsecrets.yaml');
const svsyncCrdPath = path.join(crdDir, 'secretvault.io_secretvaultsyncs.yaml');

assert(fs.existsSync(svsCrdPath), `SecretVaultSecret CRD file must exist at ${svsCrdPath}`);
assert(fs.existsSync(svsyncCrdPath), `SecretVaultSync CRD file must exist at ${svsyncCrdPath}`);

const svsContent = fs.readFileSync(svsCrdPath, 'utf8');
const svsyncContent = fs.readFileSync(svsyncCrdPath, 'utf8');

// Test 1: CRD Metadata & Group/Version validation
console.log('--- Test 1: CRD Group, Version & Kind Validation ---');
assert(svsContent.includes('group: secretvault.io'), 'SecretVaultSecret group must be secretvault.io');
assert(svsContent.includes('name: v1alpha1'), 'SecretVaultSecret version must be v1alpha1');
assert(svsContent.includes('kind: SecretVaultSecret'), 'SecretVaultSecret kind must be SecretVaultSecret');
assert(svsContent.includes('shortNames:\n      - svs'), 'SecretVaultSecret shortName must be svs');

assert(svsyncContent.includes('group: secretvault.io'), 'SecretVaultSync group must be secretvault.io');
assert(svsyncContent.includes('name: v1alpha1'), 'SecretVaultSync version must be v1alpha1');
assert(svsyncContent.includes('kind: SecretVaultSync'), 'SecretVaultSync kind must be SecretVaultSync');
assert(svsyncContent.includes('shortNames:\n      - svsync'), 'SecretVaultSync shortName must be svsync');
console.log('✔ Test 1 passed: API Group, Version, and Kinds are correct.');

// Test 2: Required Fields in Spec
console.log('--- Test 2: Required Specification Fields ---');
const svsRequiredMatches = svsContent.match(/required:\s*\n\s*-\s*workspace\s*\n\s*-\s*project\s*\n\s*-\s*environment\s*\n\s*-\s*secretName/);
assert(svsRequiredMatches, 'SecretVaultSecret must enforce workspace, project, environment, and secretName as required');

const svsyncRequiredMatches = svsyncContent.match(/required:\s*\n\s*-\s*workspace\s*\n\s*-\s*project\s*\n\s*-\s*environment\s*\n\s*-\s*target/);
assert(svsyncRequiredMatches, 'SecretVaultSync must enforce workspace, project, environment, and target as required');
console.log('✔ Test 2 passed: Required fields in spec are strictly enforced.');

// Test 3: Zero-Plaintext Security Invariant
console.log('--- Test 3: Zero-Plaintext & No-Credential Security Invariant ---');
const prohibitedFields = [
    'value:',
    'secretValue:',
    'plaintext:',
    'rawSecret:',
    'password:',
    'apiToken:',
    'refreshToken:',
    'accessToken:',
    'jwtToken:',
    'masterKey:',
    'privateKey:',
    'dek:'
];

for (const field of prohibitedFields) {
    assert(!svsContent.includes(field), `SecretVaultSecret CRD schema must NEVER contain sensitive field: '${field}'`);
    assert(!svsyncContent.includes(field), `SecretVaultSync CRD schema must NEVER contain sensitive field: '${field}'`);
}
console.log('✔ Test 3 passed: No plaintext secrets, tokens, or credential fields exist in CRD schemas.');

// Test 4: Enums and Constraints
console.log('--- Test 4: Enum Validation & String Constraints ---');
assert(svsContent.includes('- LATEST\n                    - PINNED'), 'SecretVaultSecret must validate VersionPolicy enum');
assert(svsContent.includes('- Owner\n                        - Merge\n                        - None'), 'SecretVaultSecret must validate CreationPolicy enum');
assert(svsContent.includes('- CONTAINER\n                        - SERVICE\n                        - WORKER\n                        - JOB'), 'SecretVaultSecret must validate ConsumerType enum');

assert(svsyncContent.includes('- RestartWorkload\n                        - NotifyOnly\n                        - SyncOnly'), 'SecretVaultSync must validate RotationAction enum');
assert(svsyncContent.includes('- Enforce\n                    - DetectOnly\n                    - Ignore'), 'SecretVaultSync must validate DriftPolicy enum');
console.log('✔ Test 4 passed: Enums and structural constraints are properly configured.');

// Test 5: Status Schema & Conditions
console.log('--- Test 5: Status Schema & Conditions ---');
assert(svsContent.includes('conditions:'), 'SecretVaultSecret status must support Kubernetes conditions');
assert(svsContent.includes('observedGeneration:'), 'SecretVaultSecret status must contain observedGeneration');
assert(svsContent.includes('secretFingerprint:'), 'SecretVaultSecret status must support non-sensitive fingerprint');
assert(svsContent.includes('leaseId:'), 'SecretVaultSecret status must support leaseId');

assert(svsyncContent.includes('conditions:'), 'SecretVaultSync status must support Kubernetes conditions');
assert(svsyncContent.includes('syncedSecretCount:'), 'SecretVaultSync status must report syncedSecretCount');
assert(svsyncContent.includes('driftDetected:'), 'SecretVaultSync status must report driftDetected');
console.log('✔ Test 5 passed: Status subresources adhere to Kubernetes API conventions.');

// Test 6: Printer Columns
console.log('--- Test 6: Additional Printer Columns ---');
assert(svsContent.includes('name: WORKSPACE'), 'SecretVaultSecret must have WORKSPACE printer column');
assert(svsContent.includes('name: PROJECT'), 'SecretVaultSecret must have PROJECT printer column');
assert(svsContent.includes('name: SECRET'), 'SecretVaultSecret must have SECRET printer column');
assert(svsContent.includes('name: SYNCED'), 'SecretVaultSecret must have SYNCED printer column');

assert(svsyncContent.includes('name: TARGET'), 'SecretVaultSync must have TARGET printer column');
assert(svsyncContent.includes('name: SECRETS'), 'SecretVaultSync must have SECRETS printer column');
assert(svsyncContent.includes('name: DRIFT'), 'SecretVaultSync must have DRIFT printer column');
console.log('✔ Test 6 passed: Printer columns properly configured for kubectl get.');

// Test 7: Validate Sample Manifests
console.log('--- Test 7: Sample Manifest Validation ---');
const samples = [
    'secretvault_v1alpha1_secretvaultsecret.yaml',
    'secretvault_v1alpha1_secretvaultsecret_pinned.yaml',
    'secretvault_v1alpha1_secretvaultsync.yaml',
    'secretvault_v1alpha1_secretvaultsync_filtered.yaml'
];

for (const sample of samples) {
    const samplePath = path.join(samplesDir, sample);
    assert(fs.existsSync(samplePath), `Sample manifest must exist: ${sample}`);
    const sampleContent = fs.readFileSync(samplePath, 'utf8');
    assert(sampleContent.includes('apiVersion: secretvault.io/v1alpha1'), `Sample ${sample} must use apiVersion secretvault.io/v1alpha1`);
    assert(sampleContent.includes('metadata:'), `Sample ${sample} must contain metadata`);
    assert(sampleContent.includes('spec:'), `Sample ${sample} must contain spec`);
    assert(sampleContent.includes('workspace: default'), `Sample ${sample} must specify workspace`);
    assert(sampleContent.includes('project:'), `Sample ${sample} must specify project`);
    assert(sampleContent.includes('environment:'), `Sample ${sample} must specify environment`);

    // Verify zero plaintext secrets in sample files
    for (const field of prohibitedFields) {
        assert(!sampleContent.includes(field), `Sample ${sample} must NEVER contain plaintext secret field: '${field}'`);
    }
}
console.log('✔ Test 7 passed: All sample manifests are valid and conform strictly to security invariants.');

console.log('\n========================================');
console.log('ALL 7 KUBERNETES CRD TESTS PASSED (100%)');
console.log('========================================\n');

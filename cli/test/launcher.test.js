const assert = require('assert');
const { execSync, spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

console.log('Running @secretvault/cli launcher tests...');

const binScript = path.resolve(__dirname, '..', 'bin', 'secretvault.js');
assert(fs.existsSync(binScript), 'bin/secretvault.js must exist');

// 1. Test Launcher Version output
const versionResult = spawnSync('node', [binScript, 'version'], { encoding: 'utf8' });
assert.strictEqual(versionResult.status, 0, `Expected exit code 0, got: ${versionResult.status}. Output: ${versionResult.stderr}`);
assert(versionResult.stdout.includes('SecretVault CLI'), 'Output must contain SecretVault CLI');
console.log('✔ Test 1 passed: secretvault version executes via launcher');

// 2. Test Launcher Help output
const helpResult = spawnSync('node', [binScript, '--help'], { encoding: 'utf8' });
assert.strictEqual(helpResult.status, 0, `Expected exit code 0, got: ${helpResult.status}`);
assert(helpResult.stdout.includes('Usage: secretvault'), 'Output must contain Usage string');
assert(helpResult.stdout.includes('auth'), 'Output must list auth command');
assert(helpResult.stdout.includes('run'), 'Output must list run command');
assert(helpResult.stdout.includes('secret'), 'Output must list secret command');
assert(helpResult.stdout.includes('machine'), 'Output must list machine command');
console.log('✔ Test 2 passed: secretvault --help renders complete command tree');

// 3. Test Doctor Command
const doctorResult = spawnSync('node', [binScript, 'doctor'], { encoding: 'utf8' });
assert(doctorResult.stdout.includes('SecretVault Doctor') || doctorResult.stdout.includes('Diagnostic'), 'Doctor must execute');
console.log('✔ Test 3 passed: secretvault doctor runs diagnostic suite');

console.log('\nAll launcher tests PASSED successfully!');

import { test, describe } from 'node:test';
import assert from 'node:assert';
import { SecretVaultClient } from '../src/client.js';
import { SecretCache } from '../src/cache.js';
import { SecretNotFoundError, AuthenticationError } from '../src/errors.js';

describe('SecretVault JS SDK Unit Tests', () => {
  test('SecretCache stores and expires entries with TTL', async () => {
    const cache = new SecretCache({ ttlMs: 50, maxStaleMs: 200 });
    cache.set('DB_PASS', { value: 'secret123' });

    // Immediate lookup
    const immediate = cache.get('DB_PASS');
    assert.strictEqual(immediate.isStale, false);
    assert.strictEqual(immediate.value.value, 'secret123');

    // Wait past TTL
    await new Promise((r) => setTimeout(r, 60));
    const stale = cache.get('DB_PASS');
    assert.strictEqual(stale.isStale, true);
    assert.strictEqual(stale.value.value, 'secret123');

    // Invalidation
    cache.invalidate('DB_PASS');
    assert.strictEqual(cache.get('DB_PASS'), null);
  });

  test('SecretVaultClient initializes with defaults and exposes secrets API', () => {
    const client = new SecretVaultClient({
      endpoint: 'http://localhost:8080',
      workspace: 'myspace',
      project: 'test-app',
      environment: 'production',
      token: 'test_token_123'
    });

    assert.strictEqual(client.endpoint, 'http://localhost:8080');
    assert.strictEqual(client.workspace, 'myspace');
    assert.strictEqual(client.project, 'test-app');
    assert.strictEqual(client.environment, 'production');
    assert.strictEqual(typeof client.secrets.get, 'function');
    assert.strictEqual(typeof client.secrets.getMany, 'function');
    assert.strictEqual(typeof client.secrets.list, 'function');
    assert.strictEqual(typeof client.secrets.watch, 'function');
    assert.strictEqual(typeof client.ping, 'function');
  });

  test('Error hierarchy conforms to SecretVault standards', () => {
    const notFound = new SecretNotFoundError('API_KEY');
    assert.strictEqual(notFound.status, 404);
    assert.strictEqual(notFound.secretName, 'API_KEY');
    assert.match(notFound.message, /API_KEY/);

    const authErr = new AuthenticationError();
    assert.strictEqual(authErr.status, 401);
  });
});

import { describe, it, expect, vi } from 'vitest';
import {
  isWebAuthnSupported,
  base64UrlToUint8Array,
  arrayBufferToBase64Url,
  prepareCreationOptions,
  prepareRequestOptions,
  credentialCreationToJSON,
  credentialRequestToJSON,
  formatWebAuthnError,
} from '../utils/webauthn';

describe('WebAuthn Utility Tests', () => {
  it('converts between Base64URL and Uint8Array correctly', () => {
    const rawString = 'Hello-WebAuthn_2026';
    const uint8 = new Uint8Array([...rawString].map((c) => c.charCodeAt(0)));
    const base64Url = arrayBufferToBase64Url(uint8);

    expect(base64Url).toBeDefined();
    expect(base64Url).not.toContain('+');
    expect(base64Url).not.toContain('/');
    expect(base64Url).not.toContain('=');

    const decoded = base64UrlToUint8Array(base64Url);
    expect(decoded).toEqual(uint8);
  });

  it('handles empty or null Base64URL values gracefully', () => {
    expect(base64UrlToUint8Array(null)).toEqual(new Uint8Array(0));
    expect(base64UrlToUint8Array('')).toEqual(new Uint8Array(0));
    expect(arrayBufferToBase64Url(null)).toBe('');
  });

  it('prepares creation options by converting challenge, user.id, and excludeCredentials to Uint8Array', () => {
    const serverOptions = {
      publicKey: {
        rp: { name: 'SecretVault', id: 'localhost' },
        user: { id: 'dXNlcl9pZF8xMjM', name: 'user@example.com', displayName: 'User' },
        challenge: 'Y2hhbGxlbmdlXzEyMw',
        pubKeyCredParams: [{ alg: -7, type: 'public-key' }],
        excludeCredentials: [
          { id: 'ZXhjbHVkZV8x', type: 'public-key' },
        ],
      },
    };

    const prepared = prepareCreationOptions(serverOptions);
    expect(prepared.publicKey.challenge instanceof Uint8Array).toBe(true);
    expect(prepared.publicKey.user.id instanceof Uint8Array).toBe(true);
    expect(prepared.publicKey.excludeCredentials[0].id instanceof Uint8Array).toBe(true);
  });

  it('prepares request options by converting challenge and allowCredentials to Uint8Array', () => {
    const serverOptions = {
      publicKey: {
        rpId: 'localhost',
        challenge: 'Y2hhbGxlbmdlXzEyMw',
        allowCredentials: [
          { id: 'Y3JlZF8x', type: 'public-key' },
        ],
      },
    };

    const prepared = prepareRequestOptions(serverOptions);
    expect(prepared.publicKey.challenge instanceof Uint8Array).toBe(true);
    expect(prepared.publicKey.allowCredentials[0].id instanceof Uint8Array).toBe(true);
  });

  it('formats credential creation response to JSON', () => {
    const mockCred = {
      id: 'cred_id_1',
      rawId: new Uint8Array([1, 2, 3]).buffer,
      type: 'public-key',
      response: {
        clientDataJSON: new Uint8Array([4, 5, 6]).buffer,
        attestationObject: new Uint8Array([7, 8, 9]).buffer,
        getTransports: () => ['internal', 'hybrid'],
      },
      getClientExtensionResults: () => ({ credProps: { rk: true } }),
    };

    const jsonStr = credentialCreationToJSON(mockCred);
    const parsed = JSON.parse(jsonStr);

    expect(parsed.id).toBe('cred_id_1');
    expect(parsed.type).toBe('public-key');
    expect(parsed.response.clientDataJSON).toBe(arrayBufferToBase64Url(new Uint8Array([4, 5, 6])));
    expect(parsed.response.attestationObject).toBe(arrayBufferToBase64Url(new Uint8Array([7, 8, 9])));
    expect(parsed.response.transports).toEqual(['internal', 'hybrid']);
    expect(parsed.clientExtensionResults.credProps.rk).toBe(true);
  });

  it('formats credential assertion request to JSON', () => {
    const mockAssertion = {
      id: 'assertion_id_1',
      rawId: new Uint8Array([10, 20, 30]).buffer,
      type: 'public-key',
      response: {
        clientDataJSON: new Uint8Array([1, 2]).buffer,
        authenticatorData: new Uint8Array([3, 4]).buffer,
        signature: new Uint8Array([5, 6]).buffer,
        userHandle: new Uint8Array([7, 8]).buffer,
      },
      getClientExtensionResults: () => ({}),
    };

    const jsonStr = credentialRequestToJSON(mockAssertion);
    const parsed = JSON.parse(jsonStr);

    expect(parsed.id).toBe('assertion_id_1');
    expect(parsed.type).toBe('public-key');
    expect(parsed.response.clientDataJSON).toBe(arrayBufferToBase64Url(new Uint8Array([1, 2])));
    expect(parsed.response.signature).toBe(arrayBufferToBase64Url(new Uint8Array([5, 6])));
  });

  it('formats WebAuthn DOMException errors into user-friendly messages', () => {
    const notAllowed = new DOMException('User cancelled', 'NotAllowedError');
    expect(formatWebAuthnError(notAllowed)).toContain('cancelled or timed out');

    const invalidState = new DOMException('Already registered', 'InvalidStateError');
    expect(formatWebAuthnError(invalidState)).toContain('already registered');

    const notSupported = new DOMException('Not supported', 'NotSupportedError');
    expect(formatWebAuthnError(notSupported)).toContain('not supported');

    const securityErr = new DOMException('Origin mismatch', 'SecurityError');
    expect(formatWebAuthnError(securityErr)).toContain('Security verification failed');

    expect(formatWebAuthnError('Simple string error')).toBe('Simple string error');
    expect(formatWebAuthnError(null)).toBe('An unexpected WebAuthn error occurred.');
  });
});

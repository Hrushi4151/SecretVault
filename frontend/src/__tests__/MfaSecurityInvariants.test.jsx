import { describe, it, expect, beforeEach } from 'vitest';
import { apiClient } from '../api/client';

describe('MFA Security Invariants & Storage Prohibitions', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('prohibits TOTP secrets, recovery codes, and challenge payloads in localStorage and sessionStorage', () => {
    // Normal authenticated session only stores access and refresh tokens
    apiClient.setSession({
      accessToken: 'sample-jwt-access-token',
      refreshToken: 'sample-refresh-token',
      activeWorkspaceId: 'workspace-1234',
    });

    const localStore = localStorage._getStore ? localStorage._getStore() : localStorage;
    const sessionStore = sessionStorage._getStore ? sessionStorage._getStore() : sessionStorage;

    const localKeys = Object.keys(localStore);
    const sessionKeys = Object.keys(sessionStore);

    const forbiddenPatterns = [
      'secret',
      'totp',
      'recovery',
      'challenge',
      'otp',
      'provisioning',
      'qr',
      'password',
    ];

    // Check localStorage
    for (const key of localKeys) {
      const lowerKey = key.toLowerCase();
      const val = (localStorage.getItem(key) || '').toLowerCase();
      for (const pattern of forbiddenPatterns) {
        expect(lowerKey).not.toContain(pattern);
        expect(val).not.toContain('otpauth://');
        expect(val).not.toContain('jbswy3'); // Base32 test vectors
      }
    }

    // Check sessionStorage
    for (const key of sessionKeys) {
      const lowerKey = key.toLowerCase();
      const val = (sessionStorage.getItem(key) || '').toLowerCase();
      for (const pattern of forbiddenPatterns) {
        expect(lowerKey).not.toContain(pattern);
        expect(val).not.toContain('otpauth://');
      }
    }
  });

  it('clears session tokens completely on logout without lingering artifacts', () => {
    apiClient.setSession({
      accessToken: 'sample-jwt',
      refreshToken: 'sample-refresh',
      activeWorkspaceId: 'ws-1',
    });

    expect(apiClient.getAccessToken()).toBe('sample-jwt');
    expect(apiClient.getRefreshToken()).toBe('sample-refresh');

    // Logout
    apiClient.setSession(null);

    expect(apiClient.getAccessToken()).toBeNull();
    expect(apiClient.getRefreshToken()).toBeNull();
    expect(localStorage.getItem('sv_access_token')).toBeNull();
    expect(localStorage.getItem('sv_refresh_token')).toBeNull();
  });
});

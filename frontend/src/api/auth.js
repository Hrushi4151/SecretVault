import { apiClient } from './client';

export const authApi = {
  register: async (payload) => {
    return apiClient.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  login: async (payload) => {
    return apiClient.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  refresh: async (payload) => {
    return apiClient.request('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getCurrentUser: async () => {
    return apiClient.request('/auth/me', {
      method: 'GET',
    });
  },

  logout: async () => {
    return apiClient.request('/auth/logout', {
      method: 'POST',
    });
  },

  getMfaStatus: async () => {
    return apiClient.request('/auth/mfa/status', {
      method: 'GET',
    });
  },

  enrollMfa: async () => {
    return apiClient.request('/auth/mfa/enroll', {
      method: 'POST',
    });
  },

  activateMfa: async ({ code }) => {
    return apiClient.request('/auth/mfa/activate', {
      method: 'POST',
      body: JSON.stringify({ code }),
    });
  },

  verifyMfaTotp: async ({ challengeId, code }) => {
    return apiClient.request('/auth/mfa/verify-totp', {
      method: 'POST',
      body: JSON.stringify({ challengeId, code }),
    });
  },

  verifyMfaRecovery: async ({ challengeId, recoveryCode }) => {
    return apiClient.request('/auth/mfa/verify-recovery', {
      method: 'POST',
      body: JSON.stringify({ challengeId, recoveryCode }),
    });
  },

  disableMfa: async ({ password, code, recoveryCode }) => {
    const body = { password };
    if (code) body.code = code;
    if (recoveryCode) body.recoveryCode = recoveryCode;
    return apiClient.request('/auth/mfa/disable', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  getSessions: async () => {
    return apiClient.request('/auth/sessions', {
      method: 'GET',
    });
  },

  revokeSession: async (sessionId) => {
    return apiClient.request(`/auth/sessions/${encodeURIComponent(sessionId)}`, {
      method: 'DELETE',
    });
  },

  revokeOtherSessions: async () => {
    return apiClient.request('/auth/sessions/revoke-others', {
      method: 'POST',
    });
  },

  revokeAllSessions: async () => {
    return apiClient.request('/auth/sessions/revoke-all', {
      method: 'POST',
    });
  },

  createStepUpChallenge: async ({ action, context }) => {
    return apiClient.request('/auth/step-up/challenges', {
      method: 'POST',
      body: JSON.stringify({ action, context }),
    });
  },

  verifyStepUpPassword: async ({ challengeId, password }) => {
    return apiClient.request(`/auth/step-up/challenges/${encodeURIComponent(challengeId)}/verify-password`, {
      method: 'POST',
      body: JSON.stringify({ password }),
    });
  },

  verifyStepUpTotp: async ({ challengeId, code }) => {
    return apiClient.request(`/auth/step-up/challenges/${encodeURIComponent(challengeId)}/verify-totp`, {
      method: 'POST',
      body: JSON.stringify({ code }),
    });
  },

  verifyStepUpRecoveryCode: async ({ challengeId, recoveryCode }) => {
    return apiClient.request(`/auth/step-up/challenges/${encodeURIComponent(challengeId)}/verify-recovery-code`, {
      method: 'POST',
      body: JSON.stringify({ recoveryCode }),
    });
  },

  getStepUpWebAuthnOptions: async (challengeId) => {
    return apiClient.request(`/auth/step-up/challenges/${encodeURIComponent(challengeId)}/webauthn/options`, {
      method: 'POST',
    });
  },

  verifyStepUpWebAuthn: async ({ challengeId, credentialJson }) => {
    return apiClient.request(`/auth/step-up/challenges/${encodeURIComponent(challengeId)}/verify-webauthn`, {
      method: 'POST',
      body: JSON.stringify({ credentialJson }),
    });
  },

  // WebAuthn / Passkeys
  getWebAuthnRegistrationOptions: async (friendlyName) => {
    const params = friendlyName ? `?friendlyName=${encodeURIComponent(friendlyName)}` : '';
    return apiClient.request(`/auth/webauthn/registration/options${params}`, {
      method: 'POST',
    });
  },

  verifyWebAuthnRegistration: async ({ challengeId, friendlyName, credentialJson }) => {
    return apiClient.request('/auth/webauthn/registration/verify', {
      method: 'POST',
      body: JSON.stringify({ challengeId, friendlyName, credentialJson }),
    });
  },

  getWebAuthnAuthenticationOptions: async (email) => {
    return apiClient.request('/auth/webauthn/authentication/options', {
      method: 'POST',
      body: email ? JSON.stringify({ email }) : undefined,
    });
  },

  verifyWebAuthnAuthentication: async ({ challengeId, credentialJson }) => {
    return apiClient.request('/auth/webauthn/authentication/verify', {
      method: 'POST',
      body: JSON.stringify({ challengeId, credentialJson }),
    });
  },

  getWebAuthnCredentials: async () => {
    return apiClient.request('/auth/webauthn/credentials', {
      method: 'GET',
    });
  },

  renameWebAuthnCredential: async ({ credentialId, friendlyName }) => {
    return apiClient.request(`/auth/webauthn/credentials/${encodeURIComponent(credentialId)}`, {
      method: 'PATCH',
      body: JSON.stringify({ friendlyName }),
    });
  },

  revokeWebAuthnCredential: async (credentialId) => {
    return apiClient.request(`/auth/webauthn/credentials/${encodeURIComponent(credentialId)}`, {
      method: 'DELETE',
    });
  },
};

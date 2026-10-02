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
};

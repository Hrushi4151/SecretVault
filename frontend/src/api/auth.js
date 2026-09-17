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
};

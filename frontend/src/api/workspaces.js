import { apiClient } from './client';

export const workspaceApi = {
  list: async () => {
    return apiClient.request('/workspaces', {
      method: 'GET',
    });
  },

  create: async (payload) => {
    return apiClient.request('/workspaces', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getById: async (id) => {
    return apiClient.request(`/workspaces/${id}`, {
      method: 'GET',
    });
  },

  addMember: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/members`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  listMembers: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/members`, {
      method: 'GET',
    });
  },
};

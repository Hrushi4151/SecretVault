import { apiClient } from './client';

export const projectApi = {
  list: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects`, {
      method: 'GET',
    });
  },

  create: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getById: async (workspaceId, projectId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}`, {
      method: 'GET',
    });
  },

  update: async (workspaceId, projectId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  delete: async (workspaceId, projectId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}`, {
      method: 'DELETE',
    });
  },
};

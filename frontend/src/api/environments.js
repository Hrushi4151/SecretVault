import { apiClient } from './client';

export const environmentApi = {
  list: async (workspaceId, projectId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/environments`, {
      method: 'GET',
    });
  },

  create: async (workspaceId, projectId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/environments`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getById: async (workspaceId, projectId, environmentId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}`, {
      method: 'GET',
    });
  },

  update: async (workspaceId, projectId, environmentId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  delete: async (workspaceId, projectId, environmentId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}`, {
      method: 'DELETE',
    });
  },
};

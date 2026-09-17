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

  listMembers: async (workspaceId, projectId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/members`, {
      method: 'GET',
    });
  },

  grantMemberAccess: async (workspaceId, projectId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/members`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  updateMemberAccess: async (workspaceId, projectId, userId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/members/${userId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  removeMemberAccess: async (workspaceId, projectId, userId) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/members/${userId}`, {
      method: 'DELETE',
    });
  },
};

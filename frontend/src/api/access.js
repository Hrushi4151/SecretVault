import { apiClient } from './client';

export const accessApi = {
  listGrants(workspaceId) {
    return apiClient.request(`/workspaces/${workspaceId}/access/grants`, {
      method: 'GET',
    });
  },

  createGrant(workspaceId, grantData) {
    return apiClient.request(`/workspaces/${workspaceId}/access/grants`, {
      method: 'POST',
      body: JSON.stringify(grantData),
    });
  },

  revokeGrant(workspaceId, grantId) {
    return apiClient.request(`/workspaces/${workspaceId}/access/grants/${grantId}`, {
      method: 'DELETE',
    });
  },

  getEffectivePermissions(workspaceId, params = {}) {
    const query = new URLSearchParams();
    if (params.userId) query.append('userId', params.userId);
    if (params.projectId) query.append('projectId', params.projectId);
    if (params.environmentId) query.append('environmentId', params.environmentId);
    if (params.secretId) query.append('secretId', params.secretId);
    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient.request(`/workspaces/${workspaceId}/access/effective${qs}`, {
      method: 'GET',
    });
  },

  getMemberAccess(workspaceId, userId) {
    return apiClient.request(`/workspaces/${workspaceId}/members/${userId}/access`, {
      method: 'GET',
    });
  },

  updateMemberAccess(workspaceId, userId, payload) {
    return apiClient.request(`/workspaces/${workspaceId}/members/${userId}/access`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },
};

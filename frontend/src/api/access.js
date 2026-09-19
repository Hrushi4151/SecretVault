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
    if (params.projectId) query.append('projectId', params.projectId);
    if (params.environmentId) query.append('environmentId', params.environmentId);
    if (params.secretId) query.append('secretId', params.secretId);
    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient.request(`/workspaces/${workspaceId}/access/effective${qs}`, {
      method: 'GET',
    });
  },
};

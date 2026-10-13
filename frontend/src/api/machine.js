import { apiClient } from './client';

export const machineApi = {
  list(workspaceId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities`, {
      method: 'GET',
    });
  },

  get(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}`, {
      method: 'GET',
    });
  },

  create(workspaceId, data) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  update(workspaceId, machineId, data) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  },

  disable(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/disable`, {
      method: 'POST',
    });
  },

  enable(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/enable`, {
      method: 'POST',
    });
  },

  revoke(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/revoke`, {
      method: 'POST',
    });
  },

  delete(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}`, {
      method: 'DELETE',
    });
  },

  listGrants(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/grants`, {
      method: 'GET',
    });
  },

  createGrant(workspaceId, machineId, grantData) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/grants`, {
      method: 'POST',
      body: JSON.stringify(grantData),
    });
  },

  revokeGrant(workspaceId, machineId, grantId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/grants/${grantId}`, {
      method: 'DELETE',
    });
  },

  listSessions(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/sessions`, {
      method: 'GET',
    });
  },

  revokeSession(workspaceId, machineId, sessionId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/sessions/${sessionId}/revoke`, {
      method: 'POST',
    });
  },

  revokeAllSessions(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/sessions/revoke-all`, {
      method: 'POST',
    });
  },

  whyAccess(workspaceId, machineId, params = {}) {
    const query = new URLSearchParams(params).toString();
    const endpoint = `/workspaces/${workspaceId}/machine-identities/${machineId}/why-access${query ? `?${query}` : ''}`;
    return apiClient.request(endpoint, {
      method: 'GET',
    });
  },

  generateToken(workspaceId, machineId, data = {}) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/tokens`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },
};

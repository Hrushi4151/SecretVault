import { apiClient } from './client';

export const jitApi = {
  listRequests(workspaceId, status = null) {
    const qs = status ? `?status=${status}` : '';
    return apiClient.request(`/workspaces/${workspaceId}/jit/requests${qs}`, {
      method: 'GET',
    });
  },

  getActiveGrants(workspaceId) {
    return apiClient.request(`/workspaces/${workspaceId}/jit/active`, {
      method: 'GET',
    });
  },

  submitRequest(workspaceId, requestData) {
    return apiClient.request(`/workspaces/${workspaceId}/jit/requests`, {
      method: 'POST',
      body: JSON.stringify(requestData),
    });
  },

  approveRequest(workspaceId, requestId, body = {}) {
    return apiClient.request(`/workspaces/${workspaceId}/jit/requests/${requestId}/approve`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  rejectRequest(workspaceId, requestId, body) {
    return apiClient.request(`/workspaces/${workspaceId}/jit/requests/${requestId}/reject`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  revokeGrant(workspaceId, requestId) {
    return apiClient.request(`/workspaces/${workspaceId}/jit/requests/${requestId}/revoke`, {
      method: 'POST',
    });
  },

  cancelRequest(workspaceId, requestId) {
    return apiClient.request(`/workspaces/${workspaceId}/jit/requests/${requestId}/cancel`, {
      method: 'POST',
    });
  },
};

import { apiClient } from './client';

export const privilegedAccessApi = {
  listRequests(workspaceId, options = {}) {
    const params = new URLSearchParams();
    if (options.status) params.append('status', options.status);
    if (options.myRequestsOnly !== undefined && options.myRequestsOnly !== null) {
      params.append('myRequestsOnly', options.myRequestsOnly);
    }
    if (options.awaitingMyApprovalOnly !== undefined && options.awaitingMyApprovalOnly !== null) {
      params.append('awaitingMyApprovalOnly', options.awaitingMyApprovalOnly);
    }
    const qs = params.toString() ? `?${params.toString()}` : '';
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests${qs}`, {
      method: 'GET',
    });
  },

  getRequest(workspaceId, requestId) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests/${requestId}`, {
      method: 'GET',
    });
  },

  createRequest(workspaceId, requestData) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests`, {
      method: 'POST',
      body: JSON.stringify(requestData),
    });
  },

  approveRequest(workspaceId, requestId, body = {}) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests/${requestId}/approve`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  rejectRequest(workspaceId, requestId, body = {}) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests/${requestId}/reject`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  cancelRequest(workspaceId, requestId) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests/${requestId}/cancel`, {
      method: 'POST',
    });
  },

  revokeRequest(workspaceId, requestId, body = {}) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests/${requestId}/revoke`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  executeRequest(workspaceId, requestId, body = {}) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/requests/${requestId}/execute`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  breakGlass(workspaceId, breakGlassData) {
    return apiClient.request(`/workspaces/${workspaceId}/break-glass`, {
      method: 'POST',
      body: JSON.stringify(breakGlassData),
    });
  },

  listElevations(workspaceId, options = {}) {
    const params = new URLSearchParams();
    if (options.activeOnly !== undefined && options.activeOnly !== null) {
      params.append('activeOnly', options.activeOnly);
    }
    const qs = params.toString() ? `?${params.toString()}` : '';
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/elevations${qs}`, {
      method: 'GET',
    });
  },

  revokeElevation(workspaceId, elevationId, body = {}) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/elevations/${elevationId}/revoke`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  listPolicies(workspaceId) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/policies`, {
      method: 'GET',
    });
  },

  updatePolicy(workspaceId, policyId, policyData) {
    return apiClient.request(`/workspaces/${workspaceId}/privileged-access/policies/${policyId}`, {
      method: 'PUT',
      body: JSON.stringify(policyData),
    });
  },
};

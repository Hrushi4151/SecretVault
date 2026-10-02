import { apiClient } from './client';

export const oidcApi = {
  listProviders(workspaceId) {
    return apiClient.request(`/workspaces/${workspaceId}/oidc-providers`, {
      method: 'GET',
    });
  },

  getProvider(workspaceId, providerId) {
    return apiClient.request(`/workspaces/${workspaceId}/oidc-providers/${providerId}`, {
      method: 'GET',
    });
  },

  createProvider(workspaceId, data) {
    return apiClient.request(`/workspaces/${workspaceId}/oidc-providers`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  updateProvider(workspaceId, providerId, data) {
    return apiClient.request(`/workspaces/${workspaceId}/oidc-providers/${providerId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  deleteProvider(workspaceId, providerId) {
    return apiClient.request(`/workspaces/${workspaceId}/oidc-providers/${providerId}`, {
      method: 'DELETE',
    });
  },

  refreshJwks(workspaceId, providerId) {
    return apiClient.request(`/workspaces/${workspaceId}/oidc-providers/${providerId}/refresh-jwks`, {
      method: 'POST',
    });
  },

  testConnection(workspaceId, providerId) {
    return apiClient.request(`/workspaces/${workspaceId}/oidc-providers/${providerId}/test-connection`, {
      method: 'POST',
    });
  },

  listTrustPolicies(workspaceId, machineId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/trust-policies`, {
      method: 'GET',
    });
  },

  createTrustPolicy(workspaceId, machineId, data) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/trust-policies`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  updateTrustPolicy(workspaceId, machineId, policyId, data) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/trust-policies/${policyId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  deleteTrustPolicy(workspaceId, machineId, policyId) {
    return apiClient.request(`/workspaces/${workspaceId}/machine-identities/${machineId}/trust-policies/${policyId}`, {
      method: 'DELETE',
    });
  },
};

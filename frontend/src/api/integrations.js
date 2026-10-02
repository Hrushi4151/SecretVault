import { apiClient } from './client';

export const integrationsApi = {
  list: async (workspaceId, params = {}) => {
    const queryParams = new URLSearchParams();
    if (params.providerType) queryParams.append('providerType', params.providerType);
    if (params.status) queryParams.append('status', params.status);
    if (params.search) queryParams.append('search', params.search);
    if (params.page !== undefined) queryParams.append('page', params.page);
    if (params.size !== undefined) queryParams.append('size', params.size);
    if (params.sort) queryParams.append('sort', params.sort);
    const queryString = queryParams.toString();
    const endpoint = `/workspaces/${workspaceId}/integrations${queryString ? `?${queryString}` : ''}`;
    return apiClient.request(endpoint, { method: 'GET' });
  },

  getById: async (workspaceId, integrationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}`, {
      method: 'GET',
    });
  },

  create: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  update: async (workspaceId, integrationId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  delete: async (workspaceId, integrationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}`, {
      method: 'DELETE',
    });
  },

  validate: async (workspaceId, integrationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/validate`, {
      method: 'POST',
    });
  },

  getCapabilities: async (workspaceId, integrationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/capabilities`, {
      method: 'GET',
    });
  },

  discoverResources: async (workspaceId, integrationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/resources`, {
      method: 'GET',
    });
  },

  discoverEnvironments: async (workspaceId, integrationId, providerResourceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/resources/${encodeURIComponent(providerResourceId)}/environments`, {
      method: 'GET',
    });
  },

  listMappings: async (workspaceId, integrationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/mappings`, {
      method: 'GET',
    });
  },

  createMapping: async (workspaceId, integrationId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/mappings`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  updateMapping: async (workspaceId, integrationId, mappingId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/mappings/${mappingId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  deleteMapping: async (workspaceId, integrationId, mappingId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/mappings/${mappingId}`, {
      method: 'DELETE',
    });
  },

  pushSecret: async (workspaceId, integrationId, mappingId, secretId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/mappings/${mappingId}/push/${secretId}`, {
      method: 'POST',
    });
  },

  deleteProviderSecret: async (workspaceId, integrationId, mappingId, secretKey) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/mappings/${mappingId}/secrets/${encodeURIComponent(secretKey)}`, {
      method: 'DELETE',
    });
  },

  listProviderSecrets: async (workspaceId, integrationId, mappingId) => {
    return apiClient.request(`/workspaces/${workspaceId}/integrations/${integrationId}/mappings/${mappingId}/secrets`, {
      method: 'GET',
    });
  },
};

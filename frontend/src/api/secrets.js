import { apiClient } from './client';

export const secretApi = {
  list: async (workspaceId, projectId, environmentId, params = {}) => {
    const queryParams = new URLSearchParams();
    if (params.search) queryParams.append('search', params.search);
    if (params.status) queryParams.append('status', params.status);
    const queryString = queryParams.toString();
    const endpoint = `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets${
      queryString ? `?${queryString}` : ''
    }`;

    return apiClient.request(endpoint, {
      method: 'GET',
    });
  },

  getById: async (workspaceId, projectId, environmentId, secretId) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}`,
      {
        method: 'GET',
      }
    );
  },

  getVersions: async (workspaceId, projectId, environmentId, secretId) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions`,
      {
        method: 'GET',
      }
    );
  },

  create: async (workspaceId, projectId, environmentId, payload) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    );
  },

  batchImport: async (workspaceId, projectId, environmentId, payload) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/batch-import`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    );
  },

  update: async (workspaceId, projectId, environmentId, secretId, payload) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}`,
      {
        method: 'PATCH',
        body: JSON.stringify(payload),
      }
    );
  },

  reveal: async (workspaceId, projectId, environmentId, secretId, version = null, stepUpProof = null) => {
    const endpoint = `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/reveal${
      version ? `?version=${version}` : ''
    }`;
    const headers = stepUpProof ? { 'X-Step-Up-Proof': stepUpProof } : {};
    return apiClient.request(endpoint, {
      method: 'POST',
      headers,
    });
  },

  delete: async (workspaceId, projectId, environmentId, secretId) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}`,
      {
        method: 'DELETE',
      }
    );
  },
};

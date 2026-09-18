import { apiClient } from './client';

export const branchesApi = {
  getBranches: async (workspaceId, projectId, environmentId, secretId) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/branches`,
      { method: 'GET' }
    );
  },

  createBranch: async (workspaceId, projectId, environmentId, secretId, data) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/branches`,
      {
        method: 'POST',
        body: JSON.stringify(data)
      }
    );
  },

  getBranchById: async (workspaceId, projectId, environmentId, secretId, branchId) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/branches/${branchId}`,
      { method: 'GET' }
    );
  },

  commitBranchVersion: async (workspaceId, projectId, environmentId, secretId, branchId, data) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/branches/${branchId}/versions`,
      {
        method: 'POST',
        body: JSON.stringify(data)
      }
    );
  },

  compareBranch: async (workspaceId, projectId, environmentId, secretId, branchId) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/branches/${branchId}/compare`,
      { method: 'GET' }
    );
  },

  mergeBranch: async (workspaceId, projectId, environmentId, secretId, branchId, data = {}) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/branches/${branchId}/merge`,
      {
        method: 'POST',
        body: JSON.stringify(data)
      }
    );
  },

  archiveBranch: async (workspaceId, projectId, environmentId, secretId, branchId) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/branches/${branchId}/archive`,
      { method: 'POST' }
    );
  }
};

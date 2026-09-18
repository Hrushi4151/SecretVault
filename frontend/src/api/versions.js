import { apiClient } from './client';

export const versionsApi = {
  getVersions: async (workspaceId, projectId, environmentId, secretId, page = 0, size = 20, versionType = null) => {
    let endpoint = `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions?page=${page}&size=${size}`;
    if (versionType) {
      endpoint += `&versionType=${versionType}`;
    }
    return apiClient.request(endpoint, {
      method: 'GET'
    });
  },

  getVersion: async (workspaceId, projectId, environmentId, secretId, versionNumber) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions/${versionNumber}`,
      { method: 'GET' }
    );
  },

  revealHistoricalVersion: async (workspaceId, projectId, environmentId, secretId, versionNumber) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions/${versionNumber}/reveal`,
      { method: 'POST' }
    );
  },

  compareVersions: async (workspaceId, projectId, environmentId, secretId, from, to) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions/compare?from=${from}&to=${to}`,
      { method: 'GET' }
    );
  },

  computeValueDiff: async (workspaceId, projectId, environmentId, secretId, from, to) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions/diff?from=${from}&to=${to}`,
      { method: 'GET' }
    );
  },

  rollbackSecret: async (workspaceId, projectId, environmentId, secretId, data) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/rollback`,
      {
        method: 'POST',
        body: JSON.stringify(data)
      }
    );
  },

  addTag: async (workspaceId, projectId, environmentId, secretId, versionNumber, name) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions/${versionNumber}/tags`,
      {
        method: 'POST',
        body: JSON.stringify({ name })
      }
    );
  },

  getTags: async (workspaceId, projectId, environmentId, secretId, versionNumber) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions/${versionNumber}/tags`,
      { method: 'GET' }
    );
  },

  removeTag: async (workspaceId, projectId, environmentId, secretId, versionNumber, tagName) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/versions/${versionNumber}/tags/${tagName}`,
      { method: 'DELETE' }
    );
  }
};

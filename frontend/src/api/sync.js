import { apiClient } from './client';

export const syncApi = {
  // Drift Detection endpoints
  getDriftRecords: async (workspaceId, params = {}) => {
    const queryParams = new URLSearchParams();
    if (params.status) queryParams.append('status', params.status);
    if (params.driftType) queryParams.append('driftType', params.driftType);
    if (params.severity) queryParams.append('severity', params.severity);
    if (params.projectId) queryParams.append('projectId', params.projectId);
    if (params.environmentId) queryParams.append('environmentId', params.environmentId);
    if (params.integrationId) queryParams.append('integrationId', params.integrationId);
    if (params.mappingId) queryParams.append('mappingId', params.mappingId);
    if (params.page !== undefined) queryParams.append('page', params.page);
    if (params.size !== undefined) queryParams.append('size', params.size);
    if (params.sort) queryParams.append('sort', params.sort);
    const queryString = queryParams.toString();
    const endpoint = `/workspaces/${workspaceId}/drift${queryString ? `?${queryString}` : ''}`;
    return apiClient.request(endpoint, { method: 'GET' });
  },

  getDriftRecordById: async (workspaceId, driftId) => {
    return apiClient.request(`/workspaces/${workspaceId}/drift/${driftId}`, {
      method: 'GET',
    });
  },

  updateDriftStatus: async (workspaceId, driftId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/drift/${driftId}/status`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  triggerDriftDetection: async (workspaceId, scope = null, scopeResourceId = null) => {
    const queryParams = new URLSearchParams();
    if (scope) queryParams.append('scope', scope);
    if (scopeResourceId) queryParams.append('scopeResourceId', scopeResourceId);
    const queryString = queryParams.toString();
    const endpoint = `/workspaces/${workspaceId}/drift/detect${queryString ? `?${queryString}` : ''}`;
    return apiClient.request(endpoint, { method: 'POST' });
  },

  // Sync Engine execution endpoints
  executeDryRun: async (workspaceId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/sync/dry-run`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  executeSync: async (workspaceId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/sync`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  executeProjectDryRun: async (workspaceId, projectId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/sync/dry-run`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  executeProjectSync: async (workspaceId, projectId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/projects/${projectId}/sync`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  executeEnvironmentDryRun: async (workspaceId, environmentId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/environments/${environmentId}/sync/dry-run`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  executeEnvironmentSync: async (workspaceId, environmentId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/environments/${environmentId}/sync`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  // Sync Jobs & Audit Trail
  getSyncJobs: async (workspaceId, params = {}) => {
    const queryParams = new URLSearchParams();
    if (params.status) queryParams.append('status', params.status);
    if (params.dryRun !== undefined) queryParams.append('dryRun', params.dryRun);
    if (params.page !== undefined) queryParams.append('page', params.page);
    if (params.size !== undefined) queryParams.append('size', params.size);
    if (params.sort) queryParams.append('sort', params.sort);
    const queryString = queryParams.toString();
    const endpoint = `/workspaces/${workspaceId}/sync/jobs${queryString ? `?${queryString}` : ''}`;
    return apiClient.request(endpoint, { method: 'GET' });
  },

  getSyncJobById: async (workspaceId, jobId) => {
    return apiClient.request(`/workspaces/${workspaceId}/sync/jobs/${jobId}`, {
      method: 'GET',
    });
  },

  getSyncJobOperations: async (workspaceId, jobId, params = {}) => {
    const queryParams = new URLSearchParams();
    if (params.page !== undefined) queryParams.append('page', params.page);
    if (params.size !== undefined) queryParams.append('size', params.size);
    const queryString = queryParams.toString();
    const endpoint = `/workspaces/${workspaceId}/sync/jobs/${jobId}/operations${queryString ? `?${queryString}` : ''}`;
    return apiClient.request(endpoint, { method: 'GET' });
  },
};

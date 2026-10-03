import { apiClient } from './client';

export const rotationApi = {
  // Policies
  createPolicy: async (workspaceId, projectId, environmentId, secretId, payload) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/secrets/${secretId}/rotation-policy`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    );
  },

  getPolicy: async (workspaceId, secretId) => {
    return apiClient.request(`/workspaces/${workspaceId}/secrets/${secretId}/rotation-policy`, {
      method: 'GET',
    });
  },

  updatePolicy: async (workspaceId, secretId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/secrets/${secretId}/rotation-policy`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },

  disablePolicy: async (workspaceId, secretId) => {
    return apiClient.request(`/workspaces/${workspaceId}/secrets/${secretId}/rotation-policy`, {
      method: 'DELETE',
    });
  },

  // Jobs & Execution
  triggerRotation: async (workspaceId, secretId, payload = {}, idempotencyKey = null) => {
    const headers = {};
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    return apiClient.request(`/workspaces/${workspaceId}/secrets/${secretId}/rotate`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
  },

  listJobsBySecret: async (workspaceId, secretId, page = 0, size = 20) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/secrets/${secretId}/rotations?page=${page}&size=${size}`,
      {
        method: 'GET',
      }
    );
  },

  listWorkspaceJobs: async (workspaceId, secretId = null, page = 0, size = 20) => {
    const query = new URLSearchParams({ page, size });
    if (secretId) query.append('secretId', secretId);
    return apiClient.request(`/workspaces/${workspaceId}/rotations?${query.toString()}`, {
      method: 'GET',
    });
  },

  getJob: async (workspaceId, jobId) => {
    return apiClient.request(`/workspaces/${workspaceId}/rotations/${jobId}`, {
      method: 'GET',
    });
  },

  cancelJob: async (workspaceId, jobId) => {
    return apiClient.request(`/workspaces/${workspaceId}/rotations/${jobId}/cancel`, {
      method: 'POST',
    });
  },

  retryJob: async (workspaceId, jobId) => {
    return apiClient.request(`/workspaces/${workspaceId}/rotations/${jobId}/retry`, {
      method: 'POST',
    });
  },

  rollbackJob: async (workspaceId, secretId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/secrets/${secretId}/rotate/rollback`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  markCompromised: async (workspaceId, secretId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/secrets/${secretId}/rotate/compromise`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  // Impact Analysis
  getImpact: async (workspaceId, secretId) => {
    return apiClient.request(`/workspaces/${workspaceId}/secrets/${secretId}/rotation-impact`, {
      method: 'GET',
    });
  },

  // Leases
  createLease: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/leases`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getLease: async (workspaceId, leaseId) => {
    return apiClient.request(`/workspaces/${workspaceId}/leases/${leaseId}`, {
      method: 'GET',
    });
  },

  listLeases: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.secretId) query.append('secretId', params.secretId);
    if (params.status) query.append('status', params.status);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);

    return apiClient.request(`/workspaces/${workspaceId}/leases?${query.toString()}`, {
      method: 'GET',
    });
  },

  renewLease: async (workspaceId, leaseId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/leases/${leaseId}/renew`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  revokeLease: async (workspaceId, leaseId) => {
    return apiClient.request(`/workspaces/${workspaceId}/leases/${leaseId}`, {
      method: 'DELETE',
    });
  },

  // Consumers & Workloads
  registerConsumer: async (workspaceId, projectId, environmentId, payload) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${environmentId}/consumers/register`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    );
  },

  listConsumers: async (workspaceId, page = 0, size = 20) => {
    return apiClient.request(`/workspaces/${workspaceId}/consumers?page=${page}&size=${size}`, {
      method: 'GET',
    });
  },

  getConsumer: async (workspaceId, consumerId) => {
    return apiClient.request(`/workspaces/${workspaceId}/consumers/${consumerId}`, {
      method: 'GET',
    });
  },

  disableConsumer: async (workspaceId, consumerId) => {
    return apiClient.request(`/workspaces/${workspaceId}/consumers/${consumerId}`, {
      method: 'DELETE',
    });
  },
};

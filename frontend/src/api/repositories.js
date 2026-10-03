import { apiClient } from './client';

export const repositoryApi = {
  // Repositories
  listRepositories: async (workspaceId, page = 0, size = 20) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories?page=${page}&size=${size}`, {
      method: 'GET',
    });
  },

  getRepository: async (workspaceId, repositoryId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/${repositoryId}`, {
      method: 'GET',
    });
  },

  connectRepository: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  disconnectRepository: async (workspaceId, repositoryId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/${repositoryId}`, {
      method: 'DELETE',
    });
  },

  // Scans
  triggerScan: async (workspaceId, repositoryId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/${repositoryId}/scans`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  scanLocalDirectory: async (workspaceId, repositoryId, payload) => {
    const url = `/workspaces/${workspaceId}/repository-scans/local${repositoryId ? `?repositoryId=${repositoryId}` : ''}`;
    return apiClient.request(url, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  listScans: async (workspaceId, repositoryId = null, page = 0, size = 20) => {
    let url = `/workspaces/${workspaceId}/repository-scans?page=${page}&size=${size}`;
    if (repositoryId) url += `&repositoryId=${repositoryId}`;
    return apiClient.request(url, {
      method: 'GET',
    });
  },

  getScan: async (workspaceId, scanId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repository-scans/${scanId}`, {
      method: 'GET',
    });
  },

  // Findings
  listFindings: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    if (params.repositoryId) query.append('repositoryId', params.repositoryId);
    if (params.severity) query.append('severity', params.severity);
    if (params.status) query.append('status', params.status);
    if (params.search) query.append('search', params.search);

    return apiClient.request(`/workspaces/${workspaceId}/secret-findings?${query.toString()}`, {
      method: 'GET',
    });
  },

  getFindingStats: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/stats`, {
      method: 'GET',
    });
  },

  getFinding: async (workspaceId, findingId) => {
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/${findingId}`, {
      method: 'GET',
    });
  },

  getOccurrences: async (workspaceId, findingId, page = 0, size = 20) => {
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/${findingId}/occurrences?page=${page}&size=${size}`, {
      method: 'GET',
    });
  },

  whyExposed: async (workspaceId, findingId) => {
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/${findingId}/why-exposed`, {
      method: 'GET',
    });
  },

  updateFindingStatus: async (workspaceId, findingId, status, reason = '') => {
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/${findingId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, reason }),
    });
  },

  allowlistFinding: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/allowlist`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  // Remediation
  remediateFinding: async (workspaceId, findingId, action, notes = '') => {
    return apiClient.request(`/workspaces/${workspaceId}/repository-remediations/${findingId}`, {
      method: 'POST',
      body: JSON.stringify({ action, notes }),
    });
  },

  // Policies
  getPolicy: async (workspaceId, repositoryId = null) => {
    const url = `/workspaces/${workspaceId}/repository-policies${repositoryId ? `?repositoryId=${repositoryId}` : ''}`;
    return apiClient.request(url, {
      method: 'GET',
    });
  },

  savePolicy: async (workspaceId, repositoryId, policy) => {
    const url = `/workspaces/${workspaceId}/repository-policies${repositoryId ? `?repositoryId=${repositoryId}` : ''}`;
    return apiClient.request(url, {
      method: 'PUT',
      body: JSON.stringify(policy),
    });
  },
};

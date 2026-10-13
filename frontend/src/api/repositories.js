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

  discoverRemoteRepositories: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.provider) query.append('provider', params.provider);
    if (params.token) query.append('token', params.token);
    if (params.username) query.append('username', params.username);
    return apiClient.request(`/workspaces/${workspaceId}/repositories/remote/discover?${query.toString()}`, {
      method: 'GET',
    });
  },

  // GitHub Account Integration
  getGitHubStatus: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/github/status`, {
      method: 'GET',
    });
  },

  connectGitHub: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/github/connect`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  refreshGitHub: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/github/refresh`, {
      method: 'POST',
    });
  },

  disconnectGitHub: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/github`, {
      method: 'DELETE',
    });
  },

  toggleMonitoring: async (workspaceId, repositoryId, enabled) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/${repositoryId}/monitoring`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    });
  },

  // Scans
  triggerScan: async (workspaceId, repositoryId, payload = {}) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/${repositoryId}/scans`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  syncRepository: async (workspaceId, repositoryId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repositories/${repositoryId}/sync`, {
      method: 'POST',
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

  listAllowlists: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    if (params.repositoryId) query.append('repositoryId', params.repositoryId);
    if (params.activeOnly !== undefined) query.append('activeOnly', params.activeOnly);
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/allowlists?${query.toString()}`, {
      method: 'GET',
    });
  },

  revokeAllowlist: async (workspaceId, allowlistId, reason = 'Revoked by user') => {
    const query = new URLSearchParams({ reason });
    return apiClient.request(`/workspaces/${workspaceId}/secret-findings/allowlists/${allowlistId}?${query.toString()}`, {
      method: 'DELETE',
    });
  },

  // Remediation
  getRemediationTargetInfo: async (workspaceId, findingId) => {
    return apiClient.request(`/workspaces/${workspaceId}/repository-remediations/${findingId}/target-info`, {
      method: 'GET',
    });
  },

  remediateFinding: async (workspaceId, findingId, actionOrPayload, notes = '') => {
    const payload = typeof actionOrPayload === 'object'
      ? actionOrPayload
      : { action: actionOrPayload, notes };

    return apiClient.request(`/workspaces/${workspaceId}/repository-remediations/${findingId}`, {
      method: 'POST',
      body: JSON.stringify(payload),
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

  getRepositories: async (workspaceId, page = 0, size = 100) => {
    return repositoryApi.listRepositories(workspaceId, page, size);
  },
};

export const repositoriesApi = repositoryApi;

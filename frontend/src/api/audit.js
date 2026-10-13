import { apiClient } from './client';

export const auditApi = {
  getWorkspaceLogs: async (workspaceId, limit = 100) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/audit?limit=${limit}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  searchLogs: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.action && params.action !== 'ALL') query.append('action', params.action);
    if (params.outcome && params.outcome !== 'ALL') query.append('outcome', params.outcome);
    if (params.resourceType) query.append('resourceType', params.resourceType);
    if (params.actorId) query.append('actorId', params.actorId);
    if (params.search) query.append('search', params.search);
    if (params.from) query.append('from', params.from);
    if (params.to) query.append('to', params.to);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    if (params.sort) query.append('sort', params.sort);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/audit/search${qs}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  exportLogs: async (workspaceId, format = 'csv', params = {}) => {
    const query = new URLSearchParams();
    query.append('format', format);
    if (params.action && params.action !== 'ALL') query.append('action', params.action);
    if (params.outcome && params.outcome !== 'ALL') query.append('outcome', params.outcome);
    if (params.resourceType) query.append('resourceType', params.resourceType);
    if (params.search) query.append('search', params.search);
    if (params.limit) query.append('limit', params.limit);
    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient.get(`/workspaces/${workspaceId}/audit/export${qs}`);
  },

  getSecretLogs: async (secretId) => {
    const res = await apiClient.get(`/secrets/${secretId}/audit`);
    return (res && res.data !== undefined) ? res.data : res;
  }
};


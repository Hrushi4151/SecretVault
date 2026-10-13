import { apiClient } from './client';

export const securityApi = {
  getOverview: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/security/overview`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getPosture: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/security/posture`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getRiskBreakdown: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/security/risk`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getFindings: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.status) query.append('status', params.status);
    if (params.severity) query.append('severity', params.severity);
    if (params.category) query.append('category', params.category);
    if (params.search) query.append('search', params.search);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    if (params.sort) query.append('sort', params.sort);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/security/findings${qs}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getFindingDetails: async (workspaceId, findingId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/security/findings/${findingId}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  updateFindingStatus: async (workspaceId, findingId, status, resolutionReason = '') => {
    const res = await apiClient.patch(`/workspaces/${workspaceId}/security/findings/${findingId}`, {
      status,
      resolutionReason
    });
    return (res && res.data !== undefined) ? res.data : res;
  },

  assignFinding: async (workspaceId, findingId, assigneeUserId) => {
    const res = await apiClient.patch(`/workspaces/${workspaceId}/security/findings/${findingId}/assign`, {
      assigneeUserId
    });
    return (res && res.data !== undefined) ? res.data : res;
  },

  getTimeline: async (workspaceId, limit = 20) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/security/timeline?limit=${limit}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getEvents: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.eventType) query.append('eventType', params.eventType);
    if (params.severity) query.append('severity', params.severity);
    if (params.outcome) query.append('outcome', params.outcome);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    if (params.sort) query.append('sort', params.sort);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/security/events${qs}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  triggerAnalysis: async (workspaceId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/security/analyze`);
    return (res && res.data !== undefined) ? res.data : res;
  }
};

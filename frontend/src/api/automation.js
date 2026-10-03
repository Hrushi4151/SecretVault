import { apiClient } from './client';

export const automationApi = {
  listPolicies: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/automation-policies${qs}`);
    return res.data;
  },

  getPolicy: async (workspaceId, policyId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/automation-policies/${policyId}`);
    return res.data;
  },

  createPolicy: async (workspaceId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/automation-policies`, data);
    return res.data;
  },

  updatePolicy: async (workspaceId, policyId, data) => {
    const res = await apiClient.put(`/workspaces/${workspaceId}/automation-policies/${policyId}`, data);
    return res.data;
  },

  deletePolicy: async (workspaceId, policyId) => {
    const res = await apiClient.delete(`/workspaces/${workspaceId}/automation-policies/${policyId}`);
    return res.data;
  },

  simulate: async (workspaceId, sampleEvent) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/automation-policies/simulate`, sampleEvent);
    return res.data;
  },

  listExecutions: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.policyId) query.append('policyId', params.policyId);
    if (params.status) query.append('status', params.status);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/automation-executions${qs}`);
    return res.data;
  },

  listApprovals: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.status) query.append('status', params.status);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/automation-approvals${qs}`);
    return res.data;
  },

  decideApproval: async (workspaceId, approvalId, approve, rejectionReason = '') => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/automation-approvals/${approvalId}/decide`, {
      approve,
      rejectionReason
    });
    return res.data;
  }
};

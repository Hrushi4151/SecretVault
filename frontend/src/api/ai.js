import { apiClient } from './client';

export const aiApi = {
  // Chat & Inquiries
  chat: async (workspaceId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/ai/chat`, data);
    return res.data;
  },

  listInquiries: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/ai/inquiries`);
    return res.data;
  },

  // Deployment / Sync RCA
  runRca: async (workspaceId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/ai/rca`, data);
    return res.data;
  },

  getRcaReport: async (workspaceId, reportId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/ai/rca/${reportId}`);
    return res.data;
  },

  // Security Posture Forecasting
  getPostureForecast: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/ai/posture/forecast`);
    return res.data;
  },

  // Remediation Plans
  listPlans: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/ai/plans`);
    return res.data;
  },

  getPlan: async (workspaceId, planId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/ai/plans/${planId}`);
    return res.data;
  },

  generatePlan: async (workspaceId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/ai/plans/generate`, data);
    return res.data;
  },

  approvePlan: async (workspaceId, planId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/ai/plans/${planId}/approve`);
    return res.data;
  },

  executePlan: async (workspaceId, planId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/ai/plans/${planId}/execute`);
    return res.data;
  },

  rejectPlan: async (workspaceId, planId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/ai/plans/${planId}/reject`);
    return res.data;
  },

  submitFeedback: async (workspaceId, planId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/ai/plans/${planId}/feedback`, data);
    return res.data;
  },

  // Token Budget & Quota
  getTokenBudget: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/ai/token-budget`);
    return res.data;
  }
};

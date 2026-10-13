import { apiClient } from './client';

export const aiApi = {
  // Chat & Inquiries
  chat: async (workspaceId, data) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/chat`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  listInquiries: async (workspaceId, page = 0, size = 20) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/inquiries?page=${page}&size=${size}`, {
      method: 'GET',
    });
  },

  // Deployment / Sync RCA
  runRca: async (workspaceId, data) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/rca`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  getRcaReport: async (workspaceId, reportId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/rca/${reportId}`, {
      method: 'GET',
    });
  },

  // Security Posture Forecasting
  getPostureForecast: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/posture/forecast`, {
      method: 'GET',
    });
  },

  // Remediation Plans
  listPlans: async (workspaceId, page = 0, size = 20) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/plans?page=${page}&size=${size}`, {
      method: 'GET',
    });
  },

  getPlan: async (workspaceId, planId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/plans/${planId}`, {
      method: 'GET',
    });
  },

  generatePlan: async (workspaceId, data) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/plans/generate`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  approvePlan: async (workspaceId, planId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/plans/${planId}/approve`, {
      method: 'POST',
    });
  },

  executePlan: async (workspaceId, planId, data = {}) => {
    const headers = {};
    if (data?.stepUpProof) {
      headers['X-Step-Up-Proof'] = data.stepUpProof;
    }
    return apiClient.request(`/workspaces/${workspaceId}/ai/plans/${planId}/execute`, {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
    });
  },

  rejectPlan: async (workspaceId, planId, reason = '') => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/plans/${planId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  },

  submitFeedback: async (workspaceId, planId, data) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/plans/${planId}/feedback`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  // Token Budget & Quota
  getTokenBudget: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/token-budget`, {
      method: 'GET',
    });
  },

  // LLM Providers
  listProviders: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/providers`, {
      method: 'GET',
    });
  },

  // Persistent Conversations
  listConversations: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/conversations`, {
      method: 'GET',
    });
  },

  getConversation: async (workspaceId, conversationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/conversations/${conversationId}`, {
      method: 'GET',
    });
  },

  deleteConversation: async (workspaceId, conversationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/ai/conversations/${conversationId}`, {
      method: 'DELETE',
    });
  },
};

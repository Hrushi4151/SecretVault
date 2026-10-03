import { apiClient } from './client';

export const incidentsApi = {
  listIncidents: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.status) query.append('status', params.status);
    if (params.severity) query.append('severity', params.severity);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/incidents${qs}`);
    return res.data;
  },

  getIncident: async (workspaceId, incidentId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/incidents/${incidentId}`);
    return res.data;
  },

  createIncident: async (workspaceId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/incidents`, data);
    return res.data;
  },

  updateIncidentStatus: async (workspaceId, incidentId, status, resolutionSummary = '') => {
    const res = await apiClient.put(`/workspaces/${workspaceId}/incidents/${incidentId}/status`, {
      status,
      resolutionSummary
    });
    return res.data;
  },

  getIncidentEvents: async (workspaceId, incidentId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/incidents/${incidentId}/events`);
    return res.data;
  }
};

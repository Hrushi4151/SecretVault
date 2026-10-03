import { apiClient } from './client';

export const webhooksApi = {
  listEndpoints: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks${qs}`);
    return res.data;
  },

  getEndpoint: async (workspaceId, endpointId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks/${endpointId}`);
    return res.data;
  },

  createEndpoint: async (workspaceId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/webhooks`, data);
    return res.data;
  },

  updateEndpoint: async (workspaceId, endpointId, data) => {
    const res = await apiClient.put(`/workspaces/${workspaceId}/webhooks/${endpointId}`, data);
    return res.data;
  },

  deleteEndpoint: async (workspaceId, endpointId) => {
    const res = await apiClient.delete(`/workspaces/${workspaceId}/webhooks/${endpointId}`);
    return res.data;
  },

  revealSecret: async (workspaceId, endpointId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks/${endpointId}/signing-secret`);
    return res.data;
  },

  listDeliveries: async (workspaceId, endpointId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks/${endpointId}/deliveries${qs}`);
    return res.data;
  },

  testEndpoint: async (workspaceId, endpointId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/webhooks/${endpointId}/test`, {});
    return res.data;
  },

  retryDelivery: async (workspaceId, deliveryId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/webhooks/deliveries/${deliveryId}/retry`, {});
    return res.data;
  }
};

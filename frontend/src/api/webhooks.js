import { apiClient } from './client';

export const webhooksApi = {
  listEndpoints: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks${qs}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getEndpoint: async (workspaceId, endpointId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks/${endpointId}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  createEndpoint: async (workspaceId, data) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/webhooks`, data);
    return (res && res.data !== undefined) ? res.data : res;
  },

  updateEndpoint: async (workspaceId, endpointId, data) => {
    const res = await apiClient.put(`/workspaces/${workspaceId}/webhooks/${endpointId}`, data);
    return (res && res.data !== undefined) ? res.data : res;
  },

  deleteEndpoint: async (workspaceId, endpointId) => {
    const res = await apiClient.delete(`/workspaces/${workspaceId}/webhooks/${endpointId}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  revealSecret: async (workspaceId, endpointId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks/${endpointId}/signing-secret`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  rotateSecret: async (workspaceId, endpointId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/webhooks/${endpointId}/rotate-secret`, {});
    return (res && res.data !== undefined) ? res.data : res;
  },

  listDeliveries: async (workspaceId, endpointId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/webhooks/${endpointId}/deliveries${qs}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  testEndpoint: async (workspaceId, endpointId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/webhooks/${endpointId}/test`, {});
    return (res && res.data !== undefined) ? res.data : res;
  },

  retryDelivery: async (workspaceId, deliveryId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/webhooks/deliveries/${deliveryId}/retry`, {});
    return (res && res.data !== undefined) ? res.data : res;
  }
};

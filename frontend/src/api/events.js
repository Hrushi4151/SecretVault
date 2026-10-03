import { apiClient } from './client';

export const eventsApi = {
  listEvents: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.eventType) query.append('eventType', params.eventType);
    if (params.aggregateType) query.append('aggregateType', params.aggregateType);
    if (params.aggregateId) query.append('aggregateId', params.aggregateId);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/events${qs}`);
    return res.data;
  },

  getEvent: async (workspaceId, eventId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/events/${eventId}`);
    return res.data;
  },

  replayEvents: async (workspaceId, payload) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/events/replay`, payload);
    return res.data;
  }
};

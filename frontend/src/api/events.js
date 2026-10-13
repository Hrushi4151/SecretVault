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
    return (res && res.data !== undefined) ? res.data : res;
  },

  getEvent: async (workspaceId, eventId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/events/${eventId}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getOutboxStats: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/events/stats`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  getDeadLetterEvents: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/events/dead-letter${qs}`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  retryDeadLetterEvent: async (workspaceId, eventId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/events/dead-letter/${eventId}/retry`);
    return (res && res.data !== undefined) ? res.data : res;
  },

  replayEvents: async (workspaceId, payload) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/events/replay`, payload);
    return (res && res.data !== undefined) ? res.data : res;
  }
};

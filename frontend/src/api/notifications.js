import { apiClient } from './client';

export const notificationsApi = {
  listNotifications: async (workspaceId, params = {}) => {
    const query = new URLSearchParams();
    if (params.status) query.append('status', params.status);
    if (params.page !== undefined) query.append('page', params.page);
    if (params.size !== undefined) query.append('size', params.size);
    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient.get(`/workspaces/${workspaceId}/notifications${qs}`);
    return res.data;
  },

  markRead: async (workspaceId, notificationId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/notifications/${notificationId}/read`, {});
    return res.data;
  },

  markAllRead: async (workspaceId) => {
    const res = await apiClient.post(`/workspaces/${workspaceId}/notifications/read-all`, {});
    return res.data;
  },

  getPreferences: async (workspaceId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/notification-preferences`);
    return res.data;
  },

  updatePreferences: async (workspaceId, data) => {
    const res = await apiClient.put(`/workspaces/${workspaceId}/notification-preferences`, data);
    return res.data;
  }
};

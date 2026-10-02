import { apiClient } from './client';

export const workspaceApi = {
  list: async () => {
    return apiClient.request('/workspaces', {
      method: 'GET',
    });
  },

  create: async (payload) => {
    return apiClient.request('/workspaces', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getById: async (id) => {
    return apiClient.request(`/workspaces/${id}`, {
      method: 'GET',
    });
  },

  addMember: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/members`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  listMembers: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/members`, {
      method: 'GET',
    });
  },

  updateMemberRole: async (workspaceId, userId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/members/${userId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  removeMember: async (workspaceId, userId) => {
    return apiClient.request(`/workspaces/${workspaceId}/members/${userId}`, {
      method: 'DELETE',
    });
  },

  getSettings: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/settings`, {
      method: 'GET',
    });
  },

  updateSettings: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/settings`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },

  createInvitation: async (workspaceId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/invitations`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  listInvitations: async (workspaceId) => {
    return apiClient.request(`/workspaces/${workspaceId}/invitations`, {
      method: 'GET',
    });
  },

  revokeInvitation: async (workspaceId, invitationId) => {
    return apiClient.request(`/workspaces/${workspaceId}/invitations/${invitationId}/revoke`, {
      method: 'POST',
    });
  },

  lookupUser: async (workspaceId, email) => {
    return apiClient.request(`/workspaces/${workspaceId}/invitations/lookup?email=${encodeURIComponent(email)}`, {
      method: 'GET',
    });
  },

  getMyInvitations: async () => {
    return apiClient.request('/invitations/me', {
      method: 'GET',
    });
  },

  acceptInvitationById: async (invitationId) => {
    return apiClient.request(`/invitations/${invitationId}/accept`, {
      method: 'POST',
    });
  },

  declineInvitationById: async (invitationId) => {
    return apiClient.request(`/invitations/${invitationId}/decline`, {
      method: 'POST',
    });
  },

  acceptInvitation: async (payload) => {
    return apiClient.request('/invitations/accept', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getMemberAccess: async (workspaceId, userId) => {
    return apiClient.request(`/workspaces/${workspaceId}/members/${userId}/access`, {
      method: 'GET',
    });
  },

  updateMemberAccess: async (workspaceId, userId, payload) => {
    return apiClient.request(`/workspaces/${workspaceId}/members/${userId}/access`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },
};

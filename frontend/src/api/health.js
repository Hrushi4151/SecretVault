import { apiClient } from './client';

export const healthApi = {
  getSecretHealth: async (workspaceId, secretId) => {
    const res = await apiClient.get(`/workspaces/${workspaceId}/secrets/${secretId}/health`);
    return res.data;
  }
};

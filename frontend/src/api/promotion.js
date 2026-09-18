import { apiClient } from './client';

export const promotionApi = {
  previewPromotion: async (workspaceId, projectId, sourceEnvironmentId, data) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${sourceEnvironmentId}/promote/preview`,
      {
        method: 'POST',
        body: JSON.stringify(data)
      }
    );
  },

  executePromotion: async (workspaceId, projectId, sourceEnvironmentId, data) => {
    return apiClient.request(
      `/workspaces/${workspaceId}/projects/${projectId}/environments/${sourceEnvironmentId}/promote`,
      {
        method: 'POST',
        body: JSON.stringify(data)
      }
    );
  }
};

import { apiClient } from './client';

export const reviewsApi = {
  listCampaigns(workspaceId) {
    return apiClient.request(`/workspaces/${workspaceId}/access-reviews`, {
      method: 'GET',
    });
  },

  createCampaign(workspaceId, campaignData) {
    return apiClient.request(`/workspaces/${workspaceId}/access-reviews`, {
      method: 'POST',
      body: JSON.stringify(campaignData),
    });
  },

  getCampaign(workspaceId, campaignId) {
    return apiClient.request(`/workspaces/${workspaceId}/access-reviews/${campaignId}`, {
      method: 'GET',
    });
  },

  listCampaignItems(workspaceId, campaignId) {
    return apiClient.request(`/workspaces/${workspaceId}/access-reviews/${campaignId}/items`, {
      method: 'GET',
    });
  },

  decideItem(workspaceId, campaignId, itemId, decisionData) {
    return apiClient.request(`/workspaces/${workspaceId}/access-reviews/${campaignId}/items/${itemId}/decide`, {
      method: 'POST',
      body: JSON.stringify(decisionData),
    });
  },

  completeCampaign(workspaceId, campaignId) {
    return apiClient.request(`/workspaces/${workspaceId}/access-reviews/${campaignId}/complete`, {
      method: 'POST',
    });
  },

  getAttestation(workspaceId, campaignId) {
    return apiClient.request(`/workspaces/${workspaceId}/access-reviews/${campaignId}/attestation`, {
      method: 'GET',
    });
  },
};

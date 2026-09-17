const API_BASE_URL = '/api/v1';

const ACCESS_TOKEN_KEY = 'sv_access_token';
const REFRESH_TOKEN_KEY = 'sv_refresh_token';
const ACTIVE_WORKSPACE_KEY = 'sv_active_workspace_id';

class ApiClient {
  constructor() {
    this.accessToken = localStorage.getItem(ACCESS_TOKEN_KEY);
    this.refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    this.activeWorkspaceId = localStorage.getItem(ACTIVE_WORKSPACE_KEY);
    this.refreshPromise = null;
  }

  setSession(tokens) {
    if (tokens) {
      this.accessToken = tokens.accessToken;
      this.refreshToken = tokens.refreshToken;
      localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
      if (tokens.activeWorkspaceId) {
        this.activeWorkspaceId = tokens.activeWorkspaceId;
        localStorage.setItem(ACTIVE_WORKSPACE_KEY, tokens.activeWorkspaceId);
      }
    } else {
      this.accessToken = null;
      this.refreshToken = null;
      this.activeWorkspaceId = null;
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      localStorage.removeItem(ACTIVE_WORKSPACE_KEY);
    }
  }

  setActiveWorkspaceId(workspaceId) {
    this.activeWorkspaceId = workspaceId;
    if (workspaceId) {
      localStorage.setItem(ACTIVE_WORKSPACE_KEY, workspaceId);
    } else {
      localStorage.removeItem(ACTIVE_WORKSPACE_KEY);
    }
  }

  getActiveWorkspaceId() {
    return this.activeWorkspaceId;
  }

  getAccessToken() {
    return this.accessToken;
  }

  getRefreshToken() {
    return this.refreshToken;
  }

  async tryRefreshToken() {
    if (!this.refreshToken) {
      return null;
    }

    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    this.refreshPromise = (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ refreshToken: this.refreshToken }),
        });

        if (!res.ok) {
          this.setSession(null);
          return null;
        }

        const json = await res.json();
        if (json.success && json.data) {
          this.setSession({
            accessToken: json.data.accessToken,
            refreshToken: json.data.refreshToken,
            activeWorkspaceId: this.activeWorkspaceId || json.data.activeWorkspace?.id,
          });
          return json.data.accessToken;
        }
        this.setSession(null);
        return null;
      } catch {
        this.setSession(null);
        return null;
      } finally {
        this.refreshPromise = null;
      }
    })();

    return this.refreshPromise;
  }

  async request(endpoint, options = {}, retryOnUnauthorized = true) {
    const url = `${API_BASE_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
    const headers = {
      'Content-Type': 'application/json',
      'X-Correlation-ID': typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `req-${Date.now()}`,
      ...(options.headers || {}),
    };

    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }

    if (this.activeWorkspaceId) {
      headers['X-Workspace-ID'] = this.activeWorkspaceId;
    }

    let response;
    try {
      response = await fetch(url, {
        ...options,
        headers,
      });
    } catch (networkError) {
      throw new Error(
        'Unable to connect to SecretVault backend. Please verify your network connection or backend service status.'
      );
    }

    // Handle 401 Unauthorized & Silent Refresh
    if (response.status === 401 && retryOnUnauthorized && this.refreshToken && !endpoint.includes('/auth/')) {
      const newAccessToken = await this.tryRefreshToken();
      if (newAccessToken) {
        return this.request(endpoint, options, false);
      }
    }

    // Parse Response Body
    let body = null;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      body = await response.json();
    }

    if (!response.ok) {
      const errorPayload = body || {
        timestamp: new Date().toISOString(),
        status: response.status,
        code: response.statusText || 'API_ERROR',
        message: `HTTP Error ${response.status}: ${response.statusText}`,
        requestId: response.headers.get('x-request-id') || 'unknown',
      };

      const error = new Error(errorPayload.message || 'An unexpected error occurred.');
      error.payload = errorPayload;
      error.status = response.status;
      throw error;
    }

    if (body && typeof body === 'object' && 'success' in body) {
      return body.data;
    }

    return body;
  }
}

export const apiClient = new ApiClient();

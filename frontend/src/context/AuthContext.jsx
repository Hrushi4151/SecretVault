import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '../api/auth';
import { workspaceApi } from '../api/workspaces';
import { apiClient } from '../api/client';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [workspaces, setWorkspaces] = useState([]);
  const [activeWorkspace, setActiveWorkspace] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const clearError = useCallback(() => setError(null), []);

  const refreshWorkspaces = useCallback(async () => {
    try {
      const fetchedWorkspaces = await workspaceApi.list();
      setWorkspaces(fetchedWorkspaces);

      const savedWorkspaceId = apiClient.getActiveWorkspaceId();
      if (savedWorkspaceId) {
        const matching = fetchedWorkspaces.find((w) => w.id === savedWorkspaceId);
        if (matching) {
          setActiveWorkspace(matching);
          return;
        }
      }

      if (fetchedWorkspaces && fetchedWorkspaces.length > 0) {
        const defaultWs = fetchedWorkspaces.find((w) => w.isDefault) || fetchedWorkspaces[0];
        setActiveWorkspace(defaultWs);
        apiClient.setActiveWorkspaceId(defaultWs.id);
      } else {
        setActiveWorkspace(null);
        apiClient.setActiveWorkspaceId(null);
      }
    } catch (err) {
      console.warn('Failed to refresh workspaces:', err.message);
    }
  }, []);

  const initSession = useCallback(async () => {
    setIsLoading(true);
    const accessToken = apiClient.getAccessToken();
    const refreshToken = apiClient.getRefreshToken();

    if (!accessToken && !refreshToken) {
      setIsLoading(false);
      return;
    }

    try {
      const currentUser = await authApi.getCurrentUser();
      setUser(currentUser);
      await refreshWorkspaces();
    } catch (err) {
      console.warn('Session verification failed, resetting session:', err.message);
      apiClient.setSession(null);
      setUser(null);
      setWorkspaces([]);
      setActiveWorkspace(null);
    } finally {
      setIsLoading(false);
    }
  }, [refreshWorkspaces]);

  useEffect(() => {
    initSession();
  }, [initSession]);

  const login = async (credentials) => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await authApi.login(credentials);
      apiClient.setSession({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        activeWorkspaceId: response.activeWorkspace?.id,
      });

      setUser(response.user);
      if (response.activeWorkspace) {
        setActiveWorkspace(response.activeWorkspace);
      }

      await refreshWorkspaces();
    } catch (err) {
      const errMsg = err.payload?.message || err.message || 'Login failed. Please check your credentials.';
      setError(errMsg);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (data) => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await authApi.register(data);
      apiClient.setSession({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        activeWorkspaceId: response.activeWorkspace?.id,
      });

      setUser(response.user);
      if (response.activeWorkspace) {
        setActiveWorkspace(response.activeWorkspace);
      }

      await refreshWorkspaces();
    } catch (err) {
      const errMsg = err.payload?.message || err.message || 'Registration failed. Please review the inputs.';
      setError(errMsg);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      await authApi.logout();
    } catch (err) {
      console.warn('Logout API warning:', err);
    } finally {
      apiClient.setSession(null);
      setUser(null);
      setWorkspaces([]);
      setActiveWorkspace(null);
      setIsLoading(false);
    }
  };

  const switchWorkspace = (workspaceId) => {
    const target = workspaces.find((w) => w.id === workspaceId);
    if (target) {
      setActiveWorkspace(target);
      apiClient.setActiveWorkspaceId(target.id);
    }
  };

  const createWorkspace = async (data) => {
    setError(null);
    try {
      const newWorkspace = await workspaceApi.create(data);
      setWorkspaces((prev) => [...prev, newWorkspace]);
      setActiveWorkspace(newWorkspace);
      apiClient.setActiveWorkspaceId(newWorkspace.id);
      return newWorkspace;
    } catch (err) {
      const errMsg = err.payload?.message || err.message || 'Failed to create workspace.';
      setError(errMsg);
      throw err;
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        workspaces,
        activeWorkspace,
        isAuthenticated: !!user,
        isLoading,
        error,
        login,
        register,
        logout,
        switchWorkspace,
        createWorkspace,
        refreshWorkspaces,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

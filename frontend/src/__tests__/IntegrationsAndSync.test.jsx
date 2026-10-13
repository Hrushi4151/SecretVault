import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { IntegrationsView } from '../components/integrations/IntegrationsView';
import { SyncCenterView } from '../components/sync/SyncCenterView';
import { integrationsApi } from '../api/integrations';
import { syncApi } from '../api/sync';
import { projectApi } from '../api/projects';
import { environmentApi } from '../api/environments';
import { secretApi } from '../api/secrets';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../api/integrations', () => ({
  integrationsApi: {
    list: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    disconnect: vi.fn(),
    validate: vi.fn(),
    getCapabilities: vi.fn(),
    discoverResources: vi.fn(),
    discoverEnvironments: vi.fn(),
    listMappings: vi.fn(),
    createMapping: vi.fn(),
    updateMapping: vi.fn(),
    deleteMapping: vi.fn(),
    pushSecret: vi.fn(),
    deleteProviderSecret: vi.fn(),
    listProviderSecrets: vi.fn(),
  },
}));

vi.mock('../api/sync', () => ({
  syncApi: {
    getDriftRecords: vi.fn(),
    getDriftRecordById: vi.fn(),
    updateDriftStatus: vi.fn(),
    triggerDriftDetection: vi.fn(),
    executeDryRun: vi.fn(),
    executeSync: vi.fn(),
    executeProjectDryRun: vi.fn(),
    executeProjectSync: vi.fn(),
    getSyncJobs: vi.fn(),
    getSyncJobById: vi.fn(),
    getJobOperations: vi.fn(),
  },
}));

vi.mock('../api/projects', () => ({
  projectApi: {
    list: vi.fn(),
  },
}));

vi.mock('../api/environments', () => ({
  environmentApi: {
    list: vi.fn(),
  },
}));

vi.mock('../api/secrets', () => ({
  secretApi: {
    list: vi.fn(),
  },
}));

describe('Provider Integrations and Sync Engine UI Suite', () => {
  const mockWorkspace = { id: 'ws-test-111', name: 'Production Vault' };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuth.mockReturnValue({
      activeWorkspace: mockWorkspace,
    });
    // Mock window.confirm
    window.confirm = vi.fn(() => true);
  });

  describe('IntegrationsView', () => {
    const mockIntegrations = [
      {
        id: 'int-vercel-1',
        displayName: 'Vercel Production Frontend',
        providerType: 'VERCEL',
        status: 'ACTIVE',
        redactedCredentialHint: 'vcel_••••••••1234',
        createdAt: '2026-03-01T10:00:00Z',
        lastValidatedAt: '2026-03-01T12:00:00Z',
      },
      {
        id: 'int-render-1',
        displayName: 'Render Backend API',
        providerType: 'RENDER',
        status: 'REVOKED',
        redactedCredentialHint: 'rnd_••••••••5678',
        createdAt: '2026-03-01T11:00:00Z',
        lastValidatedAt: null,
      },
    ];

    const mockProjects = [
      { id: 'proj-1', name: 'Core Web Platform' },
    ];

    const mockMappings = [
      {
        id: 'map-1',
        providerIntegrationId: 'int-vercel-1',
        projectId: 'proj-1',
        environmentId: 'env-1',
        providerResourceType: 'PROJECT',
        providerResourceId: 'prj_vercel_123',
        providerResourceName: 'nextjs-web',
        providerEnvironment: 'production',
        syncEnabled: true,
      },
    ];

    it('renders connected providers with credentials hints and status badges', async () => {
      integrationsApi.list.mockResolvedValueOnce({ content: mockIntegrations });
      projectApi.list.mockResolvedValueOnce(mockProjects);
      integrationsApi.listMappings.mockResolvedValueOnce(mockMappings);
      integrationsApi.listMappings.mockResolvedValueOnce([]);
      environmentApi.list.mockResolvedValueOnce([{ id: 'env-1', name: 'Production' }]);

      render(<IntegrationsView />);

      await waitFor(() => {
        expect(screen.getByText('Vercel Production Frontend')).toBeInTheDocument();
        expect(screen.getByText('Render Backend API')).toBeInTheDocument();
      });

      expect(screen.getByText('ACTIVE')).toBeInTheDocument();
      expect(screen.getByText('REVOKED')).toBeInTheDocument();
      expect(screen.getByText('vcel_••••••••1234')).toBeInTheDocument();
    });

    it('tests connection via validate button and displays feedback', async () => {
      integrationsApi.list.mockResolvedValue({ content: mockIntegrations });
      projectApi.list.mockResolvedValue(mockProjects);
      integrationsApi.listMappings.mockResolvedValue([]);
      environmentApi.list.mockResolvedValue([]);
      integrationsApi.validate.mockResolvedValueOnce({
        valid: true,
        message: 'Successfully validated token against Vercel API',
      });

      render(<IntegrationsView />);

      await waitFor(() => {
        expect(screen.getByText('Vercel Production Frontend')).toBeInTheDocument();
      });

      const testButtons = screen.getAllByText('Test Connection');
      fireEvent.click(testButtons[0]);

      await waitFor(() => {
        expect(integrationsApi.validate).toHaveBeenCalledWith('ws-test-111', 'int-vercel-1');
      });
    });

    it('disconnects integration safely while preserving mappings', async () => {
      integrationsApi.list.mockResolvedValue({ content: mockIntegrations });
      projectApi.list.mockResolvedValue(mockProjects);
      integrationsApi.listMappings.mockResolvedValue([]);
      environmentApi.list.mockResolvedValue([]);
      integrationsApi.disconnect.mockResolvedValueOnce({
        id: 'int-vercel-1',
        status: 'REVOKED',
      });

      render(<IntegrationsView />);

      await waitFor(() => {
        expect(screen.getByText('Vercel Production Frontend')).toBeInTheDocument();
      });

      const disconnectBtn = screen.getByTitle('Disconnect Integration (Preserve Mappings)');
      fireEvent.click(disconnectBtn);

      await waitFor(() => {
        expect(integrationsApi.disconnect).toHaveBeenCalledWith('ws-test-111', 'int-vercel-1');
        // Delete must NOT have been called
        expect(integrationsApi.delete).not.toHaveBeenCalled();
      });
    });

    it('separates GitHub repository integrations from Provider Integrations view and prevents display', async () => {
      const integrationsWithGitHub = [
        ...mockIntegrations,
        {
          id: 'int-github-1',
          displayName: 'GitHub (hrushi4151)',
          providerType: 'GITHUB',
          status: 'ACTIVE',
          redactedCredentialHint: 'ghp_••••••••9999',
          createdAt: '2026-03-01T12:00:00Z',
          lastValidatedAt: '2026-03-01T12:00:00Z',
        },
      ];

      integrationsApi.list.mockResolvedValueOnce({ content: integrationsWithGitHub });
      projectApi.list.mockResolvedValueOnce(mockProjects);
      integrationsApi.listMappings.mockResolvedValue([]);
      environmentApi.list.mockResolvedValue([]);

      render(<IntegrationsView />);

      await waitFor(() => {
        expect(screen.getByText('Vercel Production Frontend')).toBeInTheDocument();
        expect(screen.getByText('Render Backend API')).toBeInTheDocument();
      });

      // GitHub repository integration must NOT be visible in Provider Integrations view
      expect(screen.queryByText('GitHub (hrushi4151)')).not.toBeInTheDocument();
      expect(screen.queryByText('ghp_••••••••9999')).not.toBeInTheDocument();
    });
  });

  describe('SyncCenterView', () => {
    const mockDriftRecords = [
      {
        id: 'drift-1',
        secretKey: 'DATABASE_URL',
        driftType: 'MISSING_ON_PROVIDER',
        severity: 'HIGH',
        status: 'DRIFT_DETECTED',
        providerResourceName: 'nextjs-web',
        providerEnvironment: 'production',
        detectedAt: '2026-03-01T14:00:00Z',
      },
    ];

    it('renders active drift findings with severity indicators', async () => {
      syncApi.getDriftRecords.mockResolvedValue({ content: mockDriftRecords });
      syncApi.getSyncJobs.mockResolvedValue({ content: [] });
      projectApi.list.mockResolvedValue([]);

      render(<SyncCenterView />);

      await waitFor(() => {
        expect(screen.getByText('DATABASE_URL')).toBeInTheDocument();
      });
      expect(screen.getAllByText('MISSING_ON_PROVIDER').length).toBeGreaterThan(0);
      expect(screen.getAllByText('HIGH').length).toBeGreaterThan(0);
    });

    it('executes dry-run simulation and renders planned operations', async () => {
      syncApi.getDriftRecords.mockResolvedValue({ content: [] });
      syncApi.getSyncJobs.mockResolvedValue({ content: [] });
      projectApi.list.mockResolvedValue([]);

      const mockDryRun = {
        scope: 'WORKSPACE',
        reconciliationPolicy: 'MIRROR_EXACT',
        totalOperations: 2,
        createCount: 1,
        updateCount: 0,
        deleteCount: 1,
        noOpCount: 0,
        plannedOperations: [
          {
            secretKey: 'REDIS_URL',
            operationType: 'CREATE',
            initialStatus: 'PENDING',
            reason: 'Missing remotely, pushing desired state',
          },
          {
            secretKey: 'OLD_UNUSED_KEY',
            operationType: 'DELETE',
            initialStatus: 'PENDING',
            reason: 'Reconciliation policy MIRROR_EXACT deletes extra remote variables',
          },
        ],
        warnings: [],
      };

      syncApi.executeDryRun.mockResolvedValueOnce(mockDryRun);

      render(<SyncCenterView />);

      await waitFor(() => {
        expect(screen.getByText(/Simulation & Sync Planner/i)).toBeInTheDocument();
      });

      // Switch to simulation tab
      fireEvent.click(screen.getByText(/Simulation & Sync Planner/i));

      await waitFor(() => {
        expect(screen.getByText('Calculate Dry-Run Plan')).toBeInTheDocument();
      });

      const simulateBtn = screen.getByText('Calculate Dry-Run Plan');
      fireEvent.click(simulateBtn);

      await waitFor(() => {
        expect(syncApi.executeDryRun).toHaveBeenCalled();
        expect(screen.getByText('REDIS_URL')).toBeInTheDocument();
        expect(screen.getByText('OLD_UNUSED_KEY')).toBeInTheDocument();
      });
    });
  });
});

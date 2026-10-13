import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MachineIdentitiesView } from '../components/machine/MachineIdentitiesView';
import { OidcProvidersView } from '../components/machine/OidcProvidersView';
import { MachineDetailsModal } from '../components/machine/MachineDetailsModal';
import { machineApi } from '../api/machine';
import { oidcApi } from '../api/oidc';
import { projectApi } from '../api/projects';
import { environmentApi } from '../api/environments';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../api/projects', () => ({
  projectApi: {
    list: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock('../api/environments', () => ({
  environmentApi: {
    list: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock('../api/machine', () => ({
  machineApi: {
    list: vi.fn(),
    create: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    disable: vi.fn(),
    enable: vi.fn(),
    revoke: vi.fn(),
    listGrants: vi.fn(),
    createGrant: vi.fn(),
    revokeGrant: vi.fn(),
    deleteGrant: vi.fn(),
    listSessions: vi.fn(),
    revokeSession: vi.fn(),
    revokeAllSessions: vi.fn(),
    generateToken: vi.fn(),
    whyAccess: vi.fn(),
  },
}));

vi.mock('../api/oidc', () => ({
  oidcApi: {
    listProviders: vi.fn(),
    createProvider: vi.fn(),
    updateProvider: vi.fn(),
    deleteProvider: vi.fn(),
    testConnection: vi.fn(),
    refreshJwks: vi.fn(),
    listTrustPolicies: vi.fn(),
    createTrustPolicy: vi.fn(),
    updateTrustPolicy: vi.fn(),
    deleteTrustPolicy: vi.fn(),
    listPolicies: vi.fn(),
    listMachinePolicies: vi.fn(),
    createPolicy: vi.fn(),
    deletePolicy: vi.fn(),
  },
}));

describe('Machine Identities & OIDC Providers UI Suite', () => {
  const mockWorkspace = { id: 'ws-test-456', name: 'DevSecOps Core' };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuth.mockReturnValue({
      activeWorkspace: mockWorkspace,
    });
  });

  describe('MachineIdentitiesView', () => {
    it('correctly unwraps raw array response from apiClient and renders machines', async () => {
      const mockMachines = [
        {
          id: 'mach-1',
          name: 'github-actions-prod-runner',
          description: 'Automated CI runner for main branch deploy',
          status: 'ACTIVE',
          maxSessionDurationSeconds: 3600,
          createdAt: '2026-10-10T12:00:00Z',
        },
        {
          id: 'mach-2',
          name: 'argocd-sync-agent',
          description: 'GitOps controller workload',
          status: 'DISABLED',
          maxSessionDurationSeconds: 1800,
          createdAt: '2026-10-11T08:00:00Z',
        },
      ];

      machineApi.list.mockResolvedValue(mockMachines);

      render(<MachineIdentitiesView />);

      await waitFor(() => {
        expect(screen.getByText('github-actions-prod-runner')).toBeInTheDocument();
        expect(screen.getByText('argocd-sync-agent')).toBeInTheDocument();
      });

      expect(screen.getByText('Automated CI runner for main branch deploy')).toBeInTheDocument();
      expect(screen.getAllByText('ACTIVE').length).toBeGreaterThan(0);
      expect(screen.getAllByText('DISABLED').length).toBeGreaterThan(0);
    });

    it('creates a new machine identity and updates list with unwrapped response', async () => {
      const existing = [];
      const createdMachine = {
        id: 'mach-new',
        name: 'gitlab-runner-staging',
        description: 'GitLab CI pipeline',
        status: 'ACTIVE',
        maxSessionDurationSeconds: 3600,
        createdAt: '2026-10-11T10:00:00Z',
      };

      machineApi.list.mockResolvedValueOnce(existing).mockResolvedValueOnce([createdMachine]);
      machineApi.create.mockResolvedValue(createdMachine);
      machineApi.get.mockResolvedValue(createdMachine);
      machineApi.listGrants.mockResolvedValue([]);
      machineApi.listSessions.mockResolvedValue([]);
      oidcApi.listMachinePolicies.mockResolvedValue([]);

      render(<MachineIdentitiesView />);

      await waitFor(() => {
        expect(screen.getByText('No Machine Identities Found')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('New Machine Identity'));

      fireEvent.change(screen.getByPlaceholderText('e.g. github-rally-ci'), {
        target: { value: 'gitlab-runner-staging' },
      });
      fireEvent.change(screen.getByPlaceholderText('e.g. CI/CD pipeline actor for deployment secrets and testing'), {
        target: { value: 'GitLab CI pipeline' },
      });

      const submitBtns = screen.getAllByRole('button', { name: 'Create Machine Identity' });
      const submitBtn = submitBtns.find((b) => b.getAttribute('type') === 'submit') || submitBtns[0];
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(machineApi.create).toHaveBeenCalledWith('ws-test-456', {
          name: 'gitlab-runner-staging',
          description: 'GitLab CI pipeline',
          type: 'CI_CD',
          expiresAt: null,
        });
      });
    });
  });

  describe('OIDC Providers View', () => {
    it('correctly unwraps raw array response and renders existing providers', async () => {
      const mockProviders = [
        {
          id: 'oidc-1',
          name: 'GitHub Actions',
          issuer: 'https://token.actions.githubusercontent.com',
          jwksUri: 'https://token.actions.githubusercontent.com/.well-known/jwks',
          providerType: 'GITHUB_ACTIONS',
          createdAt: '2026-10-10T12:00:00Z',
        },
      ];

      oidcApi.listProviders.mockResolvedValue(mockProviders);

      render(<OidcProvidersView workspaceId="ws-test-456" />);

      await waitFor(() => {
        expect(screen.getByText('GitHub Actions')).toBeInTheDocument();
        expect(screen.getByText('https://token.actions.githubusercontent.com')).toBeInTheDocument();
      });
    });

    it('handles 409 conflict gracefully when duplicate provider is added and reloads existing providers', async () => {
      const mockExisting = [
        {
          id: 'oidc-1',
          name: 'GitHub Actions',
          issuer: 'https://token.actions.githubusercontent.com',
          providerType: 'GITHUB_ACTIONS',
        },
      ];

      oidcApi.listProviders.mockResolvedValue(mockExisting);
      oidcApi.createProvider.mockRejectedValue({
        status: 409,
        payload: {
          code: 'CONFLICT',
          message: 'An OIDC provider with this issuer already exists in this workspace',
        },
      });

      render(<OidcProvidersView workspaceId="ws-test-456" />);

      await waitFor(() => {
        expect(screen.getByText('GitHub Actions')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Register OIDC Provider'));

      // Fill in duplicate details
      fireEvent.change(screen.getByPlaceholderText('e.g. GitHub Actions Production'), {
        target: { value: 'GitHub Actions' },
      });

      fireEvent.click(screen.getByRole('button', { name: 'Register Provider' }));

      await waitFor(() => {
        expect(
          screen.getByText('An OIDC provider with this issuer already exists in this workspace')
        ).toBeInTheDocument();
      });

      // Verify listProviders was refreshed on conflict
      expect(oidcApi.listProviders).toHaveBeenCalledTimes(2);
    });

    it('tests OIDC discovery connection successfully and shows discovered keys', async () => {
      const mockProviders = [
        {
          id: 'oidc-gh',
          name: 'GitHub Actions',
          issuer: 'https://token.actions.githubusercontent.com',
          jwksUri: 'https://token.actions.githubusercontent.com/.well-known/jwks',
          providerType: 'GITHUB_ACTIONS',
          status: 'ACTIVE',
        },
      ];

      oidcApi.listProviders.mockResolvedValue(mockProviders);
      oidcApi.testConnection.mockResolvedValue({
        success: true,
        status: 'SUCCESS',
        keysCount: 3,
        keysLoadedCount: 3,
        message: 'Discovered 3 valid public keys from JWKS endpoint',
      });

      render(<OidcProvidersView workspaceId="ws-test-456" />);

      await waitFor(() => {
        expect(screen.getByText('GitHub Actions')).toBeInTheDocument();
      });

      const testBtn = screen.getByRole('button', { name: /Test Discovery & JWKS/i });
      fireEvent.click(testBtn);

      await waitFor(() => {
        expect(oidcApi.testConnection).toHaveBeenCalledWith('ws-test-456', 'oidc-gh');
        expect(screen.getByText(/Discovery & JWKS Reachable/i)).toBeInTheDocument();
        expect(screen.getByText(/Discovered Public Keys: 3/i)).toBeInTheDocument();
      });
    });
  });

  describe('MachineDetailsModal & Direct Token Generation', () => {
    const mockMachine = {
      id: 'mach-prod-runner',
      name: 'k8s-pod-deployer',
      description: 'Kubernetes production secret agent',
      type: 'DAEMON',
      status: 'ACTIVE',
      maxSessionDurationSeconds: 2592000,
      createdAt: '2026-10-10T08:00:00Z',
    };

    beforeEach(() => {
      machineApi.get.mockResolvedValue(mockMachine);
      machineApi.listGrants.mockResolvedValue([
        {
          id: 'grant-1',
          scopeType: 'WORKSPACE',
          permission: 'secret.read',
          effect: 'ALLOW',
          secretPattern: 'prod/*',
        },
      ]);
      machineApi.listSessions.mockResolvedValue([
        {
          id: 'sess-1',
          status: 'ACTIVE',
          active: true,
          ipAddress: '10.0.4.12',
          userAgent: 'SecretVault-SDK/1.0',
          createdAt: '2026-10-12T10:00:00Z',
          expiresAt: '2026-11-12T10:00:00Z',
        },
      ]);
      oidcApi.listTrustPolicies.mockResolvedValue([]);
      oidcApi.listProviders.mockResolvedValue([]);
      projectApi.list.mockResolvedValue([]);
    });

    it('renders machine overview with active sessions badge and grant details', async () => {
      render(
        <MachineDetailsModal
          workspaceId="ws-test-456"
          machineId="mach-prod-runner"
          onClose={vi.fn()}
          onRefresh={vi.fn()}
        />
      );

      await waitFor(() => {
        expect(screen.getByText('k8s-pod-deployer')).toBeInTheDocument();
        expect(screen.getByText('Kubernetes production secret agent')).toBeInTheDocument();
        expect(screen.getByText('1 active')).toBeInTheDocument();
      });
    });

    it('opens Generate Token modal and receives production SDK token snippet', async () => {
      machineApi.generateToken.mockResolvedValue({
        token: 'sv_mt_test_token_abc123xyz789',
        tokenType: 'BEARER',
        expiresInSeconds: 2592000,
        expiresAt: '2026-11-12T10:00:00Z',
        sessionId: 'sess-new-123',
      });

      render(
        <MachineDetailsModal
          workspaceId="ws-test-456"
          machineId="mach-prod-runner"
          onClose={vi.fn()}
          onRefresh={vi.fn()}
        />
      );

      await waitFor(() => {
        expect(screen.getByText('k8s-pod-deployer')).toBeInTheDocument();
      });

      // Click "Generate Token" from overview lifecycle controls
      const genTokenBtn = screen.getByRole('button', { name: /Generate Token/i });
      fireEvent.click(genTokenBtn);

      await waitFor(() => {
        expect(screen.getByText(/Issue Direct Machine Token/i)).toBeInTheDocument();
      });

      // Submit token issuance
      const submitIssueBtn = screen.getByRole('button', { name: 'Issue Token' });
      fireEvent.click(submitIssueBtn);

      await waitFor(() => {
        expect(machineApi.generateToken).toHaveBeenCalledWith('ws-test-456', 'mach-prod-runner', {
          ttlSeconds: 2592000,
          description: 'Workload API Token',
        });
        expect(screen.getByText('Machine Token Issued Successfully')).toBeInTheDocument();
        expect(screen.getByText('sv_mt_test_token_abc123xyz789')).toBeInTheDocument();
      });

      // Check SDK snippets tabs (Node.js SDK, Python SDK, Java SDK, cURL)
      expect(screen.getByRole('button', { name: 'Node.js SDK' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Python SDK' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Java SDK' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'cURL' })).toBeInTheDocument();

      // Switch to Python SDK tab
      fireEvent.click(screen.getByRole('button', { name: 'Python SDK' }));
      expect(screen.getByText(/from secretvault import SecretVaultClient/i)).toBeInTheDocument();

      // Switch to Java SDK tab
      fireEvent.click(screen.getByRole('button', { name: 'Java SDK' }));
      expect(screen.getByText(/SecretVaultClient\.builder\(\)/i)).toBeInTheDocument();
    });

    it('creates a granular scoped access grant', async () => {
      machineApi.createGrant.mockResolvedValue({
        id: 'grant-new-2',
        scopeType: 'PROJECT',
        permission: 'secret.read',
        effect: 'ALLOW',
      });

      render(
        <MachineDetailsModal
          workspaceId="ws-test-456"
          machineId="mach-prod-runner"
          onClose={vi.fn()}
          onRefresh={vi.fn()}
        />
      );

      await waitFor(() => {
        expect(screen.getByText('k8s-pod-deployer')).toBeInTheDocument();
      });

      // Switch to Grants tab
      fireEvent.click(screen.getByRole('button', { name: /Grants/i }));

      // Click Add Granular Grant
      fireEvent.click(screen.getByRole('button', { name: /Add Granular Grant/i }));

      // Submit Grant Form
      const saveGrantBtn = screen.getByRole('button', { name: 'Assign Grant' });
      fireEvent.click(saveGrantBtn);

      await waitFor(() => {
        expect(machineApi.createGrant).toHaveBeenCalledWith('ws-test-456', 'mach-prod-runner', {
          scopeType: 'PROJECT',
          projectId: null,
          environmentId: null,
          secretId: null,
          permission: 'secret.read',
          effect: 'ALLOW',
          secretPattern: null,
        });
      });
    });

    it('revokes an active session token', async () => {
      machineApi.revokeSession.mockResolvedValue({ success: true });

      render(
        <MachineDetailsModal
          workspaceId="ws-test-456"
          machineId="mach-prod-runner"
          onClose={vi.fn()}
          onRefresh={vi.fn()}
        />
      );

      await waitFor(() => {
        expect(screen.getByText('k8s-pod-deployer')).toBeInTheDocument();
      });

      // Switch to Sessions tab
      fireEvent.click(screen.getByRole('button', { name: /Sessions/i }));

      await waitFor(() => {
        expect(screen.getByText('sess-1...')).toBeInTheDocument();
      });

      // Click Revoke Session
      const revokeBtn = screen.getByRole('button', { name: 'Revoke' });
      fireEvent.click(revokeBtn);

      await waitFor(() => {
        expect(machineApi.revokeSession).toHaveBeenCalledWith('ws-test-456', 'mach-prod-runner', 'sess-1');
      });
    });
  });
});


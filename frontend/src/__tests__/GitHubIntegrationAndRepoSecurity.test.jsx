import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { GitHubSettingsView } from '../components/settings/GitHubSettingsView';
import { RepositorySecurityView } from '../components/repository/RepositorySecurityView';
import { repositoryApi } from '../api/repositories';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'usr-1', fullName: 'Hrushi Admin', email: 'hrushi@secretvault.io' },
    activeWorkspace: { id: 'ws-test-1', name: 'Security Workspace' },
  }),
}));

// Mock repositoryApi
vi.mock('../api/repositories', () => ({
  repositoryApi: {
    getGitHubStatus: vi.fn(),
    connectGitHub: vi.fn(),
    refreshGitHub: vi.fn(),
    disconnectGitHub: vi.fn(),
    discoverRemoteRepositories: vi.fn(),
    connectRepository: vi.fn(),
    toggleMonitoring: vi.fn(),
    disconnectRepository: vi.fn(),
    listRepositories: vi.fn(),
    getFindingStats: vi.fn(),
    listFindings: vi.fn(),
    listScans: vi.fn(),
    listAllowlists: vi.fn(),
    triggerScan: vi.fn(),
    syncRepository: vi.fn(),
  },
}));

describe('GitHubSettingsView: Account Connection & Management', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders disconnected state and allows connecting via token', async () => {
    repositoryApi.getGitHubStatus.mockResolvedValue({
      connected: false,
    });
    repositoryApi.connectGitHub.mockResolvedValue({
      connected: true,
      username: 'octocat',
      scopes: ['repo', 'admin:repo_hook'],
      monitoredRepositoriesCount: 0,
    });

    render(<GitHubSettingsView />);

    await waitFor(() => {
      expect(screen.getByText(/GitHub Account Integration/i)).toBeInTheDocument();
      expect(screen.getByText(/NOT CONNECTED/i)).toBeInTheDocument();
    });

    const tokenInput = screen.getByPlaceholderText(/ghp_/i);
    fireEvent.change(tokenInput, { target: { value: 'ghp_mock_token_123456789' } });

    const connectButton = screen.getByRole('button', { name: /Connect GitHub Account/i });
    fireEvent.click(connectButton);

    await waitFor(() => {
      expect(repositoryApi.connectGitHub).toHaveBeenCalledWith('ws-test-1', {
        token: 'ghp_mock_token_123456789',
      });
    });
  });

  it('renders connected state with identity, scopes, and refresh button', async () => {
    repositoryApi.getGitHubStatus.mockResolvedValue({
      connected: true,
      username: 'Hrushi4151',
      avatarUrl: 'https://avatars.githubusercontent.com/u/1234567',
      scopes: ['repo', 'admin:repo_hook'],
      monitoredRepositoriesCount: 3,
      connectedAt: '2026-10-10T10:00:00Z',
    });
    repositoryApi.refreshGitHub.mockResolvedValue({
      connected: true,
      username: 'Hrushi4151',
      scopes: ['repo', 'admin:repo_hook'],
      monitoredRepositoriesCount: 3,
    });

    render(<GitHubSettingsView />);

    await waitFor(() => {
      expect(screen.getByText('@Hrushi4151')).toBeInTheDocument();
      expect(screen.getByText(/Monitored Repos:/i)).toBeInTheDocument();
    });

    const refreshBtn = screen.getByRole('button', { name: /Refresh Access/i });
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(repositoryApi.refreshGitHub).toHaveBeenCalledWith('ws-test-1');
    });
  });

  it('opens disconnect confirmation modal and calls disconnect API', async () => {
    repositoryApi.getGitHubStatus.mockResolvedValue({
      connected: true,
      username: 'Hrushi4151',
      scopes: ['repo'],
      monitoredRepositoriesCount: 1,
    });
    repositoryApi.disconnectGitHub.mockResolvedValue({});

    render(<GitHubSettingsView />);

    await waitFor(() => {
      expect(screen.getByText('@Hrushi4151')).toBeInTheDocument();
    });

    const disconnectBtn = screen.getByRole('button', { name: /Disconnect/i });
    fireEvent.click(disconnectBtn);

    await waitFor(() => {
      expect(screen.getByText(/Disconnect GitHub Account/i)).toBeInTheDocument();
      expect(screen.getByText(/Impact on Monitoring:/i)).toBeInTheDocument();
    });

    const confirmBtn = screen.getByRole('button', { name: /Confirm Disconnect/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(repositoryApi.disconnectGitHub).toHaveBeenCalledWith('ws-test-1');
    });
  });
});

describe('RepositorySecurityView: GitHub Discovery, Monitoring & Isolation', () => {
  const mockDiscoveredRepos = [
    {
      id: 101,
      owner: 'Hrushi4151',
      name: 'SecretVaultApp',
      cloneUrl: 'https://github.com/Hrushi4151/SecretVaultApp.git',
      defaultBranch: 'main',
      visibility: 'PUBLIC',
      role: 'OWNER',
      canAdminHooks: true,
      isAlreadyMonitored: false,
    },
    {
      id: 102,
      owner: 'acme-corp',
      name: 'payment-gateway',
      cloneUrl: 'https://github.com/acme-corp/payment-gateway.git',
      defaultBranch: 'main',
      visibility: 'PRIVATE',
      role: 'COLLABORATOR',
      canAdminHooks: false,
      permissionWarning: 'Push Webhooks Require Admin',
      isAlreadyMonitored: false,
    },
    {
      id: 103,
      owner: 'Hrushi4151',
      name: 'already-monitored-app',
      cloneUrl: 'https://github.com/Hrushi4151/already-monitored-app.git',
      defaultBranch: 'main',
      visibility: 'PRIVATE',
      role: 'OWNER',
      canAdminHooks: true,
      isAlreadyMonitored: true,
      lastScanStatus: 'COMPLETED',
    },
  ];

  const mockConnectedRepos = [
    {
      id: 'repo-prod-1',
      owner: 'Hrushi4151',
      name: 'SecretVaultRepo',
      visibility: 'PUBLIC',
      defaultBranch: 'main',
      provider: 'GITHUB',
      status: 'ACTIVE',
      totalFindings: 2,
      criticalFindings: 1,
    },
    {
      id: 'repo-prod-2',
      owner: 'Hrushi4151',
      name: 'legacy-api',
      visibility: 'PRIVATE',
      defaultBranch: 'master',
      provider: 'GITHUB',
      status: 'DISABLED',
      totalFindings: 0,
      criticalFindings: 0,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();

    repositoryApi.getGitHubStatus.mockResolvedValue({
      connected: true,
      username: 'Hrushi4151',
      scopes: ['repo', 'admin:repo_hook'],
      monitoredRepositoriesCount: 2,
    });

    repositoryApi.listRepositories.mockResolvedValue({
      content: mockConnectedRepos,
    });

    repositoryApi.getFindingStats.mockResolvedValue({
      activeFindings: 2,
      criticalFindings: 1,
      resolvedFindings: 5,
    });

    repositoryApi.listFindings.mockResolvedValue({ content: [] });
    repositoryApi.listScans.mockResolvedValue({ content: [] });
    repositoryApi.listAllowlists.mockResolvedValue({ content: [] });
    repositoryApi.discoverRemoteRepositories.mockResolvedValue(mockDiscoveredRepos);
    repositoryApi.connectRepository.mockResolvedValue({ id: 'repo-new-1', name: 'SecretVaultApp' });
    repositoryApi.toggleMonitoring.mockResolvedValue({ id: 'repo-prod-1', status: 'DISABLED' });
  });

  afterEach(() => {
    cleanup();
  });

  it('opens Connect modal and displays discovered repositories with role and webhook indicators', async () => {
    render(<RepositorySecurityView />);

    await waitFor(() => {
      expect(screen.getByText(/Monitored Repos/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Connect Repository/i })).toBeInTheDocument();
    });

    const connectRepoBtn = screen.getByTestId('connect-repo-btn');
    fireEvent.click(connectRepoBtn);

    await waitFor(() => {
      expect(screen.getByText(/Connect GitHub Repository/i)).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getAllByText(/Push Webhooks Supported/i).length).toBeGreaterThan(0);
      expect(screen.getByText(/Push Webhooks Require Admin/i)).toBeInTheDocument();
    });

    // Select the first repo and connect
    const repoCard = screen.getByText('SecretVaultApp');
    fireEvent.click(repoCard);

    const submitBtn = screen.getByRole('button', { name: /Connect SecretVaultApp/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(repositoryApi.connectRepository).toHaveBeenCalledWith('ws-test-1', expect.objectContaining({
        owner: 'Hrushi4151',
        name: 'SecretVaultApp',
        defaultBranch: 'main',
      }));
    });
  });

  it('renders repository monitoring status badges and toggles monitoring', async () => {
    render(<RepositorySecurityView />);

    await waitFor(() => {
      expect(screen.getByText(/Monitored Repos/i)).toBeInTheDocument();
    });

    const reposTab = screen.getByRole('button', { name: /Repositories/i });
    fireEvent.click(reposTab);

    await waitFor(() => {
      expect(screen.getByText('ACTIVE')).toBeInTheDocument();
      expect(screen.getByText('PAUSED')).toBeInTheDocument();
    });

    // Toggle active repo
    const pauseBtn = screen.getByTitle(/Pause automatic monitoring/i);
    fireEvent.click(pauseBtn);

    await waitFor(() => {
      expect(repositoryApi.toggleMonitoring).toHaveBeenCalledWith('ws-test-1', 'repo-prod-1', {
        enabled: false,
      });
    });
  });
});

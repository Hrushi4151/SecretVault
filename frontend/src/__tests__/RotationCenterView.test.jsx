import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RotationCenterView } from '../components/rotation/RotationCenterView';
import { RotationImpactModal } from '../components/rotation/RotationImpactModal';
import { rotationApi } from '../api/rotation';
import { secretApi } from '../api/secrets';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'usr-sec-1', fullName: 'Security Officer', email: 'secops@secretvault.io' },
    activeWorkspace: { id: 'ws-prod-1', name: 'Production Workspace' },
  }),
}));

// Mock Rotation API
vi.mock('../api/rotation', () => ({
  rotationApi: {
    listWorkspaceJobs: vi.fn(),
    listLeases: vi.fn(),
    listConsumers: vi.fn(),
    getPolicy: vi.fn(),
    createPolicy: vi.fn(),
    updatePolicy: vi.fn(),
    disablePolicy: vi.fn(),
    triggerRotation: vi.fn(),
    getImpact: vi.fn(),
    renewLease: vi.fn(),
    revokeLease: vi.fn(),
    disableConsumer: vi.fn(),
    retryJob: vi.fn(),
    cancelJob: vi.fn(),
    rollbackJob: vi.fn(),
    markCompromised: vi.fn(),
  },
}));

// Mock Secrets API
vi.mock('../api/secrets', () => ({
  secretApi: {
    list: vi.fn(),
    getById: vi.fn(),
    getVersions: vi.fn(),
  },
}));

describe('RotationCenterView: Rotation Center & Runtime Lifecycle', () => {
  const mockSecrets = [
    { id: 'sec-db-1', name: 'DATABASE_URL', projectId: 'prj-1', environmentId: 'env-prod' },
    { id: 'sec-api-2', name: 'STRIPE_API_KEY', projectId: 'prj-1', environmentId: 'env-prod' },
  ];

  const mockJobs = [
    {
      id: 'job-101',
      secretId: 'sec-db-1',
      status: 'ACTIVE',
      triggerType: 'SCHEDULED',
      targetVersionNumber: 2,
      previousVersionNumber: 1,
      startedAt: '2026-10-04T12:00:00Z',
      completedAt: null,
    },
  ];

  const mockLeases = [
    {
      id: 'lease-99',
      secretId: 'sec-db-1',
      status: 'ACTIVE',
      ttlSeconds: 3600,
      ipAddress: '10.0.1.5',
      userAgent: 'secretvault-sdk-java/1.0.0',
      expiresAt: '2026-10-04T18:00:00Z',
    },
  ];

  const mockConsumers = [
    {
      id: 'consumer-alpha',
      name: 'payment-service-pod-1',
      consumerType: 'KUBERNETES_POD',
      status: 'ACTIVE',
      currentAcknowledgedVersion: 2,
      supportsDynamicRefresh: true,
      lastHeartbeatAt: '2026-10-04T14:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();

    secretApi.list.mockResolvedValue({
      data: { content: mockSecrets },
    });

    rotationApi.listWorkspaceJobs.mockResolvedValue({
      data: { content: mockJobs },
    });

    rotationApi.listLeases.mockResolvedValue({
      data: { content: mockLeases },
    });

    rotationApi.listConsumers.mockResolvedValue({
      data: { content: mockConsumers },
    });

    rotationApi.getImpact.mockResolvedValue({
      data: {
        secretId: 'sec-db-1',
        secretName: 'DATABASE_URL',
        currentActiveVersion: 1,
        totalAffectedConsumers: 3,
        dynamicRefreshCount: 2,
        restartRequiredCount: 1,
        activeLeasesCount: 1,
        affectedEnvironments: ['production'],
        consumers: [
          { consumerName: 'payment-service-pod-1', consumerType: 'KUBERNETES_POD', status: 'ACTIVE' },
        ],
        providerIntegrations: ['VERCEL', 'RENDER'],
      },
    });
  });

  it('renders RotationCenterView header, badge, and navigation tabs', async () => {
    render(<RotationCenterView />);

    expect(screen.getByRole('heading', { level: 1, name: /^Secret Rotation & Runtime Lifecycle$/i })).toBeInTheDocument();
    expect(screen.getByText(/Remediate Compromise/i)).toBeInTheDocument();

    // Verify all 6 tabs
    expect(screen.getByRole('button', { name: /Overview/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Policies/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Execution Jobs/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Secret Leases/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Workloads & SDKs/i })).toBeInTheDocument();
    expect(screen.getByText('Rotation Wizard')).toBeInTheDocument();
  });

  it('renders Overview tab with summary statistics and quick action triggers', async () => {
    render(<RotationCenterView />);

    await waitFor(() => {
      expect(rotationApi.listWorkspaceJobs).toHaveBeenCalledWith('ws-prod-1', null, 0, 10);
      expect(rotationApi.listLeases).toHaveBeenCalled();
      expect(rotationApi.listConsumers).toHaveBeenCalled();
    });

    expect(screen.getByText(/Active Runtime Leases/i)).toBeInTheDocument();
    expect(screen.getByText(/Registered Consumers/i)).toBeInTheDocument();
    expect(screen.getByText(/Launch Rotation Wizard/i)).toBeInTheDocument();
  });

  it('switches to Policies tab and renders secret policy management', async () => {
    render(<RotationCenterView />);

    const policiesTab = screen.getByRole('button', { name: /^Policies$/i });
    fireEvent.click(policiesTab);

    await waitFor(() => {
      expect(screen.getByText(/Automated Rotation Policies/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/Search secrets\.\.\./i)).toBeInTheDocument();
    });
  });

  it('switches to Execution Jobs tab and renders rotation jobs with status badges', async () => {
    render(<RotationCenterView />);

    const jobsTab = screen.getByRole('button', { name: /^Execution Jobs$/i });
    fireEvent.click(jobsTab);

    await waitFor(() => {
      expect(screen.getByText(/Rotation Execution Jobs/i)).toBeInTheDocument();
      expect(rotationApi.listWorkspaceJobs).toHaveBeenCalled();
    });
  });

  it('switches to Secret Leases tab and displays active ephemeral leases', async () => {
    render(<RotationCenterView />);

    const leasesTab = screen.getByRole('button', { name: /^Secret Leases$/i });
    fireEvent.click(leasesTab);

    await waitFor(() => {
      expect(screen.getByText(/Runtime Secret Leases/i)).toBeInTheDocument();
      expect(rotationApi.listLeases).toHaveBeenCalled();
    });
  });

  it('switches to Workloads & SDKs tab and displays consumer registry with heartbeats', async () => {
    render(<RotationCenterView />);

    const consumersTab = screen.getByRole('button', { name: /^Workloads & SDKs$/i });
    fireEvent.click(consumersTab);

    await waitFor(() => {
      expect(screen.getByText(/Workload Consumer Registry/i)).toBeInTheDocument();
      expect(rotationApi.listConsumers).toHaveBeenCalled();
    });
  });

  it('switches to Rotation Wizard tab and displays guided rollover configuration', async () => {
    render(<RotationCenterView />);

    const wizardTab = screen.getByText('Rotation Wizard');
    fireEvent.click(wizardTab);

    await waitFor(() => {
      expect(screen.getByText(/Zero-Downtime Rotation Wizard/i)).toBeInTheDocument();
      expect(screen.getByText(/Exit Wizard/i)).toBeInTheDocument();
    });
  });

  it('opens and executes Emergency Compromise workflow with reason and instant rollover', async () => {
    rotationApi.markCompromised.mockResolvedValue({ message: 'Compromise remediation dispatched' });

    render(<RotationCenterView />);

    // Click Remediate Compromise button
    const emergencyButton = screen.getByText(/Remediate Compromise/i);
    fireEvent.click(emergencyButton);

    await waitFor(() => {
      expect(screen.getByText(/Compromised Secret Incident Remediation/i)).toBeInTheDocument();
    });

    const incidentInput = screen.getByPlaceholderText(/e\.g\. Credential detected in public GitHub repository/i);
    fireEvent.change(incidentInput, { target: { value: 'API key leaked in external Slack channel' } });

    const executeButton = screen.getByRole('button', { name: /Execute Emergency Remediation/i });
    fireEvent.click(executeButton);

    await waitFor(() => {
      expect(rotationApi.markCompromised).toHaveBeenCalledWith(
        'ws-prod-1',
        expect.any(String),
        expect.objectContaining({
          incidentDetails: 'API key leaked in external Slack channel',
          rotateImmediately: true,
          revokeLeasesImmediately: true,
        })
      );
    });
  });

  it('renders RotationImpactModal and displays dependency graph telemetry', async () => {
    render(
      <RotationImpactModal
        workspaceId="ws-prod-1"
        secretId="sec-db-1"
        secretName="DATABASE_URL"
        isOpen={true}
        onClose={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(rotationApi.getImpact).toHaveBeenCalledWith('ws-prod-1', 'sec-db-1');
      expect(screen.getByText(/Runtime Dependency Graph & Impact Telemetry/i)).toBeInTheDocument();
      expect(screen.getByText(/payment-service-pod-1/i)).toBeInTheDocument();
      expect(screen.getByText(/KUBERNETES_POD/i)).toBeInTheDocument();
    });
  });

  it('handles API error states gracefully without crashing', async () => {
    rotationApi.listWorkspaceJobs.mockRejectedValue(new Error('Network error fetching jobs'));

    render(<RotationCenterView />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: /^Secret Rotation & Runtime Lifecycle$/i })).toBeInTheDocument();
    });
  });

  it('Security Invariant: Sensitive secret values remain masked and never displayed in plaintext', async () => {
    render(<RotationCenterView />);

    // Ensure common secret patterns do not appear in DOM
    const bodyText = document.body.textContent;
    expect(bodyText).not.toMatch(/sk_live_[0-9a-zA-Z]{24}/);
    expect(bodyText).not.toMatch(/postgres:\/\/.*:.*@/);
    expect(bodyText).not.toMatch(/ghp_[0-9a-zA-Z]{36}/);
  });
});

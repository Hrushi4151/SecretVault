import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RepositorySecurityView } from '../components/repository/RepositorySecurityView';
import { repositoryApi } from '../api/repositories';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'usr-sec-1', fullName: 'Security Officer', email: 'secops@secretvault.io' },
    activeWorkspace: { id: 'ws-prod-1', name: 'Production Workspace' },
  }),
}));

// Mock Repository API
vi.mock('../api/repositories', () => ({
  repositoryApi: {
    listRepositories: vi.fn(),
    getFindingStats: vi.fn(),
    listFindings: vi.fn(),
    listScans: vi.fn(),
    triggerScan: vi.fn(),
    remediateFinding: vi.fn(),
    getRemediationTargetInfo: vi.fn(),
    listAllowlists: vi.fn(),
    revokeAllowlist: vi.fn(),
  },
}));

describe('RepositorySecurityRemediation: Frontend Remediation Modal & Workflow', () => {
  const mockRepos = [
    {
      id: 'repo-101',
      name: 'core-payment-gateway',
      defaultBranch: 'main',
      url: 'https://github.com/Hrushi4151/SecretVault',
    },
  ];

  const mockFinding = {
    id: 'find-999',
    repositoryId: 'repo-101',
    secretType: 'AWS_ACCESS_KEY',
    severity: 'CRITICAL',
    confidence: 'HIGH',
    status: 'DETECTED',
    remediationStatus: 'PENDING',
    filePath: 'src/config/aws.js',
    lineNumber: 14,
    maskedValue: 'AK************14',
    fingerprint: 'fp_abc1234567890',
    commitSha: 'commit_sha_xyz',
    branch: 'main',
    matchedSecretId: 'sec-aws-1',
  };

  const mockTargetInfoManaged = {
    findingId: 'find-999',
    secretType: 'AWS_ACCESS_KEY',
    severity: 'CRITICAL',
    maskedValue: 'AK************14',
    filePath: 'src/config/aws.js',
    lineNumber: 14,
    commitSha: 'commit_sha_xyz',
    branch: 'main',
    isManaged: true,
    matchedSecretId: 'sec-aws-1',
    matchedSecretName: 'PROD_AWS_ACCESS_KEY',
    environmentName: 'production',
    projectName: 'Core Payments',
    currentVersionNumber: 3,
    providerName: 'Amazon Web Services (AWS)',
    rotationSupported: true,
    revocationSupported: true,
    rotationStrategy: 'EMERGENCY',
    impactWarning: 'Executing emergency rotation invalidates active consumer leases and rolls out Version 4.',
    manualInstructions: ['Step 1: Check pods', 'Step 2: Confirm rotation'],
  };

  beforeEach(() => {
    vi.clearAllMocks();

    repositoryApi.listRepositories.mockResolvedValue({
      content: mockRepos,
    });

    repositoryApi.getFindingStats.mockResolvedValue({
      totalFindings: 1,
      criticalFindings: 1,
      resolvedFindings: 0,
    });

    repositoryApi.listScans.mockResolvedValue({
      content: [],
    });

    repositoryApi.listFindings.mockResolvedValue({
      content: [mockFinding],
      totalElements: 1,
    });

    repositoryApi.listAllowlists.mockResolvedValue({
      content: [
        {
          id: 'allow-1',
          fingerprint: 'fp_safe_mock',
          detectorType: 'AWS Access Key',
          filePattern: 'test/fixtures/**',
          reason: 'Synthetic unit test credential verified safe',
          createdBy: 'usr-sec-1',
          active: true,
          createdAt: '2026-10-09T20:00:00Z',
        },
      ],
    });

    repositoryApi.getRemediationTargetInfo.mockResolvedValue(mockTargetInfoManaged);
  });

  it('renders repository findings list and opens remediation modal with pre-fetched target info', async () => {
    render(<RepositorySecurityView />);

    await waitFor(() => {
      expect(screen.getByText(/src\/config\/aws.js:14/i)).toBeInTheDocument();
      expect(screen.getByText(/AWS_ACCESS_KEY/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /^Remediate$/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /^Remediate$/i }));

    await waitFor(() => {
      expect(screen.getByText(/Remediate Exposed Credential/i)).toBeInTheDocument();
      expect(screen.getByText(/PROD_AWS_ACCESS_KEY/i)).toBeInTheDocument();
      expect(screen.getByText(/Core Payments/i)).toBeInTheDocument();
      expect(screen.getByText(/Version 3/i)).toBeInTheDocument();
    });
  });

  it('Action A: Trigger Emergency Secret Rotation executes verified workflow and displays success', async () => {
    repositoryApi.remediateFinding.mockResolvedValue({
      id: 'job-rot-1',
      status: 'ROTATION_SUCCEEDED',
      detailsJson: '{"status":"SUCCESS"}',
    });

    render(<RepositorySecurityView />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Remediate/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Remediate/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Execute Emergency Rotation/i })).toBeInTheDocument();
    });

    const submitBtn = screen.getByRole('button', { name: /Execute Emergency Rotation/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(repositoryApi.remediateFinding).toHaveBeenCalledWith(
        'ws-prod-1',
        'find-999',
        expect.objectContaining({
          action: 'ROTATE_SECRET',
        })
      );
      expect(screen.getByText(/OPERATION STATUS: ROTATION_SUCCEEDED/i)).toBeInTheDocument();
    });
  });

  it('Action B: Revoke Secret & Mark Compromised requires explicit confirmation checkbox', async () => {
    repositoryApi.remediateFinding.mockResolvedValue({
      id: 'job-rev-1',
      status: 'REVOCATION_SUCCEEDED',
      detailsJson: '{"leasesRevoked":true}',
    });

    render(<RepositorySecurityView />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Remediate/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Remediate/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /2\. Revoke & Compromise/i })).toBeInTheDocument();
    });

    // Switch to Revoke tab
    fireEvent.click(screen.getByRole('button', { name: /2\. Revoke & Compromise/i }));

    const submitBtn = screen.getByRole('button', { name: /Revoke Secret & Mark Compromised/i });

    // Submit revocation before confirmation -> shows validation error
    fireEvent.click(submitBtn);
    expect(screen.getByText(/explicitly confirm that this credential is leaked/i)).toBeInTheDocument();

    // Check confirmation checkbox
    const confirmCheckbox = screen.getByRole('checkbox');
    fireEvent.click(confirmCheckbox);

    // Submit revocation after confirmation
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(repositoryApi.remediateFinding).toHaveBeenCalledWith(
        'ws-prod-1',
        'find-999',
        expect.objectContaining({
          action: 'REVOKE_SECRET',
        })
      );
      expect(screen.getByText(/OPERATION STATUS: REVOCATION_SUCCEEDED/i)).toBeInTheDocument();
    });
  });

  it('Action C: Allowlist requires meaningful reason >= 10 chars and submits narrow scope', async () => {
    repositoryApi.remediateFinding.mockResolvedValue({
      id: 'job-fp-1',
      status: 'COMPLETED',
    });

    render(<RepositorySecurityView />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Remediate/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Remediate/i }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /3\. Allowlist Rule/i })).toBeInTheDocument();
    });

    // Switch to False Positive tab
    fireEvent.click(screen.getByRole('button', { name: /3\. Allowlist Rule/i }));

    const submitBtn = screen.getByRole('button', { name: /Add to Allowlist & Suppress/i });

    // Enter reason too short (< 10 chars) and submit -> shows validation error
    const reasonInput = screen.getByPlaceholderText(/Synthetic non-functional test fixture/i);
    fireEvent.change(reasonInput, { target: { value: 'short' } });
    fireEvent.click(submitBtn);
    expect(screen.getByText(/at least 10 characters/i)).toBeInTheDocument();

    // Enter valid meaningful reason >= 10 chars
    fireEvent.change(reasonInput, { target: { value: 'Synthetic test dummy credential in mock suite' } });

    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(repositoryApi.remediateFinding).toHaveBeenCalledWith(
        'ws-prod-1',
        'find-999',
        expect.objectContaining({
          action: 'MARK_FALSE_POSITIVE',
          notes: 'Synthetic test dummy credential in mock suite',
          filePattern: 'src/config/aws.js',
        })
      );
    });
  });

  it('Allowlist Tab: displays active suppressions and supports revocation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    repositoryApi.revokeAllowlist.mockResolvedValue({});

    render(<RepositorySecurityView />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Allowlist & Suppressions/i })).toBeInTheDocument();
    });

    // Switch to Allowlist tab
    fireEvent.click(screen.getByRole('button', { name: /Allowlist & Suppressions/i }));

    await waitFor(() => {
      expect(screen.getByText(/Synthetic unit test credential verified safe/i)).toBeInTheDocument();
      expect(screen.getByText(/test\/fixtures\/\*\*/i)).toBeInTheDocument();
    });

    const revokeRuleBtn = screen.getByRole('button', { name: /Revoke Rule/i });
    fireEvent.click(revokeRuleBtn);

    await waitFor(() => {
      expect(repositoryApi.revokeAllowlist).toHaveBeenCalledWith('ws-prod-1', 'allow-1', 'Revoked by user via UI');
    });
  });
});

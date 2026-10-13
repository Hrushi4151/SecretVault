import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SecurityOverviewView } from '../components/security/SecurityOverviewView';
import { securityApi } from '../api/security';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'user-sec-01', fullName: 'Lead Security Engineer', email: 'sec@secretvault.io' },
    activeWorkspace: { id: 'ws-security-audit', name: 'Security Audit Workspace' }
  })
}));

// Mock securityApi
vi.mock('../api/security', () => ({
  securityApi: {
    getOverview: vi.fn(),
    getPosture: vi.fn(),
    getRiskBreakdown: vi.fn(),
    getFindings: vi.fn(),
    getTimeline: vi.fn(),
    triggerAnalysis: vi.fn(),
    updateFindingStatus: vi.fn()
  }
}));

const mockOverviewData = {
  workspaceId: 'ws-security-audit',
  overallRiskScore: 13,
  overallRiskLevel: 'LOW',
  totalActiveFindings: 2,
  criticalFindings: 0,
  highFindings: 1,
  recentEventsCount: 42,
  recentDenialsCount: 2,
  accessReviewStatus: 'HEALTHY',
  topRiskFindings: [],
  recentTimeline: [],
  generatedAt: new Date().toISOString()
};

const mockPostureData = {
  workspaceId: 'ws-security-audit',
  overallRiskScore: 13,
  overallRiskLevel: 'LOW',
  openFindings: 2,
  criticalFindings: 0,
  highFindings: 1,
  mediumFindings: 1,
  lowFindings: 0,
  unresolvedAccessFindings: 1,
  jitActivity24h: 3,
  authorizationDenials24h: 2,
  adminChanges24h: 1,
  accessReviewStatus: 'HEALTHY',
  privilegedUserCount: 2,
  unusedGrantCount: 0,
  dormantPrivilegedAccountCount: 0,
  riskFactors: [
    {
      factorName: 'High Severity Findings',
      scoreContribution: 15,
      explanation: '1 high severity finding requires review'
    }
  ],
  calculatedAt: new Date().toISOString()
};

const mockRiskData = {
  score: 13,
  level: 'LOW',
  factors: [
    {
      factorName: 'High Severity Findings',
      scoreContribution: 15,
      explanation: '1 high severity finding requires review'
    }
  ]
};

const mockFindingsData = {
  content: [
    {
      id: 'finding-01',
      category: 'EXCESSIVE_PRIVILEGE',
      severity: 'HIGH',
      status: 'OPEN',
      title: 'Unscoped Standing Administrator Privileges',
      safeDescription: 'User assigned standing owner permissions without scoping boundary.',
      remediationGuidance: 'Constrain user to scoped role or ephemeral JIT grant.',
      occurrenceCount: 1,
      fingerprint: 'fp-sha256-abc1234',
      evidenceJson: JSON.stringify({ userId: 'user-target-01', role: 'OWNER' })
    },
    {
      id: 'finding-02',
      category: 'SECRET_HYGIENE',
      severity: 'MEDIUM',
      status: 'ACKNOWLEDGED',
      title: 'Stale Development API Token',
      safeDescription: 'API token unrotated for 120 days in staging environment.',
      remediationGuidance: 'Trigger zero-downtime secret rotation cycle.',
      occurrenceCount: 2,
      fingerprint: 'fp-sha256-def5678',
      evidenceJson: JSON.stringify({ secretKey: 'STAGING_API_KEY' })
    }
  ],
  totalElements: 2,
  totalPages: 1
};

const mockTimelineData = [
  {
    id: 'tl-01',
    timelineType: 'SECURITY_EVENT',
    title: 'SECURITY_ANALYSIS_EXECUTED',
    safeDescription: 'Security Event: SECURITY_ANALYSIS_EXECUTED (Outcome: SUCCESS)',
    severity: 'INFO',
    outcome: 'SUCCESS',
    timestamp: new Date().toISOString()
  }
];

describe('SecurityOverviewView: Security Center & Posture Management', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    securityApi.getOverview.mockResolvedValue(mockOverviewData);
    securityApi.getPosture.mockResolvedValue(mockPostureData);
    securityApi.getRiskBreakdown.mockResolvedValue(mockRiskData);
    securityApi.getFindings.mockResolvedValue(mockFindingsData);
    securityApi.getTimeline.mockResolvedValue(mockTimelineData);
  });

  it('renders SecurityOverviewView with posture dial gauge, score, and breakdown metrics', async () => {
    render(<SecurityOverviewView />);

    // Await async fetch
    await screen.findByText(/Security Center & Posture Management/i);
    expect(screen.getByText(/Posture Directive 0x4F9/i)).toBeInTheDocument();

    // Check Posture Index: 100 - 13 = 87 Grade B
    expect(screen.getByText('87')).toBeInTheDocument();
    expect(screen.getByText('/100')).toBeInTheDocument();
    expect(screen.getByText('GRADE B')).toBeInTheDocument();

    // Check breakdown categories
    expect(screen.getAllByText(/Hygiene/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/Rotation/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/Access/i).length).toBeGreaterThanOrEqual(1);
  });

  it('renders findings inventory and filters by severity correctly', async () => {
    render(<SecurityOverviewView />);

    await screen.findByText(/Unscoped Standing Administrator Privileges/i);
    expect(screen.getByText(/Stale Development API Token/i)).toBeInTheDocument();

    // Click on "High" filter tab
    const highFilterBtn = screen.getByRole('button', { name: /High/i });
    fireEvent.click(highFilterBtn);

    // High finding should remain, Medium should be filtered out
    expect(screen.getByText(/Unscoped Standing Administrator Privileges/i)).toBeInTheDocument();
    expect(screen.queryByText(/Stale Development API Token/i)).not.toBeInTheDocument();
  });

  it('opens detailed inspection modal when clicking Inspect on a finding', async () => {
    render(<SecurityOverviewView />);

    await screen.findByText(/Unscoped Standing Administrator Privileges/i);

    const inspectButtons = screen.getAllByRole('button', { name: /Inspect/i });
    fireEvent.click(inspectButtons[0]);

    // Modal contents
    await screen.findByText(/Deterministic Evidence/i);
    expect(screen.getAllByText(/Constrain user to scoped role or ephemeral JIT grant/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('fp-sha256-abc1234')).toBeInTheDocument();
  });

  it('calls updateFindingStatus when clicking Acknowledge', async () => {
    securityApi.updateFindingStatus.mockResolvedValue({});
    render(<SecurityOverviewView />);

    await screen.findByText(/Unscoped Standing Administrator Privileges/i);

    const ackButton = screen.getByRole('button', { name: /Acknowledge/i });
    fireEvent.click(ackButton);

    await waitFor(() => {
      expect(securityApi.updateFindingStatus).toHaveBeenCalledWith(
        'ws-security-audit',
        'finding-01',
        'ACKNOWLEDGED',
        ''
      );
    });
  });

  it('triggers on-demand deep audit scan when Run Deep Audit is clicked', async () => {
    securityApi.triggerAnalysis.mockResolvedValue({
      workspaceId: 'ws-security-audit',
      rulesExecuted: 14,
      findingsEvaluated: 2,
      durationMs: 45
    });

    render(<SecurityOverviewView />);

    await screen.findByText(/Security Center & Posture Management/i);

    const runScanBtn = screen.getByRole('button', { name: /Run Deep Audit/i });
    fireEvent.click(runScanBtn);

    await waitFor(() => {
      expect(securityApi.triggerAnalysis).toHaveBeenCalledWith('ws-security-audit');
    });

    await screen.findByText(/Deep scan completed in 45ms across 14 detection rules/i);
  });

  it('renders error state with retry option when server connection fails', async () => {
    securityApi.getOverview.mockRejectedValue(new Error('Connection refused by telemetry server'));
    securityApi.getPosture.mockRejectedValue(new Error('Connection refused by telemetry server'));

    render(<SecurityOverviewView />);

    await screen.findByText(/Security Center Unavailable/i);
    expect(screen.getByText(/Unable to retrieve workspace security posture from server/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Retry Connection/i })).toBeInTheDocument();
  });
});

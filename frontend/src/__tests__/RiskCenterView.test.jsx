import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RiskCenterView } from '../components/risk/RiskCenterView';
import { securityApi } from '../api/security';
import { projectsApi } from '../api/projects';
import { repositoriesApi } from '../api/repositories';

// Mock dependencies
vi.mock('../api/security', () => ({
  securityApi: {
    getRiskBreakdown: vi.fn(),
    getFindings: vi.fn(),
    getFindingDetails: vi.fn(),
    updateFindingStatus: vi.fn(),
    assignFinding: vi.fn(),
    triggerAnalysis: vi.fn()
  }
}));

vi.mock('../api/projects', () => ({
  projectsApi: {
    getProjects: vi.fn()
  }
}));

vi.mock('../api/repositories', () => ({
  repositoriesApi: {
    getRepositories: vi.fn()
  }
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    activeWorkspace: { id: 'ws-risk-center-test', name: 'Test Risk Workspace' }
  })
}));

describe('RiskCenterView: Risk Center & Vulnerability Governance', () => {
  const mockRiskBreakdown = {
    score: 73,
    level: 'HIGH',
    factors: [
      {
        factorId: 'critical_findings',
        title: 'Critical Security Findings',
        weight: 25,
        explanation: '1 unresolved critical severity finding requires immediate remediation',
        resourceIds: ['finding-crit-1']
      },
      {
        factorId: 'overdue_access_reviews',
        title: 'Delinquent Access Certification',
        weight: 15,
        explanation: '1 active campaign overdue for compliance sign-off',
        resourceIds: ['campaign-1']
      }
    ]
  };

  const mockFindingsPage = {
    content: [
      {
        id: 'finding-01',
        workspaceId: 'ws-risk-center-test',
        projectId: 'proj-gateway',
        category: 'REPOSITORY_SECRET_EXPOSURE',
        severity: 'CRITICAL',
        confidence: 'HIGH',
        status: 'OPEN',
        title: 'Exposed PRODUCTION_API_KEY in src/config/gateway.js',
        safeDescription: 'Detected plaintext AWS secret access key in source repository.',
        remediationGuidance: 'Immediately rotate credentials in AWS IAM and purge git history.',
        occurrenceCount: 2,
        lastObservedAt: new Date().toISOString(),
        evidence: {
          filePath: 'src/config/gateway.js',
          lineNumber: 42,
          secretType: 'PRODUCTION_API_KEY',
          maskedValue: 'AKIA••••••••••••'
        }
      },
      {
        id: 'finding-02',
        workspaceId: 'ws-risk-center-test',
        projectId: 'proj-gateway',
        category: 'DORMANT_PRIVILEGED_ACCESS',
        severity: 'MEDIUM',
        confidence: 'HIGH',
        status: 'ACKNOWLEDGED',
        title: 'Dormant Administrator Grant in Database Layer',
        safeDescription: 'User has had standing administrative role without login for 45 days.',
        remediationGuidance: 'Revoke administrator role or switch to JIT approval.',
        occurrenceCount: 1,
        lastObservedAt: new Date().toISOString(),
        evidence: {}
      }
    ],
    totalPages: 1,
    totalElements: 2,
    number: 0
  };

  beforeEach(() => {
    vi.clearAllMocks();
    securityApi.getRiskBreakdown.mockResolvedValue(mockRiskBreakdown);
    securityApi.getFindings.mockResolvedValue(mockFindingsPage);
    projectsApi.getProjects.mockResolvedValue([{ id: 'proj-gateway', name: 'API Gateway Project' }]);
    repositoriesApi.getRepositories.mockResolvedValue([{ id: 'repo-1', name: 'gateway-service' }]);
  });

  it('renders RiskCenterView with authoritative score and risk factors', async () => {
    render(<RiskCenterView />);

    await screen.findByText(/Risk Center & Vulnerability Governance/i);
    expect(screen.getByText('73')).toBeInTheDocument();
    expect(screen.getByText(/HIGH RISK/i)).toBeInTheDocument();

    // Contributing factors
    expect(screen.getByText('Critical Security Findings')).toBeInTheDocument();
    expect(screen.getByText('+25 pts')).toBeInTheDocument();
    expect(screen.getByText('Delinquent Access Certification')).toBeInTheDocument();
    expect(screen.getByText('+15 pts')).toBeInTheDocument();
  });

  it('renders findings inventory and applies severity filter', async () => {
    render(<RiskCenterView />);

    await screen.findByText(/Exposed PRODUCTION_API_KEY in src\/config\/gateway.js/i);
    expect(screen.getByText(/Dormant Administrator Grant/i)).toBeInTheDocument();

    // Click Critical filter button
    const critFilterBtn = screen.getByRole('button', { name: /^CRITICAL$/i });
    fireEvent.click(critFilterBtn);

    await waitFor(() => {
      expect(securityApi.getFindings).toHaveBeenCalledWith(
        'ws-risk-center-test',
        expect.objectContaining({ severity: 'CRITICAL' })
      );
    });
  });

  it('opens detailed inspection modal with file, line, and purge command', async () => {
    render(<RiskCenterView />);

    await screen.findByText(/Exposed PRODUCTION_API_KEY in src\/config\/gateway.js/i);

    const inspectButtons = screen.getAllByRole('button', { name: /Inspect/i });
    fireEvent.click(inspectButtons[0]);

    // Modal contents
    await waitFor(() => {
      expect(screen.getAllByText(/src\/config\/gateway.js/i).length).toBeGreaterThanOrEqual(2);
    });
    expect(screen.getByText('L42')).toBeInTheDocument();
    expect(screen.getByText('AKIA••••••••••••')).toBeInTheDocument();
    expect(screen.getByText(/git filter-repo --path src\/config\/gateway.js --invert-paths/i)).toBeInTheDocument();
  });

  it('transitions finding status to ACKNOWLEDGED when clicking Acknowledge', async () => {
    securityApi.updateFindingStatus.mockResolvedValue({});
    render(<RiskCenterView />);

    await screen.findByText(/Exposed PRODUCTION_API_KEY in src\/config\/gateway.js/i);

    const ackButton = screen.getByRole('button', { name: /^Acknowledge$/i });
    fireEvent.click(ackButton);

    await waitFor(() => {
      expect(securityApi.updateFindingStatus).toHaveBeenCalledWith(
        'ws-risk-center-test',
        'finding-01',
        'ACKNOWLEDGED',
        ''
      );
    });
  });

  it('triggers on-demand audit scan when Run Deep Audit is clicked', async () => {
    securityApi.triggerAnalysis.mockResolvedValue({
      workspaceId: 'ws-risk-center-test',
      rulesExecuted: 14
    });

    render(<RiskCenterView />);

    await screen.findByText(/Risk Center & Vulnerability Governance/i);

    const scanBtn = screen.getByRole('button', { name: /Run Deep Audit/i });
    fireEvent.click(scanBtn);

    await waitFor(() => {
      expect(securityApi.triggerAnalysis).toHaveBeenCalledWith('ws-risk-center-test');
    });
  });

  it('triggers cross-navigation to audit logs when button is clicked', async () => {
    const onNavigateToAudit = vi.fn();
    render(<RiskCenterView onNavigateToAudit={onNavigateToAudit} />);

    await screen.findByText(/Risk Center & Vulnerability Governance/i);

    const auditBtns = screen.getAllByRole('button', { name: /Audit Trail/i });
    fireEvent.click(auditBtns[0]);

    expect(onNavigateToAudit).toHaveBeenCalled();
  });
});

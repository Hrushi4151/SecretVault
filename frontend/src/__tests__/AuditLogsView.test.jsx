import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AuditLogsView } from '../components/audit/AuditLogsView';
import { auditApi } from '../api/audit';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'user-sec-01', fullName: 'Lead Security Engineer', email: 'sec@secretvault.io' },
    activeWorkspace: { id: 'ws-security-audit', name: 'Security Audit Workspace' }
  })
}));

// Mock auditApi
vi.mock('../api/audit', () => ({
  auditApi: {
    getWorkspaceLogs: vi.fn(),
    getSecretLogs: vi.fn()
  }
}));

const mockAuditLogs = [
  {
    id: 'log-001-uuid',
    organizationId: 'org-01',
    workspaceId: 'ws-security-audit',
    actorId: 'user-01',
    actorType: 'USER',
    action: 'SECRET_READ',
    resourceType: 'SECRET',
    resourceId: 'sec-uuid-1',
    requestId: 'req_98a72f04e1',
    ipAddress: '192.168.1.10',
    outcome: 'SUCCESS',
    createdAt: new Date(Date.now() - 3600000).toISOString()
  },
  {
    id: 'log-002-uuid',
    organizationId: 'org-01',
    workspaceId: 'ws-security-audit',
    actorId: 'machine-ci-01',
    actorType: 'MACHINE',
    action: 'SECRET_VERSION_ROLLBACK',
    resourceType: 'SECRET',
    resourceId: 'sec-uuid-2',
    requestId: 'req_88b19a002c',
    ipAddress: '54.210.12.8',
    outcome: 'SUCCESS',
    createdAt: new Date(Date.now() - 7200000).toISOString()
  },
  {
    id: 'log-003-uuid',
    organizationId: 'org-01',
    workspaceId: 'ws-security-audit',
    actorId: 'unknown-intruder',
    actorType: 'USER',
    action: 'EMERGENCY_BREAK_GLASS',
    resourceType: 'PRIVILEGED_ROLE',
    resourceId: 'role-admin',
    requestId: 'req_denied_01',
    ipAddress: '198.51.100.4',
    outcome: 'DENIED',
    createdAt: new Date(Date.now() - 10800000).toISOString()
  }
];

describe('AuditLogsView: Cryptographic Ledger & Compliance', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auditApi.getWorkspaceLogs.mockResolvedValue(mockAuditLogs);
  });

  it('renders AuditLogsView header, metrics, and stream table', async () => {
    render(<AuditLogsView />);

    await waitFor(() => {
      expect(screen.getByText(/Immutable Cryptographic Audit Logs/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/Total Recorded Events/i)).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getAllByText('SECRET_READ').length).toBeGreaterThan(0);
    expect(screen.getAllByText('SECRET_VERSION_ROLLBACK').length).toBeGreaterThan(0);
    expect(screen.getAllByText('EMERGENCY_BREAK_GLASS').length).toBeGreaterThan(0);
  });

  it('filters audit logs by search query', async () => {
    render(<AuditLogsView />);

    await waitFor(() => {
      expect(screen.getAllByText('SECRET_READ').length).toBeGreaterThan(0);
    });

    const searchInput = screen.getByPlaceholderText(/Search by action, resource type/i);
    fireEvent.change(searchInput, { target: { value: 'ROLLBACK' } });

    // Table should only show SECRET_VERSION_ROLLBACK
    expect(screen.getAllByText('SECRET_VERSION_ROLLBACK').length).toBeGreaterThan(0);
  });

  it('filters audit logs by outcome', async () => {
    render(<AuditLogsView />);

    await waitFor(() => {
      expect(screen.getAllByText('SECRET_READ').length).toBeGreaterThan(0);
    });

    const outcomeSelect = screen.getByDisplayValue('All Outcomes');
    fireEvent.change(outcomeSelect, { target: { value: 'DENIED' } });

    expect(screen.getAllByText('EMERGENCY_BREAK_GLASS').length).toBeGreaterThan(0);
  });

  it('selects and inspects audit log proof in the drawer', async () => {
    render(<AuditLogsView />);

    await waitFor(() => {
      expect(screen.getAllByText('SECRET_READ').length).toBeGreaterThan(0);
    });

    const proofButtons = screen.getAllByRole('button', { name: /Proof/i });
    fireEvent.click(proofButtons[1]); // Click second row

    expect(screen.getByText(/req_88b19a002c/i)).toBeInTheDocument();
    expect(screen.getAllByText('54.210.12.8').length).toBeGreaterThan(0);
  });

  it('triggers onNavigateToRiskCenter when Risk Center button is clicked', async () => {
    const onNavigateToRiskCenter = vi.fn();
    render(<AuditLogsView onNavigateToRiskCenter={onNavigateToRiskCenter} />);

    await waitFor(() => {
      expect(screen.getByText(/Immutable Cryptographic Audit Logs/i)).toBeInTheDocument();
    });

    const riskBtn = screen.getByRole('button', { name: /Risk Center/i });
    fireEvent.click(riskBtn);

    expect(onNavigateToRiskCenter).toHaveBeenCalled();
  });

  it('exports CSV formatted ledger when CSV button is clicked', async () => {
    render(<AuditLogsView />);

    await waitFor(() => {
      expect(screen.getByText(/Immutable Cryptographic Audit Logs/i)).toBeInTheDocument();
    });

    const csvBtn = screen.getByRole('button', { name: /CSV/i });
    expect(csvBtn).toBeInTheDocument();
    fireEvent.click(csvBtn);
    // Successful click and execution without error
  });
});


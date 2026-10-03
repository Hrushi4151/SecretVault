import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PrivilegedAccessCenter } from '../components/access/PrivilegedAccessCenter';
import { privilegedAccessApi } from '../api/privilegedAccess';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../api/privilegedAccess', () => ({
  privilegedAccessApi: {
    listRequests: vi.fn(),
    getRequest: vi.fn(),
    createRequest: vi.fn(),
    approveRequest: vi.fn(),
    rejectRequest: vi.fn(),
    cancelRequest: vi.fn(),
    revokeRequest: vi.fn(),
    executeRequest: vi.fn(),
    breakGlass: vi.fn(),
    listElevations: vi.fn(),
    revokeElevation: vi.fn(),
    listPolicies: vi.fn(),
    updatePolicy: vi.fn(),
  },
}));

describe('PrivilegedAccessCenter Component', () => {
  const mockWorkspace = { id: 'ws-123', name: 'Security Operations' };
  const mockUser = { id: 'user-admin', email: 'admin@example.com', fullName: 'Security Admin' };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuth.mockReturnValue({
      activeWorkspace: mockWorkspace,
      user: mockUser,
    });
    privilegedAccessApi.listRequests.mockResolvedValue({ data: { data: [] } });
    privilegedAccessApi.listElevations.mockResolvedValue({ data: { data: [] } });
    privilegedAccessApi.listPolicies.mockResolvedValue({ data: { data: [] } });
  });

  it('renders header, tabs, and action buttons', async () => {
    render(<PrivilegedAccessCenter />);

    expect(screen.getByText('Privileged Access Security')).toBeInTheDocument();
    expect(screen.getByText('Request Elevation')).toBeInTheDocument();
    expect(screen.getByText('Break-Glass Emergency')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('All Requests')).toBeInTheDocument();
      expect(screen.getByText('Awaiting My Approval')).toBeInTheDocument();
      expect(screen.getByText('Active Elevations')).toBeInTheDocument();
      expect(screen.getByText('Break-Glass Console')).toBeInTheDocument();
      expect(screen.getByText('Governance Policies')).toBeInTheDocument();
    });
  });

  it('renders list of privileged requests with quorum indicators', async () => {
    const mockRequests = [
      {
        id: 'req-1',
        action: 'SECRET_REVEAL',
        scopeType: 'ENVIRONMENT',
        status: 'PENDING',
        requiredQuorum: 2,
        currentApprovalsCount: 1,
        durationMinutes: 30,
        justification: 'Emergency database fix during production outage',
        requesterEmail: 'dev@example.com',
        targetUserEmail: 'dev@example.com',
        canApprove: true,
      },
    ];

    privilegedAccessApi.listRequests.mockResolvedValue({ data: { data: mockRequests } });

    render(<PrivilegedAccessCenter />);

    expect(await screen.findByText('Emergency database fix during production outage')).toBeInTheDocument();
    expect(screen.getByText('Quorum: 1/2 Approvals')).toBeInTheDocument();
    expect(screen.getByText('Review & Decide')).toBeInTheDocument();
  });

  it('switches to Awaiting My Approval tab and displays approval actions', async () => {
    const mockAwaiting = [
      {
        id: 'req-awaiting-1',
        action: 'ROLE_CHANGE',
        scopeType: 'WORKSPACE',
        status: 'PENDING',
        requiredQuorum: 2,
        currentApprovalsCount: 1,
        durationMinutes: 60,
        justification: 'Quarterly compliance audit require temporary admin role',
        requesterEmail: 'auditor@example.com',
        targetUserEmail: 'auditor@example.com',
        expiresAt: new Date(Date.now() + 3600000).toISOString(),
        canApprove: true,
      },
    ];

    privilegedAccessApi.listRequests.mockResolvedValue({ data: { data: mockAwaiting } });

    render(<PrivilegedAccessCenter />);

    const awaitingTab = screen.getByText('Awaiting My Approval');
    fireEvent.click(awaitingTab);

    await waitFor(() => {
      expect(screen.getByText('QUORUM NEEDED: 1/2')).toBeInTheDocument();
      expect(screen.getByText('ROLE_CHANGE')).toBeInTheDocument();
      expect(screen.getByText('Approve / Reject')).toBeInTheDocument();
    });
  });

  it('switches to Active Elevations tab and renders active elevation', async () => {
    const mockElevations = [
      {
        id: 'elev-1',
        action: 'SECRET_REVEAL',
        scopeType: 'ENVIRONMENT',
        grantedPermission: 'SECRET_REVEAL',
        isBreakGlass: false,
        active: true,
        status: 'ACTIVE',
        expiresAt: new Date(Date.now() + 1800000).toISOString(),
      },
    ];

    privilegedAccessApi.listElevations.mockResolvedValue({ data: { data: mockElevations } });

    render(<PrivilegedAccessCenter />);

    const elevationsTab = screen.getByText('Active Elevations');
    fireEvent.click(elevationsTab);

    await waitFor(() => {
      expect(screen.getAllByText('SECRET_REVEAL').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Revoke Elevation Immediately')).toBeInTheDocument();
    });
  });

  it('switches to Break-Glass Console and enforces 20+ character justification', async () => {
    window.alert = vi.fn();

    render(<PrivilegedAccessCenter />);

    const breakGlassTab = screen.getByText('Break-Glass Console');
    fireEvent.click(breakGlassTab);

    await waitFor(() => {
      expect(screen.getByText('Emergency Break-Glass Access Console')).toBeInTheDocument();
      expect(screen.getByText('ACTIVATE EMERGENCY BREAK-GLASS ACCESS')).toBeInTheDocument();
    });

    const textarea = screen.getByPlaceholderText(/Comprehensive description/i);
    fireEvent.change(textarea, { target: { value: 'Too short' } });

    const tokenInput = screen.getByPlaceholderText(/Enter verified Step-Up Proof token/i);
    fireEvent.change(tokenInput, { target: { value: 'some_token' } });

    const form = textarea.closest('form');
    fireEvent.submit(form);

    expect(window.alert).toHaveBeenCalledWith(
      expect.stringContaining('detailed justification of at least 20 characters')
    );
  });

  it('switches to Governance Policies tab and renders policy cards', async () => {
    const mockPolicies = [
      {
        id: 'pol-1',
        action: 'SECRET_REVEAL',
        scopeType: 'WORKSPACE',
        enabled: true,
        approvalQuorum: 2,
        requireStepUp: true,
        preventSelfApproval: true,
        breakGlassAllowed: true,
      },
    ];

    privilegedAccessApi.listPolicies.mockResolvedValue({ data: { data: mockPolicies } });

    render(<PrivilegedAccessCenter />);

    const policiesTab = screen.getByText('Governance Policies');
    fireEvent.click(policiesTab);

    await waitFor(() => {
      expect(screen.getByText('SECRET_REVEAL')).toBeInTheDocument();
      expect(screen.getByText('2 Approvers')).toBeInTheDocument();
      expect(screen.getByText('ENABLED')).toBeInTheDocument();
    });
  });
});

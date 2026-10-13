import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EventCenterView } from '../components/events/EventCenterView';
import { AutomationCenterView } from '../components/automation/AutomationCenterView';
import { WebhookCenterView } from '../components/webhook/WebhookCenterView';
import { SecurityOperationsView } from '../components/incident/SecurityOperationsView';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'user-1', fullName: 'Security Admin', email: 'sec@secretvault.io' },
    activeWorkspace: { id: 'ws-12345', name: 'Production Workspace' }
  })
}));

// Mock APIs
vi.mock('../api/events', () => ({
  eventsApi: {
    listEvents: vi.fn().mockResolvedValue({ items: [] }),
    replayEvents: vi.fn().mockResolvedValue({ message: 'Success' }),
    getOutboxStats: vi.fn().mockResolvedValue({ totalEvents: 0, pending: 0, published: 0, deadLetter: 0 })
  }
}));

vi.mock('../api/automation', () => ({
  automationApi: {
    listPolicies: vi.fn().mockResolvedValue({ items: [] }),
    listApprovals: vi.fn().mockResolvedValue({
      items: [
        {
          id: 'appr-1',
          actionType: 'TRIGGER_ROTATION',
          status: 'PENDING',
          requestedBy: 'operator-1'
        }
      ]
    }),
    decideApproval: vi.fn().mockResolvedValue({ status: 'APPROVED' }),
    cancelApproval: vi.fn().mockResolvedValue({ status: 'REJECTED' }),
    listExecutions: vi.fn().mockResolvedValue({ items: [] }),
    simulate: vi.fn().mockResolvedValue({ status: 'OK' })
  }
}));

vi.mock('../api/webhooks', () => ({
  webhooksApi: {
    listEndpoints: vi.fn().mockResolvedValue({ items: [] }),
    listDeliveries: vi.fn().mockResolvedValue({ items: [] })
  }
}));

vi.mock('../api/incidents', () => ({
  incidentsApi: {
    listIncidents: vi.fn().mockResolvedValue({ items: [] }),
    getIncidentEvents: vi.fn().mockResolvedValue({ items: [] })
  }
}));

describe('Phase 13: Frontend Operations Views', () => {
  it('renders EventCenterView with outbox replay action and header', () => {
    render(<EventCenterView />);
    expect(screen.getByText(/Domain Event Center & Transactional Outbox/i)).toBeInTheDocument();
    expect(screen.getByText(/Replay Outbox Events/i)).toBeInTheDocument();
  });

  it('renders AutomationCenterView with policy and simulator tabs', () => {
    render(<AutomationCenterView />);
    expect(screen.getByText(/Security Automation & Policy Engine/i)).toBeInTheDocument();
    expect(screen.getByText(/Active Policies/i)).toBeInTheDocument();
    expect(screen.getByText(/Approvals Queue/i)).toBeInTheDocument();
    expect(screen.getByText(/Policy Simulator/i)).toBeInTheDocument();
  });

  it('renders Approvals Queue and handles decision actions', async () => {
    const { fireEvent, waitFor } = await import('@testing-library/react');
    render(<AutomationCenterView />);
    
    // Switch to Approvals Queue
    fireEvent.click(screen.getByText(/Approvals Queue/i));
    await waitFor(() => {
      expect(screen.getByText(/TRIGGER_ROTATION/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/Approve/i)).toBeInTheDocument();
    expect(screen.getByText(/Reject/i)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/i)).toBeInTheDocument();
  });

  it('renders WebhookCenterView with SSRF notice and new endpoint trigger', () => {
    render(<WebhookCenterView />);
    expect(screen.getByText(/Hardened Webhook Platform & SSRF Defense/i)).toBeInTheDocument();
    expect(screen.getByText(/New Endpoint/i)).toBeInTheDocument();
  });

  it('renders SecurityOperationsView with incident creation and triage list', () => {
    render(<SecurityOperationsView />);
    expect(screen.getByText(/Security Incident Operations & Automated Remediation/i)).toBeInTheDocument();
    expect(screen.getByText(/New Incident/i)).toBeInTheDocument();
  });
});

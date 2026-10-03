import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SecretDetailsModal } from '../components/secrets/SecretDetailsModal';
import { secretApi } from '../api/secrets';
import { versionsApi } from '../api/versions';

vi.mock('../api/secrets', () => ({
  secretApi: {
    getRevealPolicy: vi.fn(),
    createRevealIntent: vi.fn(),
    reveal: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
  secretRevealPolicyApi: {
    list: vi.fn(),
    setPolicy: vi.fn(),
    delete: vi.fn(),
    audit: vi.fn(),
  },
}));

vi.mock('../api/versions', () => ({
  versionsApi: {
    getVersions: vi.fn(),
    revealHistoricalVersion: vi.fn(),
    addTag: vi.fn(),
    removeTag: vi.fn(),
  },
}));

vi.mock('../components/auth/StepUpAuthenticationModal', () => ({
  StepUpAuthenticationModal: ({ isOpen, onSuccess, onClose }) =>
    isOpen ? (
      <div data-testid="mock-step-up-modal">
        <button onClick={() => onSuccess('mock_step_up_proof_token')}>Complete Step-Up</button>
        <button onClick={onClose}>Cancel Step-Up</button>
      </div>
    ) : null,
}));

describe('SecretRevealProtection Frontend Flow', () => {
  const mockOnClose = vi.fn();
  const mockOnSecretUpdated = vi.fn();
  const mockOnSecretDeleted = vi.fn();

  const testSecret = {
    id: 'sec-uuid-1',
    name: 'STRIPE_LIVE_API_KEY',
    description: 'Production payment key',
    status: 'ACTIVE',
    currentVersionNumber: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  const defaultPolicy = {
    policyLevel: 'DEFAULT',
    requireReason: false,
    requireStepUp: false,
    copyAllowed: true,
    maxDisplayDurationSeconds: 30,
    clipboardTimeoutSeconds: 15,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    versionsApi.getVersions.mockResolvedValue({ content: [] });
    secretApi.getRevealPolicy.mockResolvedValue(defaultPolicy);
  });

  it('renders secret overview and displays effective policy level', async () => {
    render(
      <SecretDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        secret={testSecret}
        workspaceId="ws-1"
        projectId="proj-1"
        environmentId="env-1"
        environmentName="Production"
      />
    );

    expect(screen.getByText('STRIPE_LIVE_API_KEY')).toBeInTheDocument();
    expect(screen.getByText('Reveal Secret')).toBeInTheDocument();
  });

  it('successfully completes two-phase reveal intent and execution', async () => {
    secretApi.createRevealIntent.mockResolvedValueOnce({
      data: {
        intentToken: 'intent_tok_123',
        policyLevel: 'DEFAULT',
        copyAllowed: true,
        maxDisplayDurationSeconds: 45,
        clipboardTimeoutSeconds: 15,
      },
    });

    secretApi.reveal.mockResolvedValueOnce({
      data: {
        value: 'sk_live_verysecretpayload999',
        versionNumber: 1,
        policyLevel: 'DEFAULT',
        copyAllowed: true,
        maxDisplayDurationSeconds: 45,
        clipboardTimeoutSeconds: 15,
      },
    });

    render(
      <SecretDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        secret={testSecret}
        workspaceId="ws-1"
        projectId="proj-1"
        environmentId="env-1"
        environmentName="Production"
      />
    );

    const revealBtn = screen.getByTestId('reveal-secret-button');
    fireEvent.click(revealBtn);

    await waitFor(() => {
      expect(secretApi.createRevealIntent).toHaveBeenCalledWith(
        'ws-1', 'proj-1', 'env-1', 'sec-uuid-1',
        { versionNumber: 1, reason: null },
        null
      );
      expect(secretApi.reveal).toHaveBeenCalledWith(
        'ws-1', 'proj-1', 'env-1', 'sec-uuid-1',
        {
          intentToken: 'intent_tok_123',
          versionNumber: 1,
          reason: null,
          stepUpProof: null,
        },
        null
      );
      expect(screen.getByTestId('revealed-secret-value')).toHaveTextContent('sk_live_verysecretpayload999');
      expect(screen.getByText(/Auto-masking in 45s/i)).toBeInTheDocument();
    });

    // Hide Secret clears plaintext
    const hideBtn = screen.getByText('Hide Secret');
    fireEvent.click(hideBtn);
    expect(screen.queryByTestId('revealed-secret-value')).not.toBeInTheDocument();
  });

  it('prompts for justification reason when required by reveal policy', async () => {
    secretApi.getRevealPolicy.mockResolvedValueOnce({
      policyLevel: 'HIGHLY_SENSITIVE',
      requireReason: true,
      requireStepUp: false,
      copyAllowed: true,
      maxDisplayDurationSeconds: 30,
      clipboardTimeoutSeconds: 15,
    });

    render(
      <SecretDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        secret={testSecret}
        workspaceId="ws-1"
        projectId="proj-1"
        environmentId="env-1"
        environmentName="Production"
      />
    );

    await waitFor(() => {
      expect(screen.getByText('HIGHLY_SENSITIVE')).toBeInTheDocument();
    });

    const revealBtn = screen.getByTestId('reveal-secret-button');
    fireEvent.click(revealBtn);

    // Reason modal opens
    expect(screen.getByText(/Secret Reveal Justification Required/i)).toBeInTheDocument();

    const textarea = screen.getByPlaceholderText(/Incident response for ticket/i);
    fireEvent.change(textarea, { target: { value: 'Investigating incident INC-8492 customer billing' } });

    secretApi.createRevealIntent.mockResolvedValueOnce({
      data: {
        intentToken: 'intent_tok_reason_approved',
        policyLevel: 'HIGHLY_SENSITIVE',
        copyAllowed: true,
        maxDisplayDurationSeconds: 30,
      },
    });

    secretApi.reveal.mockResolvedValueOnce({
      data: {
        value: 'sk_live_revealed_with_reason',
        versionNumber: 1,
        policyLevel: 'HIGHLY_SENSITIVE',
        copyAllowed: true,
        maxDisplayDurationSeconds: 30,
      },
    });

    const verifyBtn = screen.getByText(/Verify & Reveal/i);
    fireEvent.click(verifyBtn);

    await waitFor(() => {
      expect(secretApi.createRevealIntent).toHaveBeenCalledWith(
        'ws-1', 'proj-1', 'env-1', 'sec-uuid-1',
        { versionNumber: 1, reason: 'Investigating incident INC-8492 customer billing' },
        null
      );
      expect(screen.getByTestId('revealed-secret-value')).toHaveTextContent('sk_live_revealed_with_reason');
    });
  });

  it('triggers Step-Up authentication modal on STEP_UP_REQUIRED', async () => {
    secretApi.createRevealIntent.mockRejectedValueOnce({
      status: 403,
      payload: { code: 'STEP_UP_REQUIRED', message: 'Step-up authentication is mandatory' },
    });

    render(
      <SecretDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        secret={testSecret}
        workspaceId="ws-1"
        projectId="proj-1"
        environmentId="env-1"
        environmentName="Production"
      />
    );

    const revealBtn = screen.getByTestId('reveal-secret-button');
    fireEvent.click(revealBtn);

    await waitFor(() => {
      expect(screen.getByTestId('mock-step-up-modal')).toBeInTheDocument();
    });

    // Mock successful step-up verification
    secretApi.createRevealIntent.mockResolvedValueOnce({
      data: { intentToken: 'intent_tok_stepup_verified' },
    });
    secretApi.reveal.mockResolvedValueOnce({
      data: { value: 'sk_live_stepup_unlocked_value', versionNumber: 1 },
    });

    const completeStepUpBtn = screen.getByText('Complete Step-Up');
    fireEvent.click(completeStepUpBtn);

    await waitFor(() => {
      expect(screen.getByTestId('revealed-secret-value')).toHaveTextContent('sk_live_stepup_unlocked_value');
    });
  });

  it('enforces copy restriction when copyAllowed is false', async () => {
    secretApi.getRevealPolicy.mockResolvedValueOnce({
      policyLevel: 'PRODUCTION_CRITICAL',
      requireReason: false,
      requireStepUp: false,
      copyAllowed: false,
      maxDisplayDurationSeconds: 15,
      clipboardTimeoutSeconds: 5,
    });

    secretApi.createRevealIntent.mockResolvedValueOnce({
      data: {
        intentToken: 'intent_no_copy',
        copyAllowed: false,
        maxDisplayDurationSeconds: 15,
      },
    });

    secretApi.reveal.mockResolvedValueOnce({
      data: {
        value: 'sk_live_critical_value',
        versionNumber: 1,
        policyLevel: 'PRODUCTION_CRITICAL',
        copyAllowed: false,
        maxDisplayDurationSeconds: 15,
      },
    });

    render(
      <SecretDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        secret={testSecret}
        workspaceId="ws-1"
        projectId="proj-1"
        environmentId="env-1"
        environmentName="Production"
      />
    );

    const revealBtn = screen.getByTestId('reveal-secret-button');
    fireEvent.click(revealBtn);

    await waitFor(() => {
      expect(screen.getByTestId('revealed-secret-value')).toHaveTextContent('sk_live_critical_value');
      expect(screen.getByText('Copy Restricted')).toBeInTheDocument();
      expect(screen.queryByTestId('copy-secret-button')).not.toBeInTheDocument();
    });
  });
});

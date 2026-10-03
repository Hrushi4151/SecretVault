import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { StepUpAuthenticationModal } from '../components/auth/StepUpAuthenticationModal';
import { authApi } from '../api/auth';

vi.mock('../api/auth', () => ({
  authApi: {
    createStepUpChallenge: vi.fn(),
    verifyStepUpPassword: vi.fn(),
    verifyStepUpTotp: vi.fn(),
    verifyStepUpRecoveryCode: vi.fn(),
  },
}));

describe('StepUpAuthenticationModal Component', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();
  const testContext = {
    workspaceId: 'ws-1',
    projectId: 'proj-1',
    environmentId: 'env-prod',
    secretId: 'sec-1',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('initializes challenge on open and renders password verification tab', async () => {
    authApi.createStepUpChallenge.mockResolvedValueOnce({
      challengeId: 'chal-123',
      action: 'SECRET_REVEAL',
      supportedFactors: ['PASSWORD'],
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    render(
      <StepUpAuthenticationModal
        isOpen={true}
        onClose={mockOnClose}
        action="SECRET_REVEAL"
        context={testContext}
        actionTitle="Reveal Protected Secret"
        actionDescription="Step-up verification required."
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText('Reveal Protected Secret')).toBeInTheDocument();
    expect(screen.getByText('Step-up verification required.')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Enter your account password')).toBeInTheDocument();
    });

    expect(authApi.createStepUpChallenge).toHaveBeenCalledWith({
      action: 'SECRET_REVEAL',
      context: testContext,
    });
  });

  it('handles successful password verification and triggers onSuccess with proof token', async () => {
    authApi.createStepUpChallenge.mockResolvedValueOnce({
      challengeId: 'chal-pwd-123',
      action: 'SECRET_REVEAL',
      supportedFactors: ['PASSWORD'],
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    authApi.verifyStepUpPassword.mockResolvedValueOnce({
      proofToken: 'stup_proof_token_abc123',
      action: 'SECRET_REVEAL',
      factorUsed: 'PASSWORD',
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    render(
      <StepUpAuthenticationModal
        isOpen={true}
        onClose={mockOnClose}
        action="SECRET_REVEAL"
        context={testContext}
        onSuccess={mockOnSuccess}
      />
    );

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Enter your account password')).toBeInTheDocument();
    });

    const passwordInput = screen.getByPlaceholderText('Enter your account password');
    fireEvent.change(passwordInput, { target: { value: 'SecretPassword123!' } });

    const submitBtn = screen.getByText('Verify & Continue');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(authApi.verifyStepUpPassword).toHaveBeenCalledWith({
        challengeId: 'chal-pwd-123',
        password: 'SecretPassword123!',
      });
      expect(mockOnSuccess).toHaveBeenCalledWith('stup_proof_token_abc123');
      expect(mockOnClose).toHaveBeenCalled();
    });
  });

  it('renders TOTP and Password tabs when MFA is enabled and verifies TOTP code', async () => {
    authApi.createStepUpChallenge.mockResolvedValueOnce({
      challengeId: 'chal-totp-123',
      action: 'SECRET_REVEAL',
      supportedFactors: ['PASSWORD', 'TOTP', 'RECOVERY_CODE'],
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    authApi.verifyStepUpTotp.mockResolvedValueOnce({
      proofToken: 'stup_totp_proof_xyz',
      action: 'SECRET_REVEAL',
      factorUsed: 'TOTP',
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    render(
      <StepUpAuthenticationModal
        isOpen={true}
        onClose={mockOnClose}
        action="SECRET_REVEAL"
        context={testContext}
        onSuccess={mockOnSuccess}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Authenticator App')).toBeInTheDocument();
      expect(screen.getByText('Account Password')).toBeInTheDocument();
      expect(screen.getByText('Recovery Code')).toBeInTheDocument();
    });

    const digitInputs = screen.getAllByRole('textbox');
    expect(digitInputs).toHaveLength(6);

    // Paste 6 digits into first box
    fireEvent.change(digitInputs[0], { target: { value: '654321' } });

    const submitBtn = screen.getByText('Verify & Continue');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(authApi.verifyStepUpTotp).toHaveBeenCalledWith({
        challengeId: 'chal-totp-123',
        code: '654321',
      });
      expect(mockOnSuccess).toHaveBeenCalledWith('stup_totp_proof_xyz');
    });
  });

  it('switches to recovery code tab and verifies backup recovery code', async () => {
    authApi.createStepUpChallenge.mockResolvedValueOnce({
      challengeId: 'chal-rec-123',
      action: 'SECRET_REVEAL',
      supportedFactors: ['PASSWORD', 'TOTP', 'RECOVERY_CODE'],
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    authApi.verifyStepUpRecoveryCode.mockResolvedValueOnce({
      proofToken: 'stup_rec_proof_999',
      action: 'SECRET_REVEAL',
      factorUsed: 'RECOVERY_CODE',
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    render(
      <StepUpAuthenticationModal
        isOpen={true}
        onClose={mockOnClose}
        action="SECRET_REVEAL"
        context={testContext}
        onSuccess={mockOnSuccess}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Recovery Code')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Recovery Code'));

    const recoveryInput = screen.getByPlaceholderText('e.g. A1B2-C3D4-E5');
    fireEvent.change(recoveryInput, { target: { value: 'REC-9999-8888' } });

    fireEvent.click(screen.getByText('Verify & Continue'));

    await waitFor(() => {
      expect(authApi.verifyStepUpRecoveryCode).toHaveBeenCalledWith({
        challengeId: 'chal-rec-123',
        recoveryCode: 'REC-9999-8888',
      });
      expect(mockOnSuccess).toHaveBeenCalledWith('stup_rec_proof_999');
    });
  });

  it('displays error message on verification failure and clears input', async () => {
    authApi.createStepUpChallenge.mockResolvedValueOnce({
      challengeId: 'chal-err-123',
      action: 'SECRET_REVEAL',
      supportedFactors: ['PASSWORD'],
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    authApi.verifyStepUpPassword.mockRejectedValueOnce(
      new Error('Invalid password for step-up verification.')
    );

    render(
      <StepUpAuthenticationModal
        isOpen={true}
        onClose={mockOnClose}
        action="SECRET_REVEAL"
        context={testContext}
        onSuccess={mockOnSuccess}
      />
    );

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Enter your account password')).toBeInTheDocument();
    });

    const passwordInput = screen.getByPlaceholderText('Enter your account password');
    fireEvent.change(passwordInput, { target: { value: 'WrongPassword' } });

    fireEvent.click(screen.getByText('Verify & Continue'));

    await waitFor(() => {
      expect(screen.getByText('Invalid password for step-up verification.')).toBeInTheDocument();
      expect(passwordInput.value).toBe('');
      expect(mockOnSuccess).not.toHaveBeenCalled();
    });
  });

  it('clicking Cancel closes the modal', async () => {
    authApi.createStepUpChallenge.mockResolvedValueOnce({
      challengeId: 'chal-cancel-123',
      action: 'SECRET_REVEAL',
      supportedFactors: ['PASSWORD'],
      expiresAt: new Date(Date.now() + 300000).toISOString(),
    });

    render(
      <StepUpAuthenticationModal
        isOpen={true}
        onClose={mockOnClose}
        action="SECRET_REVEAL"
        context={testContext}
        onSuccess={mockOnSuccess}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Cancel')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Cancel'));
    expect(mockOnClose).toHaveBeenCalled();
  });
});

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MfaChallengeScreen } from '../components/auth/MfaChallengeScreen';
import { AuthProvider, useAuth } from '../context/AuthContext';

const mockCompleteMfaTotpLogin = vi.fn();
const mockCompleteMfaRecoveryLogin = vi.fn();

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    completeMfaTotpLogin: mockCompleteMfaTotpLogin,
    completeMfaRecoveryLogin: mockCompleteMfaRecoveryLogin,
  }),
  AuthProvider: ({ children }) => <div>{children}</div>,
}));

describe('MfaChallengeScreen Component', () => {
  const mockOnBackToLogin = vi.fn();
  const mockOnSuccess = vi.fn();
  const testChallengeId = 'challenge-1234-uuid';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders TOTP verification screen with 6 digit inputs', () => {
    render(
      <MfaChallengeScreen
        challengeId={testChallengeId}
        onBackToLogin={mockOnBackToLogin}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText('Verify Your Identity')).toBeInTheDocument();
    expect(screen.getByText('Authenticator One-Time Code')).toBeInTheDocument();
    expect(screen.getByText('Verify & Continue')).toBeInTheDocument();
    expect(screen.getByText('Use a backup recovery code instead')).toBeInTheDocument();
  });

  it('allows pasting a 6-digit code and submitting verification', async () => {
    mockCompleteMfaTotpLogin.mockResolvedValueOnce({
      accessToken: 'token-abc',
      refreshToken: 'refresh-xyz',
    });

    render(
      <MfaChallengeScreen
        challengeId={testChallengeId}
        onBackToLogin={mockOnBackToLogin}
        onSuccess={mockOnSuccess}
      />
    );

    const inputs = screen.getAllByRole('textbox');
    expect(inputs).toHaveLength(6);

    // Simulate pasting 6 digits into first box
    fireEvent.change(inputs[0], { target: { value: '987654' } });

    expect(inputs[0].value).toBe('9');
    expect(inputs[1].value).toBe('8');
    expect(inputs[2].value).toBe('7');
    expect(inputs[3].value).toBe('6');
    expect(inputs[4].value).toBe('5');
    expect(inputs[5].value).toBe('4');

    const submitBtn = screen.getByText('Verify & Continue');
    expect(submitBtn).not.toBeDisabled();
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(mockCompleteMfaTotpLogin).toHaveBeenCalledWith({
        challengeId: testChallengeId,
        code: '987654',
      });
      expect(mockOnSuccess).toHaveBeenCalled();
    });
  });

  it('switches to recovery code mode and submits recovery code', async () => {
    mockCompleteMfaRecoveryLogin.mockResolvedValueOnce({
      accessToken: 'token-abc',
      refreshToken: 'refresh-xyz',
    });

    render(
      <MfaChallengeScreen
        challengeId={testChallengeId}
        onBackToLogin={mockOnBackToLogin}
        onSuccess={mockOnSuccess}
      />
    );

    fireEvent.click(screen.getByText('Use a backup recovery code instead'));

    expect(screen.getByText('Backup Recovery Code')).toBeInTheDocument();
    const recoveryInput = screen.getByPlaceholderText('XXXX-XXXX-XXXX');

    fireEvent.change(recoveryInput, { target: { value: '2345-6789-ABCD' } });

    const submitBtn = screen.getByText('Verify Recovery Code');
    expect(submitBtn).not.toBeDisabled();
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(mockCompleteMfaRecoveryLogin).toHaveBeenCalledWith({
        challengeId: testChallengeId,
        recoveryCode: '2345-6789-ABCD',
      });
      expect(mockOnSuccess).toHaveBeenCalled();
    });
  });

  it('displays session expired message and returns to login on expired challenge', async () => {
    mockCompleteMfaTotpLogin.mockRejectedValueOnce({
      status: 401,
      payload: { code: 'MFA_CHALLENGE_EXPIRED', message: 'Challenge expired' },
    });

    render(
      <MfaChallengeScreen
        challengeId={testChallengeId}
        onBackToLogin={mockOnBackToLogin}
        onSuccess={mockOnSuccess}
      />
    );

    const inputs = screen.getAllByRole('textbox');
    fireEvent.change(inputs[0], { target: { value: '123456' } });
    fireEvent.click(screen.getByText('Verify & Continue'));

    await waitFor(() => {
      expect(screen.getByText('Return to Sign In')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Return to Sign In'));
    expect(mockOnBackToLogin).toHaveBeenCalled();
  });

  it('displays rate limit exceeded message on HTTP 429', async () => {
    mockCompleteMfaTotpLogin.mockRejectedValueOnce({
      status: 429,
      payload: { code: 'RATE_LIMIT_EXCEEDED', message: 'Too many attempts' },
    });

    render(
      <MfaChallengeScreen
        challengeId={testChallengeId}
        onBackToLogin={mockOnBackToLogin}
        onSuccess={mockOnSuccess}
      />
    );

    const inputs = screen.getAllByRole('textbox');
    fireEvent.change(inputs[0], { target: { value: '111111' } });
    fireEvent.click(screen.getByText('Verify & Continue'));

    await waitFor(() => {
      expect(screen.getByText('Too many verification attempts. Please wait before trying again.')).toBeInTheDocument();
    });
  });
});

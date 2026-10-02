import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MfaDisableModal } from '../components/settings/mfa/MfaDisableModal';
import { authApi } from '../api/auth';

vi.mock('../api/auth', () => ({
  authApi: {
    disableMfa: vi.fn(),
  },
}));

describe('MfaDisableModal Component', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders step-up password and factor inputs', () => {
    render(
      <MfaDisableModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText('Disable Multi-Factor Authentication')).toBeInTheDocument();
    expect(screen.getByText('Security Warning')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Enter your current password')).toBeInTheDocument();
    expect(screen.getByText('Authenticator App')).toBeInTheDocument();
    expect(screen.getByText('Recovery Code')).toBeInTheDocument();
  });

  it('submits disable request with password and TOTP code', async () => {
    authApi.disableMfa.mockResolvedValueOnce(null);

    render(
      <MfaDisableModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const passwordInput = screen.getByPlaceholderText('Enter your current password');
    fireEvent.change(passwordInput, { target: { value: 'SecretPassword123!' } });

    const totpInput = screen.getByPlaceholderText('000000');
    fireEvent.change(totpInput, { target: { value: '654321' } });

    const submitBtn = screen.getByText('Confirm & Disable MFA');
    expect(submitBtn).not.toBeDisabled();
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(authApi.disableMfa).toHaveBeenCalledWith({
        password: 'SecretPassword123!',
        code: '654321',
        recoveryCode: null,
      });
      expect(mockOnSuccess).toHaveBeenCalled();
    });
  });

  it('submits disable request with password and backup recovery code', async () => {
    authApi.disableMfa.mockResolvedValueOnce(null);

    render(
      <MfaDisableModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const passwordInput = screen.getByPlaceholderText('Enter your current password');
    fireEvent.change(passwordInput, { target: { value: 'SecretPassword123!' } });

    // Switch to recovery code tab
    fireEvent.click(screen.getByText('Recovery Code'));

    const recoveryInput = screen.getByPlaceholderText('XXXX-XXXX-XXXX');
    fireEvent.change(recoveryInput, { target: { value: 'ABCD-1234-EFGH' } });

    const submitBtn = screen.getByText('Confirm & Disable MFA');
    expect(submitBtn).not.toBeDisabled();
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(authApi.disableMfa).toHaveBeenCalledWith({
        password: 'SecretPassword123!',
        code: null,
        recoveryCode: 'ABCD-1234-EFGH',
      });
      expect(mockOnSuccess).toHaveBeenCalled();
    });
  });

  it('displays error message when credentials are wrong', async () => {
    authApi.disableMfa.mockRejectedValueOnce({
      status: 401,
      payload: { message: 'Invalid current password' },
    });

    render(
      <MfaDisableModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    fireEvent.change(screen.getByPlaceholderText('Enter your current password'), {
      target: { value: 'WrongPassword!' },
    });
    fireEvent.change(screen.getByPlaceholderText('000000'), {
      target: { value: '111111' },
    });

    fireEvent.click(screen.getByText('Confirm & Disable MFA'));

    await waitFor(() => {
      expect(screen.getByText('Invalid current password')).toBeInTheDocument();
    });
  });
});

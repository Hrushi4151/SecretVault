import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MfaEnrollmentModal } from '../components/settings/mfa/MfaEnrollmentModal';
import { authApi } from '../api/auth';

vi.mock('../api/auth', () => ({
  authApi: {
    enrollMfa: vi.fn(),
    activateMfa: vi.fn(),
  },
}));

describe('MfaEnrollmentModal Component', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders Step 1 (Overview) when opened', () => {
    render(
      <MfaEnrollmentModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
        userEmail="alice@example.com"
      />
    );

    expect(screen.getByText('Configure Multi-Factor Authentication')).toBeInTheDocument();
    expect(screen.getByText('Supported Authenticator Apps')).toBeInTheDocument();
    expect(screen.getByText('Continue Setup')).toBeInTheDocument();
  });

  it('progresses to Step 2 (QR code) and generates QR from provisioning URI', async () => {
    authApi.enrollMfa.mockResolvedValueOnce({
      status: 'PENDING_VERIFICATION',
      secret: 'JBSWY3DPEHPK3PXP',
      provisioningUri: 'otpauth://totp/SecretVault:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=SecretVault',
    });

    render(
      <MfaEnrollmentModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
        userEmail="alice@example.com"
      />
    );

    fireEvent.click(screen.getByText('Continue Setup'));

    await waitFor(() => {
      expect(screen.getByText('Scan with Authenticator')).toBeInTheDocument();
    });

    // Check manual setup key fallback toggle
    expect(screen.getByText("Can't scan the QR code?")).toBeInTheDocument();
    fireEvent.click(screen.getByText('Show setup key'));
    expect(screen.getByText('JBSWY3DPEHPK3PXP')).toBeInTheDocument();
  });

  it('progresses to Step 3 (Verification) and successfully activates MFA', async () => {
    authApi.enrollMfa.mockResolvedValueOnce({
      status: 'PENDING_VERIFICATION',
      secret: 'JBSWY3DPEHPK3PXP',
      provisioningUri: 'otpauth://totp/SecretVault:alice@example.com?secret=JBSWY3DPEHPK3PXP',
    });

    authApi.activateMfa.mockResolvedValueOnce({
      status: 'ENABLED',
      recoveryCodes: [
        '1111-2222-3333',
        '4444-5555-6666',
        '7777-8888-9999',
        'AAAA-BBBB-CCCC',
        'DDDD-EEEE-FFFF',
        'GGGG-HHHH-JJJJ',
        'KKKK-MMMM-NNNN',
        'PPPP-RRRR-TTTT',
        'UUUU-VVVV-WWWW',
        'XXXX-YYYY-ZZZZ',
      ],
    });

    render(
      <MfaEnrollmentModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
        userEmail="alice@example.com"
      />
    );

    fireEvent.click(screen.getByText('Continue Setup'));
    await waitFor(() => screen.getByText('I have scanned the code'));
    fireEvent.click(screen.getByText('I have scanned the code'));

    expect(screen.getByText('Enter the 6-Digit Code')).toBeInTheDocument();

    const input = screen.getByPlaceholderText('000000');
    fireEvent.change(input, { target: { value: '123456' } });

    fireEvent.click(screen.getByText('Activate & Reveal Recovery Codes'));

    await waitFor(() => {
      expect(screen.getByText('MFA Successfully Activated!')).toBeInTheDocument();
    });

    // Verify recovery codes are displayed
    expect(screen.getByText('1111-2222-3333')).toBeInTheDocument();
    expect(screen.getByText('XXXX-YYYY-ZZZZ')).toBeInTheDocument();

    // Check acknowledgement gate: "Complete MFA Setup" must be disabled until checked
    const completeBtn = screen.getByRole('button', { name: /complete mfa setup/i });
    expect(completeBtn).toBeDisabled();

    const checkbox = screen.getByRole('checkbox');
    fireEvent.click(checkbox);
    expect(completeBtn).not.toBeDisabled();

    fireEvent.click(completeBtn);
    expect(mockOnSuccess).toHaveBeenCalled();
  });

  it('displays safe error when activation code is invalid', async () => {
    authApi.enrollMfa.mockResolvedValueOnce({
      status: 'PENDING_VERIFICATION',
      secret: 'JBSWY3DPEHPK3PXP',
      provisioningUri: 'otpauth://totp/SecretVault:alice@example.com?secret=JBSWY3DPEHPK3PXP',
    });

    authApi.activateMfa.mockRejectedValueOnce({
      status: 400,
      payload: { message: 'Invalid verification code' },
    });

    render(
      <MfaEnrollmentModal
        isOpen={true}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
        userEmail="alice@example.com"
      />
    );

    fireEvent.click(screen.getByText('Continue Setup'));
    await waitFor(() => screen.getByText('I have scanned the code'));
    fireEvent.click(screen.getByText('I have scanned the code'));

    const input = screen.getByPlaceholderText('000000');
    fireEvent.change(input, { target: { value: '000000' } });

    fireEvent.click(screen.getByText('Activate & Reveal Recovery Codes'));

    await waitFor(() => {
      expect(screen.getByText('Invalid verification code')).toBeInTheDocument();
    });
  });
});

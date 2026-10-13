import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LoginScreen } from '../components/auth/LoginScreen';
import { RegisterScreen } from '../components/auth/RegisterScreen';
import { ForgotPasswordModal } from '../components/auth/ForgotPasswordModal';
import { MfaSuggestionModal } from '../components/auth/MfaSuggestionModal';
import { authApi } from '../api/auth';
import { AuthProvider } from '../context/AuthContext';

vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    sendRegistrationOtp: vi.fn(),
    verifyRegistrationOtp: vi.fn(),
    requestPasswordReset: vi.fn(),
    verifyPasswordResetOtp: vi.fn(),
    verifyPasswordResetMfa: vi.fn(),
    resetPassword: vi.fn(),
    getCurrentUser: vi.fn(),
    getOtpPreview: vi.fn().mockResolvedValue({ otp: '123456', smtpDelivered: false, smtpConfigured: false, message: 'Sandbox mode' }),
  },
}));

describe('Authentication: Gmail Restriction & OTP Verification', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('LoginScreen Gmail Enforcement', () => {
    it('rejects non-gmail addresses on submission', async () => {
      render(
        <AuthProvider>
          <LoginScreen onNavigateToRegister={vi.fn()} onNavigateToMfa={vi.fn()} />
        </AuthProvider>
      );

      const emailInput = screen.getByLabelText(/Google Mail/i);
      const passwordInput = screen.getByLabelText(/^Password/i);
      const submitButton = screen.getByRole('button', { name: /Sign In to Vault/i });

      fireEvent.change(emailInput, { target: { value: 'user@yahoo.com' } });
      fireEvent.change(passwordInput, { target: { value: 'SecretPassword123' } });
      fireEvent.click(submitButton);

      expect(
        await screen.findByText(/Only Google Mail \(@gmail\.com\) addresses are permitted for sign in/i)
      ).toBeInTheDocument();
      expect(authApi.login).not.toHaveBeenCalled();
    });

    it('allows valid @gmail.com addresses to proceed with authentication', async () => {
      authApi.login.mockResolvedValueOnce({
        accessToken: 'access_jwt',
        refreshToken: 'refresh_tok',
        user: { id: 'u-1', email: 'alice@gmail.com', fullName: 'Alice' },
      });

      render(
        <AuthProvider>
          <LoginScreen onNavigateToRegister={vi.fn()} onNavigateToMfa={vi.fn()} />
        </AuthProvider>
      );

      const emailInput = screen.getByLabelText(/Google Mail/i);
      const passwordInput = screen.getByLabelText(/^Password/i);
      const submitButton = screen.getByRole('button', { name: /Sign In to Vault/i });

      fireEvent.change(emailInput, { target: { value: 'alice@gmail.com' } });
      fireEvent.change(passwordInput, { target: { value: 'ValidPassword123' } });
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(authApi.login).toHaveBeenCalledWith({
          email: 'alice@gmail.com',
          password: 'ValidPassword123',
        });
      });
    });
  });

  describe('RegisterScreen: 2-Step Gmail OTP Registration', () => {
    it('blocks non-gmail addresses and enforces 8+ char password', async () => {
      render(
        <AuthProvider>
          <RegisterScreen onNavigateToLogin={vi.fn()} />
        </AuthProvider>
      );

      const nameInput = screen.getByLabelText(/Full Name/i);
      const emailInput = screen.getByLabelText(/Google Mail/i);
      const passwordInput = screen.getByLabelText(/Master Password/i);
      const continueBtn = screen.getByRole('button', { name: /Verify Gmail & Continue/i });

      fireEvent.change(nameInput, { target: { value: 'Bob Vance' } });
      fireEvent.change(emailInput, { target: { value: 'bob@corporate.org' } });
      fireEvent.change(passwordInput, { target: { value: 'Password123' } });
      fireEvent.click(continueBtn);

      expect(
        await screen.findByText(/Registration is restricted to Google Mail \(@gmail\.com\) accounts only/i)
      ).toBeInTheDocument();
      expect(authApi.sendRegistrationOtp).not.toHaveBeenCalled();
    });

    it('sends OTP, transitions to Step 2, and verifies 6-digit code', async () => {
      authApi.sendRegistrationOtp.mockResolvedValueOnce({});
      authApi.verifyRegistrationOtp.mockResolvedValueOnce({
        accessToken: 'reg_access_jwt',
        refreshToken: 'reg_refresh_tok',
        user: { id: 'u-reg', email: 'engineer@gmail.com', fullName: 'Engineer' },
      });

      render(
        <AuthProvider>
          <RegisterScreen onNavigateToLogin={vi.fn()} />
        </AuthProvider>
      );

      // Step 1: Input details
      fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'Engineer' } });
      fireEvent.change(screen.getByLabelText(/Google Mail/i), { target: { value: 'engineer@gmail.com' } });
      fireEvent.change(screen.getByLabelText(/Master Password/i), { target: { value: 'SuperSecretPass123' } });
      fireEvent.click(screen.getByRole('button', { name: /Verify Gmail & Continue/i }));

      await waitFor(() => {
        expect(authApi.sendRegistrationOtp).toHaveBeenCalledWith({
          fullName: 'Engineer',
          email: 'engineer@gmail.com',
          password: 'SuperSecretPass123',
          organizationName: undefined,
        });
      });

      // Step 2: Verification step rendered
      expect(await screen.findByText(/Verify Your Gmail Account/i)).toBeInTheDocument();
      expect(screen.getAllByText(/engineer@gmail.com/i).length).toBeGreaterThanOrEqual(1);

      const otpInput = screen.getByLabelText(/6-Digit Verification Code/i);
      fireEvent.change(otpInput, { target: { value: '987654' } });

      const confirmBtn = screen.getByRole('button', { name: /Confirm OTP & Create Account/i });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(authApi.verifyRegistrationOtp).toHaveBeenCalledWith({
          email: 'engineer@gmail.com',
          otp: '987654',
        });
      });
    });
  });

  describe('MfaSuggestionModal Prompt', () => {
    it('renders security advisory and triggers callbacks on user action', () => {
      const handleEnable = vi.fn();
      const handleDismiss = vi.fn();

      render(
        <MfaSuggestionModal
          isOpen={true}
          onEnableMfa={handleEnable}
          onDismiss={handleDismiss}
        />
      );

      expect(screen.getByText(/Fortify Your Account with MFA/i)).toBeInTheDocument();
      expect(screen.getByText(/Recommended: Multi-Factor Authentication/i)).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /Enable MFA Now/i }));
      expect(handleEnable).toHaveBeenCalled();

      fireEvent.click(screen.getByRole('button', { name: /I'll do this later/i }));
      expect(handleDismiss).toHaveBeenCalled();
    });
  });

  describe('ForgotPasswordModal: Comprehensive Secure Reset Flow', () => {
    it('executes full flow with step-up MFA for protected accounts', async () => {
      authApi.requestPasswordReset.mockResolvedValueOnce({});
      authApi.verifyPasswordResetOtp.mockResolvedValueOnce({
        resetToken: 'rst_xyz_123',
        mfaRequired: true,
      });
      authApi.verifyPasswordResetMfa.mockResolvedValueOnce({
        resetToken: 'rst_xyz_123',
        mfaRequired: true,
      });
      authApi.resetPassword.mockResolvedValueOnce({
        resetToken: 'rst_xyz_123',
      });

      const handleClose = vi.fn();
      render(<ForgotPasswordModal isOpen={true} onClose={handleClose} />);

      // Step 1: Input Gmail
      const emailInput = screen.getByLabelText(/Google Mail/i);
      fireEvent.change(emailInput, { target: { value: 'security_lead@gmail.com' } });
      fireEvent.click(screen.getByRole('button', { name: /Send Reset Code/i }));

      await waitFor(() => {
        expect(authApi.requestPasswordReset).toHaveBeenCalledWith({
          email: 'security_lead@gmail.com',
        });
      });

      // Step 2: OTP verification
      expect(await screen.findByText(/STAGE 2 OF 4/i)).toBeInTheDocument();
      const otpInput = screen.getByLabelText(/6-Digit Reset Code/i);
      fireEvent.change(otpInput, { target: { value: '456789' } });
      fireEvent.click(screen.getByRole('button', { name: /Verify Code/i }));

      await waitFor(() => {
        expect(authApi.verifyPasswordResetOtp).toHaveBeenCalledWith({
          email: 'security_lead@gmail.com',
          otp: '456789',
        });
      });

      // Step 3: MFA Step-Up Defense (since mfaRequired is true)
      expect(await screen.findByText(/STAGE 3 OF 4/i)).toBeInTheDocument();
      expect(screen.getByText(/MFA Defense Enforced/i)).toBeInTheDocument();

      const mfaInput = screen.getByLabelText(/6-Digit Authenticator Code/i);
      fireEvent.change(mfaInput, { target: { value: '112233' } });
      fireEvent.click(screen.getByRole('button', { name: /Verify MFA Factor/i }));

      await waitFor(() => {
        expect(authApi.verifyPasswordResetMfa).toHaveBeenCalledWith({
          resetToken: 'rst_xyz_123',
          code: '112233',
          recoveryCode: undefined,
        });
      });

      // Step 4: New Password creation
      expect(await screen.findByText(/STAGE 4 OF 4/i)).toBeInTheDocument();
      const newPwdInput = screen.getByLabelText(/^New Master Password/i);
      const confirmPwdInput = screen.getByLabelText(/Confirm New Password/i);

      fireEvent.change(newPwdInput, { target: { value: 'BrandNewSecurePassword123' } });
      fireEvent.change(confirmPwdInput, { target: { value: 'BrandNewSecurePassword123' } });

      const saveBtn = screen.getByRole('button', { name: /Save & Invalidate Sessions/i });
      fireEvent.click(saveBtn);

      await waitFor(() => {
        expect(authApi.resetPassword).toHaveBeenCalledWith({
          resetToken: 'rst_xyz_123',
          newPassword: 'BrandNewSecurePassword123',
        });
      });

      // Step 5: Success screen
      expect(await screen.findByText(/Password Reset Successful/i)).toBeInTheDocument();
      expect(screen.getByText(/all prior active browser sessions and refresh tokens have been revoked/i)).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /Sign In with New Password/i }));
      expect(handleClose).toHaveBeenCalled();
    });

    it('skips MFA step if user account does not have MFA enabled', async () => {
      authApi.requestPasswordReset.mockResolvedValueOnce({});
      authApi.verifyPasswordResetOtp.mockResolvedValueOnce({
        resetToken: 'rst_no_mfa_789',
        mfaRequired: false,
      });

      render(<ForgotPasswordModal isOpen={true} onClose={vi.fn()} />);

      // Step 1: Input Gmail
      fireEvent.change(screen.getByLabelText(/Google Mail/i), { target: { value: 'dev@gmail.com' } });
      fireEvent.click(screen.getByRole('button', { name: /Send Reset Code/i }));

      // Step 2: OTP
      const otpInput = await screen.findByLabelText(/6-Digit Reset Code/i);
      fireEvent.change(otpInput, { target: { value: '123456' } });
      fireEvent.click(screen.getByRole('button', { name: /Verify Code/i }));

      // Step 4: Directly transitions to New Password (skipping Stage 3 MFA)
      expect(await screen.findByLabelText(/^New Master Password/i)).toBeInTheDocument();
      expect(screen.queryByText(/MFA Defense Enforced/i)).not.toBeInTheDocument();
    });
  });
});

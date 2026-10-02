import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AccountSecurityView } from '../components/settings/AccountSecurityView';
import { authApi } from '../api/auth';

const mockLogout = vi.fn();

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { fullName: 'Alice Developer', email: 'alice@company.com' },
    activeWorkspace: { role: 'ADMIN' },
    logout: mockLogout,
  }),
}));

vi.mock('../api/auth', () => ({
  authApi: {
    getMfaStatus: vi.fn(),
    enrollMfa: vi.fn(),
    activateMfa: vi.fn(),
    disableMfa: vi.fn(),
    getSessions: vi.fn().mockResolvedValue([]),
    revokeSession: vi.fn(),
    revokeOtherSessions: vi.fn(),
    revokeAllSessions: vi.fn(),
  },
}));

describe('AccountSecurityView Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders Disabled MFA status with Enable MFA button when not configured', async () => {
    authApi.getMfaStatus.mockResolvedValueOnce({
      enabled: false,
      status: 'DISABLED',
      remainingRecoveryCodes: 0,
    });

    render(<AccountSecurityView />);

    await waitFor(() => {
      expect(screen.getByText('NOT ENABLED')).toBeInTheDocument();
      expect(screen.getByText('Multi-Factor Authentication is Not Configured')).toBeInTheDocument();
      expect(screen.getByText('Enable MFA')).toBeInTheDocument();
    });
  });

  it('renders Enabled MFA status with View Details and Disable MFA buttons when configured', async () => {
    authApi.getMfaStatus.mockResolvedValueOnce({
      enabled: true,
      status: 'ENABLED',
      enrolledAt: '2026-10-02T10:00:00Z',
      verifiedAt: '2026-10-02T10:05:00Z',
      remainingRecoveryCodes: 8,
    });

    render(<AccountSecurityView />);

    await waitFor(() => {
      expect(screen.getByText('ENABLED')).toBeInTheDocument();
      expect(screen.getByText('Authenticator App Protection')).toBeInTheDocument();
      expect(screen.getByText('8')).toBeInTheDocument(); // 8 remaining recovery codes
      expect(screen.getByText('View Details')).toBeInTheDocument();
      expect(screen.getByText('Disable MFA')).toBeInTheDocument();
    });
  });

  it('opens enrollment modal when clicking Enable MFA', async () => {
    authApi.getMfaStatus.mockResolvedValueOnce({
      enabled: false,
      status: 'DISABLED',
      remainingRecoveryCodes: 0,
    });

    render(<AccountSecurityView />);

    await waitFor(() => screen.getByText('Enable MFA'));
    fireEvent.click(screen.getByText('Enable MFA'));

    expect(screen.getByText('Configure Multi-Factor Authentication')).toBeInTheDocument();
  });

  it('opens disable modal when clicking Disable MFA', async () => {
    authApi.getMfaStatus.mockResolvedValueOnce({
      enabled: true,
      status: 'ENABLED',
      remainingRecoveryCodes: 10,
    });

    render(<AccountSecurityView />);

    await waitFor(() => screen.getByText('Disable MFA'));
    fireEvent.click(screen.getByText('Disable MFA'));

    expect(screen.getByText('Disable Multi-Factor Authentication')).toBeInTheDocument();
  });
});

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SessionsView } from '../components/settings/session/SessionsView';
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
    getSessions: vi.fn(),
    revokeSession: vi.fn(),
    revokeOtherSessions: vi.fn(),
    revokeAllSessions: vi.fn(),
  },
}));

describe('SessionsView Component', () => {
  const mockSessions = [
    {
      id: 'sess_current_123',
      current: true,
      deviceName: 'Chrome on Windows',
      browser: 'Chrome',
      operatingSystem: 'Windows',
      ipAddress: '192.168.1.50',
      authMethod: 'PASSWORD_MFA_TOTP',
      createdAt: '2026-10-02T10:00:00Z',
      lastUsedAt: '2026-10-02T12:00:00Z',
      expiresAt: '2026-10-09T12:00:00Z',
      revoked: false,
    },
    {
      id: 'sess_other_456',
      current: false,
      deviceName: 'Safari on macOS',
      browser: 'Safari',
      operatingSystem: 'macOS',
      ipAddress: '10.0.0.12',
      authMethod: 'PASSWORD',
      createdAt: '2026-10-01T08:00:00Z',
      lastUsedAt: '2026-10-02T09:00:00Z',
      expiresAt: '2026-10-08T09:00:00Z',
      revoked: false,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders current session with THIS DEVICE badge and metadata', async () => {
    authApi.getSessions.mockResolvedValue(mockSessions);

    render(<SessionsView />);

    await waitFor(() => {
      expect(screen.getByText('THIS DEVICE')).toBeInTheDocument();
      expect(screen.getByText(/Chrome on Windows/)).toBeInTheDocument();
      expect(screen.getByText('192.168.1.50')).toBeInTheDocument();
      expect(screen.getByText('Password + MFA TOTP')).toBeInTheDocument();
    });
  });

  it('renders other active sessions and safe device info', async () => {
    authApi.getSessions.mockResolvedValue(mockSessions);

    render(<SessionsView />);

    await waitFor(() => {
      expect(screen.getByText(/Safari on macOS/)).toBeInTheDocument();
      expect(screen.getByText('10.0.0.12')).toBeInTheDocument();
      expect(screen.getByText('Revoke Other Sessions')).toBeInTheDocument();
    });
  });

  it('ensures no sensitive token, secret, or hash is rendered into DOM', async () => {
    authApi.getSessions.mockResolvedValue(mockSessions);

    const { container } = render(<SessionsView />);

    await waitFor(() => screen.getByText('THIS DEVICE'));

    const html = container.innerHTML;
    expect(html).not.toContain('refreshToken');
    expect(html).not.toContain('tokenHash');
    expect(html).not.toContain('secret');
    expect(html).not.toContain('recoveryCode');
    expect(html).not.toContain('otp');
  });

  it('opens confirmation modal and revokes single other session on confirmation', async () => {
    authApi.getSessions.mockResolvedValue(mockSessions);
    authApi.revokeSession.mockResolvedValue({ status: 'revoked' });

    render(<SessionsView />);

    await waitFor(() => screen.getByText(/Safari on macOS/));

    const singleRevokeBtn = screen.getByRole('button', { name: 'Revoke' });
    fireEvent.click(singleRevokeBtn);

    expect(screen.getByText('Revoke Device Session?')).toBeInTheDocument();

    const confirmRevokeBtn = screen.getByRole('button', { name: 'Revoke Session' });
    fireEvent.click(confirmRevokeBtn);

    await waitFor(() => {
      expect(authApi.revokeSession).toHaveBeenCalledWith('sess_other_456');
      expect(screen.getByText(/Session on Safari was successfully revoked/i)).toBeInTheDocument();
    });
  });

  it('revoking current session prompts confirmation and triggers logout', async () => {
    authApi.getSessions.mockResolvedValue(mockSessions);
    authApi.revokeSession.mockResolvedValue({ status: 'revoked' });

    render(<SessionsView />);

    await waitFor(() => screen.getByText('THIS DEVICE'));

    const signOutBtn = screen.getByRole('button', { name: 'Sign Out' });
    fireEvent.click(signOutBtn);

    expect(screen.getByText('Sign Out Current Session?')).toBeInTheDocument();

    const confirmSignOut = screen.getByRole('button', { name: 'Sign Out Current Device' });
    fireEvent.click(confirmSignOut);

    await waitFor(() => {
      expect(authApi.revokeSession).toHaveBeenCalledWith('sess_current_123');
      expect(mockLogout).toHaveBeenCalled();
    });
  });

  it('revokes all other sessions through bulk action modal', async () => {
    authApi.getSessions.mockResolvedValue(mockSessions);
    authApi.revokeOtherSessions.mockResolvedValue({ revokedCount: 1 });

    render(<SessionsView />);

    await waitFor(() => screen.getByText('Revoke Other Sessions'));

    fireEvent.click(screen.getByText('Revoke Other Sessions'));

    expect(screen.getByText('Revoke All Other Active Sessions?')).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: 'Confirm Revoke Others' });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(authApi.revokeOtherSessions).toHaveBeenCalled();
      expect(screen.getByText('All other active sessions have been successfully terminated.')).toBeInTheDocument();
    });
  });

  it('revokes all sessions and immediately signs out', async () => {
    authApi.getSessions.mockResolvedValue(mockSessions);
    authApi.revokeAllSessions.mockResolvedValue({ revokedCount: 2 });

    render(<SessionsView />);

    await waitFor(() => screen.getByText('Sign Out All'));

    fireEvent.click(screen.getByText('Sign Out All'));

    expect(screen.getByText('Sign Out All Sessions?')).toBeInTheDocument();

    const confirmAllBtn = screen.getByRole('button', { name: 'Sign Out All Devices' });
    fireEvent.click(confirmAllBtn);

    await waitFor(() => {
      expect(authApi.revokeAllSessions).toHaveBeenCalled();
      expect(mockLogout).toHaveBeenCalled();
    });
  });

  it('displays empty state when no other sessions exist', async () => {
    authApi.getSessions.mockResolvedValue([mockSessions[0]]);

    render(<SessionsView />);

    await waitFor(() => {
      expect(screen.getByText('No other active sessions')).toBeInTheDocument();
    });
  });

  it('handles and displays API error gracefully', async () => {
    authApi.getSessions.mockRejectedValue(new Error('Rate limit exceeded. Please try again later.'));

    render(<SessionsView />);

    await waitFor(() => {
      expect(screen.getByText('Rate limit exceeded. Please try again later.')).toBeInTheDocument();
    });
  });
});

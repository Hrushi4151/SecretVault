import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PasskeysSection } from '../components/settings/webauthn/PasskeysSection';
import { authApi } from '../api/auth';
import * as webauthnUtils from '../utils/webauthn';

vi.mock('../api/auth', () => ({
  authApi: {
    getWebAuthnCredentials: vi.fn(),
    getWebAuthnRegistrationOptions: vi.fn(),
    verifyWebAuthnRegistration: vi.fn(),
    renameWebAuthnCredential: vi.fn(),
    revokeWebAuthnCredential: vi.fn(),
  },
}));

describe('PasskeysSection Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state then empty list state when no passkeys exist', async () => {
    authApi.getWebAuthnCredentials.mockResolvedValueOnce([]);

    render(<PasskeysSection />);

    await waitFor(() => {
      expect(screen.getByText('No Passkeys or Security Keys Registered')).toBeInTheDocument();
      expect(screen.getByText('NO PASSKEYS')).toBeInTheDocument();
    });
  });

  it('renders list of registered passkeys with metadata', async () => {
    const mockCredentials = [
      {
        id: 'cred-uuid-1',
        friendlyName: 'MacBook Touch ID',
        credentialType: 'public-key',
        transports: ['internal'],
        userVerifiedCapable: true,
        backupEligible: true,
        discoverable: true,
        createdAt: '2026-10-01T10:00:00Z',
        lastUsedAt: '2026-10-02T15:30:00Z',
        lastUsedIp: '192.168.1.50',
      },
      {
        id: 'cred-uuid-2',
        friendlyName: 'Work YubiKey 5C',
        credentialType: 'public-key',
        transports: ['usb', 'nfc'],
        userVerifiedCapable: true,
        backupEligible: false,
        discoverable: false,
        createdAt: '2026-09-15T08:00:00Z',
        lastUsedAt: null,
        lastUsedIp: null,
      },
    ];

    authApi.getWebAuthnCredentials.mockResolvedValueOnce(mockCredentials);

    render(<PasskeysSection />);

    await waitFor(() => {
      expect(screen.getByText('MacBook Touch ID')).toBeInTheDocument();
      expect(screen.getByText('Work YubiKey 5C')).toBeInTheDocument();
      expect(screen.getByText('2 ACTIVE PASSKEYS')).toBeInTheDocument();
      expect(screen.getByText('Synced Passkey')).toBeInTheDocument();
      expect(screen.getAllByText('Biometric Capable')).toHaveLength(2);
    });
  });

  it('handles renaming a passkey', async () => {
    const mockCredentials = [
      {
        id: 'cred-uuid-1',
        friendlyName: 'Old Key Name',
        credentialType: 'public-key',
        createdAt: '2026-10-01T10:00:00Z',
      },
    ];

    authApi.getWebAuthnCredentials
      .mockResolvedValueOnce(mockCredentials)
      .mockResolvedValueOnce([
        {
          id: 'cred-uuid-1',
          friendlyName: 'Personal MacBook Pro',
          credentialType: 'public-key',
          createdAt: '2026-10-01T10:00:00Z',
        },
      ]);

    authApi.renameWebAuthnCredential.mockResolvedValueOnce({
      id: 'cred-uuid-1',
      friendlyName: 'Personal MacBook Pro',
    });

    render(<PasskeysSection />);

    await waitFor(() => {
      expect(screen.getByText('Old Key Name')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Rename'));

    const input = screen.getByPlaceholderText('e.g. Personal MacBook');
    fireEvent.change(input, { target: { value: 'Personal MacBook Pro' } });

    fireEvent.click(screen.getByText('Save Changes'));

    await waitFor(() => {
      expect(authApi.renameWebAuthnCredential).toHaveBeenCalledWith({
        credentialId: 'cred-uuid-1',
        friendlyName: 'Personal MacBook Pro',
      });
      expect(screen.getByText('Passkey renamed successfully.')).toBeInTheDocument();
    });
  });

  it('handles revoking a passkey with lockout warning and confirmation', async () => {
    const mockCredentials = [
      {
        id: 'cred-uuid-1',
        friendlyName: 'MacBook Touch ID',
        credentialType: 'public-key',
        createdAt: '2026-10-01T10:00:00Z',
      },
    ];

    authApi.getWebAuthnCredentials
      .mockResolvedValueOnce(mockCredentials)
      .mockResolvedValueOnce([]);

    authApi.revokeWebAuthnCredential.mockResolvedValueOnce({});

    render(<PasskeysSection />);

    await waitFor(() => {
      expect(screen.getByText('MacBook Touch ID')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Remove'));

    expect(screen.getByText('Lockout Prevention Notice')).toBeInTheDocument();
    expect(screen.getByText('Are you sure you want to remove "MacBook Touch ID"?')).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: 'Remove Passkey' });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(authApi.revokeWebAuthnCredential).toHaveBeenCalledWith('cred-uuid-1');
      expect(screen.getByText('Passkey removed successfully.')).toBeInTheDocument();
    });
  });
});

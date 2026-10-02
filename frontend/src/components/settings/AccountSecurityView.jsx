import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { authApi } from '../../api/auth';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { MfaEnrollmentModal } from './mfa/MfaEnrollmentModal';
import { MfaDisableModal } from './mfa/MfaDisableModal';
import { MfaSecurityDetailsModal } from './mfa/MfaSecurityDetailsModal';
import {
  Shield,
  ShieldCheck,
  ShieldAlert,
  Smartphone,
  Key,
  Lock,
  User,
  Mail,
  Building2,
  Clock,
  Loader2,
  RefreshCw,
  Sparkles,
} from 'lucide-react';

export const AccountSecurityView = () => {
  const { user, activeWorkspace, logout } = useAuth();
  const [mfaStatus, setMfaStatus] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Modals
  const [isEnrollModalOpen, setIsEnrollModalOpen] = useState(false);
  const [isDisableModalOpen, setIsDisableModalOpen] = useState(false);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);

  const fetchMfaStatus = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const status = await authApi.getMfaStatus();
      setMfaStatus(status);
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Unable to load MFA security status. Please try again.';
      setError(errMsg);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMfaStatus();
  }, [fetchMfaStatus]);

  const handleEnrollSuccess = () => {
    setSuccessMessage('Multi-Factor Authentication has been successfully activated.');
    fetchMfaStatus();
    setTimeout(() => setSuccessMessage(null), 6000);
  };

  const handleDisableSuccess = () => {
    setSuccessMessage('Multi-Factor Authentication has been disabled for this account.');
    fetchMfaStatus();
    setTimeout(() => setSuccessMessage(null), 6000);
  };

  return (
    <div className="flex flex-col gap-8 max-w-4xl text-white font-body animate-fade-in">
      {/* Header Notification Banner */}
      {successMessage && (
        <Alert
          variant="success"
          message={successMessage}
          onDismiss={() => setSuccessMessage(null)}
        />
      )}

      {error && (
        <div className="flex items-center justify-between p-4 rounded-2xl bg-[#3F0016] border border-[#EF4444]/30 text-xs">
          <div className="flex items-center gap-3">
            <ShieldAlert className="w-5 h-5 text-[#EF4444] shrink-0" />
            <span className="text-[#F4B5C8]">{error}</span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={fetchMfaStatus}
            leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
          >
            Retry
          </Button>
        </div>
      )}

      {/* 1. Account Security & Identity Card */}
      <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6 shadow-xl shadow-black/40">
        <div className="flex flex-col gap-1">
          <h2 className="text-base font-headline font-bold text-white flex items-center gap-2">
            <User className="w-4 h-4 text-[#FF2D6D]" />
            Authentication Identity
          </h2>
          <p className="text-xs text-[#A26377]">
            Authenticated user profile and active workspace credentials.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
            <span className="text-[10px] font-mono text-[#A26377] uppercase tracking-wider">
              Full Name
            </span>
            <span className="text-xs font-bold text-white">{user?.fullName || 'Authenticated User'}</span>
          </div>

          <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
            <span className="text-[10px] font-mono text-[#A26377] uppercase tracking-wider">
              Work Email Address
            </span>
            <span className="text-xs font-mono text-[#F4B5C8]">{user?.email || 'N/A'}</span>
          </div>

          <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
            <span className="text-[10px] font-mono text-[#A26377] uppercase tracking-wider">
              Active Workspace Role
            </span>
            <span className="text-xs font-mono font-bold text-[#4ADE80]">
              {activeWorkspace?.role || 'MEMBER'}
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
            <span className="text-[10px] font-mono text-[#A26377] uppercase tracking-wider">
              Token Security
            </span>
            <span className="text-xs font-mono text-[#FFB4C8]">
              HMAC-SHA256 Signed JWT (24h TTL)
            </span>
          </div>
        </div>
      </div>

      {/* 2. Multi-Factor Authentication Section */}
      <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6 shadow-xl shadow-black/40">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <h2 className="text-base font-headline font-bold text-white flex items-center gap-2">
                <Shield className="w-4 h-4 text-[#FF2D6D]" />
                Multi-Factor Authentication (MFA)
              </h2>
              {isLoading ? (
                <span className="flex items-center gap-1 text-[10px] font-mono text-[#A26377]">
                  <Loader2 className="w-3 h-3 animate-spin text-[#FF2D6D]" />
                  Loading...
                </span>
              ) : mfaStatus?.enabled ? (
                <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#34D399] border border-[#34D399]/30 font-semibold">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#34D399] animate-pulse" />
                  ENABLED
                </span>
              ) : (
                <span className="px-2.5 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#A26377] border border-[#FFB4C8]/15 font-semibold">
                  NOT ENABLED
                </span>
              )}
            </div>
            <p className="text-xs text-[#A26377]">
              Time-based one-time password (TOTP) verification for all logins and sensitive operations.
            </p>
          </div>

          <Button
            variant="ghost"
            size="sm"
            onClick={fetchMfaStatus}
            disabled={isLoading}
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            title="Refresh MFA Status"
          >
            Refresh
          </Button>
        </div>

        {/* Skeleton Loading State */}
        {isLoading && !mfaStatus && (
          <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-3 animate-pulse">
            <div className="h-4 bg-[#3F0016] rounded-md w-1/3" />
            <div className="h-3 bg-[#3F0016] rounded-md w-2/3" />
            <div className="h-9 bg-[#3F0016] rounded-xl w-28 mt-2" />
          </div>
        )}

        {/* MFA Enabled State */}
        {!isLoading && mfaStatus?.enabled && (
          <div className="flex flex-col gap-4">
            <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              <div className="flex items-start gap-3.5">
                <div className="w-11 h-11 rounded-2xl bg-[#3F0016] border border-[#34D399]/40 flex items-center justify-center text-[#34D399] shrink-0 shadow-inner">
                  <ShieldCheck className="w-6 h-6" />
                </div>
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-headline font-bold text-white">
                      Authenticator App Protection
                    </span>
                    <span className="px-2 py-0.5 rounded-md bg-[#1E000A] text-[10px] font-mono text-[#F4B5C8] border border-[#FFB4C8]/15">
                      RFC 6238 TOTP
                    </span>
                  </div>
                  <p className="text-xs text-[#F4B5C8]">
                    Account is protected with hardware/software authenticator verification.
                  </p>
                  <div className="flex items-center gap-3 text-[11px] font-mono text-[#A26377] pt-1">
                    <span className="flex items-center gap-1.5">
                      <Key className="w-3.5 h-3.5 text-[#FF2D6D]" />
                      Recovery codes remaining: <strong className="text-white">{mfaStatus.remainingRecoveryCodes ?? 10}</strong>
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2.5 w-full sm:w-auto justify-end">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setIsDetailsModalOpen(true)}
                >
                  View Details
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => setIsDisableModalOpen(true)}
                >
                  Disable MFA
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* MFA Disabled State */}
        {!isLoading && !mfaStatus?.enabled && (
          <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div className="flex items-start gap-3.5">
              <div className="w-11 h-11 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shrink-0 shadow-inner">
                <Smartphone className="w-6 h-6" />
              </div>
              <div className="flex flex-col gap-1">
                <h4 className="text-sm font-headline font-bold text-white">
                  Multi-Factor Authentication is Not Configured
                </h4>
                <p className="text-xs text-[#F4B5C8] leading-relaxed max-w-lg">
                  Add an authenticator app as an additional layer of account protection. You will need your authenticator app to log in and authorize sensitive security actions.
                </p>
              </div>
            </div>

            <Button
              variant="primary"
              size="md"
              onClick={() => setIsEnrollModalOpen(true)}
              leftIcon={<Shield className="w-4 h-4" />}
              className="shrink-0 w-full sm:w-auto"
            >
              Enable MFA
            </Button>
          </div>
        )}
      </div>

      {/* 3. Session Controls Card */}
      <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-4 shadow-xl shadow-black/40">
        <div className="flex items-center justify-between">
          <div className="flex flex-col gap-0.5">
            <h3 className="text-sm font-headline font-bold text-white flex items-center gap-2">
              <Lock className="w-4 h-4 text-[#FF2D6D]" />
              Active Session Governance
            </h3>
            <p className="text-xs text-[#A26377]">
              Terminate current authentication session and clear all cryptographic credentials.
            </p>
          </div>
          <Button variant="secondary" size="sm" onClick={logout}>
            Sign Out
          </Button>
        </div>
      </div>

      {/* Modals */}
      <MfaEnrollmentModal
        isOpen={isEnrollModalOpen}
        onClose={() => setIsEnrollModalOpen(false)}
        onSuccess={handleEnrollSuccess}
        userEmail={user?.email}
      />

      <MfaDisableModal
        isOpen={isDisableModalOpen}
        onClose={() => setIsDisableModalOpen(false)}
        onSuccess={handleDisableSuccess}
      />

      <MfaSecurityDetailsModal
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
        mfaStatus={mfaStatus}
      />
    </div>
  );
};

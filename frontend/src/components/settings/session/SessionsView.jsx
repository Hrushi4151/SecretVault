import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { authApi } from '../../../api/auth';
import { Button } from '../../common/Button';
import { Alert } from '../../common/Alert';
import { Modal } from '../../common/Modal';
import {
  Shield,
  Laptop,
  Smartphone,
  Monitor,
  Globe,
  Clock,
  MapPin,
  LogOut,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  Lock,
  Key,
  Trash2,
  Radio,
} from 'lucide-react';

export const SessionsView = () => {
  const { logout } = useAuth();
  const [sessions, setSessions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [actionInProgress, setActionInProgress] = useState(false);

  // Modals for confirmation
  const [revokeTargetSession, setRevokeTargetSession] = useState(null);
  const [isRevokeOthersModalOpen, setIsRevokeOthersModalOpen] = useState(false);
  const [isRevokeAllModalOpen, setIsRevokeAllModalOpen] = useState(false);

  const fetchSessions = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await authApi.getSessions();
      setSessions(Array.isArray(data) ? data : []);
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Unable to load active sessions. Please try again.';
      setError(errMsg);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  const handleRevokeSingle = async () => {
    if (!revokeTargetSession) return;
    setActionInProgress(true);
    setError(null);
    try {
      await authApi.revokeSession(revokeTargetSession.id);
      
      // If user revoked their current session, sign out immediately
      if (revokeTargetSession.current) {
        setRevokeTargetSession(null);
        await logout();
        return;
      }

      setSuccessMessage(`Session on ${revokeTargetSession.browser || 'device'} was successfully revoked.`);
      setRevokeTargetSession(null);
      await fetchSessions();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Failed to revoke session. Please try again.';
      setError(errMsg);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleRevokeOthers = async () => {
    setActionInProgress(true);
    setError(null);
    try {
      await authApi.revokeOtherSessions();
      setSuccessMessage('All other active sessions have been successfully terminated.');
      setIsRevokeOthersModalOpen(false);
      await fetchSessions();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Failed to revoke other sessions. Please try again.';
      setError(errMsg);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleRevokeAll = async () => {
    setActionInProgress(true);
    setError(null);
    try {
      await authApi.revokeAllSessions();
      setIsRevokeAllModalOpen(false);
      await logout();
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Failed to revoke all sessions. Please try again.';
      setError(errMsg);
      setActionInProgress(false);
    }
  };

  const formatTimestamp = (isoString) => {
    if (!isoString) return 'Unknown';
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMins = Math.floor(diffMs / (1000 * 60));
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

      if (diffMins < 2) return 'Just now';
      if (diffMins < 60) return `${diffMins} min ago`;
      if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
      if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;

      return date.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const formatAuthMethod = (method) => {
    switch (method) {
      case 'PASSWORD_MFA_TOTP':
        return 'Password + MFA TOTP';
      case 'PASSWORD_MFA_RECOVERY':
        return 'Password + Recovery Code';
      case 'PASSWORD':
        return 'Password';
      default:
        return method || 'Standard Auth';
    }
  };

  const getDeviceIcon = (deviceType, browser) => {
    const dt = (deviceType || '').toLowerCase();
    const b = (browser || '').toLowerCase();
    if (dt.includes('mobile') || dt.includes('phone') || dt.includes('android') || dt.includes('iphone')) {
      return <Smartphone className="w-5 h-5 text-[#FF2D6D]" />;
    }
    if (dt.includes('tablet') || dt.includes('ipad')) {
      return <Smartphone className="w-5 h-5 text-[#FF2D6D]" />;
    }
    if (dt.includes('mac') || dt.includes('laptop') || dt.includes('desktop') || dt.includes('windows') || dt.includes('linux')) {
      return <Laptop className="w-5 h-5 text-[#FF2D6D]" />;
    }
    return <Globe className="w-5 h-5 text-[#FF2D6D]" />;
  };

  const currentSession = sessions.find((s) => s.current);
  const otherSessions = sessions.filter((s) => !s.current && !s.revoked);

  return (
    <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6 shadow-xl shadow-black/40 text-white font-body">
      {/* Header */}
      <div className="flex items-start justify-between flex-wrap gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <h2 className="text-base font-headline font-bold text-white flex items-center gap-2">
              <Lock className="w-4 h-4 text-[#FF2D6D]" />
              Sessions &amp; Devices Governance
            </h2>
            <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#F4B5C8] border border-[#FFB4C8]/15 font-semibold">
              {sessions.filter((s) => !s.revoked).length} ACTIVE
            </span>
          </div>
          <p className="text-xs text-[#A26377]">
            Active login contexts, token bindings, and authenticated device telemetry across all access points.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={fetchSessions}
            disabled={isLoading || actionInProgress}
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            title="Refresh Sessions List"
          >
            Refresh
          </Button>
          {otherSessions.length > 0 && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setIsRevokeOthersModalOpen(true)}
              disabled={isLoading || actionInProgress}
              leftIcon={<Radio className="w-3.5 h-3.5 text-[#FF2D6D]" />}
            >
              Revoke Other Sessions
            </Button>
          )}
          <Button
            variant="danger"
            size="sm"
            onClick={() => setIsRevokeAllModalOpen(true)}
            disabled={isLoading || actionInProgress}
            leftIcon={<LogOut className="w-3.5 h-3.5" />}
          >
            Sign Out All
          </Button>
        </div>
      </div>

      {/* Success Notification */}
      {successMessage && (
        <Alert
          variant="success"
          message={successMessage}
          onDismiss={() => setSuccessMessage(null)}
        />
      )}

      {/* Error Notification */}
      {error && (
        <Alert
          variant="danger"
          message={error}
          onDismiss={() => setError(null)}
        />
      )}

      {/* Skeleton Loading State */}
      {isLoading && sessions.length === 0 && (
        <div className="flex flex-col gap-4 animate-pulse">
          <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/10 h-28" />
          <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/10 h-24" />
        </div>
      )}

      {/* Content */}
      {!isLoading || sessions.length > 0 ? (
        <div className="flex flex-col gap-6">
          {/* 1. Current Session Banner */}
          {currentSession && (
            <div className="flex flex-col gap-2">
              <span className="text-[10px] font-mono text-[#A26377] uppercase tracking-wider font-semibold">
                Current Authenticated Session
              </span>
              <div className="p-5 rounded-2xl bg-[#30000F] border-2 border-[#34D399]/40 flex flex-col md:flex-row items-start md:items-center justify-between gap-4 shadow-lg shadow-black/30">
                <div className="flex items-start gap-3.5">
                  <div className="w-11 h-11 rounded-2xl bg-[#3F0016] border border-[#34D399]/40 flex items-center justify-center text-[#34D399] shrink-0 shadow-inner">
                    {getDeviceIcon(currentSession.operatingSystem, currentSession.browser)}
                  </div>
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-headline font-bold text-white">
                        {currentSession.browser || 'Web Browser'} on {currentSession.operatingSystem || 'Current OS'}
                      </span>
                      <span className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#1E000A] text-[10px] font-mono text-[#34D399] border border-[#34D399]/30 font-semibold">
                        <span className="w-1.5 h-1.5 rounded-full bg-[#34D399] animate-pulse" />
                        THIS DEVICE
                      </span>
                      <span className="px-2 py-0.5 rounded-md bg-[#1E000A] text-[10px] font-mono text-[#F4B5C8] border border-[#FFB4C8]/15">
                        {formatAuthMethod(currentSession.authMethod)}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-4 text-[11px] font-mono text-[#A26377] pt-0.5">
                      <span className="flex items-center gap-1">
                        <MapPin className="w-3 h-3 text-[#FF2D6D]" />
                        IP: <strong className="text-[#F4B5C8]">{currentSession.ipAddress || '127.0.0.1'}</strong>
                      </span>
                      <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3 text-[#FF2D6D]" />
                        Last Active: <strong className="text-white">{formatTimestamp(currentSession.lastUsedAt)}</strong>
                      </span>
                      <span className="flex items-center gap-1">
                        <Lock className="w-3 h-3 text-[#FF2D6D]" />
                        Signed in: <span className="text-[#F4B5C8]">{formatTimestamp(currentSession.createdAt)}</span>
                      </span>
                    </div>
                  </div>
                </div>

                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setRevokeTargetSession(currentSession)}
                  disabled={actionInProgress}
                  className="text-[#EF4444] hover:bg-[#EF4444]/10 shrink-0 self-end md:self-center"
                >
                  Sign Out
                </Button>
              </div>
            </div>
          )}

          {/* 2. Other Active Sessions */}
          <div className="flex flex-col gap-3">
            <span className="text-[10px] font-mono text-[#A26377] uppercase tracking-wider font-semibold">
              Other Active Sessions ({otherSessions.length})
            </span>

            {otherSessions.length === 0 ? (
              <div className="p-6 rounded-2xl bg-[#30000F]/60 border border-[#FFB4C8]/10 text-center flex flex-col items-center justify-center gap-2">
                <CheckCircle2 className="w-6 h-6 text-[#34D399]/70" />
                <span className="text-xs font-headline font-bold text-white">
                  No other active sessions
                </span>
                <p className="text-[11px] text-[#A26377] max-w-sm">
                  Your account is not logged in on any other browsers, computers, or mobile devices.
                </p>
              </div>
            ) : (
              <div className="flex flex-col gap-3">
                {otherSessions.map((session) => (
                  <div
                    key={session.id}
                    className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 hover:border-[#FFB4C8]/30 transition-all"
                  >
                    <div className="flex items-start gap-3.5">
                      <div className="w-10 h-10 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 flex items-center justify-center text-[#FF2D6D] shrink-0">
                        {getDeviceIcon(session.operatingSystem, session.browser)}
                      </div>
                      <div className="flex flex-col gap-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-xs font-headline font-bold text-white">
                            {session.browser || 'Web Browser'} on {session.operatingSystem || 'Unknown OS'}
                          </span>
                          <span className="px-2 py-0.5 rounded-md bg-[#1E000A] text-[9px] font-mono text-[#F4B5C8] border border-[#FFB4C8]/15">
                            {formatAuthMethod(session.authMethod)}
                          </span>
                        </div>

                        <div className="flex flex-wrap items-center gap-4 text-[11px] font-mono text-[#A26377]">
                          <span className="flex items-center gap-1">
                            <MapPin className="w-3 h-3 text-[#FF2D6D]" />
                            IP: <strong className="text-[#F4B5C8]">{session.ipAddress || 'Unknown'}</strong>
                          </span>
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3 text-[#FF2D6D]" />
                            Last Active: <strong className="text-white">{formatTimestamp(session.lastUsedAt)}</strong>
                          </span>
                        </div>
                      </div>
                    </div>

                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => setRevokeTargetSession(session)}
                      disabled={actionInProgress}
                      leftIcon={<Trash2 className="w-3.5 h-3.5" />}
                    >
                      Revoke
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      ) : null}

      {/* Confirmation Modal: Single Session Revoke */}
      <Modal
        isOpen={Boolean(revokeTargetSession)}
        onClose={() => setRevokeTargetSession(null)}
        title={revokeTargetSession?.current ? 'Sign Out Current Session?' : 'Revoke Device Session?'}
        description={
          revokeTargetSession?.current
            ? 'This will immediately terminate your active session and sign you out of SecretVault.'
            : `Are you sure you want to revoke the session on ${revokeTargetSession?.browser || 'device'} (${revokeTargetSession?.operatingSystem || 'OS'})? Any associated refresh tokens will be permanently invalidated.`
        }
      >
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setRevokeTargetSession(null)}
            disabled={actionInProgress}
          >
            Cancel
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={handleRevokeSingle}
            disabled={actionInProgress}
            leftIcon={<LogOut className="w-3.5 h-3.5" />}
          >
            {actionInProgress ? 'Revoking...' : revokeTargetSession?.current ? 'Sign Out Current Device' : 'Revoke Session'}
          </Button>
        </div>
      </Modal>

      {/* Confirmation Modal: Revoke All Other Sessions */}
      <Modal
        isOpen={isRevokeOthersModalOpen}
        onClose={() => setIsRevokeOthersModalOpen(false)}
        title="Revoke All Other Active Sessions?"
        description="All other browsers, mobile devices, and sessions except your current device will be immediately disconnected. Their refresh tokens will be permanently revoked."
      >
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setIsRevokeOthersModalOpen(false)}
            disabled={actionInProgress}
          >
            Cancel
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={handleRevokeOthers}
            disabled={actionInProgress}
            leftIcon={<Radio className="w-3.5 h-3.5" />}
          >
            {actionInProgress ? 'Revoking...' : 'Confirm Revoke Others'}
          </Button>
        </div>
      </Modal>

      {/* Confirmation Modal: Revoke All Sessions */}
      <Modal
        isOpen={isRevokeAllModalOpen}
        onClose={() => setIsRevokeAllModalOpen(false)}
        title="Sign Out All Sessions?"
        description="Every active authentication session on all devices, including this current device, will be immediately revoked. You will be redirected to log in again."
      >
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setIsRevokeAllModalOpen(false)}
            disabled={actionInProgress}
          >
            Cancel
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={handleRevokeAll}
            disabled={actionInProgress}
            leftIcon={<LogOut className="w-3.5 h-3.5" />}
          >
            {actionInProgress ? 'Signing Out...' : 'Sign Out All Devices'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

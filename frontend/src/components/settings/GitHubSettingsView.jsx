import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { repositoryApi } from '../../api/repositories';
import {
  GitBranch,
  ShieldCheck,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  ExternalLink,
  Key,
  Lock,
  Trash2,
  Layers,
  Sparkles,
  ArrowRight,
  Check,
  X,
  Eye,
  EyeOff,
  User,
  Building2,
} from 'lucide-react';

export const GitHubSettingsView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [status, setStatus] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Connect form state
  const [tokenInput, setTokenInput] = useState('');
  const [showToken, setShowToken] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [connectError, setConnectError] = useState(null);

  // Refresh and Disconnect states
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isDisconnecting, setIsDisconnecting] = useState(false);
  const [showDisconnectModal, setShowDisconnectModal] = useState(false);

  const fetchStatus = useCallback(async () => {
    if (!workspaceId) return;
    try {
      setIsLoading(true);
      setError(null);
      const res = await repositoryApi.getGitHubStatus(workspaceId);
      setStatus(res?.data || res || { connected: false });
    } catch (err) {
      setError(err?.message || 'Failed to load GitHub integration status');
    } finally {
      setIsLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  const handleConnect = async (e) => {
    e.preventDefault();
    if (!tokenInput.trim()) {
      setConnectError('Please enter a valid GitHub Personal Access Token or authorization token.');
      return;
    }

    try {
      setIsConnecting(true);
      setConnectError(null);
      const res = await repositoryApi.connectGitHub(workspaceId, { token: tokenInput.trim() });
      setStatus(res?.data || res || { connected: true });
      setTokenInput('');
      setSuccessMessage('GitHub account connected successfully! Repositories are now ready for discovery.');
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err) {
      setConnectError(err?.message || 'Failed to connect GitHub account. Check token permissions.');
    } finally {
      setIsConnecting(false);
    }
  };

  const handleRefresh = async () => {
    try {
      setIsRefreshing(true);
      setError(null);
      const res = await repositoryApi.refreshGitHub(workspaceId);
      setStatus(res?.data || res || status);
      setSuccessMessage('GitHub connection and permissions refreshed successfully.');
      setTimeout(() => setSuccessMessage(null), 4000);
    } catch (err) {
      setError(err?.message || 'Failed to refresh GitHub credentials.');
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleDisconnect = async () => {
    try {
      setIsDisconnecting(true);
      await repositoryApi.disconnectGitHub(workspaceId);
      setShowDisconnectModal(false);
      setStatus({ connected: false });
      setSuccessMessage('GitHub integration disconnected safely. Existing repositories and findings remain stored.');
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err) {
      setError(err?.message || 'Failed to disconnect GitHub.');
    } finally {
      setIsDisconnecting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3 text-slate-400 font-mono text-xs">
        <RefreshCw className="w-5 h-5 animate-spin text-[#FF2D6D]" />
        <span>Loading GitHub integration governance...</span>
      </div>
    );
  }

  const isConnected = status?.connected === true;

  return (
    <div className="flex flex-col gap-6 font-body text-white">
      {/* Header Info */}
      <div className="flex flex-col gap-1 border-b border-[#FFB4C8]/15 pb-5">
        <div className="flex items-center gap-2">
          <GitBranch className="w-5 h-5 text-[#FF2D6D]" />
          <h2 className="text-xl font-headline font-bold text-white tracking-tight">
            GitHub Account Integration
          </h2>
          {isConnected ? (
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#10B981]/20 text-[#34D399] border border-[#10B981]/30">
              CONNECTED
            </span>
          ) : (
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#A26377]/20 text-slate-300 border border-[#A26377]/30">
              NOT CONNECTED
            </span>
          )}
        </div>
        <p className="text-xs text-[#A26377]">
          Connect your GitHub identity once to enable automated repository discovery, contributor project scanning, and real-time push webhook leak detection.
        </p>
      </div>

      {/* Success Notification */}
      {successMessage && (
        <div className="p-4 rounded-xl bg-emerald-950/40 border border-emerald-500/40 text-emerald-300 text-xs flex items-center gap-3 animate-fade-in">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Global Error Banner */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-500/40 text-rose-300 text-xs flex items-center gap-3 animate-fade-in">
          <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* View: CONNECTED STATE */}
      {isConnected ? (
        <div className="flex flex-col gap-6">
          {/* Connected Identity Card */}
          <div className="p-6 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/20 flex flex-col md:flex-row items-start md:items-center justify-between gap-6 shadow-xl">
            <div className="flex items-center gap-4">
              {status.avatarUrl ? (
                <img
                  src={status.avatarUrl}
                  alt={status.username}
                  className="w-16 h-16 rounded-2xl border-2 border-[#FF2D6D]/40 object-cover shadow-md"
                />
              ) : (
                <div className="w-16 h-16 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
                  <User className="w-8 h-8" />
                </div>
              )}
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <h3 className="text-lg font-bold text-white font-headline">
                    {status.name || status.username}
                  </h3>
                  <span className="text-xs font-mono text-[#FF2D6D] bg-[#30000F] px-2 py-0.5 rounded border border-[#FF2D6D]/30">
                    @{status.username}
                  </span>
                </div>
                <div className="flex items-center gap-3 text-xs text-slate-400 font-mono">
                  <span>Status: <strong className="text-emerald-400">{status.status || 'ACTIVE'}</strong></span>
                  <span>•</span>
                  <span>Monitored Repos: <strong className="text-white">{status.monitoredRepositoriesCount ?? status.monitoredRepoCount ?? 0}</strong></span>
                </div>
                {status.lastValidatedAt && (
                  <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                    Last validated: {new Date(status.lastValidatedAt).toLocaleString()}
                  </div>
                )}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={handleRefresh}
                disabled={isRefreshing}
                className="px-4 py-2 rounded-xl bg-[#30000F] hover:bg-[#4A001C] text-slate-200 border border-[#FFB4C8]/20 text-xs font-medium flex items-center gap-2 transition disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 text-[#FF2D6D] ${isRefreshing ? 'animate-spin' : ''}`} />
                {isRefreshing ? 'Refreshing...' : 'Refresh Access'}
              </button>

              <button
                type="button"
                onClick={() => setShowDisconnectModal(true)}
                className="px-4 py-2 rounded-xl bg-rose-950/40 hover:bg-rose-900/60 text-rose-300 border border-rose-500/30 text-xs font-medium flex items-center gap-2 transition"
              >
                <Trash2 className="w-3.5 h-3.5" />
                Disconnect
              </button>
            </div>
          </div>

          {/* Granted Permissions & Security Controls */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-5 rounded-2xl bg-[#140006] border border-[#FFB4C8]/10 flex flex-col gap-3">
              <div className="flex items-center gap-2 text-xs font-bold text-white uppercase tracking-wider font-mono">
                <ShieldCheck className="w-4 h-4 text-[#34D399]" />
                Active GitHub Scopes & Capabilities
              </div>
              <div className="flex flex-wrap gap-2 mt-1">
                {(status.scopes && status.scopes.length > 0) ? (
                  status.scopes.map((scope, idx) => (
                    <span
                      key={idx}
                      className="px-2.5 py-1 rounded-lg bg-[#30000F] border border-[#FF2D6D]/30 text-[#FF85A2] text-xs font-mono font-medium"
                    >
                      {scope}
                    </span>
                  ))
                ) : (
                  <span className="text-xs text-slate-400 font-mono">
                    Standard repository access enabled (repo, read:org)
                  </span>
                )}
              </div>
              <p className="text-[11px] text-slate-400 mt-2">
                Enables discovery of your owned repositories, organization repositories, and projects where you are an active collaborator.
              </p>
            </div>

            <div className="p-5 rounded-2xl bg-[#140006] border border-[#FFB4C8]/10 flex flex-col gap-3">
              <div className="flex items-center gap-2 text-xs font-bold text-white uppercase tracking-wider font-mono">
                <Lock className="w-4 h-4 text-[#38BDF8]" />
                Cryptographic Storage Guarantee
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                Credentials are envelope-encrypted with AES-256-GCM using Vault Master Key and unique Data Encryption Keys (DEKs). Stored tokens are never exposed to the frontend, browser storage, or AI copilot models.
              </p>
              <div className="text-[11px] font-mono text-[#38BDF8] flex items-center gap-1.5 mt-auto">
                <Check className="w-3.5 h-3.5" />
                Workspace boundary strictly enforced server-side
              </div>
            </div>
          </div>
        </div>
      ) : (
        /* View: NOT CONNECTED STATE */
        <div className="flex flex-col gap-6">
          {/* Informational Callout */}
          <div className="p-6 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/20 flex flex-col gap-4">
            <div className="flex items-start gap-3">
              <div className="p-2.5 rounded-xl bg-[#30000F] border border-[#FF2D6D]/40 text-[#FF2D6D] shrink-0">
                <Sparkles className="w-5 h-5" />
              </div>
              <div className="flex flex-col gap-1">
                <h3 className="text-base font-bold text-white font-headline">
                  Connect GitHub for Automatic Discovery & Continuous Leak Prevention
                </h3>
                <p className="text-xs text-slate-300 leading-relaxed">
                  Instead of manually typing repository URLs, connect your GitHub identity once. SecretVault will automatically discover your personal repositories, organization repositories, and contributor projects with one-click monitoring.
                </p>
              </div>
            </div>

            {/* Permission explanation pills */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2">
              <div className="p-3 rounded-xl bg-[#140006] border border-[#FFB4C8]/10 flex flex-col gap-1">
                <span className="text-[11px] font-mono font-bold text-[#FF2D6D]">repo</span>
                <span className="text-[11px] text-slate-400">Discover public & private repositories and inspect commit history.</span>
              </div>
              <div className="p-3 rounded-xl bg-[#140006] border border-[#FFB4C8]/10 flex flex-col gap-1">
                <span className="text-[11px] font-mono font-bold text-[#38BDF8]">read:org</span>
                <span className="text-[11px] text-slate-400">Fetch organization repositories where you have team access.</span>
              </div>
              <div className="p-3 rounded-xl bg-[#140006] border border-[#FFB4C8]/10 flex flex-col gap-1">
                <span className="text-[11px] font-mono font-bold text-[#34D399]">admin:repo_hook</span>
                <span className="text-[11px] text-slate-400">Automatically installs push webhooks for zero-delay scans.</span>
              </div>
            </div>
          </div>

          {/* Connect Form */}
          <form onSubmit={handleConnect} className="p-6 rounded-2xl bg-[#140006] border border-[#FFB4C8]/20 flex flex-col gap-4 shadow-xl">
            <div className="flex flex-col gap-1">
              <label className="text-xs font-mono font-semibold text-slate-200 uppercase tracking-wider">
                GitHub Personal Access Token (Classic or Fine-Grained)
              </label>
              <p className="text-[11px] text-[#A26377]">
                Generate a token at <a href="https://github.com/settings/tokens" target="_blank" rel="noreferrer" className="text-[#FF2D6D] underline hover:text-[#FF85A2] inline-flex items-center gap-0.5">github.com/settings/tokens <ExternalLink className="w-2.5 h-2.5" /></a> with <code className="text-slate-300">repo</code> and <code className="text-slate-300">read:org</code> scopes.
              </p>
            </div>

            <div className="relative">
              <input
                type={showToken ? 'text' : 'password'}
                placeholder="ghp_... or github_pat_..."
                value={tokenInput}
                onChange={(e) => setTokenInput(e.target.value)}
                disabled={isConnecting}
                className="w-full bg-[#1E000A] border border-[#FFB4C8]/30 rounded-xl px-4 py-3 text-white font-mono text-xs focus:border-[#FF2D6D] focus:outline-none pr-10"
              />
              <button
                type="button"
                onClick={() => setShowToken(!showToken)}
                className="absolute right-3 top-3 text-slate-400 hover:text-white"
              >
                {showToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>

            {connectError && (
              <div className="p-3 rounded-xl bg-rose-950/40 border border-rose-500/40 text-rose-300 text-xs flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
                <span>{connectError}</span>
              </div>
            )}

            <div className="flex items-center justify-between pt-2">
              <div className="flex items-center gap-2 text-[11px] text-slate-400 font-mono">
                <Lock className="w-3.5 h-3.5 text-[#38BDF8]" />
                AES-256-GCM Envelope Encryption Enforced
              </div>

              <button
                type="submit"
                disabled={isConnecting || !tokenInput.trim()}
                className="px-6 py-2.5 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#E0004D] hover:from-[#FF457E] hover:to-[#FF2D6D] text-white font-semibold text-xs shadow-lg shadow-[#FF2D6D]/20 transition flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                {isConnecting ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    Validating & Connecting...
                  </>
                ) : (
                  <>
                    <Key className="w-4 h-4" />
                    Connect GitHub Account
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Disconnect Confirmation Modal */}
      {showDisconnectModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="bg-[#1E000A] border border-[#FFB4C8]/30 rounded-2xl max-w-md w-full p-6 text-white shadow-2xl flex flex-col gap-4 animate-scale-up">
            <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-3">
              <div className="flex items-center gap-2">
                <AlertTriangle className="w-5 h-5 text-rose-500" />
                <h3 className="text-base font-bold font-headline">Disconnect GitHub Account</h3>
              </div>
              <button
                onClick={() => setShowDisconnectModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-slate-300 leading-relaxed">
              Are you sure you want to disconnect GitHub for this workspace?
            </p>
            <div className="p-3 rounded-xl bg-amber-950/30 border border-amber-500/30 text-amber-300 text-xs">
              <strong>Impact on Monitoring:</strong> Automated repository discovery and push webhook ingestion will be paused. Existing connected repositories, scans, and detected findings will remain securely preserved in your workspace.
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowDisconnectModal(false)}
                disabled={isDisconnecting}
                className="px-4 py-2 rounded-xl bg-[#30000F] hover:bg-[#4A001C] text-slate-300 text-xs font-medium"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDisconnect}
                disabled={isDisconnecting}
                className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold flex items-center gap-2"
              >
                {isDisconnecting ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    Disconnecting...
                  </>
                ) : (
                  <>
                    <Trash2 className="w-3.5 h-3.5" />
                    Confirm Disconnect
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default GitHubSettingsView;

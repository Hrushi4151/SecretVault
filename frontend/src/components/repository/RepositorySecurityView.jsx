import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { repositoryApi } from '../../api/repositories';
import {
  ShieldAlert,
  ShieldCheck,
  Search,
  RefreshCw,
  GitBranch,
  FolderGit2,
  AlertTriangle,
  CheckCircle2,
  ExternalLink,
  Plus,
  Play,
  RotateCw,
  Eye,
  FileCode,
  Lock,
  GitCommit,
  Clock,
  Layers,
  HelpCircle,
  X,
  Filter,
  Trash2,
  Sparkles,
  Globe,
  Key,
  Check,
  Pause,
  ArrowRight,
  UserCheck,
  Users,
  Settings,
} from 'lucide-react';

export const RepositorySecurityView = () => {
  const { user, activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [activeTab, setActiveTab] = useState('findings'); // 'findings' | 'repositories' | 'scans' | 'policies'
  const [stats, setStats] = useState(null);
  const [findings, setFindings] = useState([]);
  const [repositories, setRepositories] = useState([]);
  const [scans, setScans] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Filters
  const [selectedSeverity, setSelectedSeverity] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('DETECTED');
  const [searchQuery, setSearchQuery] = useState('');

  // Remediation & Allowlist States
  const [remediationModal, setRemediationModal] = useState(null);
  const [targetInfo, setTargetInfo] = useState(null);
  const [targetInfoLoading, setTargetInfoLoading] = useState(false);
  const [remediationAction, setRemediationAction] = useState('ROTATE_SECRET');
  const [actionNotes, setActionNotes] = useState('');
  const [confirmedCompromise, setConfirmedCompromise] = useState(false);
  const [allowlistDays, setAllowlistDays] = useState('90');
  const [isExecuting, setIsExecuting] = useState(false);
  const [remediationResult, setRemediationResult] = useState(null);
  const [remediationError, setRemediationError] = useState(null);
  const [allowlists, setAllowlists] = useState([]);

  // Modals
  const [whyExposedModal, setWhyExposedModal] = useState(null);
  const [connectRepoModal, setConnectRepoModal] = useState(false);
  const [scanningRepoIds, setScanningRepoIds] = useState(new Set());
  const [deleteRepoModal, setDeleteRepoModal] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [modalError, setModalError] = useState(null);

  // GitHub Integration & Auto-Discovery States
  const [gitHubStatus, setGitHubStatus] = useState(null);
  const [quickConnectPat, setQuickConnectPat] = useState('');
  const [isQuickConnecting, setIsQuickConnecting] = useState(false);
  const [togglingMonitoringRepoId, setTogglingMonitoringRepoId] = useState(null);
  const [discoveredRepos, setDiscoveredRepos] = useState([]);
  const [isDiscovering, setIsDiscovering] = useState(false);
  const [discoveryFilter, setDiscoveryFilter] = useState('');
  const [discoveryRoleFilter, setDiscoveryRoleFilter] = useState('ALL');
  const [discoveryVisibilityFilter, setDiscoveryVisibilityFilter] = useState('ALL');
  const [discoveryPage, setDiscoveryPage] = useState(1);
  const DISCOVERY_PAGE_SIZE = 5;
  const [selectedDiscoveredRepo, setSelectedDiscoveredRepo] = useState(null);

  // Onboarding Form
  const [newRepo, setNewRepo] = useState({
    provider: 'GITHUB',
    owner: '',
    name: '',
    defaultBranch: 'main',
    cloneUrl: '',
    visibility: 'PRIVATE',
  });

  const fetchData = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const [statsRes, findingsRes, reposRes, scansRes, allowlistsRes, ghStatusRes] = await Promise.all([
        repositoryApi.getFindingStats(workspaceId).catch(() => ({})),
        repositoryApi.listFindings(workspaceId, {
          severity: selectedSeverity || undefined,
          status: selectedStatus || undefined,
          search: searchQuery || undefined,
        }).catch(() => ({ content: [] })),
        repositoryApi.listRepositories(workspaceId).catch(() => ({ content: [] })),
        repositoryApi.listScans(workspaceId).catch(() => ({ content: [] })),
        repositoryApi.listAllowlists(workspaceId).catch(() => ({ content: [] })),
        (repositoryApi.getGitHubStatus ? repositoryApi.getGitHubStatus(workspaceId) : Promise.resolve(null)).catch(() => null),
      ]);

      setStats(statsRes?.data || statsRes || {});
      setFindings(findingsRes?.content || findingsRes?.data?.content || []);
      setRepositories(reposRes?.content || reposRes?.data?.content || []);
      setScans(scansRes?.content || scansRes?.data?.content || []);
      setAllowlists(allowlistsRes?.data?.content || allowlistsRes?.content || []);
      if (ghStatusRes) {
        setGitHubStatus(ghStatusRes?.data || ghStatusRes);
      }
    } catch (err) {
      setError(err?.message || 'Failed to fetch repository security data');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, selectedSeverity, selectedStatus, searchQuery]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Live polling when scans are in progress
  useEffect(() => {
    const hasActiveScans = scans.some((s) =>
      ['QUEUED', 'CLONING', 'SCANNING', 'CLASSIFYING', 'IN_PROGRESS'].includes(s.status)
    );
    if (hasActiveScans || scanningRepoIds.size > 0) {
      const interval = setInterval(() => {
        fetchData();
      }, 3000);
      return () => clearInterval(interval);
    }
  }, [scans, scanningRepoIds.size, fetchData]);

  const isRepoScanning = (repoId) => {
    if (scanningRepoIds.has(repoId)) return true;
    return scans.some(
      (s) => s.repositoryId === repoId && ['QUEUED', 'CLONING', 'SCANNING', 'CLASSIFYING', 'IN_PROGRESS'].includes(s.status)
    );
  };

  const openConnectModal = async () => {
    setConnectRepoModal(true);
    setModalError(null);
    setSelectedDiscoveredRepo(null);
    setDiscoveryFilter('');
    setDiscoveryRoleFilter('ALL');
    setDiscoveryVisibilityFilter('ALL');
    setDiscoveryPage(1);

    if (workspaceId && repositoryApi.getGitHubStatus) {
      try {
        const gh = await repositoryApi.getGitHubStatus(workspaceId);
        const statusData = gh?.data || gh;
        setGitHubStatus(statusData);
        if (statusData?.connected) {
          handleFetchDiscoveredRepos();
        }
      } catch (e) {
        console.warn('Failed to load GitHub status', e);
      }
    }
  };

  const handleFetchDiscoveredRepos = async (customToken = null) => {
    setIsDiscovering(true);
    setModalError(null);
    try {
      const res = await repositoryApi.discoverRemoteRepositories(workspaceId, {
        provider: 'GITHUB',
        token: customToken || undefined,
      });
      const list = res?.data || res || [];
      const safeList = Array.isArray(list) ? list : [];
      setDiscoveredRepos(safeList);
      if (safeList.length === 0) {
        setModalError('No accessible repositories found for the connected GitHub account. Verify repository permissions or reauthorize.');
      }
    } catch (err) {
      setModalError(err.message || 'Failed to discover repositories from GitHub');
    } finally {
      setIsDiscovering(false);
    }
  };

  const handleQuickConnectGitHub = async (e) => {
    if (e) e.preventDefault();
    if (!quickConnectPat.trim()) return;
    setIsQuickConnecting(true);
    setModalError(null);
    try {
      const res = await repositoryApi.connectGitHub(workspaceId, {
        token: quickConnectPat.trim(),
        provider: 'GITHUB',
      });
      const statusData = res?.data || res;
      setGitHubStatus(statusData);
      setQuickConnectPat('');
      await handleFetchDiscoveredRepos(quickConnectPat.trim());
    } catch (err) {
      setModalError(err.message || 'Failed to connect GitHub account');
    } finally {
      setIsQuickConnecting(false);
    }
  };

  const handleSelectDiscoveredRepo = (repo) => {
    if (repo.isAlreadyMonitored) return;
    setSelectedDiscoveredRepo(repo);
    setNewRepo({
      provider: 'GITHUB',
      owner: repo.owner,
      name: repo.name,
      defaultBranch: repo.defaultBranch || 'main',
      cloneUrl: repo.cloneUrl || `https://github.com/${repo.owner}/${repo.name}.git`,
      visibility: repo.visibility || (repo.isPrivate ? 'PRIVATE' : 'PUBLIC'),
    });
  };

  const handleConnectRepository = async (e) => {
    if (e) e.preventDefault();
    setModalError(null);
    if (!selectedDiscoveredRepo) {
      setModalError('Please select a repository to connect.');
      return;
    }
    if (selectedDiscoveredRepo.isAlreadyMonitored) {
      setModalError('This repository is already connected and monitored in this workspace.');
      return;
    }
    try {
      await repositoryApi.connectRepository(workspaceId, {
        provider: 'GITHUB',
        owner: selectedDiscoveredRepo.owner,
        name: selectedDiscoveredRepo.name,
        defaultBranch: selectedDiscoveredRepo.defaultBranch || 'main',
        cloneUrl: selectedDiscoveredRepo.cloneUrl || `https://github.com/${selectedDiscoveredRepo.owner}/${selectedDiscoveredRepo.name}.git`,
        visibility: selectedDiscoveredRepo.visibility || (selectedDiscoveredRepo.isPrivate ? 'PRIVATE' : 'PUBLIC'),
      });
      setConnectRepoModal(false);
      setSelectedDiscoveredRepo(null);
      await fetchData();
    } catch (err) {
      setModalError(err.message || 'Failed to connect repository');
    }
  };

  const handleToggleMonitoring = async (repo) => {
    const isPaused = repo.status === 'DISABLED';
    const nextEnable = isPaused;
    setTogglingMonitoringRepoId(repo.id);
    try {
      await repositoryApi.toggleMonitoring(workspaceId, repo.id, { enabled: nextEnable });
      await fetchData();
    } catch (err) {
      alert(err.message || 'Failed to update repository monitoring status');
    } finally {
      setTogglingMonitoringRepoId(null);
    }
  };

  const handleDeleteRepository = async (repoId) => {
    setIsDeleting(true);
    try {
      await repositoryApi.disconnectRepository(workspaceId, repoId);
      setDeleteRepoModal(null);
      fetchData();
    } catch (err) {
      alert(err.message || 'Failed to delete repository');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleTriggerScan = async (repoId) => {
    setScanningRepoIds((prev) => new Set(prev).add(repoId));
    try {
      await repositoryApi.triggerScan(workspaceId, repoId, { scanType: 'FULL' });
      await fetchData();
    } catch (err) {
      alert(err.message || 'Failed to trigger scan');
    } finally {
      setTimeout(() => {
        setScanningRepoIds((prev) => {
          const next = new Set(prev);
          next.delete(repoId);
          return next;
        });
      }, 2500);
    }
  };

  const handleSyncAndScan = async (repoId) => {
    setScanningRepoIds((prev) => new Set(prev).add(repoId));
    try {
      await repositoryApi.syncRepository(workspaceId, repoId);
      await fetchData();
    } catch (err) {
      alert(err.message || 'Failed to sync repository');
    } finally {
      setTimeout(() => {
        setScanningRepoIds((prev) => {
          const next = new Set(prev);
          next.delete(repoId);
          return next;
        });
      }, 2500);
    }
  };

  const handleWhyExposed = async (findingId) => {
    try {
      const data = await repositoryApi.whyExposed(workspaceId, findingId);
      setWhyExposedModal(data?.data || data);
    } catch (err) {
      alert(err.message || 'Failed to explain finding');
    }
  };

  const handleOpenRemediation = async (f) => {
    setRemediationModal(f);
    setActionNotes('');
    setRemediationAction('ROTATE_SECRET');
    setConfirmedCompromise(false);
    setAllowlistDays('90');
    setRemediationResult(null);
    setRemediationError(null);
    setTargetInfo(null);
    setTargetInfoLoading(true);
    try {
      const res = await repositoryApi.getRemediationTargetInfo(workspaceId, f.id);
      setTargetInfo(res?.data || res);
    } catch (e) {
      console.warn('Failed to load remediation target info', e);
    } finally {
      setTargetInfoLoading(false);
    }
  };

  const handleExecuteRemediation = async () => {
    if (!remediationModal) return;
    setRemediationError(null);

    if (remediationAction === 'REVOKE_SECRET' && !confirmedCompromise) {
      setRemediationError('You must explicitly confirm that this credential is leaked and requires emergency invalidation.');
      return;
    }

    if (['MARK_FALSE_POSITIVE', 'IGNORE'].includes(remediationAction) && (!actionNotes || actionNotes.trim().length < 10)) {
      setRemediationError('A meaningful reason (at least 10 characters) is required to suppress or allowlist this finding.');
      return;
    }

    setIsExecuting(true);
    try {
      const payload = {
        action: remediationAction,
        notes: actionNotes || (remediationAction === 'ROTATE_SECRET' ? 'Emergency rotation from Repository Security' : 'Remediation dispatched'),
        filePattern: remediationModal.filePath,
        expiresAt: allowlistDays === 'never' ? null : new Date(Date.now() + parseInt(allowlistDays, 10) * 86400000).toISOString(),
      };

      const res = await repositoryApi.remediateFinding(workspaceId, remediationModal.id, payload);
      const jobData = res?.data || res;
      setRemediationResult(jobData);
      fetchData();
    } catch (err) {
      setRemediationError(err?.message || 'Remediation operation failed');
    } finally {
      setIsExecuting(false);
    }
  };

  const handleRevokeAllowlist = async (allowlistId) => {
    if (!window.confirm('Are you sure you want to revoke this allowlist rule? Any matching findings will be reopened and scanned.')) {
      return;
    }
    try {
      await repositoryApi.revokeAllowlist(workspaceId, allowlistId, 'Revoked by user via UI');
      fetchData();
    } catch (err) {
      alert(err?.message || 'Failed to revoke allowlist entry');
    }
  };

  return (
    <div className="flex flex-col gap-6 p-6 max-w-7xl mx-auto w-full text-slate-100">
      {/* Header & Posture Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-900/60 p-6 rounded-2xl border border-slate-800 backdrop-blur-xl">
        <div className="flex items-center gap-4">
          <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-400">
            <ShieldAlert className="w-7 h-7" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
              Repository Security & Secret Leaks
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-rose-500/20 text-rose-300 font-mono border border-rose-500/30">
                Phase 12
              </span>
            </h1>
            <p className="text-sm text-slate-400 mt-1">
              Continuous secret leak detection across Git working trees, commit histories, branches, and PRs.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => fetchData()}
            disabled={loading}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-sm font-medium transition"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-rose-400' : ''}`} />
            Refresh
          </button>
          <button
            data-testid="connect-repo-btn"
            onClick={openConnectModal}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-sm font-semibold shadow-lg shadow-rose-950/40 transition"
          >
            <Plus className="w-4 h-4" />
            Connect Repository
          </button>
        </div>
      </div>

      {/* Posture Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-800 flex items-center justify-between">
          <div>
            <div className="text-xs font-mono uppercase text-slate-400">Monitored Repos</div>
            <div className="text-2xl font-bold text-white mt-1">{repositories.length}</div>
          </div>
          <FolderGit2 className="w-8 h-8 text-sky-400 opacity-60" />
        </div>
        <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-800 flex items-center justify-between">
          <div>
            <div className="text-xs font-mono uppercase text-slate-400">Active Findings</div>
            <div className="text-2xl font-bold text-rose-400 mt-1">{stats?.activeFindings ?? findings.length}</div>
          </div>
          <AlertTriangle className="w-8 h-8 text-rose-400 opacity-60" />
        </div>
        <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-800 flex items-center justify-between">
          <div>
            <div className="text-xs font-mono uppercase text-slate-400">Critical Exposures</div>
            <div className="text-2xl font-bold text-red-500 mt-1">{stats?.criticalFindings ?? 0}</div>
          </div>
          <ShieldAlert className="w-8 h-8 text-red-500 opacity-60" />
        </div>
        <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-800 flex items-center justify-between">
          <div>
            <div className="text-xs font-mono uppercase text-slate-400">Remediated / Resolved</div>
            <div className="text-2xl font-bold text-emerald-400 mt-1">{stats?.resolvedFindings ?? 0}</div>
          </div>
          <CheckCircle2 className="w-8 h-8 text-emerald-400 opacity-60" />
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex border-b border-slate-800 gap-2">
        <button
          onClick={() => setActiveTab('findings')}
          className={`pb-3 px-4 text-sm font-medium border-b-2 transition ${
            activeTab === 'findings'
              ? 'border-rose-500 text-rose-400 font-semibold'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          Active Findings ({findings.length})
        </button>
        <button
          onClick={() => setActiveTab('repositories')}
          className={`pb-3 px-4 text-sm font-medium border-b-2 transition ${
            activeTab === 'repositories'
              ? 'border-rose-500 text-rose-400 font-semibold'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          Repositories ({repositories.length})
        </button>
        <button
          onClick={() => setActiveTab('scans')}
          className={`pb-3 px-4 text-sm font-medium border-b-2 transition ${
            activeTab === 'scans'
              ? 'border-rose-500 text-rose-400 font-semibold'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          Scan History ({scans.length})
        </button>
        <button
          onClick={() => setActiveTab('allowlists')}
          className={`pb-3 px-4 text-sm font-medium border-b-2 transition ${
            activeTab === 'allowlists'
              ? 'border-rose-500 text-rose-400 font-semibold'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          Allowlist & Suppressions ({allowlists.length})
        </button>
      </div>

      {/* Tab: Findings */}
      {activeTab === 'findings' && (
        <div className="flex flex-col gap-4">
          {/* Filters Bar */}
          <div className="flex flex-wrap items-center gap-3 p-3 bg-slate-900/40 rounded-xl border border-slate-800">
            <div className="flex items-center gap-2 flex-1 min-w-[200px] bg-slate-950/60 px-3 py-1.5 rounded-lg border border-slate-800">
              <Search className="w-4 h-4 text-slate-400" />
              <input
                type="text"
                placeholder="Search file, detector, or fingerprint..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="bg-transparent border-none text-sm text-white placeholder-slate-500 focus:outline-none w-full"
              />
            </div>

            <div className="flex items-center gap-2">
              <Filter className="w-4 h-4 text-slate-400" />
              <select
                value={selectedSeverity}
                onChange={(e) => setSelectedSeverity(e.target.value)}
                className="bg-slate-950/60 border border-slate-800 text-xs rounded-lg px-2.5 py-1.5 text-slate-300 focus:outline-none"
              >
                <option value="">All Severities</option>
                <option value="CRITICAL">CRITICAL</option>
                <option value="HIGH">HIGH</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="LOW">LOW</option>
              </select>

              <select
                value={selectedStatus}
                onChange={(e) => setSelectedStatus(e.target.value)}
                className="bg-slate-950/60 border border-slate-800 text-xs rounded-lg px-2.5 py-1.5 text-slate-300 focus:outline-none"
              >
                <option value="">All Statuses</option>
                <option value="DETECTED">DETECTED</option>
                <option value="CONFIRMED">CONFIRMED</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="FALSE_POSITIVE">FALSE POSITIVE</option>
                <option value="IGNORED">IGNORED</option>
              </select>
            </div>
          </div>

          {/* Findings List */}
          {findings.length === 0 ? (
            <div className="p-12 text-center rounded-2xl bg-slate-900/30 border border-dashed border-slate-800">
              <ShieldCheck className="w-12 h-12 text-emerald-400/60 mx-auto mb-3" />
              <h3 className="text-base font-semibold text-white">No Exposed Secrets Found</h3>
              <p className="text-sm text-slate-400 mt-1 max-w-md mx-auto">
                No active credentials matching your filter were detected. Run a scan to analyze repositories or Git history.
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {findings.map((f) => (
                <div
                  key={f.id}
                  className="p-4 rounded-xl bg-slate-900/40 border border-slate-800 hover:border-slate-700 transition flex flex-col md:flex-row md:items-center justify-between gap-4"
                >
                  <div className="flex items-start gap-3 flex-1 min-w-0">
                    <div
                      className={`px-2 py-1 rounded text-xs font-mono font-bold uppercase tracking-wider shrink-0 mt-0.5 ${
                        f.severity === 'CRITICAL'
                          ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                          : f.severity === 'HIGH'
                          ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                          : 'bg-sky-500/20 text-sky-400 border border-sky-500/30'
                      }`}
                    >
                      {f.severity}
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-white text-sm">{f.secretType}</span>
                        <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
                          {f.detectorType}
                        </span>
                        {f.matchedSecretId && (
                          <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center gap-1 font-mono">
                            <Lock className="w-3 h-3" /> SecretVault Match
                          </span>
                        )}
                        <span className="text-xs text-slate-400">
                          Confidence: <strong className="text-slate-200">{f.confidence}</strong>
                        </span>
                      </div>

                      <div className="flex items-center gap-2 text-xs font-mono text-slate-400 mt-1.5 truncate">
                        <FileCode className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                        <span className="text-slate-200 font-medium">{f.filePath}:{f.lineNumber || 1}</span>
                        {f.commitSha && (
                          <span className="text-slate-500 flex items-center gap-1 shrink-0">
                            <GitCommit className="w-3 h-3" /> {f.commitSha.substring(0, 8)}
                          </span>
                        )}
                      </div>

                      <div className="mt-2 text-xs font-mono px-2.5 py-1 rounded bg-black/40 border border-slate-800 text-slate-300 inline-block">
                        Evidence: <span className="text-rose-400 font-semibold">{f.maskedValue || f.maskedEvidence}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      onClick={() => handleWhyExposed(f.id)}
                      className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium flex items-center gap-1.5 transition"
                    >
                      <HelpCircle className="w-3.5 h-3.5 text-sky-400" />
                      Why Exposed?
                    </button>
                    <button
                      onClick={() => handleOpenRemediation(f)}
                      className="px-3 py-1.5 rounded-lg bg-rose-600/80 hover:bg-rose-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow transition"
                    >
                      <RotateCw className="w-3.5 h-3.5" />
                      Remediate
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab: Repositories */}
      {activeTab === 'repositories' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {repositories.map((repo) => {
            const isPaused = repo.status === 'DISABLED';
            return (
              <div
                key={repo.id}
                className={`p-5 rounded-2xl bg-slate-900/40 border transition flex flex-col justify-between gap-4 ${
                  isPaused ? 'border-amber-500/30 bg-slate-900/20' : 'border-slate-800 hover:border-slate-700'
                }`}
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2 min-w-0">
                      <FolderGit2 className={`w-5 h-5 shrink-0 ${isPaused ? 'text-amber-400' : 'text-rose-400'}`} />
                      <h3 className="font-semibold text-white text-sm truncate">
                        {repo.owner}/{repo.name}
                      </h3>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
                        {repo.visibility || 'PRIVATE'}
                      </span>
                      {isPaused ? (
                        <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30">
                          PAUSED
                        </span>
                      ) : (
                        <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center gap-1">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                          ACTIVE
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2 text-xs font-mono text-slate-400 mt-2.5 flex-wrap">
                    <span className="flex items-center gap-1">
                      <GitBranch className="w-3.5 h-3.5" /> {repo.defaultBranch || 'main'}
                    </span>
                    <span className="text-slate-500">•</span>
                    <span>{repo.provider || 'GITHUB'}</span>
                    <span className="text-slate-500">•</span>
                    <span className="text-slate-400">Workspace: {activeWorkspace?.name || 'Default'}</span>
                    {repo.lastCommitSha && (
                      <span
                        className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-[11px] text-emerald-400 flex items-center gap-1"
                        title={`Latest commit: ${repo.lastCommitSha}`}
                      >
                        <GitCommit className="w-3 h-3" />
                        {repo.lastCommitSha.slice(0, 7)}
                      </span>
                    )}
                  </div>

                  <div className="mt-2.5 flex items-center gap-1.5 text-[11px] font-mono">
                    {isPaused ? (
                      <span className="text-amber-400/90 flex items-center gap-1">
                        <Pause className="w-3 h-3" /> Automatic commit scanning is paused
                      </span>
                    ) : (
                      <span className="text-emerald-400/90 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                        Continuous push monitoring active
                      </span>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-2 mt-3 pt-3 border-t border-slate-800/80">
                    <div>
                      <div className="text-[10px] font-mono text-slate-500 uppercase">Findings</div>
                      <div className="text-sm font-bold text-white mt-0.5">{repo.totalFindings || 0}</div>
                    </div>
                    <div>
                      <div className="text-[10px] font-mono text-slate-500 uppercase">Critical</div>
                      <div className="text-sm font-bold text-rose-400 mt-0.5">{repo.criticalFindings || 0}</div>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-3 border-t border-slate-800/80 gap-2">
                  <span className="text-[11px] text-slate-500">
                    Last scan: {repo.lastScanAt ? new Date(repo.lastScanAt).toLocaleDateString() : 'Never'}
                  </span>
                  <div className="flex items-center gap-1.5">
                    {/* Monitoring Pause/Resume Toggle */}
                    <button
                      onClick={() => handleToggleMonitoring(repo)}
                      disabled={togglingMonitoringRepoId === repo.id}
                      title={isPaused ? "Resume automatic monitoring" : "Pause automatic monitoring"}
                      className={`p-1.5 rounded-lg border text-xs transition flex items-center gap-1 ${
                        isPaused
                          ? 'bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 border-amber-500/30'
                          : 'bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-slate-200 border-slate-700/60'
                      }`}
                    >
                      {togglingMonitoringRepoId === repo.id ? (
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      ) : isPaused ? (
                        <Play className="w-3.5 h-3.5 text-amber-400" />
                      ) : (
                        <Pause className="w-3.5 h-3.5" />
                      )}
                    </button>

                    <button
                      onClick={() => setDeleteRepoModal(repo)}
                      title="Disconnect Repository"
                      className="p-1.5 rounded-lg bg-slate-800 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 border border-slate-700/60 hover:border-rose-500/40 transition"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleSyncAndScan(repo.id)}
                      disabled={isRepoScanning(repo.id)}
                      title="Sync commits from GitHub and scan immediately"
                      className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-slate-200 border border-slate-700/60 transition disabled:opacity-50"
                    >
                      <RotateCw className={`w-3.5 h-3.5 ${isRepoScanning(repo.id) ? 'animate-spin' : ''}`} />
                    </button>
                    <button
                      onClick={() => handleTriggerScan(repo.id)}
                      disabled={isRepoScanning(repo.id)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition ${
                        isRepoScanning(repo.id)
                          ? 'bg-rose-500/15 border border-rose-500/30 text-rose-300 opacity-90 cursor-not-allowed'
                          : 'bg-slate-800 hover:bg-slate-700 text-slate-200'
                      }`}
                    >
                      {isRepoScanning(repo.id) ? (
                        <>
                          <RefreshCw className="w-3.5 h-3.5 text-rose-400 animate-spin" />
                          Scanning...
                        </>
                      ) : (
                        <>
                          <Play className="w-3.5 h-3.5 text-rose-400" />
                          Scan Now
                        </>
                      )}
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Tab: Scans */}
      {activeTab === 'scans' && (
        <div className="flex flex-col gap-3">
          {scans.map((s) => (
            <div
              key={s.id}
              className="p-4 rounded-xl bg-slate-900/40 border border-slate-800 flex items-center justify-between text-sm"
            >
              <div className="flex items-center gap-3">
                <span
                  className={`px-2 py-0.5 rounded text-xs font-mono font-bold ${
                    s.status === 'COMPLETED'
                      ? 'bg-emerald-500/20 text-emerald-400'
                      : s.status === 'FAILED'
                      ? 'bg-red-500/20 text-red-400'
                      : 'bg-sky-500/20 text-sky-400 animate-pulse'
                  }`}
                >
                  {s.status}
                </span>
                <span className="font-semibold text-white">{s.scanType} Scan</span>
                <span className="text-xs text-slate-400 font-mono">
                  {s.filesScanned || 0} files | {s.commitsScanned || 0} commits
                </span>
              </div>

              <div className="flex items-center gap-4 text-xs font-mono">
                <span className="text-rose-400 font-bold">{s.findingsCount || 0} finding(s)</span>
                <span className="text-slate-500">
                  {s.completedAt ? new Date(s.completedAt).toLocaleTimeString() : 'In Progress...'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Tab: Allowlist & Suppressions */}
      {activeTab === 'allowlists' && (
        <div className="flex flex-col gap-3">
          <div className="p-4 rounded-xl bg-slate-900/40 border border-slate-800 flex items-center justify-between text-xs text-slate-400">
            <div>
              <span className="font-semibold text-white">Active Suppression Exceptions</span>
              <p className="text-[11px] text-slate-400 mt-0.5">
                Narrow allowlist rules defined by SHA-256 fingerprint, detector type, and file pattern.
              </p>
            </div>
            <span className="font-mono text-slate-300 font-bold">{allowlists.length} active rule(s)</span>
          </div>

          {allowlists.length === 0 ? (
            <div className="p-8 rounded-2xl bg-slate-900/20 border border-slate-800/80 text-center text-slate-400">
              <ShieldCheck className="w-10 h-10 mx-auto text-emerald-400/60 mb-2" />
              <div className="font-semibold text-white">No Active Allowlist Rules</div>
              <p className="text-xs text-slate-400 mt-1">
                Findings marked as false positives with narrow scopes will appear here.
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {allowlists.map((al) => (
                <div
                  key={al.id}
                  className="p-4 rounded-xl bg-slate-900/40 border border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-3 text-xs"
                >
                  <div className="flex flex-col gap-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap font-mono">
                      <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-200 font-bold">
                        {al.detectorType || 'Any Detector'}
                      </span>
                      <span className="text-slate-400">
                        Repo: <strong className="text-white">{al.repositoryName || 'All Workspace Repos'}</strong>
                      </span>
                      <span className="text-slate-400">
                        Scope: <span className="text-rose-300">{al.filePattern || 'Any File'}</span>
                      </span>
                    </div>

                    <div className="font-mono text-[11px] text-slate-500 truncate">
                      Fingerprint: {al.fingerprint}
                    </div>

                    <div className="text-slate-300 mt-1">
                      <strong className="text-slate-400">Reason:</strong> {al.reason}
                    </div>

                    <div className="flex items-center gap-3 text-[11px] text-slate-500 font-mono mt-0.5">
                      <span>Created: {al.createdAt ? new Date(al.createdAt).toLocaleDateString() : 'N/A'}</span>
                      {al.expiresAt && (
                        <span>Expires: {new Date(al.expiresAt).toLocaleDateString()}</span>
                      )}
                    </div>
                  </div>

                  <button
                    onClick={() => handleRevokeAllowlist(al.id)}
                    className="px-3 py-1.5 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/30 text-xs font-semibold self-start md:self-center transition"
                  >
                    Revoke Rule
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Modal: Why Exposed */}
      {whyExposedModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-2xl w-full p-6 text-slate-200 shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <ShieldAlert className="w-5 h-5 text-rose-500" />
                  Why is this credential considered exposed?
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  Explainability & Risk Analysis for {whyExposedModal.secretType}
                </p>
              </div>
              <button
                onClick={() => setWhyExposedModal(null)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex flex-col gap-4 mt-4 text-sm">
              <div className="grid grid-cols-2 gap-3 bg-black/30 p-3 rounded-xl border border-slate-800">
                <div>
                  <div className="text-[11px] font-mono text-slate-500">Repository</div>
                  <div className="font-semibold text-white mt-0.5">
                    {whyExposedModal.repositoryName} ({whyExposedModal.repositoryVisibility})
                  </div>
                </div>
                <div>
                  <div className="text-[11px] font-mono text-slate-500">Masked Evidence</div>
                  <div className="font-mono text-rose-400 font-bold mt-0.5">
                    {whyExposedModal.maskedValue}
                  </div>
                </div>
              </div>

              <div>
                <h4 className="text-xs font-mono uppercase text-slate-400 font-semibold mb-2">
                  Risk Factors Contributing to Severity ({whyExposedModal.severity})
                </h4>
                <ul className="list-disc pl-5 space-y-1 text-slate-300 text-xs">
                  {whyExposedModal.riskFactors?.map((rf, idx) => (
                    <li key={idx}>{rf}</li>
                  ))}
                </ul>
              </div>

              <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs">
                <strong>Recommended Remediation:</strong> {whyExposedModal.recommendedRemediation}
              </div>
            </div>

            <div className="flex justify-end mt-6">
              <button
                onClick={() => setWhyExposedModal(null)}
                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-medium"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Operational Remediation Workflow */}
      {remediationModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-2xl w-full p-6 text-slate-200 shadow-2xl my-8">
            {/* Modal Header */}
            <div className="flex items-start justify-between border-b border-slate-800 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span
                    className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold uppercase tracking-wider ${
                      remediationModal.severity === 'CRITICAL'
                        ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                        : remediationModal.severity === 'HIGH'
                        ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                        : 'bg-sky-500/20 text-sky-400 border border-sky-500/30'
                    }`}
                  >
                    {remediationModal.severity}
                  </span>
                  <h3 className="text-lg font-bold text-white flex items-center gap-2">
                    <RotateCw className="w-5 h-5 text-rose-500" />
                    Remediate Exposed Credential
                  </h3>
                </div>
                <p className="text-xs text-slate-400 mt-1">
                  {remediationModal.secretType} detected in <span className="font-mono text-slate-300">{remediationModal.filePath}:{remediationModal.lineNumber || 1}</span>
                </p>
              </div>
              <button
                onClick={() => {
                  setRemediationModal(null);
                  setRemediationResult(null);
                  setRemediationError(null);
                }}
                disabled={isExecuting}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 disabled:opacity-50"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex flex-col gap-4 mt-4 text-sm">
              {/* Redacted Evidence */}
              <div className="p-3 bg-black/40 rounded-xl border border-slate-800 font-mono text-xs">
                <div className="text-slate-400">Exposed Code Location:</div>
                <div className="text-slate-200 font-semibold mt-0.5">{remediationModal.filePath}:{remediationModal.lineNumber || 1}</div>
                <div className="mt-1.5 text-rose-400 font-bold">
                  Evidence: {targetInfo?.maskedEvidence || remediationModal.maskedValue || remediationModal.maskedEvidence}
                </div>
              </div>

              {/* Remediation Target Card */}
              {targetInfoLoading ? (
                <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex items-center justify-center gap-2 text-xs font-mono text-slate-400 animate-pulse">
                  <RefreshCw className="w-4 h-4 animate-spin text-rose-400" />
                  Inspecting SecretVault inventory & provider ownership...
                </div>
              ) : (targetInfo?.isManagedInSecretVault || targetInfo?.isManaged) ? (
                <div className="p-4 rounded-xl bg-emerald-950/30 border border-emerald-500/30 flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 text-xs font-bold text-emerald-400 font-mono">
                      <Lock className="w-4 h-4" /> MANAGED IN SECRETVAULT
                    </div>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300">
                      Version {targetInfo.currentVersionNumber || 1}
                    </span>
                  </div>
                  <div className="text-xs text-slate-300">
                    Secret: <strong className="text-white">{targetInfo.matchedSecretName}</strong>
                    {(targetInfo.matchedProjectName || targetInfo.projectName) && (
                      <span> | Project: <strong className="text-white">{targetInfo.matchedProjectName || targetInfo.projectName}</strong></span>
                    )}
                    {(targetInfo.matchedEnvironmentName || targetInfo.environmentName) && (
                      <span> | Env: <strong className="text-white">{targetInfo.matchedEnvironmentName || targetInfo.environmentName}</strong></span>
                    )}
                  </div>
                  <div className="text-[11px] text-emerald-300/90 mt-1">
                    {targetInfo.impactWarning}
                  </div>
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-amber-950/30 border border-amber-500/30 flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 text-xs font-bold text-amber-400 font-mono">
                      <AlertTriangle className="w-4 h-4" /> UNMANAGED / EXTERNAL CREDENTIAL
                    </div>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-amber-500/20 text-amber-300">
                      {targetInfo?.providerName || 'External Provider'}
                    </span>
                  </div>
                  <p className="text-xs text-amber-200/90">
                    {targetInfo?.impactWarning || 'This credential is not registered in SecretVault inventory. Automatic rotation is not supported directly.'}
                  </p>
                  {targetInfo?.manualInstructions && targetInfo.manualInstructions.length > 0 && (
                    <div className="mt-2 p-3 bg-black/40 rounded-lg border border-amber-500/20 text-xs">
                      <div className="font-semibold text-amber-300 mb-1">Manual Remediation Playbook:</div>
                      <ul className="list-disc pl-4 space-y-1 text-slate-300 text-[11px]">
                        {targetInfo.manualInstructions.map((step, idx) => (
                          <li key={idx}>{step}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              {/* Action Selection Tabs */}
              <div className="flex border-b border-slate-800 gap-1 text-xs">
                <button
                  type="button"
                  onClick={() => setRemediationAction('ROTATE_SECRET')}
                  className={`pb-2 px-3 font-semibold border-b-2 transition ${
                    remediationAction === 'ROTATE_SECRET'
                      ? 'border-amber-500 text-amber-400'
                      : 'border-transparent text-slate-400 hover:text-slate-200'
                  }`}
                >
                  1. Emergency Rotation
                </button>
                <button
                  type="button"
                  onClick={() => setRemediationAction('REVOKE_SECRET')}
                  className={`pb-2 px-3 font-semibold border-b-2 transition ${
                    remediationAction === 'REVOKE_SECRET'
                      ? 'border-red-500 text-red-400'
                      : 'border-transparent text-slate-400 hover:text-slate-200'
                  }`}
                >
                  2. Revoke & Compromise
                </button>
                <button
                  type="button"
                  onClick={() => setRemediationAction('MARK_FALSE_POSITIVE')}
                  className={`pb-2 px-3 font-semibold border-b-2 transition ${
                    remediationAction === 'MARK_FALSE_POSITIVE'
                      ? 'border-sky-500 text-sky-400'
                      : 'border-transparent text-slate-400 hover:text-slate-200'
                  }`}
                >
                  3. Allowlist Rule
                </button>
              </div>

              {/* Action Panel Content */}
              {remediationAction === 'ROTATE_SECRET' && (
                <div className="flex flex-col gap-3 p-3 bg-slate-950/40 rounded-xl border border-slate-800">
                  <div className="text-xs text-slate-300">
                    {targetInfo?.isManagedInSecretVault
                      ? 'Initiates emergency rollover in SecretVault: generates version ' + ((targetInfo.currentVersionNumber || 1) + 1) + ', rolls out the new credential, invalidates old leases, and marks finding resolved.'
                      : 'Automatic rotation cannot be invoked because this credential is not managed in SecretVault. Follow the manual playbook above or mark compromised.'}
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-400">
                      Approval / Audit Reason <span className="text-slate-500">(required for compliance)</span>
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. Approved emergency secret rollover following repository exposure"
                      value={actionNotes}
                      onChange={(e) => setActionNotes(e.target.value)}
                      disabled={isExecuting}
                      className="w-full mt-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500 disabled:opacity-50"
                    />
                  </div>
                </div>
              )}

              {remediationAction === 'REVOKE_SECRET' && (
                <div className="flex flex-col gap-3 p-3 bg-red-950/20 rounded-xl border border-red-500/30">
                  <div className="text-xs text-red-300">
                    <strong>Incident Warning:</strong> Immediately marks this finding as COMPROMISED. If the secret is managed in SecretVault, all active leases will be revoked immediately and emergency rotation dispatched.
                  </div>
                  <label className="flex items-center gap-2 text-xs text-slate-200 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={confirmedCompromise}
                      onChange={(e) => setConfirmedCompromise(e.target.checked)}
                      disabled={isExecuting}
                      className="rounded border-slate-700 text-red-600 focus:ring-red-500"
                    />
                    <span>I confirm this credential is leaked and requires emergency invalidation.</span>
                  </label>
                  <div>
                    <label className="text-xs font-medium text-slate-400">
                      Incident Reason <span className="text-slate-500">(minimum 10 characters)</span>
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. Verified compromised token in public repository commit"
                      value={actionNotes}
                      onChange={(e) => setActionNotes(e.target.value)}
                      disabled={isExecuting}
                      className="w-full mt-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-red-500 disabled:opacity-50"
                    />
                  </div>
                </div>
              )}

              {remediationAction === 'MARK_FALSE_POSITIVE' && (
                <div className="flex flex-col gap-3 p-3 bg-sky-950/20 rounded-xl border border-sky-500/30">
                  <div className="text-xs text-sky-300">
                    Defines a narrow suppression rule for this specific fingerprint ({remediationModal.filePath}). Subsequent scans will ignore this specific credential in this scope.
                  </div>
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="text-[11px] font-mono text-slate-400">Narrow Scope File Pattern</label>
                      <input
                        type="text"
                        value={remediationModal.filePath}
                        readOnly
                        className="w-full mt-1 bg-black/40 border border-slate-800 rounded-lg px-2.5 py-1.5 font-mono text-[11px] text-slate-400 cursor-not-allowed"
                      />
                    </div>
                    <div>
                      <label className="text-[11px] font-mono text-slate-400">Rule Expiration</label>
                      <select
                        value={allowlistDays}
                        onChange={(e) => setAllowlistDays(e.target.value)}
                        disabled={isExecuting}
                        className="w-full mt-1 bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:border-sky-500"
                      >
                        <option value="30">30 Days</option>
                        <option value="90">90 Days (Recommended)</option>
                        <option value="365">1 Year</option>
                        <option value="never">Permanent</option>
                      </select>
                    </div>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-400">
                      False Positive Rationale <span className="text-rose-400 font-bold">* (min 10 chars required)</span>
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. Synthetic non-functional test fixture in mock directory"
                      value={actionNotes}
                      onChange={(e) => setActionNotes(e.target.value)}
                      disabled={isExecuting}
                      className="w-full mt-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-sky-500 disabled:opacity-50"
                    />
                  </div>
                </div>
              )}

              {/* Execution Error Banner */}
              {remediationError && (
                <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 shrink-0 text-red-400" />
                  <span>{remediationError}</span>
                </div>
              )}

              {/* Remediation Result Banner */}
              {remediationResult && (
                <div
                  className={`p-3 rounded-xl border text-xs flex flex-col gap-1 ${
                    remediationResult.status === 'ROTATION_SUCCEEDED' || remediationResult.status === 'REVOCATION_SUCCEEDED' || remediationResult.status === 'COMPLETED'
                      ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                      : remediationResult.status === 'MANUAL_ACTION_REQUIRED'
                      ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
                      : 'bg-red-500/10 border-red-500/30 text-red-300'
                  }`}
                >
                  <div className="flex items-center gap-2 font-bold font-mono">
                    {remediationResult.status === 'ROTATION_SUCCEEDED' || remediationResult.status === 'REVOCATION_SUCCEEDED' || remediationResult.status === 'COMPLETED' ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                    ) : (
                      <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />
                    )}
                    <span>OPERATION STATUS: {remediationResult.status}</span>
                  </div>
                  {remediationResult.errorMessage && (
                    <div className="text-[11px] text-slate-300 mt-1">{remediationResult.errorMessage}</div>
                  )}
                  {remediationResult.notes && (
                    <div className="text-[11px] font-mono text-slate-400 mt-0.5">Notes: {remediationResult.notes}</div>
                  )}
                </div>
              )}
            </div>

            {/* Modal Actions Footer */}
            <div className="flex items-center justify-between mt-6 border-t border-slate-800 pt-4">
              <button
                type="button"
                onClick={() => {
                  setRemediationModal(null);
                  setRemediationResult(null);
                  setRemediationError(null);
                }}
                disabled={isExecuting}
                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition disabled:opacity-50"
              >
                {remediationResult ? 'Close' : 'Cancel'}
              </button>

              <button
                type="button"
                onClick={handleExecuteRemediation}
                disabled={isExecuting}
                className={`px-5 py-2.5 rounded-xl text-white font-semibold text-xs shadow-lg transition flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed ${
                  remediationAction === 'ROTATE_SECRET'
                    ? 'bg-amber-600 hover:bg-amber-500 shadow-amber-950/40'
                    : remediationAction === 'REVOKE_SECRET'
                    ? 'bg-red-600 hover:bg-red-500 shadow-red-950/40'
                    : 'bg-sky-600 hover:bg-sky-500 shadow-sky-950/40'
                }`}
              >
                {isExecuting ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    Executing Operation...
                  </>
                ) : remediationAction === 'ROTATE_SECRET' ? (
                  <>
                    <RotateCw className="w-4 h-4" />
                    Execute Emergency Rotation
                  </>
                ) : remediationAction === 'REVOKE_SECRET' ? (
                  <>
                    <AlertTriangle className="w-4 h-4" />
                    Revoke Secret & Mark Compromised
                  </>
                ) : (
                  <>
                    <ShieldCheck className="w-4 h-4" />
                    Add to Allowlist & Suppress
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Connect Repository */}
      {/* Modal: Connect Repository (GitHub Account Discovery) */}
      {connectRepoModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-2xl w-full p-6 text-slate-200 shadow-2xl flex flex-col max-h-[90vh]">
            <div className="flex items-start justify-between border-b border-slate-800 pb-3 shrink-0">
              <div>
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <FolderGit2 className="w-5 h-5 text-rose-500" />
                  Connect GitHub Repository
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Select an authorized repository from your connected GitHub account to enable continuous secret scanning.
                </p>
              </div>
              <button
                onClick={() => setConnectRepoModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {modalError && (
              <div className="mt-3 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2 shrink-0">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>{modalError}</span>
              </div>
            )}

            <div className="overflow-y-auto pr-1 mt-3 space-y-3 custom-scrollbar flex-1">
              {/* GitHub Connected Identity Banner OR Quick Connect Callout */}
              {gitHubStatus?.connected ? (
                <div className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    {gitHubStatus.avatarUrl ? (
                      <img
                        src={gitHubStatus.avatarUrl}
                        alt={gitHubStatus.username}
                        className="w-9 h-9 rounded-full border border-slate-700 object-cover"
                      />
                    ) : (
                      <div className="w-9 h-9 rounded-full bg-slate-800 flex items-center justify-center text-rose-400 font-bold border border-slate-700">
                        {gitHubStatus.username ? gitHubStatus.username.charAt(0).toUpperCase() : 'G'}
                      </div>
                    )}
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-white text-xs">@{gitHubStatus.username}</span>
                        <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 font-mono">
                          Connected
                        </span>
                      </div>
                      <div className="text-[11px] text-slate-400 mt-0.5 font-mono">
                        Scopes: {gitHubStatus.scopes?.join(', ') || 'repo, admin:repo_hook'}
                      </div>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleFetchDiscoveredRepos()}
                    disabled={isDiscovering}
                    className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center gap-1.5 border border-slate-700 transition shrink-0"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-rose-400 ${isDiscovering ? 'animate-spin' : ''}`} />
                    {isDiscovering ? 'Refreshing...' : 'Refresh Repos'}
                  </button>
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-amber-950/20 border border-amber-500/30 flex flex-col gap-3">
                  <div className="flex items-start gap-2.5">
                    <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
                    <div>
                      <div className="text-xs font-bold text-amber-300">GitHub Account Not Connected</div>
                      <p className="text-[11px] text-slate-300 mt-0.5">
                        Connect your GitHub account to automatically discover repositories you own or contribute to. Tokens are stored encrypted on the backend with AES-256-GCM.
                      </p>
                    </div>
                  </div>

                  {/* Inline quick-connect form */}
                  <form onSubmit={handleQuickConnectGitHub} className="flex gap-2 mt-1">
                    <input
                      type="password"
                      placeholder="Enter GitHub Personal Access Token (ghp_...)"
                      value={quickConnectPat}
                      onChange={(e) => setQuickConnectPat(e.target.value)}
                      className="flex-1 bg-slate-900 border border-slate-700 rounded-xl px-3 py-1.5 text-xs text-white font-mono focus:border-rose-500 focus:outline-none"
                    />
                    <button
                      type="submit"
                      disabled={isQuickConnecting || !quickConnectPat.trim()}
                      className="px-3.5 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs transition flex items-center gap-1.5 disabled:opacity-50 shrink-0"
                    >
                      {isQuickConnecting ? (
                        <>
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Connecting...
                        </>
                      ) : (
                        <>
                          <Sparkles className="w-3.5 h-3.5" /> Connect Account
                        </>
                      )}
                    </button>
                  </form>
                </div>
              )}

              {/* Filter & Search Bar */}
              {gitHubStatus?.connected && (
                <div className="flex flex-col gap-2 pt-1">
                  <div className="flex flex-col sm:flex-row gap-2">
                    <div className="relative flex-1">
                      <Search className="w-3.5 h-3.5 absolute left-3 top-2.5 text-slate-400" />
                      <input
                        type="text"
                        placeholder="Filter by repo name, owner, or description..."
                        value={discoveryFilter}
                        onChange={(e) => {
                          setDiscoveryFilter(e.target.value);
                          setDiscoveryPage(1);
                        }}
                        className="w-full bg-slate-950/60 border border-slate-800 rounded-xl pl-8 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-rose-500"
                      />
                    </div>

                    <div className="flex gap-2">
                      <select
                        value={discoveryRoleFilter}
                        onChange={(e) => {
                          setDiscoveryRoleFilter(e.target.value);
                          setDiscoveryPage(1);
                        }}
                        className="bg-slate-950/60 border border-slate-800 rounded-xl px-2.5 py-2 text-xs text-slate-300 focus:outline-none focus:border-rose-500"
                      >
                        <option value="ALL">All Roles</option>
                        <option value="OWNER">Owned</option>
                        <option value="COLLABORATOR">Collaborator</option>
                        <option value="ORGANIZATION_MEMBER">Org Member</option>
                      </select>

                      <select
                        value={discoveryVisibilityFilter}
                        onChange={(e) => {
                          setDiscoveryVisibilityFilter(e.target.value);
                          setDiscoveryPage(1);
                        }}
                        className="bg-slate-950/60 border border-slate-800 rounded-xl px-2.5 py-2 text-xs text-slate-300 focus:outline-none focus:border-rose-500"
                      >
                        <option value="ALL">All Visibility</option>
                        <option value="PUBLIC">Public</option>
                        <option value="PRIVATE">Private</option>
                      </select>
                    </div>
                  </div>

                  {/* Discovered Repository Cards */}
                  {isDiscovering ? (
                    <div className="p-8 text-center text-xs text-slate-400 flex flex-col items-center justify-center gap-2">
                      <RefreshCw className="w-6 h-6 animate-spin text-rose-500" />
                      <span>Discovering repositories accessible to @{gitHubStatus.username}...</span>
                    </div>
                  ) : (() => {
                    const filtered = discoveredRepos.filter((r) => {
                      const q = discoveryFilter.toLowerCase();
                      const matchText =
                        !q ||
                        r.name?.toLowerCase().includes(q) ||
                        r.owner?.toLowerCase().includes(q) ||
                        r.description?.toLowerCase().includes(q);

                      const matchRole =
                        discoveryRoleFilter === 'ALL' ||
                        (discoveryRoleFilter === 'OWNER' && (r.role === 'OWNER' || r.isOwner)) ||
                        (discoveryRoleFilter === 'COLLABORATOR' && (r.role === 'COLLABORATOR' || r.isCollaborator)) ||
                        (discoveryRoleFilter === 'ORGANIZATION_MEMBER' && (r.role === 'ORGANIZATION_MEMBER' || r.isOrgMember));

                      const isPriv = r.isPrivate || r.visibility === 'PRIVATE';
                      const matchVis =
                        discoveryVisibilityFilter === 'ALL' ||
                        (discoveryVisibilityFilter === 'PUBLIC' && !isPriv) ||
                        (discoveryVisibilityFilter === 'PRIVATE' && isPriv);

                      return matchText && matchRole && matchVis;
                    });

                    if (filtered.length === 0) {
                      return (
                        <div className="p-8 text-center text-xs text-slate-500 border border-dashed border-slate-800 rounded-xl">
                          No repositories match the specified filters. Try searching with a different term.
                        </div>
                      );
                    }

                    const totalPages = Math.ceil(filtered.length / DISCOVERY_PAGE_SIZE);
                    const currentRepos = filtered.slice(
                      (discoveryPage - 1) * DISCOVERY_PAGE_SIZE,
                      discoveryPage * DISCOVERY_PAGE_SIZE
                    );

                    return (
                      <div className="flex flex-col gap-2">
                        <div className="space-y-2">
                          {currentRepos.map((repo) => {
                            const isSelected =
                              selectedDiscoveredRepo?.owner === repo.owner &&
                              selectedDiscoveredRepo?.name === repo.name;
                            const isAlready = repo.isAlreadyMonitored;

                            return (
                              <div
                                key={repo.id || `${repo.owner}/${repo.name}`}
                                onClick={() => !isAlready && handleSelectDiscoveredRepo(repo)}
                                className={`p-3 rounded-xl border transition flex flex-col gap-2 ${
                                  isAlready
                                    ? 'bg-slate-950/20 border-slate-800/40 opacity-70 cursor-not-allowed'
                                    : isSelected
                                    ? 'bg-rose-500/15 border-rose-500 text-white shadow-lg shadow-rose-950/30 cursor-pointer'
                                    : 'bg-slate-950/40 border-slate-800/80 hover:border-slate-700 text-slate-300 hover:bg-slate-800/40 cursor-pointer'
                                }`}
                              >
                                <div className="flex items-start justify-between gap-2">
                                  <div className="min-w-0 flex-1">
                                    <div className="flex items-center gap-2 flex-wrap">
                                      <FolderGit2 className={`w-4 h-4 shrink-0 ${isSelected ? 'text-rose-400' : 'text-slate-400'}`} />
                                      <span className="font-semibold text-white text-xs truncate">
                                        {repo.owner} / <span className="text-rose-400">{repo.name}</span>
                                      </span>

                                      {/* Role Badge */}
                                      <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 border border-slate-700 font-mono text-slate-300">
                                        {repo.role || (repo.isOwner ? 'OWNER' : 'COLLABORATOR')}
                                      </span>

                                      {/* Visibility Badge */}
                                      <span
                                        className={`text-[10px] px-1.5 py-0.2 rounded font-mono font-medium ${
                                          repo.isPrivate || repo.visibility === 'PRIVATE'
                                            ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                                            : 'bg-sky-500/20 text-sky-300 border border-sky-500/30'
                                        }`}
                                      >
                                        {repo.isPrivate || repo.visibility === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC'}
                                      </span>

                                      <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 border border-slate-700/60 font-mono text-slate-400">
                                        {repo.defaultBranch || 'main'}
                                      </span>
                                    </div>

                                    {repo.description && (
                                      <p className="text-[11px] text-slate-400 truncate mt-1">
                                        {repo.description}
                                      </p>
                                    )}

                                    <div className="text-[10px] font-mono text-slate-500 mt-1 truncate">
                                      {repo.cloneUrl || `https://github.com/${repo.owner}/${repo.name}.git`}
                                    </div>
                                  </div>

                                  <div className="flex items-center gap-2 shrink-0">
                                    {isAlready ? (
                                      <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-emerald-400 flex items-center gap-1 font-mono">
                                        <CheckCircle2 className="w-3 h-3 text-emerald-400" /> Monitored
                                      </span>
                                    ) : (
                                      <div
                                        className={`w-5 h-5 rounded-full flex items-center justify-center border transition ${
                                          isSelected
                                            ? 'bg-rose-500 border-rose-500 text-white'
                                            : 'border-slate-700 bg-slate-900'
                                        }`}
                                      >
                                        {isSelected && <Check className="w-3 h-3 stroke-[3]" />}
                                      </div>
                                    )}
                                  </div>
                                </div>

                                {/* Webhook Capability & Monitoring info */}
                                <div className="flex items-center justify-between text-[10px] font-mono border-t border-slate-800/60 pt-1.5 mt-0.5">
                                  {repo.canAdminHooks ? (
                                    <span className="text-emerald-400 flex items-center gap-1">
                                      <CheckCircle2 className="w-3 h-3" /> Push Webhooks Supported
                                    </span>
                                  ) : (
                                    <span className="text-amber-400 flex items-center gap-1" title={repo.permissionWarning || 'Requires repository admin access to register webhooks'}>
                                      <AlertTriangle className="w-3 h-3" /> Push Webhooks Require Admin (Read-only scanning)
                                    </span>
                                  )}

                                  {isAlready && repo.lastScanStatus && (
                                    <span className="text-slate-400">
                                      Last scan: {repo.lastScanStatus}
                                    </span>
                                  )}
                                </div>
                              </div>
                            );
                          })}
                        </div>

                        {/* Pagination Bar */}
                        {totalPages > 1 && (
                          <div className="flex items-center justify-between pt-2 border-t border-slate-800 text-xs text-slate-400">
                            <span>
                              Page {discoveryPage} of {totalPages} ({filtered.length} total)
                            </span>
                            <div className="flex gap-1.5">
                              <button
                                type="button"
                                disabled={discoveryPage <= 1}
                                onClick={() => setDiscoveryPage((p) => Math.max(1, p - 1))}
                                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 disabled:opacity-40 transition text-xs"
                              >
                                Previous
                              </button>
                              <button
                                type="button"
                                disabled={discoveryPage >= totalPages}
                                onClick={() => setDiscoveryPage((p) => Math.min(totalPages, p + 1))}
                                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 disabled:opacity-40 transition text-xs"
                              >
                                Next
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })()}
                </div>
              )}
            </div>

            {/* Selected Repo Action Footer */}
            <div className="flex items-center justify-between mt-4 pt-3 border-t border-slate-800 shrink-0">
              <button
                type="button"
                onClick={() => setConnectRepoModal(false)}
                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs transition"
              >
                Cancel
              </button>

              <button
                type="button"
                onClick={() => handleConnectRepository()}
                disabled={!selectedDiscoveredRepo || selectedDiscoveredRepo.isAlreadyMonitored}
                className="px-5 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs flex items-center gap-1.5 shadow-lg shadow-rose-950/40 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Sparkles className="w-3.5 h-3.5" />
                Connect {selectedDiscoveredRepo ? `${selectedDiscoveredRepo.name}` : 'Selected Repository'}
              </button>
            </div>
          </div>
        </div>
      )}



      {/* Modal: Disconnect Repository Confirmation */}
      {deleteRepoModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 text-slate-200 shadow-2xl">
            <div className="flex items-center gap-3 text-rose-400 mb-3">
              <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/30">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">
                  Disconnect Repository
                </h3>
                <p className="text-xs text-slate-400">
                  Safely remove continuous monitoring
                </p>
              </div>
            </div>

            <p className="text-xs text-slate-300 mt-3 leading-relaxed">
              Are you sure you want to disconnect <span className="font-mono font-semibold text-rose-400">{deleteRepoModal.owner}/{deleteRepoModal.name}</span>?
            </p>

            <div className="mt-3 p-3 rounded-xl bg-slate-950/60 border border-slate-800 text-[11px] text-slate-400 space-y-1">
              <div className="flex items-center gap-1.5 text-emerald-400 font-semibold">
                <CheckCircle2 className="w-3.5 h-3.5" /> Retention Policy Guaranteed:
              </div>
              <div>• Automatic webhook push event scans will be stopped.</div>
              <div>• Existing finding records, remediation logs, and scan history will be retained in workspace <strong>{activeWorkspace?.name || 'Production'}</strong>.</div>
            </div>

            <div className="flex justify-end gap-2 mt-6 pt-4 border-t border-slate-800">
              <button
                type="button"
                onClick={() => setDeleteRepoModal(null)}
                disabled={isDeleting}
                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 font-medium transition"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => handleDeleteRepository(deleteRepoModal.id)}
                disabled={isDeleting}
                className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-xs text-white font-semibold flex items-center gap-1.5 shadow transition disabled:opacity-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                {isDeleting ? 'Disconnecting...' : 'Disconnect Repository'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

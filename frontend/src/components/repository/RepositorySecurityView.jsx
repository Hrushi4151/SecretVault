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

  // Modals
  const [whyExposedModal, setWhyExposedModal] = useState(null);
  const [connectRepoModal, setConnectRepoModal] = useState(false);
  const [scanningRepoIds, setScanningRepoIds] = useState(new Set());
  const [remediationModal, setRemediationModal] = useState(null);
  const [deleteRepoModal, setDeleteRepoModal] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [actionNotes, setActionNotes] = useState('');
  const [modalError, setModalError] = useState(null);

  // GitHub Auto-Discovery States
  const [connectMode, setConnectMode] = useState('discover'); // 'discover' | 'manual'
  const [githubAccount, setGithubAccount] = useState('');
  const [githubPat, setGithubPat] = useState('');
  const [showPatInput, setShowPatInput] = useState(false);
  const [discoveredRepos, setDiscoveredRepos] = useState([]);
  const [isDiscovering, setIsDiscovering] = useState(false);
  const [discoveryFilter, setDiscoveryFilter] = useState('');
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
      const [statsRes, findingsRes, reposRes, scansRes] = await Promise.all([
        repositoryApi.getFindingStats(workspaceId).catch(() => ({})),
        repositoryApi.listFindings(workspaceId, {
          severity: selectedSeverity || undefined,
          status: selectedStatus || undefined,
          search: searchQuery || undefined,
        }).catch(() => ({ content: [] })),
        repositoryApi.listRepositories(workspaceId).catch(() => ({ content: [] })),
        repositoryApi.listScans(workspaceId).catch(() => ({ content: [] })),
      ]);

      setStats(statsRes?.data || statsRes || {});
      setFindings(findingsRes?.content || findingsRes?.data?.content || []);
      setRepositories(reposRes?.content || reposRes?.data?.content || []);
      setScans(scansRes?.content || scansRes?.data?.content || []);
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

  const openConnectModal = () => {
    setConnectRepoModal(true);
    setModalError(null);
    setSelectedDiscoveredRepo(null);
    setDiscoveryFilter('');
    const initialAccount = githubAccount || (user?.email ? user.email.split('@')[0] : 'Hrushi4151');
    setGithubAccount(initialAccount);
    if (discoveredRepos.length === 0) {
      handleFetchGitHubRepos(initialAccount, githubPat);
    }
  };

  const handleFetchGitHubRepos = async (accountName = githubAccount, pat = githubPat) => {
    const targetAccount = (accountName || '').trim();
    if (!targetAccount && !pat) {
      setModalError('Please enter a GitHub username/organization or a Personal Access Token.');
      return;
    }
    setIsDiscovering(true);
    setModalError(null);
    try {
      const res = await repositoryApi.discoverRemoteRepositories(workspaceId, {
        provider: 'GITHUB',
        username: targetAccount || undefined,
        token: pat || undefined,
      });
      const list = res?.data || res || [];
      const safeList = Array.isArray(list) ? list : [];
      setDiscoveredRepos(safeList);
      if (safeList.length === 0) {
        setModalError(`No public repositories found for "${targetAccount}". If your repositories are private, please expand the PAT section and provide a GitHub Token.`);
      }
    } catch (err) {
      setModalError(err.message || 'Failed to fetch repositories from GitHub');
    } finally {
      setIsDiscovering(false);
    }
  };

  const handleSelectDiscoveredRepo = (repo) => {
    setSelectedDiscoveredRepo(repo);
    setNewRepo({
      provider: 'GITHUB',
      owner: repo.owner,
      name: repo.name,
      defaultBranch: repo.defaultBranch || 'main',
      cloneUrl: repo.cloneUrl,
      visibility: repo.visibility || (repo.isPrivate ? 'PRIVATE' : 'PUBLIC'),
    });
  };

  const handleConnectRepository = async (e) => {
    if (e) e.preventDefault();
    setModalError(null);
    if (!newRepo.owner || !newRepo.name || !newRepo.cloneUrl) {
      setModalError('Please select or specify a repository to connect.');
      return;
    }
    try {
      await repositoryApi.connectRepository(workspaceId, newRepo);
      setConnectRepoModal(false);
      setSelectedDiscoveredRepo(null);
      setNewRepo({
        provider: 'GITHUB',
        owner: '',
        name: '',
        defaultBranch: 'main',
        cloneUrl: '',
        visibility: 'PRIVATE',
      });
      fetchData();
    } catch (err) {
      setModalError(err.message || 'Failed to connect repository');
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

  const handleWhyExposed = async (findingId) => {
    try {
      const data = await repositoryApi.whyExposed(workspaceId, findingId);
      setWhyExposedModal(data?.data || data);
    } catch (err) {
      alert(err.message || 'Failed to explain finding');
    }
  };

  const handleExecuteRemediation = async (findingId, action) => {
    try {
      await repositoryApi.remediateFinding(workspaceId, findingId, action, actionNotes);
      setRemediationModal(null);
      setActionNotes('');
      fetchData();
    } catch (err) {
      alert(err.message || 'Remediation failed');
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
                      onClick={() => setRemediationModal(f)}
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
          {repositories.map((repo) => (
            <div
              key={repo.id}
              className="p-5 rounded-2xl bg-slate-900/40 border border-slate-800 hover:border-slate-700 transition flex flex-col justify-between gap-4"
            >
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <FolderGit2 className="w-5 h-5 text-rose-400" />
                    <h3 className="font-semibold text-white text-sm truncate">
                      {repo.owner}/{repo.name}
                    </h3>
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
                    {repo.visibility}
                  </span>
                </div>

                <div className="flex items-center gap-4 text-xs font-mono text-slate-400 mt-3">
                  <span className="flex items-center gap-1">
                    <GitBranch className="w-3.5 h-3.5" /> {repo.defaultBranch}
                  </span>
                  <span>{repo.provider}</span>
                </div>

                <div className="grid grid-cols-2 gap-2 mt-4 pt-3 border-t border-slate-800/80">
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
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setDeleteRepoModal(repo)}
                    title="Delete Repository"
                    className="p-1.5 rounded-lg bg-slate-800 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 border border-slate-700/60 hover:border-rose-500/40 transition"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
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
          ))}
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

      {/* Modal: Remediate Finding */}
      {remediationModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 text-slate-200 shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <RotateCw className="w-5 h-5 text-rose-500" />
                  Remediate Exposed Credential
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  Choose authorized remediation action for {remediationModal.secretType}
                </p>
              </div>
              <button
                onClick={() => setRemediationModal(null)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex flex-col gap-4 mt-4">
              <div className="p-3 bg-black/30 rounded-xl border border-slate-800 text-xs font-mono">
                <div>File: {remediationModal.filePath}:{remediationModal.lineNumber || 1}</div>
                <div className="mt-1 text-rose-400 font-bold">Evidence: {remediationModal.maskedValue || remediationModal.maskedEvidence}</div>
              </div>

              <div>
                <label className="text-xs font-medium text-slate-400">Audit Notes / Reason</label>
                <input
                  type="text"
                  placeholder="e.g. Approved by SecOps lead"
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="w-full mt-1.5 bg-slate-950/60 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none"
                />
              </div>

              <div className="flex flex-col gap-2 pt-2">
                <button
                  onClick={() => handleExecuteRemediation(remediationModal.id, 'ROTATE_SECRET')}
                  className="w-full py-2.5 rounded-xl bg-amber-600 hover:bg-amber-500 text-white font-semibold text-xs transition flex items-center justify-center gap-2"
                >
                  <RotateCw className="w-4 h-4" /> Trigger Emergency Secret Rotation
                </button>
                <button
                  onClick={() => handleExecuteRemediation(remediationModal.id, 'REVOKE_SECRET')}
                  className="w-full py-2.5 rounded-xl bg-red-600 hover:bg-red-500 text-white font-semibold text-xs transition flex items-center justify-center gap-2"
                >
                  <AlertTriangle className="w-4 h-4" /> Revoke Secret & Mark Compromised
                </button>
                <button
                  onClick={() => handleExecuteRemediation(remediationModal.id, 'MARK_FALSE_POSITIVE')}
                  className="w-full py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium text-xs transition"
                >
                  Mark False Positive (Add to Allowlist)
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Connect Repository */}
      {connectRepoModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-xl w-full p-6 text-slate-200 shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-800 pb-3">
              <div>
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <FolderGit2 className="w-5 h-5 text-rose-500" />
                  Connect Repository
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Onboard a Git repository for continuous secret detection & leak prevention
                </p>
              </div>
              <button
                onClick={() => setConnectRepoModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Mode Switcher Tabs */}
            <div className="flex items-center gap-2 border-b border-slate-800/80 pb-3 mt-3">
              <button
                type="button"
                onClick={() => setConnectMode('discover')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition ${
                  connectMode === 'discover'
                    ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40 shadow'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                }`}
              >
                <Sparkles className="w-3.5 h-3.5 text-rose-400" />
                Auto-Fetch from GitHub
              </button>
              <button
                type="button"
                onClick={() => setConnectMode('manual')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition ${
                  connectMode === 'manual'
                    ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40 shadow'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                }`}
              >
                <FolderGit2 className="w-3.5 h-3.5" />
                Manual URL Entry
              </button>
            </div>

            {modalError && (
              <div className="mt-3 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>{modalError}</span>
              </div>
            )}

            {/* Tab: Auto-Fetch from GitHub */}
            {connectMode === 'discover' && (
              <div className="flex flex-col gap-3 mt-3 text-xs">
                {/* Account Fetch Toolbar */}
                <div className="p-3 rounded-xl bg-slate-950/60 border border-slate-800/80 flex flex-col gap-2">
                  <label className="text-[11px] font-mono text-slate-400 uppercase tracking-wider">
                    GitHub Account / Username / Org
                  </label>
                  <div className="flex gap-2">
                    <div className="relative flex-1">
                      <input
                        type="text"
                        placeholder="e.g. Hrushi4151 or email"
                        value={githubAccount}
                        onChange={(e) => setGithubAccount(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleFetchGitHubRepos(githubAccount, githubPat)}
                        className="w-full bg-slate-900 border border-slate-700/80 rounded-xl px-3 py-2 text-white font-mono text-xs focus:border-rose-500 focus:outline-none"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => handleFetchGitHubRepos(githubAccount, githubPat)}
                      disabled={isDiscovering}
                      className="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center gap-1.5 border border-slate-700 shrink-0 transition"
                    >
                      <RefreshCw className={`w-3.5 h-3.5 text-rose-400 ${isDiscovering ? 'animate-spin' : ''}`} />
                      {isDiscovering ? 'Fetching...' : 'Fetch Repos'}
                    </button>
                  </div>

                  {/* Optional PAT Toggle */}
                  <div className="pt-1">
                    <button
                      type="button"
                      onClick={() => setShowPatInput(!showPatInput)}
                      className="text-[11px] text-rose-400 hover:text-rose-300 font-medium flex items-center gap-1 transition"
                    >
                      <Key className="w-3 h-3" />
                      {showPatInput ? 'Hide Personal Access Token' : '+ Include Private Repos (Personal Access Token / PAT)'}
                    </button>
                    {showPatInput && (
                      <div className="mt-2">
                        <input
                          type="password"
                          placeholder="GitHub PAT (ghp_... with repo scope)"
                          value={githubPat}
                          onChange={(e) => setGithubPat(e.target.value)}
                          className="w-full bg-slate-900 border border-slate-700/80 rounded-xl px-3 py-1.5 text-white font-mono text-xs focus:border-rose-500 focus:outline-none"
                        />
                        <p className="text-[10px] text-slate-500 mt-1">
                          Used strictly for discovering and scanning authorized repositories.
                        </p>
                      </div>
                    )}
                  </div>
                </div>

                {/* Repository Listing & Selection */}
                {discoveredRepos.length > 0 && (
                  <div className="flex flex-col gap-2">
                    <div className="flex items-center justify-between">
                      <label className="text-[11px] font-mono text-slate-400 uppercase tracking-wider">
                        Select Repository ({discoveredRepos.length} found)
                      </label>
                      <input
                        type="text"
                        placeholder="Search repos..."
                        value={discoveryFilter}
                        onChange={(e) => setDiscoveryFilter(e.target.value)}
                        className="w-40 bg-slate-950/60 border border-slate-800 rounded-lg px-2.5 py-1 text-slate-200 text-xs focus:border-rose-500 focus:outline-none"
                      />
                    </div>

                    <div className="max-h-56 overflow-y-auto space-y-2 pr-1 custom-scrollbar">
                      {discoveredRepos
                        .filter((r) => {
                          const q = discoveryFilter.toLowerCase();
                          return (
                            r.name?.toLowerCase().includes(q) ||
                            r.owner?.toLowerCase().includes(q) ||
                            r.description?.toLowerCase().includes(q)
                          );
                        })
                        .map((repo) => {
                          const isSelected =
                            selectedDiscoveredRepo?.owner === repo.owner &&
                            selectedDiscoveredRepo?.name === repo.name;
                          return (
                            <div
                              key={repo.id || `${repo.owner}/${repo.name}`}
                              onClick={() => handleSelectDiscoveredRepo(repo)}
                              className={`p-3 rounded-xl border cursor-pointer transition flex items-center justify-between gap-3 ${
                                isSelected
                                  ? 'bg-rose-500/15 border-rose-500 text-white shadow-lg shadow-rose-950/30'
                                  : 'bg-slate-950/40 border-slate-800/80 hover:border-slate-700 text-slate-300 hover:bg-slate-800/40'
                              }`}
                            >
                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2">
                                  <FolderGit2 className={`w-4 h-4 shrink-0 ${isSelected ? 'text-rose-400' : 'text-slate-400'}`} />
                                  <span className="font-semibold text-white text-xs truncate">
                                    {repo.owner} / <span className="text-rose-400">{repo.name}</span>
                                  </span>
                                </div>
                                {repo.description && (
                                  <p className="text-[11px] text-slate-400 truncate mt-0.5">
                                    {repo.description}
                                  </p>
                                )}
                              </div>

                              <div className="flex items-center gap-2 shrink-0">
                                <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 border border-slate-700/60 font-mono text-slate-300">
                                  {repo.defaultBranch || 'main'}
                                </span>
                                <span
                                  className={`text-[10px] px-2 py-0.5 rounded font-mono font-medium ${
                                    repo.isPrivate || repo.visibility === 'PRIVATE'
                                      ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                                      : 'bg-sky-500/20 text-sky-300 border border-sky-500/30'
                                  }`}
                                >
                                  {repo.isPrivate || repo.visibility === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC'}
                                </span>
                                <div
                                  className={`w-5 h-5 rounded-full flex items-center justify-center border transition ${
                                    isSelected
                                      ? 'bg-rose-500 border-rose-500 text-white'
                                      : 'border-slate-700 bg-slate-900'
                                  }`}
                                >
                                  {isSelected && <Check className="w-3 h-3 stroke-[3]" />}
                                </div>
                              </div>
                            </div>
                          );
                        })}
                    </div>
                  </div>
                )}

                {/* Selected Repository Confirmation Card */}
                {selectedDiscoveredRepo && (
                  <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-between text-xs">
                    <div>
                      <div className="font-semibold text-white">
                        Selected: <span className="text-rose-400">{newRepo.owner}/{newRepo.name}</span>
                      </div>
                      <div className="text-[11px] text-slate-400 mt-0.5">
                        Branch: <span className="font-mono text-slate-200">{newRepo.defaultBranch}</span> • Visibility: <span className="font-mono text-slate-200">{newRepo.visibility}</span>
                      </div>
                    </div>
                    <span className="text-emerald-400 font-semibold text-xs flex items-center gap-1">
                      <CheckCircle2 className="w-3.5 h-3.5" /> Ready
                    </span>
                  </div>
                )}

                {/* Footer */}
                <div className="flex justify-end gap-2 mt-4 pt-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => setConnectRepoModal(false)}
                    className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={() => handleConnectRepository()}
                    disabled={!selectedDiscoveredRepo}
                    className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-semibold flex items-center gap-1.5 shadow-lg shadow-rose-950/40 transition disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <Sparkles className="w-3.5 h-3.5" />
                    Connect {selectedDiscoveredRepo ? `${selectedDiscoveredRepo.name}` : 'Selected Repo'}
                  </button>
                </div>
              </div>
            )}

            {/* Tab: Manual URL Entry */}
            {connectMode === 'manual' && (
              <form onSubmit={handleConnectRepository} className="flex flex-col gap-3 mt-4 text-xs">
                <div>
                  <label className="text-slate-400">Provider</label>
                  <select
                    value={newRepo.provider}
                    onChange={(e) => setNewRepo({ ...newRepo, provider: e.target.value })}
                    className="w-full mt-1 bg-slate-950/60 border border-slate-800 rounded-xl px-3 py-2 text-white"
                  >
                    <option value="GITHUB">GitHub</option>
                    <option value="GITLAB">GitLab</option>
                    <option value="BITBUCKET">Bitbucket</option>
                    <option value="AZURE_DEVOPS">Azure DevOps</option>
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-slate-400">Owner / Org</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. acme-corp"
                      value={newRepo.owner}
                      onChange={(e) => setNewRepo({ ...newRepo, owner: e.target.value })}
                      className="w-full mt-1 bg-slate-950/60 border border-slate-800 rounded-xl px-3 py-2 text-white"
                    />
                  </div>
                  <div>
                    <label className="text-slate-400">Repository Name</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. payment-service"
                      value={newRepo.name}
                      onChange={(e) => setNewRepo({ ...newRepo, name: e.target.value })}
                      className="w-full mt-1 bg-slate-950/60 border border-slate-800 rounded-xl px-3 py-2 text-white"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-slate-400">Clone URL (HTTPS)</label>
                  <input
                    type="text"
                    placeholder="https://github.com/acme-corp/payment-service.git"
                    value={newRepo.cloneUrl}
                    onChange={(e) => setNewRepo({ ...newRepo, cloneUrl: e.target.value })}
                    className="w-full mt-1 bg-slate-950/60 border border-slate-800 rounded-xl px-3 py-2 text-white"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-slate-400">Default Branch</label>
                    <input
                      type="text"
                      value={newRepo.defaultBranch}
                      onChange={(e) => setNewRepo({ ...newRepo, defaultBranch: e.target.value })}
                      className="w-full mt-1 bg-slate-950/60 border border-slate-800 rounded-xl px-3 py-2 text-white"
                    />
                  </div>
                  <div>
                    <label className="text-slate-400">Visibility</label>
                    <select
                      value={newRepo.visibility}
                      onChange={(e) => setNewRepo({ ...newRepo, visibility: e.target.value })}
                      className="w-full mt-1 bg-slate-950/60 border border-slate-800 rounded-xl px-3 py-2 text-white"
                    >
                      <option value="PRIVATE">PRIVATE</option>
                      <option value="PUBLIC">PUBLIC</option>
                      <option value="INTERNAL">INTERNAL</option>
                    </select>
                  </div>
                </div>

                <div className="flex justify-end gap-2 mt-4 pt-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => setConnectRepoModal(false)}
                    className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-semibold"
                  >
                    Connect & Verify
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}



      {/* Modal: Delete Repository Confirmation */}
      {deleteRepoModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 text-slate-200 shadow-2xl">
            <div className="flex items-center gap-3 text-rose-400 mb-3">
              <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/30">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">
                  Delete Repository
                </h3>
                <p className="text-xs text-slate-400">
                  Permanent removal from workspace
                </p>
              </div>
            </div>

            <p className="text-xs text-slate-300 mt-3 leading-relaxed">
              Are you sure you want to delete <span className="font-mono font-semibold text-rose-400">{deleteRepoModal.owner}/{deleteRepoModal.name}</span>?
              This will remove all associated secret scans, finding history, and custom policies for this repository.
            </p>

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
                {isDeleting ? 'Deleting...' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

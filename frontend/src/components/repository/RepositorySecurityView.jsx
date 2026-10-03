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
} from 'lucide-react';

export const RepositorySecurityView = () => {
  const { activeWorkspace } = useAuth();
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
  const [triggerScanModal, setTriggerScanModal] = useState(null);
  const [remediationModal, setRemediationModal] = useState(null);
  const [actionNotes, setActionNotes] = useState('');

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

  const handleConnectRepository = async (e) => {
    e.preventDefault();
    try {
      await repositoryApi.connectRepository(workspaceId, newRepo);
      setConnectRepoModal(false);
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
      alert(err.message || 'Failed to connect repository');
    }
  };

  const handleTriggerScan = async (repoId, scanType = 'INCREMENTAL') => {
    try {
      await repositoryApi.triggerScan(workspaceId, repoId, { scanType });
      setTriggerScanModal(null);
      fetchData();
    } catch (err) {
      alert(err.message || 'Failed to trigger scan');
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
            onClick={() => setConnectRepoModal(true)}
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
                <button
                  onClick={() => setTriggerScanModal(repo)}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 flex items-center gap-1.5 transition"
                >
                  <Play className="w-3.5 h-3.5 text-rose-400" />
                  Scan Now
                </button>
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
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 text-slate-200 shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <FolderGit2 className="w-5 h-5 text-rose-500" />
                  Connect Repository
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  Onboard a Git repository for continuous secret detection
                </p>
              </div>
              <button
                onClick={() => setConnectRepoModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

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
          </div>
        </div>
      )}

      {/* Modal: Trigger Scan */}
      {triggerScanModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 text-slate-200 shadow-2xl">
            <h3 className="text-base font-bold text-white mb-2">
              Trigger Secret Leak Scan
            </h3>
            <p className="text-xs text-slate-400 mb-4">
              Select scan depth for {triggerScanModal.owner}/{triggerScanModal.name}
            </p>

            <div className="flex flex-col gap-2">
              <button
                onClick={() => handleTriggerScan(triggerScanModal.id, 'INCREMENTAL')}
                className="w-full p-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-left border border-slate-700 transition"
              >
                <div className="font-semibold text-white text-xs">Incremental Scan (Fast)</div>
                <div className="text-[11px] text-slate-400 mt-0.5">Scans commits since last successful scan</div>
              </button>
              <button
                onClick={() => handleTriggerScan(triggerScanModal.id, 'FULL')}
                className="w-full p-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-left border border-slate-700 transition"
              >
                <div className="font-semibold text-white text-xs">Full Working Tree Scan</div>
                <div className="text-[11px] text-slate-400 mt-0.5">Scans all current working files</div>
              </button>
              <button
                onClick={() => handleTriggerScan(triggerScanModal.id, 'GIT_HISTORY')}
                className="w-full p-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-left border border-slate-700 transition"
              >
                <div className="font-semibold text-white text-xs">Deep Git History Scan</div>
                <div className="text-[11px] text-slate-400 mt-0.5">Inspects all commits and historical diffs</div>
              </button>
            </div>

            <div className="flex justify-end mt-4">
              <button
                onClick={() => setTriggerScanModal(null)}
                className="px-4 py-2 rounded-xl bg-slate-800 text-xs text-slate-300"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

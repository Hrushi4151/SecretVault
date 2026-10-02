import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { syncApi } from '../../api/sync';
import { projectApi } from '../../api/projects';
import { environmentApi } from '../../api/environments';
import {
  RefreshCw,
  ShieldAlert,
  Play,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Clock,
  Layers,
  ArrowRight,
  Filter,
  Check,
  Eye,
  Sliders,
  Sparkles,
  Loader2,
  FileText,
  Info,
  ChevronDown,
  ChevronRight,
  Shield,
  Activity,
  Zap,
  FolderGit2,
} from 'lucide-react';

export const SyncCenterView = () => {
  const { activeWorkspace } = useAuth();
  const [activeTab, setActiveTab] = useState('DRIFT'); // 'DRIFT' | 'SIMULATION' | 'HISTORY'
  const [driftRecords, setDriftRecords] = useState([]);
  const [syncJobs, setSyncJobs] = useState([]);
  const [projects, setProjects] = useState([]);
  const [environmentsMap, setEnvironmentsMap] = useState({}); // projectId -> environments[]
  const [isLoading, setIsLoading] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [feedback, setFeedback] = useState(null);

  // Filters for Drift
  const [statusFilter, setStatusFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  // Simulation State
  const [simScope, setSimScope] = useState('WORKSPACE'); // 'WORKSPACE' | 'PROJECT' | 'ENVIRONMENT'
  const [simProjectId, setSimProjectId] = useState('');
  const [simEnvironmentId, setSimEnvironmentId] = useState('');
  const [simPolicy, setSimPolicy] = useState('SAFE_RECONCILIATION'); // 'SAFE_RECONCILIATION' | 'FORCE_OVERWRITE' | 'MIRROR_EXACT'
  const [isSimulating, setIsSimulating] = useState(false);
  const [dryRunResult, setDryRunResult] = useState(null);

  // Job Details Modal
  const [selectedJob, setSelectedJob] = useState(null);
  const [jobOperations, setJobOperations] = useState([]);
  const [isLoadingOperations, setIsLoadingOperations] = useState(false);

  const showFeedback = (type, message) => {
    setFeedback({ type, message });
    setTimeout(() => setFeedback(null), 6000);
  };

  const loadData = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    try {
      setIsLoading(true);
      const [driftRes, jobsRes, projRes] = await Promise.all([
        syncApi.getDriftRecords(activeWorkspace.id, {
          status: statusFilter || undefined,
          severity: severityFilter || undefined,
          driftType: typeFilter || undefined,
          size: 50,
        }),
        syncApi.getSyncJobs(activeWorkspace.id, { size: 20 }),
        projectApi.list(activeWorkspace.id),
      ]);

      const driftList = driftRes?.content || driftRes?.items || (Array.isArray(driftRes) ? driftRes : []);
      setDriftRecords(driftList);

      const jobsList = jobsRes?.content || jobsRes?.items || (Array.isArray(jobsRes) ? jobsRes : []);
      setSyncJobs(jobsList);

      const projList = Array.isArray(projRes) ? projRes : (projRes?.items || projRes?.content || []);
      setProjects(projList);

      // Preload environments
      const envMap = {};
      for (const p of projList) {
        try {
          const eRes = await environmentApi.list(activeWorkspace.id, p.id);
          envMap[p.id] = Array.isArray(eRes) ? eRes : (eRes?.items || eRes?.content || []);
        } catch (err) {
          envMap[p.id] = [];
        }
      }
      setEnvironmentsMap(envMap);

      if (projList.length > 0 && !simProjectId) {
        const firstPId = projList[0].id;
        setSimProjectId(firstPId);
        if (envMap[firstPId] && envMap[firstPId].length > 0) {
          setSimEnvironmentId(envMap[firstPId][0].id);
        }
      }
    } catch (err) {
      showFeedback('error', err.message || 'Failed to load sync engine state.');
    } finally {
      setIsLoading(false);
    }
  }, [activeWorkspace?.id, statusFilter, severityFilter, typeFilter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRunDriftScan = async () => {
    try {
      setIsScanning(true);
      const res = await syncApi.triggerDriftDetection(activeWorkspace.id);
      const list = Array.isArray(res) ? res : [];
      showFeedback('success', `Drift scan complete! Analyzed remote state across all configured mappings (${list.length} drift items detected).`);
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Drift scan execution failed.');
    } finally {
      setIsScanning(false);
    }
  };

  const handleUpdateDriftStatus = async (driftId, newStatus) => {
    try {
      setActionLoadingId(driftId);
      await syncApi.updateDriftStatus(activeWorkspace.id, driftId, {
        status: newStatus,
        resolutionNotes: `Status changed to ${newStatus} from UI console.`,
      });
      showFeedback('success', `Drift record triage updated to ${newStatus}.`);
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Failed to update drift status.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleRunDryRun = async () => {
    try {
      setIsSimulating(true);
      setDryRunResult(null);
      let res;
      if (simScope === 'PROJECT' && simProjectId) {
        res = await syncApi.executeProjectDryRun(activeWorkspace.id, simProjectId, {
          reconciliationPolicy: simPolicy,
        });
      } else if (simScope === 'ENVIRONMENT' && simEnvironmentId) {
        res = await syncApi.executeEnvironmentDryRun(activeWorkspace.id, simEnvironmentId, {
          reconciliationPolicy: simPolicy,
        });
      } else {
        res = await syncApi.executeDryRun(activeWorkspace.id, {
          reconciliationPolicy: simPolicy,
        });
      }
      setDryRunResult(res);
      showFeedback('info', 'Simulation completed successfully. Review planned operations below.');
    } catch (err) {
      showFeedback('error', err.message || 'Dry-run simulation failed.');
    } finally {
      setIsSimulating(false);
    }
  };

  const handleExecuteLiveSync = async () => {
    if (!window.confirm(`Execute live synchronization with ${simPolicy} policy? Remote provider variables will be reconciled.`)) {
      return;
    }
    try {
      setIsLoading(true);
      let res;
      if (simScope === 'PROJECT' && simProjectId) {
        res = await syncApi.executeProjectSync(activeWorkspace.id, simProjectId, {
          reconciliationPolicy: simPolicy,
        });
      } else if (simScope === 'ENVIRONMENT' && simEnvironmentId) {
        res = await syncApi.executeEnvironmentSync(activeWorkspace.id, simEnvironmentId, {
          reconciliationPolicy: simPolicy,
        });
      } else {
        res = await syncApi.executeSync(activeWorkspace.id, {
          reconciliationPolicy: simPolicy,
        });
      }
      showFeedback(
        'success',
        `Sync Job #${res?.jobId?.slice(0, 8) || ''} finished with status: ${res?.status || 'SUCCESS'} (${res?.succeededOperations || 0} succeeded).`
      );
      loadData();
      setActiveTab('HISTORY');
    } catch (err) {
      showFeedback('error', err.message || 'Live synchronization failed.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleInspectJob = async (job) => {
    setSelectedJob(job);
    try {
      setIsLoadingOperations(true);
      const res = await syncApi.getSyncJobOperations(activeWorkspace.id, job.id, { size: 100 });
      const ops = res?.content || res?.items || (Array.isArray(res) ? res : []);
      setJobOperations(ops);
    } catch (err) {
      showFeedback('error', 'Failed to load job operations audit trail.');
    } finally {
      setIsLoadingOperations(false);
    }
  };

  const activeDriftCount = driftRecords.filter((d) => d.status === 'DRIFT_DETECTED').length;
  const resolvedDriftCount = driftRecords.filter((d) => d.status === 'RESOLVED').length;

  return (
    <div className="flex flex-col gap-6 max-w-7xl mx-auto pb-16 font-body text-white animate-fade-in">
      {/* Top Banner & Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-[#30000F] border border-[#FFB4C8]/15 p-6 rounded-2xl shadow-xl">
        <div className="flex items-start gap-4">
          <div className="w-12 h-12 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/15 shrink-0">
            <RefreshCw className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-headline font-bold text-white">Sync Center &amp; Drift Detection</h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30 uppercase tracking-wide">
                Phase 8 Engine
              </span>
            </div>
            <p className="text-xs text-[#F4B5C8] mt-1 max-w-2xl leading-relaxed">
              Automated state reconciliation engine. Continuously detects state divergence between SecretVault desired state and actual remote provider state (Vercel &amp; Render), providing dry-run simulation and atomic reconciliation.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleRunDriftScan}
            disabled={isScanning}
            className="flex items-center gap-2 px-3 py-2 rounded-xl bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30 transition-all text-xs font-semibold cursor-pointer disabled:opacity-50"
          >
            <Activity className={`w-3.5 h-3.5 text-[#FF2D6D] ${isScanning ? 'animate-spin' : ''}`} />
            <span>{isScanning ? 'Scanning Providers...' : 'Run Drift Scan'}</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setActiveTab('SIMULATION');
              handleRunDryRun();
            }}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] shadow-lg shadow-[#FF2D6D]/25 transition-all text-xs font-bold cursor-pointer"
          >
            <Play className="w-3.5 h-3.5" />
            <span>Dry-Run / Sync</span>
          </button>
        </div>
      </div>

      {/* Feedback Alert */}
      {feedback && (
        <div
          className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-xs animate-fade-in ${
            feedback.type === 'success'
              ? 'bg-[#34D399]/10 border-[#34D399]/30 text-[#34D399]'
              : feedback.type === 'info'
              ? 'bg-[#818CF8]/10 border-[#818CF8]/30 text-[#818CF8]'
              : 'bg-[#F87171]/10 border-[#F87171]/30 text-[#F87171]'
          }`}
        >
          {feedback.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 shrink-0" />
          ) : feedback.type === 'info' ? (
            <Info className="w-4 h-4 shrink-0" />
          ) : (
            <AlertTriangle className="w-4 h-4 shrink-0" />
          )}
          <span className="flex-1 font-medium">{feedback.message}</span>
          <button type="button" onClick={() => setFeedback(null)} className="text-current opacity-70 hover:opacity-100">
            ×
          </button>
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Active Drift Items</span>
            <ShieldAlert className="w-4 h-4 text-[#F87171]" />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-headline font-bold text-white">{activeDriftCount}</span>
            <span className={`text-[11px] font-mono ${activeDriftCount > 0 ? 'text-[#F87171]' : 'text-[#34D399]'}`}>
              {activeDriftCount > 0 ? 'Action Recommended' : 'Clean / In Sync'}
            </span>
          </div>
        </div>

        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Resolved Reconciliations</span>
            <CheckCircle2 className="w-4 h-4 text-[#34D399]" />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-headline font-bold text-white">{resolvedDriftCount}</span>
            <span className="text-[11px] text-[#34D399] font-mono">Auto Reconciled</span>
          </div>
        </div>

        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Sync Executions</span>
            <Layers className="w-4 h-4 text-[#818CF8]" />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-headline font-bold text-white">{syncJobs.length}</span>
            <span className="text-[11px] text-[#F4B5C8] font-mono">Historical Jobs</span>
          </div>
        </div>

        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Engine Idempotency</span>
            <Zap className="w-4 h-4 text-[#FBBF24]" />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-sm font-headline font-bold text-white">100% SHA-256</span>
            <span className="text-[10px] text-[#A26377] font-mono">Zero Blind Rewrites</span>
          </div>
        </div>
      </div>

      {/* Sub Tabs */}
      <div className="flex items-center gap-2 border-b border-[#FFB4C8]/15 pb-2">
        <button
          type="button"
          onClick={() => setActiveTab('DRIFT')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
            activeTab === 'DRIFT'
              ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
              : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
          }`}
        >
          <ShieldAlert className="w-3.5 h-3.5" />
          <span>Drift Records ({driftRecords.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('SIMULATION')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
            activeTab === 'SIMULATION'
              ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
              : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
          }`}
        >
          <Sliders className="w-3.5 h-3.5" />
          <span>Simulation &amp; Sync Planner</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('HISTORY')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
            activeTab === 'HISTORY'
              ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
              : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
          }`}
        >
          <Clock className="w-3.5 h-3.5" />
          <span>Sync Job Audit Trail ({syncJobs.length})</span>
        </button>
      </div>

      {/* TAB 1: DRIFT RECORDS */}
      {activeTab === 'DRIFT' && (
        <div className="flex flex-col gap-4">
          {/* Filters Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 bg-[#30000F] border border-[#FFB4C8]/15 p-3.5 rounded-2xl">
            <div className="flex items-center gap-2 text-xs">
              <Filter className="w-3.5 h-3.5 text-[#A26377]" />
              <span className="text-[#A26377] font-mono text-[10px] uppercase font-bold">Filter By:</span>

              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-[#1E000A] border border-[#FFB4C8]/20 text-white text-xs"
              >
                <option value="">All Statuses</option>
                <option value="DRIFT_DETECTED">DRIFT_DETECTED</option>
                <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="IGNORED">IGNORED</option>
              </select>

              <select
                value={severityFilter}
                onChange={(e) => setSeverityFilter(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-[#1E000A] border border-[#FFB4C8]/20 text-white text-xs"
              >
                <option value="">All Severities</option>
                <option value="CRITICAL">CRITICAL</option>
                <option value="HIGH">HIGH</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="LOW">LOW</option>
                <option value="INFO">INFO</option>
              </select>

              <select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-[#1E000A] border border-[#FFB4C8]/20 text-white text-xs"
              >
                <option value="">All Drift Types</option>
                <option value="MISSING_ON_PROVIDER">MISSING_ON_PROVIDER</option>
                <option value="EXTRA_ON_PROVIDER">EXTRA_ON_PROVIDER</option>
                <option value="VALUE_MISMATCH">VALUE_MISMATCH</option>
                <option value="METADATA_MISMATCH">METADATA_MISMATCH</option>
              </select>
            </div>

            <button
              type="button"
              onClick={handleRunDriftScan}
              disabled={isScanning}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold transition-all cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-3 h-3 ${isScanning ? 'animate-spin' : ''}`} />
              <span>Scan Now</span>
            </button>
          </div>

          {/* Drift Table */}
          {driftRecords.length === 0 ? (
            <div className="p-12 bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl text-center flex flex-col items-center gap-3">
              <div className="w-12 h-12 rounded-xl bg-[#34D399]/15 border border-[#34D399]/30 flex items-center justify-center text-[#34D399]">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <span className="text-base font-bold text-white">Zero Configuration Drift Detected</span>
              <span className="text-xs text-[#F4B5C8] max-w-md">
                All secret values on external provider environments match SecretVault desired state exactly.
              </span>
            </div>
          ) : (
            <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[#1E000A] text-[#A26377] font-mono text-[10px] uppercase border-b border-[#FFB4C8]/15 tracking-wider">
                    <tr>
                      <th className="py-3 px-4">Secret Key</th>
                      <th className="py-3 px-4">Drift Type</th>
                      <th className="py-3 px-4">Severity</th>
                      <th className="py-3 px-4">Status</th>
                      <th className="py-3 px-4">Detected At</th>
                      <th className="py-3 px-4 text-right">Triage Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#FFB4C8]/10 text-white font-body">
                    {driftRecords.map((d) => {
                      const isPending = d.status === 'DRIFT_DETECTED';
                      const isActionLoading = actionLoadingId === d.id;

                      return (
                        <tr key={d.id} className="hover:bg-[#3F0016]/50 transition-colors">
                          <td className="py-3 px-4">
                            <div className="flex flex-col">
                              <span className="font-mono font-bold text-white text-xs">{d.secretKey}</span>
                              <span className="text-[10px] font-mono text-[#A26377]">
                                ID: {d.id.slice(0, 8)}...
                              </span>
                            </div>
                          </td>

                          <td className="py-3 px-4">
                            <span
                              className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold ${
                                d.driftType === 'MISSING_ON_PROVIDER'
                                  ? 'bg-[#F87171]/20 text-[#F87171] border border-[#F87171]/30'
                                  : d.driftType === 'EXTRA_ON_PROVIDER'
                                  ? 'bg-[#FBBF24]/20 text-[#FBBF24] border border-[#FBBF24]/30'
                                  : 'bg-[#818CF8]/20 text-[#818CF8] border border-[#818CF8]/30'
                              }`}
                            >
                              {d.driftType}
                            </span>
                          </td>

                          <td className="py-3 px-4">
                            <span
                              className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold ${
                                d.severity === 'CRITICAL' || d.severity === 'HIGH'
                                  ? 'bg-[#F87171]/20 text-[#F87171]'
                                  : d.severity === 'MEDIUM'
                                  ? 'bg-[#FBBF24]/20 text-[#FBBF24]'
                                  : 'bg-[#818CF8]/20 text-[#818CF8]'
                              }`}
                            >
                              {d.severity}
                            </span>
                          </td>

                          <td className="py-3 px-4">
                            <span
                              className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold uppercase flex items-center gap-1.5 w-max ${
                                d.status === 'RESOLVED'
                                  ? 'bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30'
                                  : d.status === 'ACKNOWLEDGED'
                                  ? 'bg-[#818CF8]/15 text-[#818CF8] border border-[#818CF8]/30'
                                  : d.status === 'IGNORED'
                                  ? 'bg-[#A26377]/15 text-[#A26377] border border-[#A26377]/30'
                                  : 'bg-[#F87171]/15 text-[#F87171] border border-[#F87171]/30'
                              }`}
                            >
                              <span
                                className={`w-1.5 h-1.5 rounded-full ${
                                  d.status === 'RESOLVED' ? 'bg-[#34D399]' : 'bg-[#F87171]'
                                }`}
                              />
                              {d.status}
                            </span>
                          </td>

                          <td className="py-3 px-4 font-mono text-[10px] text-[#A26377]">
                            {new Date(d.detectedAt || d.createdAt).toLocaleString()}
                          </td>

                          <td className="py-3 px-4 text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              {isPending && (
                                <button
                                  type="button"
                                  onClick={() => handleUpdateDriftStatus(d.id, 'ACKNOWLEDGED')}
                                  disabled={isActionLoading}
                                  className="px-2 py-1 rounded bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 text-[10px] font-semibold cursor-pointer"
                                >
                                  Ack
                                </button>
                              )}

                              {d.status !== 'RESOLVED' && (
                                <button
                                  type="button"
                                  onClick={() => handleUpdateDriftStatus(d.id, 'RESOLVED')}
                                  disabled={isActionLoading}
                                  className="px-2 py-1 rounded bg-[#34D399]/15 text-[#34D399] hover:bg-[#34D399]/25 border border-[#34D399]/30 text-[10px] font-semibold cursor-pointer"
                                >
                                  Resolve
                                </button>
                              )}

                              {d.status !== 'IGNORED' && (
                                <button
                                  type="button"
                                  onClick={() => handleUpdateDriftStatus(d.id, 'IGNORED')}
                                  disabled={isActionLoading}
                                  className="px-2 py-1 rounded text-[#A26377] hover:text-white text-[10px] cursor-pointer"
                                >
                                  Ignore
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: SIMULATION & DRY RUN */}
      {activeTab === 'SIMULATION' && (
        <div className="flex flex-col gap-6">
          {/* Simulation Config Panel */}
          <div className="bg-[#30000F] border border-[#FFB4C8]/15 p-6 rounded-2xl flex flex-col gap-5 shadow-xl">
            <div>
              <h2 className="text-base font-bold text-white">Sync Simulation &amp; Planning Configuration</h2>
              <p className="text-xs text-[#F4B5C8] mt-0.5">
                Run a non-destructive dry-run to preview mutations before applying them to external providers.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
              {/* Scope */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">Sync Scope</label>
                <select
                  value={simScope}
                  onChange={(e) => setSimScope(e.target.value)}
                  className="px-3 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
                >
                  <option value="WORKSPACE">Workspace-Wide (All Mappings)</option>
                  <option value="PROJECT">Project-Scoped</option>
                  <option value="ENVIRONMENT">Environment-Scoped</option>
                </select>
              </div>

              {/* Project picker if scoped */}
              {simScope !== 'WORKSPACE' && (
                <div className="flex flex-col gap-1.5">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">Target Project</label>
                  <select
                    value={simProjectId}
                    onChange={(e) => {
                      const pId = e.target.value;
                      setSimProjectId(pId);
                      const envList = environmentsMap[pId] || [];
                      if (envList.length > 0) {
                        setSimEnvironmentId(envList[0].id);
                      } else {
                        setSimEnvironmentId('');
                      }
                    }}
                    className="px-3 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
                  >
                    {projects.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Environment picker if ENVIRONMENT scoped */}
              {simScope === 'ENVIRONMENT' && (
                <div className="flex flex-col gap-1.5">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">Target Environment</label>
                  <select
                    value={simEnvironmentId}
                    onChange={(e) => setSimEnvironmentId(e.target.value)}
                    className="px-3 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
                  >
                    {(environmentsMap[simProjectId] || []).map((env) => (
                      <option key={env.id} value={env.id}>
                        {env.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Reconciliation Policy */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  Reconciliation Policy
                </label>
                <select
                  value={simPolicy}
                  onChange={(e) => setSimPolicy(e.target.value)}
                  className="px-3 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
                >
                  <option value="SAFE_RECONCILIATION">SAFE_RECONCILIATION (Non-destructive, create &amp; update)</option>
                  <option value="FORCE_OVERWRITE">FORCE_OVERWRITE (Overwrite provider unconditionally)</option>
                  <option value="MIRROR_EXACT">MIRROR_EXACT (Strict mirror: prune/delete extra keys)</option>
                </select>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15">
              <button
                type="button"
                onClick={handleRunDryRun}
                disabled={isSimulating}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#3F0016] text-white hover:bg-[#4A001C] border border-[#FFB4C8]/20 text-xs font-bold transition-all cursor-pointer disabled:opacity-50"
              >
                {isSimulating ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin text-[#FF2D6D]" />
                ) : (
                  <Sliders className="w-3.5 h-3.5 text-[#FF2D6D]" />
                )}
                <span>{isSimulating ? 'Simulating Dry-Run...' : 'Calculate Dry-Run Plan'}</span>
              </button>

              <button
                type="button"
                onClick={handleExecuteLiveSync}
                disabled={isLoading}
                className="flex items-center gap-2 px-6 py-2.5 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] shadow-lg shadow-[#FF2D6D]/25 text-xs font-bold transition-all cursor-pointer disabled:opacity-50"
              >
                <Play className="w-3.5 h-3.5" />
                <span>Execute Live Reconciliation</span>
              </button>
            </div>
          </div>

          {/* Dry Run Breakdown if available */}
          {dryRunResult && (
            <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-6 flex flex-col gap-4 animate-scale-in">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-white">Planned Reconciliation Operations</h3>
                  <span className="text-[11px] font-mono text-[#A26377]">
                    Simulation Plan ID: {dryRunResult.jobId?.slice(0, 8) || 'dry-run'} • Scope: {dryRunResult.scope} • Policy: {dryRunResult.reconciliationPolicy}
                  </span>
                </div>

                <div className="flex items-center gap-2 font-mono text-xs">
                  <span className="px-2.5 py-1 rounded bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30">
                    +{dryRunResult.createCount || 0} CREATE
                  </span>
                  <span className="px-2.5 py-1 rounded bg-[#818CF8]/15 text-[#818CF8] border border-[#818CF8]/30">
                    ~{dryRunResult.updateCount || 0} UPDATE
                  </span>
                  <span className="px-2.5 py-1 rounded bg-[#F87171]/15 text-[#F87171] border border-[#F87171]/30">
                    -{dryRunResult.deleteCount || 0} DELETE
                  </span>
                  <span className="px-2.5 py-1 rounded bg-[#3F0016] text-[#A26377] border border-[#FFB4C8]/10">
                    ={dryRunResult.noOpCount || 0} NO_OP
                  </span>
                </div>
              </div>

              {/* Planned Operations list */}
              {dryRunResult.plannedOperations && dryRunResult.plannedOperations.length > 0 ? (
                <div className="overflow-x-auto rounded-xl border border-[#FFB4C8]/15">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-[#1E000A] text-[#A26377] font-mono text-[10px] uppercase border-b border-[#FFB4C8]/15">
                      <tr>
                        <th className="py-2.5 px-4">Operation</th>
                        <th className="py-2.5 px-4">Secret Key</th>
                        <th className="py-2.5 px-4">Target Provider</th>
                        <th className="py-2.5 px-4">Reason / Notes</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#FFB4C8]/10 font-body">
                      {dryRunResult.plannedOperations.map((op, idx) => (
                        <tr key={idx} className="hover:bg-[#3F0016]/40">
                          <td className="py-2.5 px-4">
                            <span
                              className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                                op.operationType === 'CREATE'
                                  ? 'bg-[#34D399]/20 text-[#34D399]'
                                  : op.operationType === 'UPDATE'
                                  ? 'bg-[#818CF8]/20 text-[#818CF8]'
                                  : op.operationType === 'DELETE'
                                  ? 'bg-[#F87171]/20 text-[#F87171]'
                                  : 'bg-[#3F0016] text-[#A26377]'
                              }`}
                            >
                              {op.operationType}
                            </span>
                          </td>
                          <td className="py-2.5 px-4 font-mono font-bold text-white">{op.secretKey}</td>
                          <td className="py-2.5 px-4 text-[#F4B5C8]">{op.providerResourceType || 'PROVIDER'}</td>
                          <td className="py-2.5 px-4 text-[#A26377] text-[11px]">{op.reason || 'State divergence'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="p-4 bg-[#1E000A] rounded-xl text-center text-xs text-[#F4B5C8]">
                  No changes required. All remote targets are already in desired state.
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: SYNC JOB AUDIT TRAIL */}
      {activeTab === 'HISTORY' && (
        <div className="flex flex-col gap-4">
          <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl overflow-hidden shadow-xl">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[#1E000A] text-[#A26377] font-mono text-[10px] uppercase border-b border-[#FFB4C8]/15 tracking-wider">
                  <tr>
                    <th className="py-3 px-4">Job ID</th>
                    <th className="py-3 px-4">Type / Mode</th>
                    <th className="py-3 px-4">Scope &amp; Policy</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Operations (S / F / Total)</th>
                    <th className="py-3 px-4">Execution Time</th>
                    <th className="py-3 px-4 text-right">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#FFB4C8]/10 text-white font-body">
                  {syncJobs.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-[#A26377]">
                        No sync jobs recorded yet. Run a simulation or sync execution above.
                      </td>
                    </tr>
                  ) : (
                    syncJobs.map((job) => {
                      const isSuccess = job.status === 'SUCCESS';
                      const isFailed = job.status === 'FAILED';

                      return (
                        <tr key={job.id} className="hover:bg-[#3F0016]/50 transition-colors">
                          <td className="py-3 px-4 font-mono text-xs font-bold text-white">
                            #{job.id.slice(0, 8)}
                          </td>

                          <td className="py-3 px-4">
                            <span
                              className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                                job.dryRun
                                  ? 'bg-[#818CF8]/15 text-[#818CF8] border border-[#818CF8]/30'
                                  : 'bg-[#FF2D6D]/15 text-[#FF85A2] border border-[#FF2D6D]/30'
                              }`}
                            >
                              {job.dryRun ? 'DRY_RUN' : 'LIVE_SYNC'}
                            </span>
                          </td>

                          <td className="py-3 px-4">
                            <div className="flex flex-col">
                              <span className="font-semibold text-white">{job.scope}</span>
                              <span className="text-[10px] font-mono text-[#A26377]">{job.reconciliationPolicy}</span>
                            </div>
                          </td>

                          <td className="py-3 px-4">
                            <span
                              className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold uppercase ${
                                isSuccess
                                  ? 'bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30'
                                  : isFailed
                                  ? 'bg-[#F87171]/15 text-[#F87171] border border-[#F87171]/30'
                                  : 'bg-[#FBBF24]/15 text-[#FBBF24] border border-[#FBBF24]/30'
                              }`}
                            >
                              {job.status}
                            </span>
                          </td>

                          <td className="py-3 px-4 font-mono text-xs">
                            <span className="text-[#34D399]">{job.succeededOperations || 0}</span>
                            <span className="text-[#A26377]"> / </span>
                            <span className="text-[#F87171]">{job.failedOperations || 0}</span>
                            <span className="text-[#A26377]"> / </span>
                            <span className="text-white">{job.totalOperations || 0}</span>
                          </td>

                          <td className="py-3 px-4 font-mono text-[10px] text-[#A26377]">
                            {new Date(job.createdAt).toLocaleString()}
                          </td>

                          <td className="py-3 px-4 text-right">
                            <button
                              type="button"
                              onClick={() => handleInspectJob(job)}
                              className="p-1.5 rounded-lg bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-colors cursor-pointer"
                              title="Inspect Operations Trail"
                            >
                              <Eye className="w-3.5 h-3.5" />
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* JOB OPERATIONS AUDIT MODAL */}
      {selectedJob && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#30000F] border border-[#FFB4C8]/25 rounded-2xl w-full max-w-2xl overflow-hidden shadow-2xl animate-scale-in">
            <div className="px-6 py-4 border-b border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-bold text-white">
                  Sync Job #{selectedJob.id.slice(0, 8)} Audit Operations
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedJob(null)}
                className="text-[#A26377] hover:text-white text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <div className="p-6 flex flex-col gap-4 text-xs">
              <div className="grid grid-cols-3 gap-2 p-3 bg-[#1E000A] rounded-xl border border-[#FFB4C8]/10 text-[11px] font-mono">
                <div>
                  <span className="text-[#A26377]">Status: </span>
                  <span className="text-white font-bold">{selectedJob.status}</span>
                </div>
                <div>
                  <span className="text-[#A26377]">Scope: </span>
                  <span className="text-white">{selectedJob.scope}</span>
                </div>
                <div>
                  <span className="text-[#A26377]">Policy: </span>
                  <span className="text-white">{selectedJob.reconciliationPolicy}</span>
                </div>
              </div>

              {isLoadingOperations ? (
                <div className="p-8 text-center text-[#A26377] flex items-center justify-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin text-[#FF2D6D]" />
                  <span>Loading operations trail...</span>
                </div>
              ) : jobOperations.length === 0 ? (
                <div className="p-6 bg-[#1E000A] rounded-xl text-center text-[#F4B5C8]">
                  No individual operations recorded for this job.
                </div>
              ) : (
                <div className="max-h-72 overflow-y-auto flex flex-col gap-2 pr-1">
                  {jobOperations.map((op, idx) => (
                    <div
                      key={idx}
                      className="p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 flex items-center justify-between"
                    >
                      <div className="flex items-center gap-3">
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                            op.operationType === 'CREATE'
                              ? 'bg-[#34D399]/20 text-[#34D399]'
                              : op.operationType === 'UPDATE'
                              ? 'bg-[#818CF8]/20 text-[#818CF8]'
                              : op.operationType === 'DELETE'
                              ? 'bg-[#F87171]/20 text-[#F87171]'
                              : 'bg-[#3F0016] text-[#A26377]'
                          }`}
                        >
                          {op.operationType}
                        </span>
                        <div className="flex flex-col">
                          <span className="font-mono font-bold text-white">{op.secretKey}</span>
                          <span className="text-[10px] text-[#A26377]">
                            Target: {op.providerResourceId || 'External Provider'}
                          </span>
                        </div>
                      </div>

                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                          op.status === 'SUCCESS' ? 'text-[#34D399]' : 'text-[#F87171]'
                        }`}
                      >
                        {op.status}
                      </span>
                    </div>
                  ))}
                </div>
              )}

              <div className="flex justify-end pt-3 border-t border-[#FFB4C8]/15">
                <button
                  type="button"
                  onClick={() => setSelectedJob(null)}
                  className="px-4 py-2 rounded-xl bg-[#3F0016] text-[#F4B5C8] hover:text-white text-xs font-semibold cursor-pointer"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

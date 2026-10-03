import React, { useState, useEffect, useCallback } from 'react';
import { rotationApi } from '../../api/rotation';
import {
  RotateCw,
  Clock,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  RotateCcw,
  Ban,
  Eye,
  ArrowRight,
  ShieldCheck,
  Search,
  Filter,
} from 'lucide-react';

const STATE_STEPS = [
  'QUEUED',
  'STARTED',
  'GENERATING',
  'VALIDATING',
  'STAGING',
  'ACTIVATING',
  'GRACE_PERIOD',
  'COMPLETED',
];

export const RotationJobsView = ({
  workspaceId,
  onViewImpact,
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [jobs, setJobs] = useState([]);
  const [selectedJob, setSelectedJob] = useState(null);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [isRollbackModalOpen, setIsRollbackModalOpen] = useState(false);
  const [rollbackReason, setRollbackReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const loadJobs = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await rotationApi.listWorkspaceJobs(workspaceId, null, 0, 50);
      const list = res?.data?.content || res?.content || res?.data || [];
      setJobs(Array.isArray(list) ? list : []);
    } catch (err) {
      setError(err.message || 'Failed to load rotation jobs');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    loadJobs();
    const interval = setInterval(loadJobs, 10000); // 10s auto-refresh
    return () => clearInterval(interval);
  }, [loadJobs]);

  const handleRetryJob = async (jobId) => {
    setActionLoading(true);
    try {
      await rotationApi.retryJob(workspaceId, jobId);
      loadJobs();
    } catch (err) {
      setError(err.message || 'Failed to retry job');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelJob = async (jobId) => {
    if (!window.confirm('Are you sure you want to cancel this rotation job?')) return;
    setActionLoading(true);
    try {
      await rotationApi.cancelJob(workspaceId, jobId);
      loadJobs();
    } catch (err) {
      setError(err.message || 'Failed to cancel job');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRollback = async (e) => {
    e.preventDefault();
    if (!selectedJob) return;
    setActionLoading(true);
    try {
      await rotationApi.rollbackJob(workspaceId, selectedJob.secretId, {
        targetVersionNumber: selectedJob.oldVersionNumber,
        reason: rollbackReason || 'Rollback triggered via UI',
      });
      setIsRollbackModalOpen(false);
      setSelectedJob(null);
      loadJobs();
    } catch (err) {
      setError(err.message || 'Failed to rollback secret');
    } finally {
      setActionLoading(false);
    }
  };

  const filteredJobs = jobs.filter((j) => {
    if (statusFilter !== 'ALL' && j.status !== statusFilter) return false;
    if (searchQuery && !j.id?.toLowerCase().includes(searchQuery.toLowerCase()) && !j.secretId?.toLowerCase().includes(searchQuery.toLowerCase())) {
      return false;
    }
    return true;
  });

  const getStepStatus = (currentStatus, stepName) => {
    const idx = STATE_STEPS.indexOf(stepName);
    const currIdx = STATE_STEPS.indexOf(currentStatus);

    if (currentStatus === 'COMPLETED') return 'completed';
    if (currentStatus === 'ROLLED_BACK' || currentStatus?.includes('FAILED') || currentStatus === 'CANCELLED') {
      return stepName === currentStatus ? 'failed' : currIdx > idx ? 'completed' : 'pending';
    }
    if (currIdx === idx) return 'current';
    if (currIdx > idx) return 'completed';
    return 'pending';
  };

  return (
    <div className="space-y-6">
      {/* Filters & Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h3 className="text-base font-headline font-bold text-white">Rotation Execution Jobs</h3>
          <p className="text-xs text-[#F4B5C8]/70">
            Real-time state machine progression across cryptographic generation, staging, dual-credential grace period, and activation.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 text-xs text-white focus:outline-none"
          >
            <option value="ALL">All States</option>
            <option value="GRACE_PERIOD">Grace Period</option>
            <option value="ACTIVATING">Activating</option>
            <option value="COMPLETED">Completed</option>
            <option value="FAILED">Failed</option>
            <option value="ROLLED_BACK">Rolled Back</option>
          </select>
          <button
            onClick={loadJobs}
            className="p-2 rounded-xl bg-[#1E000A] hover:bg-[#30000F] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white transition-all cursor-pointer"
            title="Refresh"
          >
            <RotateCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-950/60 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Jobs Table */}
      <div className="rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-[#FFB4C8]/10 bg-[#26000B]/50 text-[#F4B5C8]/60 font-mono">
                <th className="py-3 px-4 font-medium">Job ID</th>
                <th className="py-3 px-4 font-medium">Secret ID</th>
                <th className="py-3 px-4 font-medium">Strategy</th>
                <th className="py-3 px-4 font-medium">Versions</th>
                <th className="py-3 px-4 font-medium">Current Status</th>
                <th className="py-3 px-4 font-medium">Retries</th>
                <th className="py-3 px-4 font-medium">Created</th>
                <th className="py-3 px-4 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#FFB4C8]/5">
              {filteredJobs.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-[#F4B5C8]/40 font-mono">
                    No rotation jobs match the filter.
                  </td>
                </tr>
              ) : (
                filteredJobs.map((job) => (
                  <tr key={job.id} className="hover:bg-[#30000F]/40 transition-colors">
                    <td className="py-3.5 px-4 font-mono font-medium text-[#F4B5C8]">
                      {job.id ? `${job.id.substring(0, 8)}...` : '—'}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-white/80">
                      {job.secretId ? `${job.secretId.substring(0, 8)}...` : '—'}
                    </td>
                    <td className="py-3.5 px-4 text-white font-medium">{job.strategy || 'MANUAL'}</td>
                    <td className="py-3.5 px-4 font-mono text-xs">
                      <span className="text-[#F4B5C8]/60">v{job.oldVersionNumber || '?'}</span>
                      <span className="mx-1 text-[#FF2D6D]">➔</span>
                      <span className="text-[#38BDF8] font-bold">
                        {job.targetVersionNumber ? `v${job.targetVersionNumber}` : 'In Gen'}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full font-mono text-[10px] ${
                          job.status === 'COMPLETED'
                            ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-500/30'
                            : job.status === 'GRACE_PERIOD'
                            ? 'bg-sky-950/80 text-sky-300 border border-sky-500/30 animate-pulse'
                            : job.status?.includes('FAILED')
                            ? 'bg-red-950/80 text-red-300 border border-red-500/30'
                            : job.status === 'ROLLED_BACK'
                            ? 'bg-purple-950/80 text-purple-300 border border-purple-500/30'
                            : 'bg-amber-950/80 text-amber-300 border border-amber-500/30'
                        }`}
                      >
                        {job.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 font-mono text-[#F4B5C8]/70">
                      {job.retryCount || 0} / {job.maxRetries || 3}
                    </td>
                    <td className="py-3.5 px-4 text-[#F4B5C8]/60 font-mono">
                      {job.createdAt ? new Date(job.createdAt).toLocaleTimeString() : '—'}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => setSelectedJob(job)}
                          className="p-1.5 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white transition-all cursor-pointer"
                          title="View Execution State Stepper"
                        >
                          <Eye className="w-3.5 h-3.5" />
                        </button>
                        {job.status?.includes('FAILED') && (
                          <button
                            onClick={() => handleRetryJob(job.id)}
                            disabled={actionLoading}
                            className="p-1.5 rounded-lg bg-amber-950/40 hover:bg-amber-900/60 text-amber-300 transition-all cursor-pointer"
                            title="Retry Job"
                          >
                            <RotateCw className="w-3.5 h-3.5" />
                          </button>
                        )}
                        {['QUEUED', 'STARTED', 'GENERATING', 'VALIDATING', 'STAGING'].includes(job.status) && (
                          <button
                            onClick={() => handleCancelJob(job.id)}
                            disabled={actionLoading}
                            className="p-1.5 rounded-lg bg-red-950/40 hover:bg-red-900/60 text-red-400 transition-all cursor-pointer"
                            title="Cancel Job"
                          >
                            <Ban className="w-3.5 h-3.5" />
                          </button>
                        )}
                        {job.status === 'COMPLETED' && (
                          <button
                            onClick={() => {
                              setSelectedJob(job);
                              setIsRollbackModalOpen(true);
                            }}
                            className="p-1.5 rounded-lg bg-purple-950/40 hover:bg-purple-900/60 text-purple-300 transition-all cursor-pointer"
                            title="Rollback Version"
                          >
                            <RotateCcw className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Selected Job State Stepper Modal */}
      {selectedJob && !isRollbackModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="relative w-full max-w-2xl rounded-2xl bg-[#1E000A] border border-[#FF2D6D]/30 shadow-2xl p-6 text-white space-y-5">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
              <div>
                <h3 className="text-base font-headline font-bold">
                  Rotation State Machine: {selectedJob.id?.substring(0, 8)}...
                </h3>
                <div className="text-xs font-mono text-[#F4B5C8]/60">Secret: {selectedJob.secretId}</div>
              </div>
              <button
                onClick={() => setSelectedJob(null)}
                className="text-[#F4B5C8]/50 hover:text-white text-xs font-mono"
              >
                ✕
              </button>
            </div>

            {/* Stepper Visualization */}
            <div className="py-2">
              <div className="grid grid-cols-4 gap-2">
                {STATE_STEPS.map((step, idx) => {
                  const status = getStepStatus(selectedJob.status, step);
                  return (
                    <div
                      key={step}
                      className={`p-2.5 rounded-xl border text-center transition-all ${
                        status === 'completed'
                          ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                          : status === 'current'
                          ? 'bg-sky-950/60 border-sky-500 text-sky-200 animate-pulse font-bold'
                          : status === 'failed'
                          ? 'bg-red-950/60 border-red-500 text-red-300'
                          : 'bg-[#26000B] border-[#FFB4C8]/10 text-[#F4B5C8]/40'
                      }`}
                    >
                      <div className="text-[10px] font-mono text-[#F4B5C8]/50 mb-1">Step {idx + 1}</div>
                      <div className="text-[11px] font-mono">{step}</div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Job Details Grid */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 p-4 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 text-xs">
              <div>
                <span className="text-[#F4B5C8]/50 font-mono text-[10px] block">Strategy</span>
                <span className="font-semibold text-white">{selectedJob.strategy}</span>
              </div>
              <div>
                <span className="text-[#F4B5C8]/50 font-mono text-[10px] block">Rollout Mode</span>
                <span className="font-semibold text-white">{selectedJob.rolloutStrategy}</span>
              </div>
              <div>
                <span className="text-[#F4B5C8]/50 font-mono text-[10px] block">Target Version</span>
                <span className="font-mono text-[#38BDF8] font-bold">
                  {selectedJob.targetVersionNumber ? `v${selectedJob.targetVersionNumber}` : '—'}
                </span>
              </div>
              <div>
                <span className="text-[#F4B5C8]/50 font-mono text-[10px] block">Started At</span>
                <span className="font-mono text-[#F4B5C8]">
                  {selectedJob.startedAt ? new Date(selectedJob.startedAt).toLocaleString() : '—'}
                </span>
              </div>
              <div>
                <span className="text-[#F4B5C8]/50 font-mono text-[10px] block">Grace Period End</span>
                <span className="font-mono text-sky-300">
                  {selectedJob.gracePeriodEndsAt ? new Date(selectedJob.gracePeriodEndsAt).toLocaleString() : '—'}
                </span>
              </div>
              <div>
                <span className="text-[#F4B5C8]/50 font-mono text-[10px] block">Completed At</span>
                <span className="font-mono text-emerald-300">
                  {selectedJob.completedAt ? new Date(selectedJob.completedAt).toLocaleString() : '—'}
                </span>
              </div>
            </div>

            {selectedJob.errorMessage && (
              <div className="p-3 rounded-xl bg-red-950/50 border border-red-500/30 text-red-300 text-xs font-mono">
                Error: {selectedJob.errorMessage}
              </div>
            )}

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setSelectedJob(null)}
                className="px-4 py-2 rounded-xl bg-[#26000B] text-[#F4B5C8]/80 hover:text-white text-xs"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Rollback Modal */}
      {isRollbackModalOpen && selectedJob && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="relative w-full max-w-md rounded-2xl bg-[#1E000A] border border-purple-500/30 shadow-2xl p-6 text-white space-y-4">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
              <div className="flex items-center gap-2">
                <RotateCcw className="w-5 h-5 text-purple-400" />
                <h3 className="text-sm font-headline font-bold">Rollback Secret Rotation</h3>
              </div>
              <button
                onClick={() => setIsRollbackModalOpen(false)}
                className="text-[#F4B5C8]/50 hover:text-white text-xs font-mono"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-[#F4B5C8]/80 leading-relaxed">
              This will create a new rollback version (vN+1) containing the previous key material from version{' '}
              <strong className="text-white">v{selectedJob.oldVersionNumber}</strong>, keeping an immutable audit trail.
            </p>

            <form onSubmit={handleRollback} className="space-y-4 text-xs">
              <div>
                <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
                  Audit Reason for Rollback
                </label>
                <input
                  type="text"
                  placeholder="e.g. Consumer downstream incompatibility identified"
                  value={rollbackReason}
                  onChange={(e) => setRollbackReason(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                  required
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#FFB4C8]/10">
                <button
                  type="button"
                  onClick={() => setIsRollbackModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-[#26000B] text-[#F4B5C8]/80 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-2 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 text-white font-semibold flex items-center gap-1.5 shadow-lg"
                >
                  {actionLoading && <RotateCw className="w-3.5 h-3.5 animate-spin" />}
                  <span>Execute Rollback</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

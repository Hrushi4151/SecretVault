import React, { useState, useEffect, useCallback } from 'react';
import { rotationApi } from '../../api/rotation';
import {
  RotateCw,
  ShieldAlert,
  Key,
  Clock,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  Cpu,
  Layers,
  ArrowRight,
  ExternalLink,
  Flame,
} from 'lucide-react';

export const RotationDashboard = ({
  workspaceId,
  onNavigateToTab,
  onStartRotation,
  onTriggerEmergency,
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [jobs, setJobs] = useState([]);
  const [leases, setLeases] = useState([]);
  const [consumers, setConsumers] = useState([]);

  const fetchData = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const [jobsRes, leasesRes, consumersRes] = await Promise.all([
        rotationApi.listWorkspaceJobs(workspaceId, null, 0, 10).catch(() => ({ data: { content: [] } })),
        rotationApi.listLeases(workspaceId, { page: 0, size: 50 }).catch(() => ({ data: { content: [] } })),
        rotationApi.listConsumers(workspaceId, 0, 50).catch(() => ({ data: { content: [] } })),
      ]);

      setJobs(jobsRes?.data?.content || jobsRes?.content || []);
      setLeases(leasesRes?.data?.content || leasesRes?.content || []);
      setConsumers(consumersRes?.data?.content || consumersRes?.content || []);
    } catch (err) {
      setError(err.message || 'Failed to load rotation overview');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const activeJobs = jobs.filter((j) =>
    ['QUEUED', 'STARTED', 'GENERATING', 'VALIDATING', 'STAGING', 'ACTIVATING', 'GRACE_PERIOD'].includes(j.status)
  );
  const failedJobs = jobs.filter((j) =>
    ['FAILED', 'VALIDATION_FAILED', 'ACTIVATION_FAILED'].includes(j.status)
  );
  const activeLeases = leases.filter((l) => l.status === 'ACTIVE');
  const staleConsumers = consumers.filter((c) => {
    if (!c.lastHeartbeatAt) return true;
    const diffMins = (Date.now() - new Date(c.lastHeartbeatAt).getTime()) / (1000 * 60);
    return diffMins > 15;
  });

  return (
    <div className="space-y-6">
      {/* Top Banner / Hero */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-[#3F0016] via-[#2A0010] to-[#1E000A] p-6 border border-[#FF2D6D]/20 shadow-xl">
        <div className="relative z-10 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <div className="p-1.5 rounded-lg bg-[#FF2D6D]/20 border border-[#FF2D6D]/30 text-[#FF2D6D]">
                <RotateCw className="w-5 h-5 animate-spin-slow" />
              </div>
              <h2 className="text-xl font-headline font-bold text-white tracking-wide">
                Secret Rotation & Runtime Lifecycle Center
              </h2>
            </div>
            <p className="text-sm text-[#F4B5C8]/80 max-w-2xl">
              Zero-downtime cryptographic key rotation, dynamic runtime leases, dual-credential grace periods,
              and live workload consumer dependency telemetry.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => onNavigateToTab('wizard')}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#E11D48] text-white text-xs font-semibold shadow-lg shadow-[#FF2D6D]/25 hover:opacity-90 transition-all cursor-pointer"
            >
              <RotateCw className="w-4 h-4" />
              <span>Launch Rotation Wizard</span>
            </button>
            <button
              onClick={onTriggerEmergency}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#4A0010] hover:bg-[#5A0015] border border-red-500/40 text-red-300 text-xs font-semibold transition-all cursor-pointer"
            >
              <Flame className="w-4 h-4 text-red-400 animate-pulse" />
              <span>Emergency Remediate</span>
            </button>
          </div>
        </div>
      </div>

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 hover:border-[#FF2D6D]/30 transition-all shadow-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-[#F4B5C8]/70">Active Rotations</span>
            <div className="p-2 rounded-xl bg-[#38BDF8]/10 text-[#38BDF8]">
              <RotateCw className={`w-4 h-4 ${activeJobs.length > 0 ? 'animate-spin' : ''}`} />
            </div>
          </div>
          <div className="text-2xl font-headline font-bold text-white mb-1">{activeJobs.length}</div>
          <div className="text-xs text-[#F4B5C8]/60 flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5 text-[#38BDF8]" />
            <span>{activeJobs.filter((j) => j.status === 'GRACE_PERIOD').length} in grace period</span>
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 hover:border-[#FF2D6D]/30 transition-all shadow-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-[#F4B5C8]/70">Active Runtime Leases</span>
            <div className="p-2 rounded-xl bg-[#34D399]/10 text-[#34D399]">
              <Key className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-headline font-bold text-white mb-1">{activeLeases.length}</div>
          <div className="text-xs text-[#F4B5C8]/60 flex items-center gap-1.5">
            <CheckCircle2 className="w-3.5 h-3.5 text-[#34D399]" />
            <span>Dynamic TTL enforced</span>
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 hover:border-[#FF2D6D]/30 transition-all shadow-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-[#F4B5C8]/70">Registered Consumers</span>
            <div className="p-2 rounded-xl bg-[#818CF8]/10 text-[#818CF8]">
              <Cpu className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-headline font-bold text-white mb-1">{consumers.length}</div>
          <div className="text-xs text-[#F4B5C8]/60 flex items-center gap-1.5">
            <Layers className="w-3.5 h-3.5 text-[#818CF8]" />
            <span>{consumers.filter((c) => c.supportsDynamicRefresh).length} dynamic auto-refresh</span>
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 hover:border-red-500/30 transition-all shadow-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-[#F4B5C8]/70">Rotation Attention</span>
            <div className="p-2 rounded-xl bg-red-500/10 text-red-400">
              <ShieldAlert className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-headline font-bold text-white mb-1">
            {failedJobs.length + staleConsumers.length}
          </div>
          <div className="text-xs text-red-300/80 flex items-center gap-1.5">
            <AlertTriangle className="w-3.5 h-3.5 text-red-400" />
            <span>{failedJobs.length} failed jobs • {staleConsumers.length} stale workloads</span>
          </div>
        </div>
      </div>

      {/* Recent Rotation Activity & Live Status */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 p-5 shadow-lg">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <RefreshCw className="w-4 h-4 text-[#FF2D6D]" />
              <h3 className="text-sm font-headline font-semibold text-white">Recent Rotation Executions</h3>
            </div>
            <button
              onClick={() => onNavigateToTab('jobs')}
              className="text-xs text-[#FF2D6D] hover:underline flex items-center gap-1 cursor-pointer"
            >
              <span>View All History</span>
              <ArrowRight className="w-3 h-3" />
            </button>
          </div>

          {jobs.length === 0 ? (
            <div className="text-center py-10 text-[#F4B5C8]/50 text-xs font-mono">
              No rotation jobs recorded in this workspace.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#FFB4C8]/10 text-[#F4B5C8]/60 font-mono">
                    <th className="pb-2.5 font-medium">Job ID</th>
                    <th className="pb-2.5 font-medium">Strategy</th>
                    <th className="pb-2.5 font-medium">Target Ver</th>
                    <th className="pb-2.5 font-medium">Status</th>
                    <th className="pb-2.5 font-medium">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#FFB4C8]/5">
                  {jobs.slice(0, 6).map((job) => (
                    <tr key={job.id} className="hover:bg-[#30000F]/40 transition-colors">
                      <td className="py-3 font-mono text-[#F4B5C8]">
                        {job.id ? `${job.id.substring(0, 8)}...` : '—'}
                      </td>
                      <td className="py-3 text-white font-medium">{job.strategy || 'MANUAL'}</td>
                      <td className="py-3 font-mono text-[#38BDF8]">
                        {job.targetVersionNumber ? `v${job.targetVersionNumber}` : '—'}
                      </td>
                      <td className="py-3">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full font-mono text-[10px] ${
                            job.status === 'COMPLETED'
                              ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-500/30'
                              : job.status === 'GRACE_PERIOD'
                              ? 'bg-sky-950/80 text-sky-300 border border-sky-500/30 animate-pulse'
                              : job.status?.includes('FAILED')
                              ? 'bg-red-950/80 text-red-300 border border-red-500/30'
                              : 'bg-amber-950/80 text-amber-300 border border-amber-500/30'
                          }`}
                        >
                          {job.status}
                        </span>
                      </td>
                      <td className="py-3 text-[#F4B5C8]/60 font-mono">
                        {job.createdAt ? new Date(job.createdAt).toLocaleString() : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Workload Consumer Status Summary */}
        <div className="rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 p-5 shadow-lg flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Cpu className="w-4 h-4 text-[#818CF8]" />
                <h3 className="text-sm font-headline font-semibold text-white">Workload Health</h3>
              </div>
              <button
                onClick={() => onNavigateToTab('consumers')}
                className="text-xs text-[#818CF8] hover:underline flex items-center gap-1 cursor-pointer"
              >
                <span>Manage</span>
                <ArrowRight className="w-3 h-3" />
              </button>
            </div>

            <div className="space-y-3">
              <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping" />
                  <span className="text-xs text-white">Healthy Heartbeats</span>
                </div>
                <span className="text-xs font-mono font-bold text-emerald-400">
                  {consumers.length - staleConsumers.length}
                </span>
              </div>

              <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-amber-400" />
                  <span className="text-xs text-white">Stale / Missing Heartbeats</span>
                </div>
                <span className="text-xs font-mono font-bold text-amber-400">
                  {staleConsumers.length}
                </span>
              </div>

              <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-[#38BDF8]" />
                  <span className="text-xs text-white">Dynamic Hot-Reload Ready</span>
                </div>
                <span className="text-xs font-mono font-bold text-[#38BDF8]">
                  {consumers.filter((c) => c.supportsDynamicRefresh).length}
                </span>
              </div>
            </div>
          </div>

          <div className="mt-6 pt-4 border-t border-[#FFB4C8]/10">
            <div className="text-[11px] text-[#F4B5C8]/70 leading-relaxed">
              Active workloads acknowledge key version switches automatically through SDK heartbeats without requiring application restarts.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

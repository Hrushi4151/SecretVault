import React, { useState, useEffect, useCallback } from 'react';
import { rotationApi } from '../../api/rotation';
import {
  Cpu,
  RotateCw,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  Server,
  Layers,
  Sparkles,
  Bot,
  Activity,
  Plus,
} from 'lucide-react';

export const SecretConsumersView = ({ workspaceId }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [consumers, setConsumers] = useState([]);
  const [actionLoading, setActionLoading] = useState(false);
  const [isRegisterModalOpen, setIsRegisterModalOpen] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [regForm, setRegForm] = useState({
    name: '',
    consumerType: 'APPLICATION',
    supportsDynamicRefresh: true,
    requiresRestart: false,
    hostname: 'k8s-pod-worker-01',
    sdkVersion: '1.2.0',
    runtimeFramework: 'Spring Boot 3.3 / Java 21',
  });

  const loadConsumers = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await rotationApi.listConsumers(workspaceId, 0, 100);
      const list = res?.data?.content || res?.content || res?.data || [];
      setConsumers(Array.isArray(list) ? list : []);
    } catch (err) {
      setError(err.message || 'Failed to load secret consumers');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    loadConsumers();
    const interval = setInterval(loadConsumers, 15000);
    return () => clearInterval(interval);
  }, [loadConsumers]);

  const handleDisableConsumer = async (consumerId) => {
    if (!window.confirm('Are you sure you want to disable this consumer? It will be blocked from renewing leases.')) return;
    setActionLoading(true);
    try {
      await rotationApi.disableConsumer(workspaceId, consumerId);
      loadConsumers();
    } catch (err) {
      setError(err.message || 'Failed to disable consumer');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRegisterConsumer = async (e) => {
    e.preventDefault();
    if (!workspaceId || !regForm.name) return;
    setRegistering(true);
    setError(null);
    try {
      await rotationApi.registerConsumer(workspaceId, regForm);
      setIsRegisterModalOpen(false);
      setRegForm({
        name: '',
        consumerType: 'APPLICATION',
        supportsDynamicRefresh: true,
        requiresRestart: false,
        hostname: 'k8s-pod-worker-01',
        sdkVersion: '1.2.0',
        runtimeFramework: 'Spring Boot 3.3 / Java 21',
      });
      loadConsumers();
    } catch (err) {
      setError(err.message || 'Failed to register consumer');
    } finally {
      setRegistering(false);
    }
  };

  const getHealthBadge = (lastSeenAt, status) => {
    if (status === 'DISABLED') {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-neutral-900 text-neutral-400 border border-neutral-700 text-[10px] font-mono">
          Disabled
        </span>
      );
    }
    if (!lastSeenAt) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-neutral-900 text-neutral-400 border border-neutral-700 text-[10px] font-mono">
          Offline
        </span>
      );
    }
    const diffMins = (Date.now() - new Date(lastSeenAt).getTime()) / (1000 * 60);
    if (diffMins <= 5) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-emerald-950/80 text-emerald-300 border border-emerald-500/30 text-[10px] font-mono">
          <Activity className="w-3 h-3 text-emerald-400 animate-pulse" />
          <span>Healthy Heartbeat</span>
        </span>
      );
    }
    if (diffMins <= 20) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-amber-950/80 text-amber-300 border border-amber-500/30 text-[10px] font-mono">
          <AlertTriangle className="w-3 h-3 text-amber-400" />
          <span>Delayed ({Math.round(diffMins)}m ago)</span>
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-red-950/80 text-red-300 border border-red-500/30 text-[10px] font-mono">
        <AlertTriangle className="w-3 h-3 text-red-400" />
        <span>Stale / Disconnected</span>
      </span>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h3 className="text-base font-headline font-bold text-white">Workload Consumer Registry</h3>
          <p className="text-xs text-[#F4B5C8]/70">
            Registered application workloads, microservices, and SDK instances with live heartbeat telemetry and version acknowledgments.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsRegisterModalOpen(true)}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-gradient-to-r from-[#818CF8] to-[#6366F1] text-white text-xs font-semibold shadow-md cursor-pointer hover:opacity-95"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Register Workload</span>
          </button>
          <button
            onClick={loadConsumers}
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

      {/* Consumers Grid / Table */}
      <div className="rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-[#FFB4C8]/10 bg-[#26000B]/50 text-[#F4B5C8]/60 font-mono">
                <th className="py-3 px-4 font-medium">Workload / Consumer</th>
                <th className="py-3 px-4 font-medium">Type</th>
                <th className="py-3 px-4 font-medium">Heartbeat Status</th>
                <th className="py-3 px-4 font-medium">Dynamic Hot-Reload</th>
                <th className="py-3 px-4 font-medium">SDK / Runtime</th>
                <th className="py-3 px-4 font-medium">Last Seen</th>
                <th className="py-3 px-4 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#FFB4C8]/5">
              {consumers.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-[#F4B5C8]/40 font-mono">
                    No workload consumers registered yet. SDK instances register on bootstrap.
                  </td>
                </tr>
              ) : (
                consumers.map((c) => (
                  <tr key={c.id} className="hover:bg-[#30000F]/40 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-2.5">
                        <div className="p-2 rounded-xl bg-[#818CF8]/10 text-[#818CF8]">
                          <Cpu className="w-4 h-4" />
                        </div>
                        <div>
                          <div className="font-semibold text-white">{c.name}</div>
                          <div className="text-[10px] font-mono text-[#F4B5C8]/50">
                            {c.hostname || c.id?.substring(0, 8)}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-white/90">
                      {c.type || 'APPLICATION'}
                    </td>
                    <td className="py-3.5 px-4">
                      {getHealthBadge(c.lastHeartbeatAt, c.status)}
                    </td>
                    <td className="py-3.5 px-4 font-mono">
                      {c.supportsDynamicRefresh ? (
                        <span className="text-emerald-400 flex items-center gap-1">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          <span>Enabled</span>
                        </span>
                      ) : (
                        <span className="text-amber-400">Restart Required</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-[#F4B5C8]/80 text-[11px]">
                      {c.sdkVersion || 'SecretVault-SDK/1.0'}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-[#F4B5C8]/60 text-[11px]">
                      {c.lastHeartbeatAt ? new Date(c.lastHeartbeatAt).toLocaleTimeString() : 'Never'}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      {c.status !== 'DISABLED' && (
                        <button
                          onClick={() => handleDisableConsumer(c.id)}
                          disabled={actionLoading}
                          className="p-1.5 rounded-lg bg-red-950/40 hover:bg-red-900/60 text-red-400 transition-all cursor-pointer"
                          title="Disable Workload"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Register Workload Modal */}
      {isRegisterModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="relative w-full max-w-md rounded-2xl bg-[#1E000A] border border-[#818CF8]/30 shadow-2xl p-6 text-white space-y-4">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
              <div className="flex items-center gap-2">
                <div className="p-2 rounded-xl bg-[#818CF8]/20 text-[#818CF8]">
                  <Cpu className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-headline font-bold">Register Workload Consumer</h3>
              </div>
              <button
                onClick={() => setIsRegisterModalOpen(false)}
                className="text-[#F4B5C8]/50 hover:text-white text-xs font-mono cursor-pointer"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleRegisterConsumer} className="space-y-4 text-xs">
              <div>
                <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
                  Workload Name
                </label>
                <input
                  type="text"
                  value={regForm.name}
                  onChange={(e) => setRegForm({ ...regForm, name: e.target.value })}
                  placeholder="e.g. auth-service-cluster-node-1"
                  className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15 text-white font-mono focus:outline-none"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
                    Consumer Type
                  </label>
                  <select
                    value={regForm.consumerType}
                    onChange={(e) => setRegForm({ ...regForm, consumerType: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15 text-white font-mono focus:outline-none"
                  >
                    <option value="APPLICATION">APPLICATION</option>
                    <option value="SERVICE">SERVICE</option>
                    <option value="WORKER">WORKER</option>
                    <option value="CONTAINER">CONTAINER</option>
                    <option value="CI_PIPELINE">CI_PIPELINE</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
                    Hostname / Node
                  </label>
                  <input
                    type="text"
                    value={regForm.hostname}
                    onChange={(e) => setRegForm({ ...regForm, hostname: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15 text-white font-mono focus:outline-none"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2 p-2.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10">
                <input
                  type="checkbox"
                  id="dynamicRefresh"
                  checked={regForm.supportsDynamicRefresh}
                  onChange={(e) => setRegForm({ ...regForm, supportsDynamicRefresh: e.target.checked, requiresRestart: !e.target.checked })}
                  className="rounded border-[#FFB4C8]/30 text-[#818CF8]"
                />
                <label htmlFor="dynamicRefresh" className="text-[11px] text-[#F4B5C8]/80 cursor-pointer">
                  Supports Dynamic In-Memory Hot-Reload (Zero Downtime)
                </label>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#FFB4C8]/10">
                <button
                  type="button"
                  onClick={() => setIsRegisterModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#F4B5C8] text-xs font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={registering}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-gradient-to-r from-[#818CF8] to-[#6366F1] text-white text-xs font-semibold shadow-md cursor-pointer hover:opacity-95"
                >
                  {registering ? <RotateCw className="w-3.5 h-3.5 animate-spin" /> : <Cpu className="w-3.5 h-3.5" />}
                  <span>Register Workload</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

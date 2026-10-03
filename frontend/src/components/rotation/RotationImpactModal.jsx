import React, { useState, useEffect } from 'react';
import { rotationApi } from '../../api/rotation';
import {
  Layers,
  RotateCw,
  Cpu,
  Key,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  Server,
  Bot,
} from 'lucide-react';

export const RotationImpactModal = ({
  workspaceId,
  secretId,
  secretName,
  isOpen,
  onClose,
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [impact, setImpact] = useState(null);

  useEffect(() => {
    if (!isOpen || !workspaceId || !secretId) return;
    setLoading(true);
    setError(null);
    rotationApi
      .getImpact(workspaceId, secretId)
      .then((res) => {
        setImpact(res?.data || null);
      })
      .catch((err) => {
        setError(err.message || 'Failed to analyze rotation impact');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [isOpen, workspaceId, secretId]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
      <div className="relative w-full max-w-2xl rounded-2xl bg-[#1E000A] border border-[#FF2D6D]/30 shadow-2xl p-6 text-white space-y-5 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[#818CF8]/10 text-[#818CF8]">
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-headline font-bold text-white">
                Runtime Dependency Graph & Impact Telemetry
              </h3>
              <p className="text-xs font-mono text-[#F4B5C8]/60">
                Secret: {secretName || secretId}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-[#F4B5C8]/50 hover:text-white text-xs font-mono cursor-pointer"
          >
            ✕
          </button>
        </div>

        {loading ? (
          <div className="flex flex-col items-center justify-center py-12 gap-3 text-xs font-mono text-[#F4B5C8]/70">
            <RotateCw className="w-6 h-6 animate-spin text-[#FF2D6D]" />
            <span>Analyzing secret consumer dependency graph...</span>
          </div>
        ) : error ? (
          <div className="p-4 rounded-xl bg-red-950/60 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
            <ShieldAlert className="w-4 h-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        ) : impact ? (
          <div className="space-y-4 text-xs">
            {/* Summary Metrics */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <div className="p-3.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 text-center">
                <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Registered Workloads</span>
                <span className="text-xl font-bold text-white">{impact.totalConsumers || 0}</span>
              </div>
              <div className="p-3.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 text-center">
                <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Auto-Refresh Ready</span>
                <span className="text-xl font-bold text-emerald-400">
                  {impact.consumersSupportingAutoRefresh || 0}
                </span>
              </div>
              <div className="p-3.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 text-center">
                <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Requires Restart</span>
                <span className="text-xl font-bold text-amber-400">
                  {impact.consumersRequiringRestart || 0}
                </span>
              </div>
              <div className="p-3.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 text-center">
                <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Active Leases</span>
                <span className="text-xl font-bold text-[#38BDF8]">{impact.activeLeaseCount || 0}</span>
              </div>
            </div>

            {/* Consumer List */}
            <div className="space-y-2">
              <h4 className="text-xs font-headline font-semibold text-white">Dependent Consumers</h4>
              {impact.consumers && impact.consumers.length > 0 ? (
                <div className="max-h-48 overflow-y-auto space-y-1.5 pr-1">
                  {impact.consumers.map((c) => (
                    <div
                      key={c.consumerId}
                      className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 flex items-center justify-between font-mono text-xs"
                    >
                      <div className="flex items-center gap-2.5">
                        <Cpu className="w-4 h-4 text-[#818CF8]" />
                        <div>
                          <span className="font-semibold text-white">{c.consumerName}</span>
                          <span className="text-[10px] text-[#F4B5C8]/50 ml-2">({c.consumerType})</span>
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span
                          className={`text-[10px] px-2 py-0.5 rounded-full ${
                            c.supportsDynamicRefresh
                              ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-500/30'
                              : 'bg-amber-950/80 text-amber-300 border border-amber-500/30'
                          }`}
                        >
                          {c.supportsDynamicRefresh ? 'Dynamic Hot-Reload' : 'Restart Required'}
                        </span>
                        <span className="text-[#38BDF8] font-bold">
                          {c.currentVersion ? `v${c.currentVersion}` : '—'}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-[#26000B] text-center text-xs font-mono text-[#F4B5C8]/50">
                  No active application consumers attached to this secret.
                </div>
              )}
            </div>

            {/* Affected Machines */}
            {impact.affectedMachineIdentities && impact.affectedMachineIdentities.length > 0 && (
              <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 flex items-center gap-2 text-xs font-mono">
                <Bot className="w-4 h-4 text-[#F43F5E]" />
                <span className="text-[#F4B5C8]/70">Affected Machine Identities:</span>
                <span className="font-bold text-white">{impact.affectedMachineIdentities.length} tokens</span>
              </div>
            )}
          </div>
        ) : null}

        <div className="flex items-center justify-end pt-3 border-t border-[#FFB4C8]/10">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#F4B5C8] text-xs font-semibold cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

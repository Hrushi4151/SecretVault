import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { automationApi } from '../../api/automation';
import {
  Zap,
  RotateCw,
  Plus,
  Play,
  CheckCircle2,
  XCircle,
  Clock,
  ShieldCheck,
  AlertTriangle,
  History,
  FileCheck2,
  Sliders,
  Check,
  X,
  Code2
} from 'lucide-react';

export const AutomationCenterView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [activeTab, setActiveTab] = useState('policies'); // 'policies' | 'approvals' | 'history' | 'simulator'
  const [policies, setPolicies] = useState([]);
  const [approvals, setApprovals] = useState([]);
  const [executions, setExecutions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Policy creation modal
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [newPolicyName, setNewPolicyName] = useState('');
  const [newPolicyDesc, setNewPolicyDesc] = useState('');
  const [newPolicyTriggers, setNewPolicyTriggers] = useState('SECRET_COMPROMISED,LEASE_EXPIRED');
  const [newPolicyApprovalRequired, setNewPolicyApprovalRequired] = useState(false);

  // Simulator
  const [simEventJson, setSimEventJson] = useState(JSON.stringify({
    eventType: "SECRET_COMPROMISED",
    severity: "CRITICAL",
    workspaceId: workspaceId || "00000000-0000-0000-0000-000000000000",
    metadata: { rotationAgeDays: 120, environment: "PRODUCTION" }
  }, null, 2));
  const [simResults, setSimResults] = useState(null);
  const [simLoading, setSimLoading] = useState(false);

  const fetchActiveTab = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      if (activeTab === 'policies') {
        const data = await automationApi.listPolicies(workspaceId);
        setPolicies(data?.content || data?.items || []);
      } else if (activeTab === 'approvals') {
        const data = await automationApi.listApprovals(workspaceId);
        setApprovals(data?.content || data?.items || []);
      } else if (activeTab === 'history') {
        const data = await automationApi.listExecutions(workspaceId);
        setExecutions(data?.content || data?.items || []);
      }
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load automation data');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, activeTab]);

  useEffect(() => {
    fetchActiveTab();
  }, [fetchActiveTab]);

  const handleCreatePolicy = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;
    try {
      await automationApi.createPolicy(workspaceId, {
        name: newPolicyName,
        description: newPolicyDesc,
        enabled: true,
        priority: 100,
        scopeType: 'WORKSPACE',
        triggerEventTypes: newPolicyTriggers,
        conditionsJson: '[]',
        actionsJson: '[{"type":"NOTIFY","parameters":{"severity":"HIGH","title":"Automation Triggered"}}]',
        dryRun: false,
        approvalRequired: newPolicyApprovalRequired
      });
      setIsCreateModalOpen(false);
      setNewPolicyName('');
      setNewPolicyDesc('');
      fetchActiveTab();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to create automation policy');
    }
  };

  const handleDecideApproval = async (approvalId, approve) => {
    if (!workspaceId) return;
    try {
      await automationApi.decideApproval(workspaceId, approvalId, approve);
      fetchActiveTab();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to decide approval');
    }
  };

  const handleRunSimulation = async () => {
    if (!workspaceId) return;
    setSimLoading(true);
    try {
      const parsed = JSON.parse(simEventJson);
      const res = await automationApi.simulate(workspaceId, parsed);
      setSimResults(res);
    } catch (err) {
      alert('Invalid JSON mock event or simulation failed: ' + err.message);
    } finally {
      setSimLoading(false);
    }
  };

  return (
    <div className="flex-1 overflow-y-auto px-4 lg:px-8 py-6 max-w-7xl mx-auto w-full space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[#FFB4C8]/10 pb-5">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-[#F59E0B] to-[#EF4444] flex items-center justify-center text-white shadow-lg shadow-[#F59E0B]/20">
            <Zap className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-headline font-bold text-white tracking-wide">
                Security Automation & Policy Engine
              </h1>
              <span className="px-2 py-0.5 rounded-md bg-[#F59E0B]/20 border border-[#F59E0B]/40 text-[#F59E0B] text-[10px] font-mono font-bold">
                Phase 13
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] mt-0.5">
              Deterministic AST condition engine, anti-loop tripwires, four-eyes approval workflows, and dry-run simulation.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[#F59E0B] hover:bg-[#F59E0B]/80 text-white text-xs font-semibold shadow-lg shadow-[#F59E0B]/20 transition"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>New Automation Policy</span>
          </button>
          <button
            onClick={fetchActiveTab}
            disabled={loading}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-surface-container-high hover:bg-surface-container-highest text-white border border-outline-variant text-xs font-semibold transition disabled:opacity-50"
          >
            <RotateCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-outline-variant/60 pb-1">
        <button
          onClick={() => setActiveTab('policies')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition ${
            activeTab === 'policies'
              ? 'bg-[#F59E0B]/15 text-[#F59E0B] border border-[#F59E0B]/30'
              : 'text-[#94A3B8] hover:text-white'
          }`}
        >
          <Sliders className="w-4 h-4" />
          <span>Active Policies</span>
        </button>

        <button
          onClick={() => setActiveTab('approvals')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition ${
            activeTab === 'approvals'
              ? 'bg-[#F59E0B]/15 text-[#F59E0B] border border-[#F59E0B]/30'
              : 'text-[#94A3B8] hover:text-white'
          }`}
        >
          <ShieldCheck className="w-4 h-4" />
          <span>Approvals Queue</span>
        </button>

        <button
          onClick={() => setActiveTab('history')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition ${
            activeTab === 'history'
              ? 'bg-[#F59E0B]/15 text-[#F59E0B] border border-[#F59E0B]/30'
              : 'text-[#94A3B8] hover:text-white'
          }`}
        >
          <History className="w-4 h-4" />
          <span>Execution History</span>
        </button>

        <button
          onClick={() => setActiveTab('simulator')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition ${
            activeTab === 'simulator'
              ? 'bg-[#F59E0B]/15 text-[#F59E0B] border border-[#F59E0B]/30'
              : 'text-[#94A3B8] hover:text-white'
          }`}
        >
          <Play className="w-4 h-4" />
          <span>Policy Simulator</span>
        </button>
      </div>

      {/* Policies List */}
      {activeTab === 'policies' && (
        <div className="space-y-3">
          {loading && policies.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#94A3B8]">
              <RotateCw className="w-5 h-5 animate-spin mx-auto mb-2 text-[#F59E0B]" />
              Loading policies...
            </div>
          ) : policies.length === 0 ? (
            <div className="bg-surface-container-low/80 p-8 text-center rounded-2xl border border-outline-variant text-xs text-[#94A3B8]">
              No automation policies configured. Click "New Automation Policy" to create one.
            </div>
          ) : (
            policies.map((p) => (
              <div
                key={p.id}
                className="bg-surface-container-low/80 p-5 rounded-2xl border border-outline-variant hover:border-[#F59E0B]/40 transition space-y-3 shadow-lg"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-400" />
                    <h3 className="font-headline font-bold text-white text-sm">{p.name}</h3>
                    {p.approvalRequired && (
                      <span className="px-2 py-0.5 rounded-md bg-amber-500/20 text-amber-400 text-[10px] font-mono border border-amber-500/30">
                        Requires Approval
                      </span>
                    )}
                    {p.dryRun && (
                      <span className="px-2 py-0.5 rounded-md bg-blue-500/20 text-blue-400 text-[10px] font-mono border border-blue-500/30">
                        Dry Run
                      </span>
                    )}
                  </div>
                  <span className="text-xs font-mono text-[#94A3B8]">Priority: {p.priority}</span>
                </div>
                <p className="text-xs text-[#94A3B8]">{p.description || 'No description provided.'}</p>
                <div className="flex flex-wrap gap-2 text-[11px] font-mono text-[#64748B]">
                  <span>Triggers: <strong className="text-white">{p.triggerEventTypes}</strong></span>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Approvals Queue */}
      {activeTab === 'approvals' && (
        <div className="space-y-3">
          {loading && approvals.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#94A3B8]">
              <RotateCw className="w-5 h-5 animate-spin mx-auto mb-2 text-[#F59E0B]" />
              Loading approvals...
            </div>
          ) : approvals.length === 0 ? (
            <div className="bg-surface-container-low/80 p-8 text-center rounded-2xl border border-outline-variant text-xs text-[#94A3B8]">
              No pending automation approvals in queue. All automated actions are clear.
            </div>
          ) : (
            approvals.map((appr) => (
              <div
                key={appr.id}
                className="bg-surface-container-low/80 p-5 rounded-2xl border border-outline-variant flex items-center justify-between gap-4 shadow-lg"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold text-white text-xs">{appr.actionType}</span>
                    <span className="px-2 py-0.5 rounded-md bg-amber-500/20 text-amber-400 text-[10px] font-mono">
                      {appr.status}
                    </span>
                  </div>
                  <p className="text-xs text-[#94A3B8] mt-1 font-mono">
                    Requested by: {appr.requestedBy}
                  </p>
                </div>

                {appr.status === 'PENDING' && (
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleDecideApproval(appr.id, true)}
                      className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/40 text-xs font-semibold transition"
                    >
                      <Check className="w-3.5 h-3.5" />
                      <span>Approve</span>
                    </button>
                    <button
                      onClick={() => handleDecideApproval(appr.id, false)}
                      className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 border border-rose-500/40 text-xs font-semibold transition"
                    >
                      <X className="w-3.5 h-3.5" />
                      <span>Reject</span>
                    </button>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* History */}
      {activeTab === 'history' && (
        <div className="bg-surface-container-low/80 rounded-2xl border border-outline-variant overflow-hidden shadow-xl">
          {executions.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#94A3B8]">
              No policy execution logs recorded yet.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-surface-container-high/60 text-[#94A3B8] font-mono text-[11px] border-b border-outline-variant">
                  <tr>
                    <th className="py-3 px-4">Trigger Event</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Duration</th>
                    <th className="py-3 px-4">Executed At</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/40">
                  {executions.map((ex) => (
                    <tr key={ex.id} className="hover:bg-surface-container-high/40 transition">
                      <td className="py-3 px-4 font-mono text-white">{ex.triggerEventType}</td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 rounded-md bg-emerald-500/20 text-emerald-400 font-mono text-[10px]">
                          {ex.status}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-mono text-[#94A3B8]">{ex.durationMs || 0} ms</td>
                      <td className="py-3 px-4 font-mono text-[#94A3B8]">{new Date(ex.createdAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Simulator */}
      {activeTab === 'simulator' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-surface-container-low/80 p-5 rounded-2xl border border-outline-variant space-y-3 shadow-xl">
            <div className="flex items-center justify-between">
              <span className="font-headline font-bold text-white text-xs flex items-center gap-2">
                <Code2 className="w-4 h-4 text-[#F59E0B]" />
                Mock Event Payload (JSON)
              </span>
              <button
                onClick={handleRunSimulation}
                disabled={simLoading}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#F59E0B] hover:bg-[#F59E0B]/80 text-white text-xs font-semibold shadow-lg shadow-[#F59E0B]/20 transition disabled:opacity-50"
              >
                <Play className={`w-3.5 h-3.5 ${simLoading ? 'animate-spin' : ''}`} />
                <span>Simulate Policies</span>
              </button>
            </div>
            <textarea
              rows={12}
              value={simEventJson}
              onChange={(e) => setSimEventJson(e.target.value)}
              className="w-full bg-surface-container-lowest font-mono text-xs text-emerald-400 p-3 rounded-xl border border-outline-variant focus:outline-none focus:border-[#F59E0B]"
            />
          </div>

          <div className="bg-surface-container-low/80 p-5 rounded-2xl border border-outline-variant space-y-3 shadow-xl">
            <span className="font-headline font-bold text-white text-xs block">
              Simulation Results & Blast Radius
            </span>
            {simResults ? (
              <pre className="bg-surface-container-lowest font-mono text-[11px] text-amber-300 p-3 rounded-xl border border-outline-variant overflow-x-auto max-h-[300px]">
                {JSON.stringify(simResults, null, 2)}
              </pre>
            ) : (
              <div className="p-8 text-center text-xs text-[#94A3B8]">
                Click "Simulate Policies" to evaluate policies against the mock event payload with zero side-effects.
              </div>
            )}
          </div>
        </div>
      )}

      {/* Create Modal */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <form
            onSubmit={handleCreatePolicy}
            className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <span className="font-headline font-bold text-white text-base">New Automation Policy</span>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="text-[#94A3B8] hover:text-white transition"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Policy Name:</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Quarantine Compromised Credentials"
                  value={newPolicyName}
                  onChange={(e) => setNewPolicyName(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#F59E0B]"
                />
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Description:</label>
                <textarea
                  rows={2}
                  placeholder="Briefly describe what this policy automates..."
                  value={newPolicyDesc}
                  onChange={(e) => setNewPolicyDesc(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#F59E0B]"
                />
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Trigger Event Types (Comma-separated):</label>
                <input
                  type="text"
                  required
                  value={newPolicyTriggers}
                  onChange={(e) => setNewPolicyTriggers(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#F59E0B]"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="requireApproval"
                  checked={newPolicyApprovalRequired}
                  onChange={(e) => setNewPolicyApprovalRequired(e.target.checked)}
                  className="rounded border-outline-variant text-[#F59E0B] focus:ring-0"
                />
                <label htmlFor="requireApproval" className="text-white font-mono text-xs cursor-pointer">
                  Require four-eyes approval before executing sensitive actions
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3">
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="px-4 py-2 rounded-xl bg-surface-container text-white text-xs font-semibold hover:bg-surface-container-highest transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 rounded-xl bg-[#F59E0B] hover:bg-[#F59E0B]/80 text-white text-xs font-semibold shadow-lg shadow-[#F59E0B]/20 transition"
              >
                Save Policy
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default AutomationCenterView;

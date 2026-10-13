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
  Code2,
  Trash2,
  Power,
  ChevronDown,
  ChevronUp,
  ShieldAlert,
  Layers,
  Sparkles,
  HelpCircle
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
  const [expandedPolicyId, setExpandedPolicyId] = useState(null);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  // Policy creation modal
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [newPolicyName, setNewPolicyName] = useState('');
  const [newPolicyDesc, setNewPolicyDesc] = useState('');
  const [newPolicyTriggers, setNewPolicyTriggers] = useState('SECRET_COMPROMISED,LEASE_EXPIRED');
  const [newPolicyPriority, setNewPolicyPriority] = useState(100);
  const [newPolicyConditions, setNewPolicyConditions] = useState('[]');
  const [newPolicyActions, setNewPolicyActions] = useState('[{"type":"NOTIFY","parameters":{"severity":"HIGH","title":"Automation Triggered"}}]');
  const [newPolicyDryRun, setNewPolicyDryRun] = useState(false);
  const [newPolicyApprovalRequired, setNewPolicyApprovalRequired] = useState(false);

  // Simulator
  const [simEventJson, setSimEventJson] = useState(() => JSON.stringify({
    eventType: "SECRET_COMPROMISED",
    severity: "CRITICAL",
    workspaceId: workspaceId || "00000000-0000-0000-0000-000000000000",
    metadata: { rotationAgeDays: 120, environment: "PRODUCTION" }
  }, null, 2));
  const [simResults, setSimResults] = useState(null);
  const [simLoading, setSimLoading] = useState(false);

  useEffect(() => {
    if (workspaceId) {
      setSimEventJson((prev) => {
        try {
          const parsed = JSON.parse(prev);
          if (parsed.workspaceId !== workspaceId) {
            parsed.workspaceId = workspaceId;
            return JSON.stringify(parsed, null, 2);
          }
        } catch (_) {}
        return prev;
      });
    }
  }, [workspaceId]);

  const fetchActiveTab = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      if (activeTab === 'policies') {
        const data = await automationApi.listPolicies(workspaceId);
        setPolicies(data?.content || data?.items || (Array.isArray(data) ? data : []));
      } else if (activeTab === 'approvals') {
        const data = await automationApi.listApprovals(workspaceId);
        setApprovals(data?.content || data?.items || (Array.isArray(data) ? data : []));
      } else if (activeTab === 'history') {
        const data = await automationApi.listExecutions(workspaceId);
        setExecutions(data?.content || data?.items || (Array.isArray(data) ? data : []));
      }
    } catch (err) {
      setError(err?.payload?.message || err?.message || 'Failed to load automation data');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, activeTab]);

  useEffect(() => {
    fetchActiveTab();
  }, [fetchActiveTab]);

  const applyTemplate = (type) => {
    if (type === 'critical_compromise') {
      setNewPolicyName('Quarantine Compromised Credentials');
      setNewPolicyDesc('Immediately flag critical security incident and send high-priority alert upon secret compromise.');
      setNewPolicyTriggers('SECRET_COMPROMISED');
      setNewPolicyConditions(JSON.stringify([
        { field: 'severity', operator: 'EQUALS', value: 'CRITICAL' }
      ], null, 2));
      setNewPolicyActions(JSON.stringify([
        { type: 'CREATE_SECURITY_INCIDENT', parameters: { severity: 'CRITICAL', title: 'Automated Quarantine Incident' } },
        { type: 'NOTIFY', parameters: { severity: 'HIGH', title: 'Critical Compromise Quarantined' } }
      ], null, 2));
      setNewPolicyPriority(10);
      setNewPolicyApprovalRequired(false);
      setNewPolicyDryRun(false);
    } else if (type === 'auto_rotation') {
      setNewPolicyName('Auto-Rotate on Lease Expiry');
      setNewPolicyDesc('Trigger dual-version zero-downtime rotation when a lease expires, with four-eyes governance.');
      setNewPolicyTriggers('LEASE_EXPIRED');
      setNewPolicyConditions('[]');
      setNewPolicyActions(JSON.stringify([
        { type: 'TRIGGER_ROTATION', parameters: { reason: 'Lease expired automatic renewal' } }
      ], null, 2));
      setNewPolicyPriority(50);
      setNewPolicyApprovalRequired(true);
      setNewPolicyDryRun(false);
    } else if (type === 'jit_access_review') {
      setNewPolicyName('JIT Access Review Requirement');
      setNewPolicyDesc('Route Just-In-Time credential requests for production environments to administrative review.');
      setNewPolicyTriggers('JIT_REQUEST_CREATED');
      setNewPolicyConditions(JSON.stringify([
        { field: 'environment', operator: 'EQUALS', value: 'PRODUCTION' }
      ], null, 2));
      setNewPolicyActions(JSON.stringify([
        { type: 'REQUEST_ACCESS_REVIEW', parameters: { requiredRole: 'ADMIN' } }
      ], null, 2));
      setNewPolicyPriority(30);
      setNewPolicyApprovalRequired(true);
      setNewPolicyDryRun(false);
    }
  };

  const handleCreatePolicy = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;

    // Validate JSON formatting
    try {
      if (newPolicyConditions.trim()) JSON.parse(newPolicyConditions);
    } catch (jsonErr) {
      setCreateError('Invalid AST Conditions JSON: ' + jsonErr.message);
      return;
    }
    try {
      if (newPolicyActions.trim()) JSON.parse(newPolicyActions);
    } catch (jsonErr) {
      setCreateError('Invalid Actions JSON: ' + jsonErr.message);
      return;
    }

    setCreateSubmitting(true);
    setCreateError(null);
    try {
      await automationApi.createPolicy(workspaceId, {
        name: newPolicyName.trim(),
        description: newPolicyDesc.trim(),
        enabled: true,
        priority: Number(newPolicyPriority) || 100,
        scopeType: 'WORKSPACE',
        triggerEventTypes: newPolicyTriggers.trim(),
        conditionsJson: newPolicyConditions.trim() || '[]',
        actionsJson: newPolicyActions.trim() || '[]',
        dryRun: newPolicyDryRun,
        approvalRequired: newPolicyApprovalRequired
      });
      setIsCreateModalOpen(false);
      setNewPolicyName('');
      setNewPolicyDesc('');
      setNewPolicyPriority(100);
      setNewPolicyConditions('[]');
      setNewPolicyActions('[{"type":"NOTIFY","parameters":{"severity":"HIGH","title":"Automation Triggered"}}]');
      setNewPolicyDryRun(false);
      setNewPolicyApprovalRequired(false);
      fetchActiveTab();
    } catch (err) {
      setCreateError(err?.payload?.message || err?.message || 'Failed to create automation policy');
    } finally {
      setCreateSubmitting(false);
    }
  };

  const handleTogglePolicy = async (policy) => {
    if (!workspaceId) return;
    setActionLoadingId(policy.id);
    try {
      if (policy.enabled) {
        await automationApi.disablePolicy(workspaceId, policy.id);
      } else {
        await automationApi.enablePolicy(workspaceId, policy.id);
      }
      await fetchActiveTab();
    } catch (err) {
      alert(err?.payload?.message || err?.message || 'Failed to toggle policy');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleDeletePolicy = async (policy) => {
    if (!workspaceId) return;
    if (!window.confirm(`Are you sure you want to delete policy "${policy.name}"? This action cannot be undone.`)) {
      return;
    }
    setActionLoadingId(policy.id);
    try {
      await automationApi.deletePolicy(workspaceId, policy.id);
      await fetchActiveTab();
    } catch (err) {
      alert(err?.payload?.message || err?.message || 'Failed to delete policy');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleDecideApproval = async (approvalId, approve) => {
    if (!workspaceId) return;
    try {
      await automationApi.decideApproval(workspaceId, approvalId, approve);
      fetchActiveTab();
    } catch (err) {
      alert(err?.payload?.message || err?.message || 'Failed to decide approval');
    }
  };

  const handleCancelApproval = async (approvalId) => {
    if (!workspaceId) return;
    try {
      await automationApi.cancelApproval(workspaceId, approvalId);
      fetchActiveTab();
    } catch (err) {
      alert(err?.payload?.message || err?.message || 'Failed to cancel approval');
    }
  };

  const handleRunSimulation = async () => {
    if (!workspaceId) return;
    setSimLoading(true);
    try {
      const parsed = JSON.parse(simEventJson);
      if (!parsed.workspaceId || parsed.workspaceId === "00000000-0000-0000-0000-000000000000") {
        parsed.workspaceId = workspaceId;
      }
      const res = await automationApi.simulate(workspaceId, parsed);
      setSimResults(res);
    } catch (err) {
      alert('Simulation failed: ' + (err?.payload?.message || err?.message || err.message));
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
            onClick={() => {
              setCreateError(null);
              setIsCreateModalOpen(true);
            }}
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

      {/* Safety & Architecture Pillars Banner */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="bg-surface-container-low/70 border border-outline-variant/60 rounded-xl p-3 flex items-start gap-2.5">
          <div className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 mt-0.5">
            <Code2 className="w-4 h-4" />
          </div>
          <div>
            <div className="text-[11px] font-bold text-white uppercase tracking-wider font-mono">AST Engine</div>
            <div className="text-[11px] text-[#94A3B8] mt-0.5">Deterministic syntax tree; no arbitrary code execution.</div>
          </div>
        </div>

        <div className="bg-surface-container-low/70 border border-outline-variant/60 rounded-xl p-3 flex items-start gap-2.5">
          <div className="p-1.5 rounded-lg bg-rose-500/10 text-rose-400 mt-0.5">
            <ShieldAlert className="w-4 h-4" />
          </div>
          <div>
            <div className="text-[11px] font-bold text-white uppercase tracking-wider font-mono">Anti-Loop Tripwires</div>
            <div className="text-[11px] text-[#94A3B8] mt-0.5">Armed. Capped at depth 5 &amp; rate budget to prevent cascades.</div>
          </div>
        </div>

        <div className="bg-surface-container-low/70 border border-outline-variant/60 rounded-xl p-3 flex items-start gap-2.5">
          <div className="p-1.5 rounded-lg bg-amber-500/10 text-amber-400 mt-0.5">
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div>
            <div className="text-[11px] font-bold text-white uppercase tracking-wider font-mono">Four-Eyes Governance</div>
            <div className="text-[11px] text-[#94A3B8] mt-0.5">Dual-operator review required for high-impact mutations.</div>
          </div>
        </div>

        <div className="bg-surface-container-low/70 border border-outline-variant/60 rounded-xl p-3 flex items-start gap-2.5">
          <div className="p-1.5 rounded-lg bg-sky-500/10 text-sky-400 mt-0.5">
            <Play className="w-4 h-4" />
          </div>
          <div>
            <div className="text-[11px] font-bold text-white uppercase tracking-wider font-mono">Dry-Run Simulator</div>
            <div className="text-[11px] text-[#94A3B8] mt-0.5">Test policy rules with zero side-effects &amp; blast radius insight.</div>
          </div>
        </div>
      </div>

      {error && (
        <div className="bg-rose-500/15 border border-rose-500/30 text-rose-300 text-xs p-3 rounded-xl flex items-center justify-between">
          <span>{error}</span>
          <button onClick={fetchActiveTab} className="underline hover:text-white text-xs">Retry</button>
        </div>
      )}

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
          <span className="px-1.5 py-0.2 rounded-md bg-surface-container-high text-[10px] font-mono text-[#94A3B8]">
            {policies.length}
          </span>
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
          {approvals.filter(a => a.status === 'PENDING').length > 0 && (
            <span className="px-1.5 py-0.2 rounded-md bg-amber-500/20 text-amber-400 text-[10px] font-mono font-bold">
              {approvals.filter(a => a.status === 'PENDING').length}
            </span>
          )}
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
            <div className="bg-surface-container-low/80 p-8 text-center rounded-2xl border border-outline-variant text-xs text-[#94A3B8] space-y-3">
              <div>No automation policies configured. Click "New Automation Policy" to create one.</div>
              <div className="flex items-center justify-center gap-2">
                <button
                  onClick={() => {
                    applyTemplate('critical_compromise');
                    setIsCreateModalOpen(true);
                  }}
                  className="px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-container-highest text-amber-300 text-xs border border-amber-500/30 font-medium transition"
                >
                  Apply "Quarantine Compromise" Template
                </button>
              </div>
            </div>
          ) : (
            policies.map((p) => {
              const isExpanded = expandedPolicyId === p.id;
              const isBusy = actionLoadingId === p.id;
              return (
                <div
                  key={p.id}
                  className={`bg-surface-container-low/80 p-5 rounded-2xl border transition space-y-3 shadow-lg ${
                    p.enabled ? 'border-outline-variant hover:border-[#F59E0B]/40' : 'border-outline-variant/40 opacity-70'
                  }`}
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="flex items-center gap-2.5 flex-wrap">
                      <span
                        className={`w-2.5 h-2.5 rounded-full ${p.enabled ? 'bg-emerald-400' : 'bg-slate-500'}`}
                        title={p.enabled ? 'Policy is Active' : 'Policy is Disabled'}
                      />
                      <h3 className="font-headline font-bold text-white text-sm">{p.name}</h3>
                      <span className="px-2 py-0.5 rounded-md bg-surface-container-high text-[#94A3B8] text-[10px] font-mono">
                        v{p.policyVersion || 1}
                      </span>
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
                      {!p.enabled && (
                        <span className="px-2 py-0.5 rounded-md bg-slate-500/20 text-slate-400 text-[10px] font-mono">
                          Disabled
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono text-[#94A3B8]">Priority: {p.priority}</span>
                      <button
                        onClick={() => handleTogglePolicy(p)}
                        disabled={isBusy}
                        title={p.enabled ? 'Disable Policy' : 'Enable Policy'}
                        className={`p-1.5 rounded-lg border text-xs transition disabled:opacity-50 ${
                          p.enabled
                            ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30 hover:bg-emerald-500/20'
                            : 'bg-slate-500/10 text-slate-400 border-slate-500/30 hover:bg-slate-500/20'
                        }`}
                      >
                        <Power className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => handleDeletePolicy(p)}
                        disabled={isBusy}
                        title="Delete Policy"
                        className="p-1.5 rounded-lg bg-rose-500/10 text-rose-300 border border-rose-500/30 hover:bg-rose-500/20 text-xs transition disabled:opacity-50"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>

                  <p className="text-xs text-[#94A3B8]">{p.description || 'No description provided.'}</p>

                  <div className="flex items-center justify-between pt-1">
                    <div className="flex flex-wrap gap-2 text-[11px] font-mono text-[#64748B]">
                      <span>Triggers: <strong className="text-white">{p.triggerEventTypes}</strong></span>
                      <span>•</span>
                      <span>Scope: <strong className="text-white">{p.scopeType || 'WORKSPACE'}</strong></span>
                    </div>

                    <button
                      onClick={() => setExpandedPolicyId(isExpanded ? null : p.id)}
                      className="text-xs font-mono text-[#F59E0B] hover:underline flex items-center gap-1"
                    >
                      <span>{isExpanded ? 'Hide AST Specs' : 'View AST Specs'}</span>
                      {isExpanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                    </button>
                  </div>

                  {isExpanded && (
                    <div className="mt-3 pt-3 border-t border-outline-variant/40 grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                      <div>
                        <div className="text-[10px] font-mono uppercase text-[#94A3B8] mb-1">Conditions AST</div>
                        <pre className="bg-surface-container-lowest p-2.5 rounded-xl text-emerald-400 font-mono text-[11px] overflow-x-auto max-h-48 border border-outline-variant">
                          {(() => {
                            try { return JSON.stringify(JSON.parse(p.conditionsJson || '[]'), null, 2); }
                            catch (_) { return p.conditionsJson || '[]'; }
                          })()}
                        </pre>
                      </div>
                      <div>
                        <div className="text-[10px] font-mono uppercase text-[#94A3B8] mb-1">Actions Spec</div>
                        <pre className="bg-surface-container-lowest p-2.5 rounded-xl text-amber-300 font-mono text-[11px] overflow-x-auto max-h-48 border border-outline-variant">
                          {(() => {
                            try { return JSON.stringify(JSON.parse(p.actionsJson || '[]'), null, 2); }
                            catch (_) { return p.actionsJson || '[]'; }
                          })()}
                        </pre>
                      </div>
                    </div>
                  )}
                </div>
              );
            })
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
                className="bg-surface-container-low/80 p-5 rounded-2xl border border-outline-variant flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-lg"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold text-white text-xs">{appr.actionType}</span>
                    <span className={`px-2 py-0.5 rounded-md text-[10px] font-mono font-bold ${
                      appr.status === 'PENDING'
                        ? 'bg-amber-500/20 text-amber-400'
                        : appr.status === 'APPROVED'
                        ? 'bg-emerald-500/20 text-emerald-400'
                        : 'bg-rose-500/20 text-rose-400'
                    }`}>
                      {appr.status}
                    </span>
                  </div>
                  <p className="text-xs text-[#94A3B8] mt-1 font-mono">
                    Requested by: {appr.requestedBy || 'Automation System'}
                  </p>
                  {appr.rejectionReason && (
                    <p className="text-xs text-rose-300 mt-1 italic">
                      Reason: {appr.rejectionReason}
                    </p>
                  )}
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
                    <button
                      onClick={() => handleCancelApproval(appr.id)}
                      className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-slate-500/20 hover:bg-slate-500/30 text-slate-300 border border-slate-500/40 text-xs font-semibold transition"
                    >
                      <span>Cancel</span>
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
        <div className="space-y-4">
          <div className="bg-surface-container-low/80 p-3.5 rounded-2xl border border-outline-variant/60 flex items-center justify-between text-xs text-[#94A3B8]">
            <div className="flex items-center gap-2">
              <ShieldAlert className="w-4 h-4 text-rose-400" />
              <span>Anti-Loop Tripwire Monitor: Recursion depth capped at 5. Events exceeding depth are aborted with status <strong className="text-rose-400 font-mono">LOOP_ABORTED</strong>.</span>
            </div>
          </div>

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
                          <span className={`px-2 py-0.5 rounded-md font-mono text-[10px] font-bold ${
                            ex.status === 'LOOP_ABORTED'
                              ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                              : ex.status === 'SUCCESS' || ex.status === 'COMPLETED'
                              ? 'bg-emerald-500/20 text-emerald-400'
                              : 'bg-amber-500/20 text-amber-400'
                          }`}>
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
              rows={14}
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
              <pre className="bg-surface-container-lowest font-mono text-[11px] text-amber-300 p-3 rounded-xl border border-outline-variant overflow-x-auto max-h-[350px]">
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
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm overflow-y-auto">
          <form
            onSubmit={handleCreatePolicy}
            className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-xl w-full p-6 space-y-4 shadow-2xl my-8 max-h-[90vh] overflow-y-auto"
          >
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <div className="flex items-center gap-2">
                <span className="font-headline font-bold text-white text-base">New Automation Policy</span>
                <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">AST Rules</span>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="text-[#94A3B8] hover:text-white transition"
              >
                ✕
              </button>
            </div>

            {/* Template Presets */}
            <div className="p-3 rounded-xl bg-surface-container-low/90 border border-outline-variant/60 space-y-2">
              <span className="text-[11px] text-[#94A3B8] font-mono uppercase font-bold flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                Quick Presets:
              </span>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => applyTemplate('critical_compromise')}
                  className="px-2.5 py-1 rounded-lg bg-surface-container-high hover:bg-surface-container-highest text-amber-300 text-[11px] font-mono border border-amber-500/30 transition"
                >
                  Quarantine Compromise
                </button>
                <button
                  type="button"
                  onClick={() => applyTemplate('auto_rotation')}
                  className="px-2.5 py-1 rounded-lg bg-surface-container-high hover:bg-surface-container-highest text-sky-300 text-[11px] font-mono border border-sky-500/30 transition"
                >
                  Auto-Rotate Lease
                </button>
                <button
                  type="button"
                  onClick={() => applyTemplate('jit_access_review')}
                  className="px-2.5 py-1 rounded-lg bg-surface-container-high hover:bg-surface-container-highest text-emerald-300 text-[11px] font-mono border border-emerald-500/30 transition"
                >
                  JIT Access Review
                </button>
              </div>
            </div>

            {createError && (
              <div className="p-3 rounded-xl bg-rose-500/15 border border-rose-500/30 text-rose-300 text-xs">
                {createError}
              </div>
            )}

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

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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

                <div>
                  <label className="block text-[#94A3B8] mb-1 font-mono">Priority (lower executes first):</label>
                  <input
                    type="number"
                    min="1"
                    max="1000"
                    value={newPolicyPriority}
                    onChange={(e) => setNewPolicyPriority(e.target.value)}
                    className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#F59E0B]"
                  />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-[#94A3B8] font-mono">AST Conditions JSON:</label>
                  <span className="text-[10px] text-[#94A3B8] font-mono">Use '[]' for unconditional match</span>
                </div>
                <textarea
                  rows={4}
                  value={newPolicyConditions}
                  onChange={(e) => setNewPolicyConditions(e.target.value)}
                  className="w-full bg-surface-container-lowest text-emerald-400 font-mono text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#F59E0B]"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-[#94A3B8] font-mono">Actions JSON:</label>
                  <span className="text-[10px] text-[#94A3B8] font-mono">Array of action objects</span>
                </div>
                <textarea
                  rows={4}
                  value={newPolicyActions}
                  onChange={(e) => setNewPolicyActions(e.target.value)}
                  className="w-full bg-surface-container-lowest text-amber-300 font-mono text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#F59E0B]"
                />
              </div>

              <div className="space-y-2 pt-1 border-t border-outline-variant/40">
                <div className="flex items-center gap-2">
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

                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    id="dryRunMode"
                    checked={newPolicyDryRun}
                    onChange={(e) => setNewPolicyDryRun(e.target.checked)}
                    className="rounded border-outline-variant text-[#F59E0B] focus:ring-0"
                  />
                  <label htmlFor="dryRunMode" className="text-white font-mono text-xs cursor-pointer">
                    Dry-run mode (evaluate AST and audit match without triggering actual mutations)
                  </label>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-outline-variant/60">
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="px-4 py-2 rounded-xl bg-surface-container text-white text-xs font-semibold hover:bg-surface-container-highest transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={createSubmitting}
                className="px-4 py-2 rounded-xl bg-[#F59E0B] hover:bg-[#F59E0B]/80 text-white text-xs font-semibold shadow-lg shadow-[#F59E0B]/20 transition disabled:opacity-50 flex items-center gap-1.5"
              >
                {createSubmitting && <RotateCw className="w-3.5 h-3.5 animate-spin" />}
                <span>Save Policy</span>
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default AutomationCenterView;

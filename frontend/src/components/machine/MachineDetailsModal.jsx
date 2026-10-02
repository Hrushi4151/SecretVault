import React, { useState, useEffect } from 'react';
import { machineApi } from '../../api/machine';
import { oidcApi } from '../../api/oidc';
import { projectApi } from '../../api/projects';
import { environmentApi } from '../../api/environments';
import {
  Bot,
  Shield,
  Key,
  Clock,
  Layers,
  FileText,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Plus,
  Trash2,
  Lock,
  Eye,
  RefreshCw,
  Loader2,
  Calendar,
  Sparkles,
  Github,
  Gitlab,
  Globe,
  HelpCircle,
  Activity,
  UserCheck,
  Ban
} from 'lucide-react';

export const MachineDetailsModal = ({ workspaceId, machineId, onClose, onRefresh }) => {
  const [machine, setMachine] = useState(null);
  const [activeTab, setActiveTab] = useState('OVERVIEW'); // 'OVERVIEW' | 'PERMISSIONS' | 'TRUST' | 'SESSIONS' | 'WHY_ACCESS'
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Sub-data states
  const [grants, setGrants] = useState([]);
  const [trustPolicies, setTrustPolicies] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [providers, setProviders] = useState([]);
  const [projects, setProjects] = useState([]);
  const [environments, setEnvironments] = useState([]);

  // Grant Form State
  const [isAddGrantOpen, setIsAddGrantOpen] = useState(false);
  const [grantFormData, setGrantFormData] = useState({
    scopeType: 'PROJECT',
    projectId: '',
    environmentId: '',
    secretId: '',
    action: 'secret.read',
    effect: 'ALLOW',
    secretRestrictions: '',
  });

  // Trust Policy Form State
  const [isAddPolicyOpen, setIsAddPolicyOpen] = useState(false);
  const [policyFormData, setPolicyFormData] = useState({
    oidcProviderId: '',
    description: '',
    rules: [
      { claimName: 'repository', operator: 'EQUALS', claimValue: '' },
      { claimName: 'ref', operator: 'EQUALS', claimValue: 'refs/heads/main' },
    ],
  });

  useEffect(() => {
    if (workspaceId && machineId) {
      loadMachineDetails();
    }
  }, [workspaceId, machineId]);

  const loadMachineDetails = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const [mRes, gRes, tRes, sRes, pRes, projRes] = await Promise.allSettled([
        machineApi.get(workspaceId, machineId),
        machineApi.listGrants(workspaceId, machineId),
        oidcApi.listTrustPolicies(workspaceId, machineId),
        machineApi.listSessions(workspaceId, machineId),
        oidcApi.listProviders(workspaceId),
        projectApi.list(workspaceId),
      ]);

      if (mRes.status === 'fulfilled') {
        const mData = mRes.value.data?.data || mRes.value.data;
        setMachine(mData);
      } else {
        throw new Error('Failed to load machine identity');
      }

      setGrants(gRes.status === 'fulfilled' ? (gRes.value.data?.data || gRes.value.data || []) : []);
      setTrustPolicies(tRes.status === 'fulfilled' ? (tRes.value.data?.data || tRes.value.data || []) : []);
      setSessions(sRes.status === 'fulfilled' ? (sRes.value.data?.data || sRes.value.data || []) : []);
      setProviders(pRes.status === 'fulfilled' ? (pRes.value.data?.data || pRes.value.data || []) : []);
      setProjects(projRes.status === 'fulfilled' ? (projRes.value.data?.data || projRes.value.data || []) : []);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to load machine identity details');
    } finally {
      setIsLoading(false);
    }
  };

  const handleStatusAction = async (action) => {
    try {
      setErrorMessage(null);
      if (action === 'disable') {
        await machineApi.disable(workspaceId, machineId);
        setSuccessMessage('Machine identity disabled. All active tokens rejected.');
      } else if (action === 'enable') {
        await machineApi.enable(workspaceId, machineId);
        setSuccessMessage('Machine identity enabled.');
      } else if (action === 'revoke') {
        if (!window.confirm('Revoking a machine identity is a PERMANENT terminal state. Continue?')) return;
        await machineApi.revoke(workspaceId, machineId);
        setSuccessMessage('Machine identity revoked.');
      }
      loadMachineDetails();
      if (onRefresh) onRefresh();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to update status');
    }
  };

  const handleCreateGrant = async (e) => {
    e.preventDefault();
    try {
      setErrorMessage(null);
      const payload = {
        scopeType: grantFormData.scopeType,
        projectId: grantFormData.projectId || null,
        environmentId: grantFormData.environmentId || null,
        secretId: grantFormData.secretId || null,
        action: grantFormData.action,
        effect: grantFormData.effect,
        secretRestrictions: grantFormData.secretRestrictions
          ? grantFormData.secretRestrictions.split(',').map((s) => s.trim()).filter(Boolean)
          : [],
      };
      await machineApi.createGrant(workspaceId, machineId, payload);
      setSuccessMessage('Granular access grant assigned.');
      setIsAddGrantOpen(false);
      loadMachineDetails();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to create grant');
    }
  };

  const handleRevokeGrant = async (grantId) => {
    if (!window.confirm('Are you sure you want to revoke this access grant?')) return;
    try {
      await machineApi.revokeGrant(workspaceId, machineId, grantId);
      setSuccessMessage('Grant revoked.');
      loadMachineDetails();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to revoke grant');
    }
  };

  const handleCreateTrustPolicy = async (e) => {
    e.preventDefault();
    try {
      setErrorMessage(null);
      if (!policyFormData.oidcProviderId) {
        setErrorMessage('Please select an OIDC provider');
        return;
      }
      const validRules = policyFormData.rules.filter((r) => r.claimName && r.claimValue);
      if (validRules.length === 0) {
        setErrorMessage('At least one claim matching rule is required.');
        return;
      }
      await oidcApi.createTrustPolicy(workspaceId, machineId, {
        oidcProviderId: policyFormData.oidcProviderId,
        description: policyFormData.description,
        rules: validRules,
      });
      setSuccessMessage('OIDC Trust Policy registered.');
      setIsAddPolicyOpen(false);
      loadMachineDetails();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to create trust policy');
    }
  };

  const handleDeleteTrustPolicy = async (policyId) => {
    if (!window.confirm('Are you sure you want to delete this trust policy?')) return;
    try {
      await oidcApi.deleteTrustPolicy(workspaceId, machineId, policyId);
      setSuccessMessage('Trust policy deleted.');
      loadMachineDetails();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to delete policy');
    }
  };

  const handleRevokeSession = async (sessionId) => {
    try {
      await machineApi.revokeSession(workspaceId, machineId, sessionId);
      setSuccessMessage('Session token revoked.');
      loadMachineDetails();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to revoke session');
    }
  };

  const handleRevokeAllSessions = async () => {
    if (!window.confirm('Revoke ALL active sessions for this machine identity immediately?')) return;
    try {
      await machineApi.revokeAllSessions(workspaceId, machineId);
      setSuccessMessage('All machine sessions revoked.');
      loadMachineDetails();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to revoke all sessions');
    }
  };

  const getNaturalLanguagePolicySummary = (rules) => {
    if (!rules || rules.length === 0) return 'Matches any claim from this provider (Overly Broad).';
    const parts = rules.map((r) => `${r.claimName} ${r.operator.toLowerCase()} "${r.claimValue}"`);
    return `Workload token matching ${parts.join(' AND ')} may authenticate as this machine identity.`;
  };

  if (isLoading && !machine) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-container-lowest/80 backdrop-blur-sm">
        <div className="flex flex-col items-center gap-3 p-8 bg-surface-container rounded-2xl border border-outline-variant">
          <Loader2 className="w-8 h-8 animate-spin text-brand-primary" />
          <span className="text-xs font-mono text-text-secondary">Loading Machine Identity profile...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-container-lowest/80 backdrop-blur-sm">
      <div className="w-full max-w-4xl bg-surface-container rounded-2xl border border-outline-variant shadow-2xl flex flex-col max-h-[90vh] overflow-hidden animate-in fade-in zoom-in-95">
        {/* Header */}
        <div className="p-5 border-b border-outline-variant flex items-start justify-between gap-4 bg-surface-container-high/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-surface-container-highest border border-outline-variant flex items-center justify-center text-brand-primary">
              <Bot className="w-6 h-6 text-[#FF2D6D]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-semibold text-text-primary font-mono">{machine?.name}</h3>
                <span className={`px-2 py-0.5 text-[10px] font-mono rounded font-medium ${
                  machine?.status === 'ACTIVE'
                    ? 'bg-status-success/10 text-status-success border border-status-success/20'
                    : machine?.status === 'DISABLED'
                    ? 'bg-status-warning/10 text-status-warning border border-status-warning/20'
                    : 'bg-status-error/10 text-status-error border border-status-error/20'
                }`}>
                  {machine?.status}
                </span>
                <span className="px-2 py-0.5 text-[10px] font-mono rounded bg-surface-container-high text-text-secondary border border-outline-variant">
                  {machine?.type}
                </span>
              </div>
              <p className="text-xs text-text-secondary mt-0.5">{machine?.description || 'No description provided.'}</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="text-text-tertiary hover:text-text-primary text-sm p-1 rounded hover:bg-surface-container-high"
          >
            ✕
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="flex items-center px-5 border-b border-outline-variant bg-surface-container-lowest/40 gap-2">
          {[
            { id: 'OVERVIEW', label: 'Overview', icon: <Bot className="w-3.5 h-3.5" /> },
            { id: 'PERMISSIONS', label: `Grants (${grants.length})`, icon: <Shield className="w-3.5 h-3.5" /> },
            { id: 'TRUST', label: `OIDC Trust (${trustPolicies.length})`, icon: <Lock className="w-3.5 h-3.5" /> },
            { id: 'SESSIONS', label: `Sessions (${sessions.length})`, icon: <Key className="w-3.5 h-3.5" /> },
          ].map((t) => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id)}
              className={`px-3 py-2.5 text-xs font-medium border-b-2 flex items-center gap-1.5 transition-colors ${
                activeTab === t.id
                  ? 'border-brand-primary text-brand-primary font-semibold'
                  : 'border-transparent text-text-secondary hover:text-text-primary'
              }`}
            >
              {t.icon}
              {t.label}
            </button>
          ))}
        </div>

        {/* Feedback Banners */}
        {errorMessage && (
          <div className="mx-5 mt-4 p-3 rounded-lg bg-status-error/10 border border-status-error/20 flex items-start gap-2 text-xs text-status-error">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            <div className="flex-1">{errorMessage}</div>
            <button onClick={() => setErrorMessage(null)}>✕</button>
          </div>
        )}

        {successMessage && (
          <div className="mx-5 mt-4 p-3 rounded-lg bg-status-success/10 border border-status-success/20 flex items-start gap-2 text-xs text-status-success">
            <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
            <div className="flex-1">{successMessage}</div>
            <button onClick={() => setSuccessMessage(null)}>✕</button>
          </div>
        )}

        {/* Tab Content Body */}
        <div className="p-5 flex-1 overflow-y-auto space-y-5">
          {/* ======================= OVERVIEW TAB ======================= */}
          {activeTab === 'OVERVIEW' && (
            <div className="space-y-5">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <div className="p-3 rounded-xl bg-surface-container-high/40 border border-outline-variant space-y-1">
                  <span className="text-[10px] font-mono text-text-tertiary">Machine ID</span>
                  <div className="text-xs font-mono text-text-primary truncate" title={machine?.id}>
                    {machine?.id}
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-surface-container-high/40 border border-outline-variant space-y-1">
                  <span className="text-[10px] font-mono text-text-tertiary">Expiration</span>
                  <div className="text-xs font-mono text-text-primary">
                    {machine?.expiresAt ? new Date(machine.expiresAt).toLocaleDateString() : 'Non-expiring'}
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-surface-container-high/40 border border-outline-variant space-y-1">
                  <span className="text-[10px] font-mono text-text-tertiary">Last Authenticated</span>
                  <div className="text-xs font-mono text-text-primary">
                    {machine?.lastAuthenticatedAt ? new Date(machine.lastAuthenticatedAt).toLocaleString() : 'Never'}
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-surface-container-high/40 border border-outline-variant space-y-1">
                  <span className="text-[10px] font-mono text-text-tertiary">Active Sessions</span>
                  <div className="text-xs font-mono text-brand-primary font-bold">
                    {sessions.filter((s) => s.status === 'ACTIVE').length} active
                  </div>
                </div>
              </div>

              {/* Status Controls */}
              <div className="p-4 rounded-xl bg-surface-container-lowest/60 border border-outline-variant space-y-3">
                <h4 className="text-xs font-semibold text-text-primary uppercase tracking-wider font-mono">
                  Lifecycle Controls
                </h4>
                <div className="flex flex-wrap items-center gap-2">
                  {machine?.status === 'ACTIVE' && (
                    <button
                      onClick={() => handleStatusAction('disable')}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg border border-status-warning/30 bg-status-warning/10 text-status-warning hover:bg-status-warning/20 transition-colors flex items-center gap-1.5"
                    >
                      <Ban className="w-3.5 h-3.5" />
                      Disable Machine
                    </button>
                  )}

                  {machine?.status === 'DISABLED' && (
                    <button
                      onClick={() => handleStatusAction('enable')}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg border border-status-success/30 bg-status-success/10 text-status-success hover:bg-status-success/20 transition-colors flex items-center gap-1.5"
                    >
                      <UserCheck className="w-3.5 h-3.5" />
                      Re-enable Machine
                    </button>
                  )}

                  {machine?.status !== 'REVOKED' && (
                    <button
                      onClick={() => handleStatusAction('revoke')}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg border border-status-error/30 bg-status-error/10 text-status-error hover:bg-status-error/20 transition-colors flex items-center gap-1.5"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Revoke Identity (Permanent)
                    </button>
                  )}
                </div>
                <p className="text-[11px] text-text-tertiary">
                  Disabled machines reject all incoming token exchange and API authorization calls immediately.
                </p>
              </div>
            </div>
          )}

          {/* ======================= PERMISSIONS TAB ======================= */}
          {activeTab === 'PERMISSIONS' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-text-primary">Granular Access Grants</h4>
                  <p className="text-xs text-text-secondary">
                    Machine identities possess NO default access. Explicit grants define project, environment, and secret boundaries.
                  </p>
                </div>
                <button
                  onClick={() => setIsAddGrantOpen(true)}
                  className="px-3 py-1.5 text-xs font-medium rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-surface-container-lowest flex items-center gap-1.5"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Granular Grant
                </button>
              </div>

              {/* Add Grant Form */}
              {isAddGrantOpen && (
                <form onSubmit={handleCreateGrant} className="p-4 rounded-xl bg-surface-container-high/60 border border-outline-variant space-y-3 animate-in fade-in">
                  <div className="flex items-center justify-between">
                    <h5 className="text-xs font-semibold text-text-primary">Configure Scoped Grant</h5>
                    <button type="button" onClick={() => setIsAddGrantOpen(false)} className="text-xs text-text-tertiary hover:text-text-primary">✕</button>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <div className="space-y-1">
                      <label className="text-[11px] text-text-secondary">Scope Level</label>
                      <select
                        value={grantFormData.scopeType}
                        onChange={(e) => setGrantFormData({ ...grantFormData, scopeType: e.target.value })}
                        className="w-full px-2.5 py-1.5 text-xs rounded border border-outline-variant bg-surface-container-lowest text-text-primary"
                      >
                        <option value="PROJECT">Project Scoped</option>
                        <option value="ENVIRONMENT">Environment Scoped</option>
                        <option value="WORKSPACE">Workspace-Wide (Privileged)</option>
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className="text-[11px] text-text-secondary">Action / Permission</label>
                      <select
                        value={grantFormData.action}
                        onChange={(e) => setGrantFormData({ ...grantFormData, action: e.target.value })}
                        className="w-full px-2.5 py-1.5 text-xs rounded border border-outline-variant bg-surface-container-lowest text-text-primary font-mono"
                      >
                        <option value="secret.read">secret.read (Metadata only)</option>
                        <option value="secret.reveal">secret.reveal (Plaintext access)</option>
                        <option value="secret.create">secret.create</option>
                        <option value="secret.update">secret.update</option>
                        <option value="secret.delete">secret.delete</option>
                        <option value="provider.sync">provider.sync</option>
                        <option value="deployment.trigger">deployment.trigger</option>
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className="text-[11px] text-text-secondary">Effect (Precedence: DENY &gt; ALLOW)</label>
                      <select
                        value={grantFormData.effect}
                        onChange={(e) => setGrantFormData({ ...grantFormData, effect: e.target.value })}
                        className="w-full px-2.5 py-1.5 text-xs rounded border border-outline-variant bg-surface-container-lowest text-text-primary font-mono"
                      >
                        <option value="ALLOW">ALLOW</option>
                        <option value="DENY">DENY (Explicit Restriction)</option>
                      </select>
                    </div>
                  </div>

                  {grantFormData.scopeType !== 'WORKSPACE' && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <label className="text-[11px] text-text-secondary">Target Project</label>
                        <select
                          value={grantFormData.projectId}
                          onChange={(e) => setGrantFormData({ ...grantFormData, projectId: e.target.value })}
                          className="w-full px-2.5 py-1.5 text-xs rounded border border-outline-variant bg-surface-container-lowest text-text-primary"
                        >
                          <option value="">-- Select Project --</option>
                          {projects.map((p) => (
                            <option key={p.id} value={p.id}>{p.name} ({p.slug})</option>
                          ))}
                        </select>
                      </div>

                      {grantFormData.scopeType === 'ENVIRONMENT' && (
                        <div className="space-y-1">
                          <label className="text-[11px] text-text-secondary">Target Environment</label>
                          <input
                            type="text"
                            placeholder="Environment Slug (e.g. development, production)"
                            value={grantFormData.environmentId}
                            onChange={(e) => setGrantFormData({ ...grantFormData, environmentId: e.target.value })}
                            className="w-full px-2.5 py-1.5 text-xs rounded border border-outline-variant bg-surface-container-lowest text-text-primary font-mono"
                          />
                        </div>
                      )}
                    </div>
                  )}

                  <div className="space-y-1">
                    <label className="text-[11px] text-text-secondary">Secret Name Allowlist / Restrictions (Optional)</label>
                    <input
                      type="text"
                      placeholder="e.g. DB_PASSWORD, API_URL (comma-separated)"
                      value={grantFormData.secretRestrictions}
                      onChange={(e) => setGrantFormData({ ...grantFormData, secretRestrictions: e.target.value })}
                      className="w-full px-2.5 py-1.5 text-xs font-mono rounded border border-outline-variant bg-surface-container-lowest text-text-primary"
                    />
                    <span className="text-[10px] text-text-tertiary">
                      If specified, this machine can only access the explicit secret keys listed above.
                    </span>
                  </div>

                  <div className="flex items-center justify-end gap-2 pt-2">
                    <button type="button" onClick={() => setIsAddGrantOpen(false)} className="px-3 py-1 text-xs rounded hover:bg-surface-container-highest">Cancel</button>
                    <button type="submit" className="px-3.5 py-1 text-xs font-medium rounded bg-brand-primary text-surface-container-lowest">Assign Grant</button>
                  </div>
                </form>
              )}

              {/* Grants Table */}
              {grants.length === 0 ? (
                <div className="p-8 text-center rounded-xl bg-surface-container/50 border border-dashed border-outline-variant">
                  <Shield className="w-8 h-8 text-text-tertiary mx-auto mb-2" />
                  <p className="text-xs text-text-secondary">No granular grants assigned. This machine cannot access any secrets.</p>
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-outline-variant">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-surface-container-high/60 text-text-secondary font-mono uppercase text-[10px] border-b border-outline-variant">
                      <tr>
                        <th className="p-3">Scope</th>
                        <th className="p-3">Action</th>
                        <th className="p-3">Effect</th>
                        <th className="p-3">Secret Restrictions</th>
                        <th className="p-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant">
                      {grants.map((g) => (
                        <tr key={g.id} className="hover:bg-surface-container-high/30">
                          <td className="p-3 font-mono">
                            <span className="px-1.5 py-0.5 rounded bg-surface-container-high text-[10px] mr-1.5">{g.scopeType}</span>
                            {g.projectId ? `Project: ${g.projectId}` : 'All Projects'}
                          </td>
                          <td className="p-3 font-mono font-semibold text-brand-primary">{g.action}</td>
                          <td className="p-3 font-mono">
                            <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                              g.effect === 'ALLOW' ? 'bg-status-success/10 text-status-success' : 'bg-status-error/10 text-status-error'
                            }`}>
                              {g.effect}
                            </span>
                          </td>
                          <td className="p-3 font-mono text-text-secondary">
                            {Array.isArray(g.secretRestrictions) && g.secretRestrictions.length > 0
                              ? g.secretRestrictions.join(', ')
                              : 'All Secrets in Scope'}
                          </td>
                          <td className="p-3 text-right">
                            <button
                              onClick={() => handleRevokeGrant(g.id)}
                              className="p-1 text-text-tertiary hover:text-status-error rounded"
                              title="Revoke Grant"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* ======================= TRUST POLICIES TAB ======================= */}
          {activeTab === 'TRUST' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-text-primary">OIDC Trust Policies</h4>
                  <p className="text-xs text-text-secondary">
                    Defines WHO (which CI/CD repository, branch, workflow, or claims) may authenticate as this machine identity.
                  </p>
                </div>
                <button
                  onClick={() => setIsAddPolicyOpen(true)}
                  className="px-3 py-1.5 text-xs font-medium rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-surface-container-lowest flex items-center gap-1.5"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Trust Policy
                </button>
              </div>

              {/* Add Policy Visual Builder */}
              {isAddPolicyOpen && (
                <form onSubmit={handleCreateTrustPolicy} className="p-4 rounded-xl bg-surface-container-high/60 border border-outline-variant space-y-4 animate-in fade-in">
                  <div className="flex items-center justify-between">
                    <h5 className="text-xs font-semibold text-text-primary flex items-center gap-1.5">
                      <Sparkles className="w-3.5 h-3.5 text-brand-primary" />
                      Visual Trust Policy Builder
                    </h5>
                    <button type="button" onClick={() => setIsAddPolicyOpen(false)} className="text-xs text-text-tertiary hover:text-text-primary">✕</button>
                  </div>

                  <div className="space-y-1">
                    <label className="text-[11px] text-text-secondary">Trusted OIDC Provider</label>
                    <select
                      required
                      value={policyFormData.oidcProviderId}
                      onChange={(e) => setPolicyFormData({ ...policyFormData, oidcProviderId: e.target.value })}
                      className="w-full px-2.5 py-1.5 text-xs rounded border border-outline-variant bg-surface-container-lowest text-text-primary"
                    >
                      <option value="">-- Select OIDC Provider --</option>
                      {providers.map((p) => (
                        <option key={p.id} value={p.id}>{p.name} ({p.issuer})</option>
                      ))}
                    </select>
                  </div>

                  {/* Conditions List */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-[11px] text-text-secondary">
                      <span>Claim Matching Conditions (Evaluated as AND)</span>
                      <button
                        type="button"
                        onClick={() => setPolicyFormData({
                          ...policyFormData,
                          rules: [...policyFormData.rules, { claimName: 'environment', operator: 'EQUALS', claimValue: 'production' }]
                        })}
                        className="text-brand-primary hover:underline flex items-center gap-1 text-[10px]"
                      >
                        + Add Claim Condition
                      </button>
                    </div>

                    {policyFormData.rules.map((rule, idx) => (
                      <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                        <div className="col-span-4">
                          <input
                            type="text"
                            placeholder="Claim (e.g. repository, ref)"
                            value={rule.claimName}
                            onChange={(e) => {
                              const updated = [...policyFormData.rules];
                              updated[idx].claimName = e.target.value;
                              setPolicyFormData({ ...policyFormData, rules: updated });
                            }}
                            className="w-full px-2.5 py-1 text-xs font-mono rounded border border-outline-variant bg-surface-container-lowest text-text-primary"
                          />
                        </div>
                        <div className="col-span-3">
                          <select
                            value={rule.operator}
                            onChange={(e) => {
                              const updated = [...policyFormData.rules];
                              updated[idx].operator = e.target.value;
                              setPolicyFormData({ ...policyFormData, rules: updated });
                            }}
                            className="w-full px-2 py-1 text-xs font-mono rounded border border-outline-variant bg-surface-container-lowest text-text-primary"
                          >
                            <option value="EQUALS">== EQUALS</option>
                            <option value="NOT_EQUALS">!= NOT EQUALS</option>
                            <option value="PREFIX">PREFIX</option>
                            <option value="SUFFIX">SUFFIX</option>
                            <option value="CONTAINS">CONTAINS</option>
                            <option value="IN">IN</option>
                          </select>
                        </div>
                        <div className="col-span-4">
                          <input
                            type="text"
                            placeholder="Expected Value (e.g. owner/repo)"
                            value={rule.claimValue}
                            onChange={(e) => {
                              const updated = [...policyFormData.rules];
                              updated[idx].claimValue = e.target.value;
                              setPolicyFormData({ ...policyFormData, rules: updated });
                            }}
                            className="w-full px-2.5 py-1 text-xs font-mono rounded border border-outline-variant bg-surface-container-lowest text-text-primary"
                          />
                        </div>
                        <div className="col-span-1 text-right">
                          <button
                            type="button"
                            onClick={() => {
                              const updated = policyFormData.rules.filter((_, i) => i !== idx);
                              setPolicyFormData({ ...policyFormData, rules: updated });
                            }}
                            className="text-text-tertiary hover:text-status-error text-xs"
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Natural Language Summary */}
                  <div className="p-3 rounded-lg bg-surface-container-lowest border border-outline-variant text-xs space-y-1">
                    <span className="text-[10px] font-mono text-text-tertiary uppercase">Human Readable Policy Summary:</span>
                    <p className="text-text-primary font-medium">
                      {getNaturalLanguagePolicySummary(policyFormData.rules)}
                    </p>
                  </div>

                  <div className="flex items-center justify-end gap-2 pt-2">
                    <button type="button" onClick={() => setIsAddPolicyOpen(false)} className="px-3 py-1 text-xs rounded hover:bg-surface-container-highest">Cancel</button>
                    <button type="submit" className="px-3.5 py-1 text-xs font-medium rounded bg-brand-primary text-surface-container-lowest">Register Trust Policy</button>
                  </div>
                </form>
              )}

              {/* Policies List */}
              {trustPolicies.length === 0 ? (
                <div className="p-8 text-center rounded-xl bg-surface-container/50 border border-dashed border-outline-variant">
                  <Lock className="w-8 h-8 text-text-tertiary mx-auto mb-2" />
                  <p className="text-xs text-text-secondary">No trust policies configured. Workload tokens cannot authenticate.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {trustPolicies.map((tp) => (
                    <div key={tp.id} className="p-4 rounded-xl bg-surface-container-high/40 border border-outline-variant space-y-2">
                      <div className="flex items-start justify-between">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-semibold text-text-primary">{tp.providerName || 'OIDC Provider'}</span>
                            <span className="px-1.5 py-0.5 text-[10px] font-mono rounded bg-status-success/10 text-status-success">
                              {tp.status || 'ACTIVE'}
                            </span>
                          </div>
                          <p className="text-xs text-text-secondary mt-1">
                            {tp.naturalLanguageSummary || getNaturalLanguagePolicySummary(tp.rules)}
                          </p>
                        </div>
                        <button
                          onClick={() => handleDeleteTrustPolicy(tp.id)}
                          className="p-1 text-text-tertiary hover:text-status-error rounded"
                          title="Delete Trust Policy"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>

                      {/* Claim Rules Chips */}
                      <div className="flex flex-wrap gap-1.5 pt-1">
                        {Array.isArray(tp.rules) && tp.rules.map((r, i) => (
                          <span key={i} className="px-2 py-0.5 rounded bg-surface-container-lowest text-[11px] font-mono border border-outline-variant/60 text-text-primary">
                            <span className="text-text-tertiary">{r.claimName}</span> {r.operator} <span className="text-brand-primary font-semibold">"{r.claimValue}"</span>
                          </span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ======================= SESSIONS TAB ======================= */}
          {activeTab === 'SESSIONS' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-text-primary">Active Machine Sessions</h4>
                  <p className="text-xs text-text-secondary">
                    Cryptographically hashed machine session tokens. SecretVault uses short-lived tokens (5-15 min TTL).
                  </p>
                </div>
                {sessions.length > 0 && (
                  <button
                    onClick={handleRevokeAllSessions}
                    className="px-3 py-1.5 text-xs font-medium rounded-lg border border-status-error/30 bg-status-error/10 text-status-error hover:bg-status-error/20 flex items-center gap-1.5"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    Revoke All Sessions
                  </button>
                )}
              </div>

              {sessions.length === 0 ? (
                <div className="p-8 text-center rounded-xl bg-surface-container/50 border border-dashed border-outline-variant">
                  <Key className="w-8 h-8 text-text-tertiary mx-auto mb-2" />
                  <p className="text-xs text-text-secondary">No active sessions found for this machine identity.</p>
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-outline-variant">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-surface-container-high/60 text-text-secondary font-mono uppercase text-[10px] border-b border-outline-variant">
                      <tr>
                        <th className="p-3">Session ID / Hash</th>
                        <th className="p-3">Status</th>
                        <th className="p-3">Issued At</th>
                        <th className="p-3">Expires At</th>
                        <th className="p-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant">
                      {sessions.map((s) => (
                        <tr key={s.id} className="hover:bg-surface-container-high/30">
                          <td className="p-3 font-mono text-text-primary">{s.id ? s.id.slice(0, 16) + '...' : '-'}</td>
                          <td className="p-3 font-mono">
                            <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                              s.status === 'ACTIVE' ? 'bg-status-success/10 text-status-success' : 'bg-status-error/10 text-status-error'
                            }`}>
                              {s.status}
                            </span>
                          </td>
                          <td className="p-3 font-mono text-text-secondary">{s.createdAt ? new Date(s.createdAt).toLocaleString() : '-'}</td>
                          <td className="p-3 font-mono text-text-secondary">{s.expiresAt ? new Date(s.expiresAt).toLocaleString() : '-'}</td>
                          <td className="p-3 text-right">
                            {s.status === 'ACTIVE' && (
                              <button
                                onClick={() => handleRevokeSession(s.id)}
                                className="px-2 py-1 text-[11px] font-medium rounded border border-status-error/20 bg-status-error/10 text-status-error hover:bg-status-error/20"
                              >
                                Revoke
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { machineApi } from '../../api/machine';
import { OidcProvidersView } from './OidcProvidersView';
import { MachineDetailsModal } from './MachineDetailsModal';
import {
  Bot,
  Plus,
  RefreshCw,
  Search,
  Filter,
  CheckCircle2,
  AlertTriangle,
  Server,
  Layers,
  Key,
  Clock,
  Shield,
  Loader2,
  MoreVertical,
  Trash2,
  Lock,
  ExternalLink,
  ChevronRight,
  Sparkles,
  Ban,
  UserCheck
} from 'lucide-react';

export const MachineIdentitiesView = () => {
  const { activeWorkspace } = useAuth();
  const [machines, setMachines] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // View Navigation
  const [currentSubView, setCurrentSubView] = useState('LIST'); // 'LIST' | 'PROVIDERS'
  const [selectedMachineId, setSelectedMachineId] = useState(null);

  // Filter & Search
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');

  // Create Modal
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [createFormData, setCreateFormData] = useState({
    name: '',
    description: '',
    type: 'CI_CD',
    expiresAt: '',
  });
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    if (activeWorkspace?.id) {
      loadMachines();
    }
  }, [activeWorkspace?.id]);

  const loadMachines = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const res = await machineApi.list(activeWorkspace.id);
      const data = res.data?.data || res.data || [];
      setMachines(Array.isArray(data) ? data : []);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to load machine identities');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateMachine = async (e) => {
    e.preventDefault();
    try {
      setIsCreating(true);
      setErrorMessage(null);

      // Validate name pattern
      const nameRegex = /^[a-z0-9][a-z0-9-_]{1,63}$/;
      if (!nameRegex.test(createFormData.name.trim())) {
        setErrorMessage('Machine name must be 2-64 characters, lowercase alphanumeric with hyphens or underscores (e.g. github-rally-ci).');
        setIsCreating(false);
        return;
      }

      const payload = {
        name: createFormData.name.trim(),
        description: createFormData.description.trim(),
        type: createFormData.type,
        expiresAt: createFormData.expiresAt ? new Date(createFormData.expiresAt).toISOString() : null,
      };

      const res = await machineApi.create(activeWorkspace.id, payload);
      const created = res.data?.data || res.data;
      setSuccessMessage(`Machine Identity "${payload.name}" created successfully.`);
      setIsCreateModalOpen(false);
      setCreateFormData({ name: '', description: '', type: 'CI_CD', expiresAt: '' });
      loadMachines();
      if (created?.id) {
        setSelectedMachineId(created.id);
      }
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to create machine identity');
    } finally {
      setIsCreating(false);
    }
  };

  const handleToggleStatus = async (machine, e) => {
    e.stopPropagation();
    try {
      setErrorMessage(null);
      if (machine.status === 'ACTIVE') {
        await machineApi.disable(activeWorkspace.id, machine.id);
        setSuccessMessage(`Machine "${machine.name}" disabled.`);
      } else if (machine.status === 'DISABLED') {
        await machineApi.enable(activeWorkspace.id, machine.id);
        setSuccessMessage(`Machine "${machine.name}" enabled.`);
      }
      loadMachines();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to update machine status');
    }
  };

  const handleDeleteMachine = async (machine, e) => {
    e.stopPropagation();
    if (!window.confirm(`Are you sure you want to delete machine identity "${machine.name}"? Audit history will be securely preserved.`)) {
      return;
    }
    try {
      await machineApi.delete(activeWorkspace.id, machine.id);
      setSuccessMessage(`Machine identity "${machine.name}" deleted.`);
      loadMachines();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to delete machine identity');
    }
  };

  // Filtered list
  const filteredMachines = machines.filter((m) => {
    const matchesSearch =
      !searchQuery ||
      m.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      m.description?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || m.status === statusFilter;
    const matchesType = typeFilter === 'ALL' || m.type === typeFilter;
    return matchesSearch && matchesStatus && matchesType;
  });

  // Metrics
  const activeCount = machines.filter((m) => m.status === 'ACTIVE').length;
  const disabledCount = machines.filter((m) => m.status === 'DISABLED').length;
  const revokedCount = machines.filter((m) => m.status === 'REVOKED').length;

  if (currentSubView === 'PROVIDERS') {
    return (
      <OidcProvidersView
        workspaceId={activeWorkspace?.id}
        onBack={() => setCurrentSubView('LIST')}
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold text-text-primary flex items-center gap-2">
            <Bot className="w-5 h-5 text-brand-primary" />
            Machine Identities & CI/CD Workload Authentication
            <span className="px-2 py-0.5 text-xs font-mono rounded bg-brand-primary/10 text-brand-primary border border-brand-primary/20">
              Phase 9
            </span>
          </h2>
          <p className="text-xs text-text-secondary mt-0.5">
            Cryptographic non-human service accounts, GitHub Actions/GitLab CI OIDC trust policies, and least-privilege machine authorization.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setCurrentSubView('PROVIDERS')}
            className="px-3 py-1.5 text-xs font-medium rounded-lg border border-outline-variant bg-surface-container hover:bg-surface-container-high text-text-secondary hover:text-text-primary transition-colors flex items-center gap-1.5"
          >
            <Server className="w-3.5 h-3.5 text-brand-primary" />
            OIDC Providers
          </button>
          <button
            onClick={loadMachines}
            disabled={isLoading}
            className="px-3 py-1.5 text-xs font-medium rounded-lg border border-outline-variant bg-surface-container hover:bg-surface-container-high text-text-secondary hover:text-text-primary transition-colors flex items-center gap-1.5"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="px-3 py-1.5 text-xs font-medium rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-surface-container-lowest font-medium transition-colors flex items-center gap-1.5 shadow-sm"
          >
            <Plus className="w-3.5 h-3.5" />
            New Machine Identity
          </button>
        </div>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="p-4 rounded-xl bg-surface-container border border-outline-variant flex items-center justify-between">
          <div>
            <span className="text-xs text-text-tertiary">Total Machines</span>
            <div className="text-xl font-bold text-text-primary font-mono mt-0.5">{machines.length}</div>
          </div>
          <div className="w-9 h-9 rounded-lg bg-surface-container-high flex items-center justify-center text-text-secondary">
            <Bot className="w-5 h-5" />
          </div>
        </div>

        <div className="p-4 rounded-xl bg-surface-container border border-outline-variant flex items-center justify-between">
          <div>
            <span className="text-xs text-text-tertiary">Active</span>
            <div className="text-xl font-bold text-status-success font-mono mt-0.5">{activeCount}</div>
          </div>
          <div className="w-9 h-9 rounded-lg bg-status-success/10 flex items-center justify-center text-status-success">
            <CheckCircle2 className="w-5 h-5" />
          </div>
        </div>

        <div className="p-4 rounded-xl bg-surface-container border border-outline-variant flex items-center justify-between">
          <div>
            <span className="text-xs text-text-tertiary">Disabled</span>
            <div className="text-xl font-bold text-status-warning font-mono mt-0.5">{disabledCount}</div>
          </div>
          <div className="w-9 h-9 rounded-lg bg-status-warning/10 flex items-center justify-center text-status-warning">
            <Ban className="w-5 h-5" />
          </div>
        </div>

        <div className="p-4 rounded-xl bg-surface-container border border-outline-variant flex items-center justify-between">
          <div>
            <span className="text-xs text-text-tertiary">Revoked / Terminal</span>
            <div className="text-xl font-bold text-status-error font-mono mt-0.5">{revokedCount}</div>
          </div>
          <div className="w-9 h-9 rounded-lg bg-status-error/10 flex items-center justify-center text-status-error">
            <Shield className="w-5 h-5" />
          </div>
        </div>
      </div>

      {/* Status Messages */}
      {errorMessage && (
        <div className="p-3 rounded-lg bg-status-error/10 border border-status-error/20 flex items-start gap-2 text-xs text-status-error">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <div className="flex-1">{errorMessage}</div>
          <button onClick={() => setErrorMessage(null)}>✕</button>
        </div>
      )}

      {successMessage && (
        <div className="p-3 rounded-lg bg-status-success/10 border border-status-success/20 flex items-start gap-2 text-xs text-status-success">
          <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
          <div className="flex-1">{successMessage}</div>
          <button onClick={() => setSuccessMessage(null)}>✕</button>
        </div>
      )}

      {/* Search & Filters */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-3 bg-surface-container rounded-xl border border-outline-variant">
        <div className="relative flex-1 w-full">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-text-tertiary" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search machine identities by name or description..."
            className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
          />
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
          >
            <option value="ALL">All Statuses</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="DISABLED">DISABLED</option>
            <option value="REVOKED">REVOKED</option>
            <option value="EXPIRED">EXPIRED</option>
          </select>

          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
          >
            <option value="ALL">All Types</option>
            <option value="CI_CD">CI/CD Workload</option>
            <option value="SERVICE_ACCOUNT">Service Account</option>
            <option value="WORKLOAD">Workload</option>
            <option value="AUTOMATION">Automation</option>
          </select>
        </div>
      </div>

      {/* Machine Identities Table */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center p-12 bg-surface-container rounded-xl border border-outline-variant">
          <Loader2 className="w-6 h-6 animate-spin text-brand-primary mb-2" />
          <span className="text-xs text-text-secondary font-mono">Loading Machine Identities...</span>
        </div>
      ) : filteredMachines.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 bg-surface-container/50 rounded-xl border border-dashed border-outline-variant text-center">
          <Bot className="w-10 h-10 text-text-tertiary mb-3" />
          <h3 className="text-sm font-medium text-text-primary">No Machine Identities Found</h3>
          <p className="text-xs text-text-secondary max-w-md mt-1 mb-4">
            Create a machine identity to enable non-human workloads (such as GitHub Actions runners or background sync workers) to authenticate securely with zero standing passwords.
          </p>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="px-3.5 py-1.5 text-xs font-medium rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-surface-container-lowest flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            Create Machine Identity
          </button>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-outline-variant bg-surface-container">
          <table className="w-full text-left text-xs">
            <thead className="bg-surface-container-high/60 text-text-secondary font-mono uppercase text-[10px] border-b border-outline-variant">
              <tr>
                <th className="p-3.5">Machine Identity</th>
                <th className="p-3.5">Type</th>
                <th className="p-3.5">Status</th>
                <th className="p-3.5">Last Authenticated</th>
                <th className="p-3.5">Expires At</th>
                <th className="p-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {filteredMachines.map((m) => (
                <tr
                  key={m.id}
                  onClick={() => setSelectedMachineId(m.id)}
                  className="hover:bg-surface-container-high/40 cursor-pointer transition-colors"
                >
                  <td className="p-3.5">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-surface-container-high border border-outline-variant flex items-center justify-center text-text-primary">
                        <Bot className="w-4 h-4 text-[#FF2D6D]" />
                      </div>
                      <div>
                        <div className="font-semibold text-text-primary font-mono flex items-center gap-1.5">
                          {m.name}
                        </div>
                        <div className="text-[11px] text-text-secondary truncate max-w-xs">
                          {m.description || 'No description'}
                        </div>
                      </div>
                    </div>
                  </td>

                  <td className="p-3.5 font-mono">
                    <span className="px-2 py-0.5 rounded bg-surface-container-high text-text-secondary border border-outline-variant text-[10px]">
                      {m.type}
                    </span>
                  </td>

                  <td className="p-3.5 font-mono">
                    <span className={`px-2 py-0.5 rounded text-[10px] font-semibold flex items-center gap-1.5 w-fit ${
                      m.status === 'ACTIVE'
                        ? 'bg-status-success/10 text-status-success border border-status-success/20'
                        : m.status === 'DISABLED'
                        ? 'bg-status-warning/10 text-status-warning border border-status-warning/20'
                        : 'bg-status-error/10 text-status-error border border-status-error/20'
                    }`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${
                        m.status === 'ACTIVE' ? 'bg-status-success animate-pulse' : m.status === 'DISABLED' ? 'bg-status-warning' : 'bg-status-error'
                      }`} />
                      {m.status}
                    </span>
                  </td>

                  <td className="p-3.5 font-mono text-text-secondary">
                    {m.lastAuthenticatedAt ? new Date(m.lastAuthenticatedAt).toLocaleString() : 'Never'}
                  </td>

                  <td className="p-3.5 font-mono text-text-secondary">
                    {m.expiresAt ? new Date(m.expiresAt).toLocaleDateString() : 'Non-expiring'}
                  </td>

                  <td className="p-3.5 text-right" onClick={(e) => e.stopPropagation()}>
                    <div className="flex items-center justify-end gap-1.5">
                      <button
                        onClick={(e) => handleToggleStatus(m, e)}
                        className="p-1.5 text-text-tertiary hover:text-text-primary rounded hover:bg-surface-container-high transition-colors"
                        title={m.status === 'ACTIVE' ? 'Disable Machine' : 'Enable Machine'}
                      >
                        {m.status === 'ACTIVE' ? <Ban className="w-3.5 h-3.5 text-status-warning" /> : <UserCheck className="w-3.5 h-3.5 text-status-success" />}
                      </button>

                      <button
                        onClick={(e) => handleDeleteMachine(m, e)}
                        className="p-1.5 text-text-tertiary hover:text-status-error rounded hover:bg-surface-container-high transition-colors"
                        title="Delete Machine"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>

                      <button
                        onClick={() => setSelectedMachineId(m.id)}
                        className="p-1.5 text-brand-primary hover:bg-surface-container-high rounded transition-colors"
                        title="Configure"
                      >
                        <ChevronRight className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create Machine Identity Modal */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-container-lowest/80 backdrop-blur-sm">
          <div className="w-full max-w-md bg-surface-container rounded-2xl border border-outline-variant shadow-2xl p-6 space-y-5 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-semibold text-text-primary flex items-center gap-2">
                  <Bot className="w-5 h-5 text-brand-primary" />
                  New Machine Identity
                </h3>
                <p className="text-xs text-text-secondary mt-0.5">
                  Create a non-human identity for CI/CD or background workers.
                </p>
              </div>
              <button
                onClick={() => setIsCreateModalOpen(false)}
                className="text-text-tertiary hover:text-text-primary text-sm"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateMachine} className="space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-medium text-text-secondary">Machine Name (Identifier)</label>
                <input
                  type="text"
                  required
                  value={createFormData.name}
                  onChange={(e) => setCreateFormData({ ...createFormData, name: e.target.value })}
                  placeholder="e.g. github-rally-ci"
                  className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                />
                <span className="text-[10px] text-text-tertiary">
                  Must be unique in workspace, lowercase alphanumeric with hyphens.
                </span>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-text-secondary">Identity Type</label>
                <select
                  value={createFormData.type}
                  onChange={(e) => setCreateFormData({ ...createFormData, type: e.target.value })}
                  className="w-full px-3 py-2 text-xs rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                >
                  <option value="CI_CD">CI/CD Workload (GitHub Actions, GitLab CI)</option>
                  <option value="SERVICE_ACCOUNT">Service Account</option>
                  <option value="WORKLOAD">Workload / Daemon</option>
                  <option value="AUTOMATION">Automation Task</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-text-secondary">Description</label>
                <textarea
                  rows={2}
                  value={createFormData.description}
                  onChange={(e) => setCreateFormData({ ...createFormData, description: e.target.value })}
                  placeholder="e.g. CI/CD pipeline actor for deployment secrets and testing"
                  className="w-full px-3 py-2 text-xs rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-text-secondary">Expiration Date & Time (Optional)</label>
                <input
                  type="datetime-local"
                  value={createFormData.expiresAt}
                  onChange={(e) => setCreateFormData({ ...createFormData, expiresAt: e.target.value })}
                  className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                />
              </div>

              <div className="p-3 rounded-lg bg-surface-container-lowest border border-outline-variant text-[11px] text-text-secondary space-y-1">
                <div className="flex items-center gap-1.5 text-brand-primary font-semibold">
                  <Shield className="w-3.5 h-3.5" />
                  Least Privilege Invariant
                </div>
                <div>
                  Newly created machine identities have ZERO secret access by default. After creation, you can configure scoped grants and OIDC trust policies.
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="px-3.5 py-1.5 text-xs font-medium rounded-lg border border-outline-variant hover:bg-surface-container-high text-text-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isCreating}
                  className="px-4 py-1.5 text-xs font-medium rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-surface-container-lowest flex items-center gap-1.5"
                >
                  {isCreating && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  Create Machine Identity
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Machine Details & Configuration Modal */}
      {selectedMachineId && (
        <MachineDetailsModal
          workspaceId={activeWorkspace?.id}
          machineId={selectedMachineId}
          onClose={() => setSelectedMachineId(null)}
          onRefresh={loadMachines}
        />
      )}
    </div>
  );
};

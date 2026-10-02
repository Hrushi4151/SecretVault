import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { workspaceApi } from '../../api/workspaces';
import { MemberAccessManagementDialog } from '../access/MemberAccessManagementDialog';
import {
  Shield,
  ShieldCheck,
  Building2,
  Users,
  MailCheck,
  Lock,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
  Save,
  Trash2,
  UserPlus,
  Copy,
  Check,
  AlertCircle,
  ExternalLink,
  ChevronRight,
  LogOut,
  Sparkles,
} from 'lucide-react';

export const WorkspaceSettingsView = ({ onNavigateToAccess, onNavigateToProjects }) => {
  const { activeWorkspace, user, refreshWorkspaces, switchWorkspace } = useAuth();

  const [activeSubTab, setActiveSubTab] = useState('general'); // 'general' | 'governance' | 'members' | 'invitations' | 'security' | 'danger'
  const [settings, setSettings] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // General Form State
  const [workspaceName, setWorkspaceName] = useState('');

  // Governance Form State
  const [projectPolicy, setProjectPolicy] = useState('ALL_MEMBERS');
  const [environmentPolicy, setEnvironmentPolicy] = useState('ADMIN_ONLY');
  const [productionProtection, setProductionProtection] = useState(true);

  // Members State
  const [members, setMembers] = useState([]);
  const [isLoadingMembers, setIsLoadingMembers] = useState(false);
  const [selectedMemberForAccess, setSelectedMemberForAccess] = useState(null);
  const [updatingMemberId, setUpdatingMemberId] = useState(null);

  // Invitations State
  const [invitations, setInvitations] = useState([]);
  const [isLoadingInvitations, setIsLoadingInvitations] = useState(false);
  const [lookupEmail, setLookupEmail] = useState('');
  const [lookupResult, setLookupResult] = useState(null);
  const [isLookingUp, setIsLookingUp] = useState(false);
  const [inviteRole, setInviteRole] = useState('DEVELOPER');
  const [inviteExpiresDays, setInviteExpiresDays] = useState(7);
  const [isSendingInvite, setIsSendingInvite] = useState(false);
  const [inviteFeedback, setInviteFeedback] = useState(null);

  // Copy state
  const [copiedField, setCopiedField] = useState(null);

  const isOwner = activeWorkspace?.role === 'OWNER';
  const isAdmin = activeWorkspace?.role === 'ADMIN' || isOwner;

  const fetchSettings = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await workspaceApi.getSettings(activeWorkspace.id);
      setSettings(data);
      setWorkspaceName(data.name || activeWorkspace.name || '');
      setProjectPolicy(data.projectCreationPolicy || 'ALL_MEMBERS');
      setEnvironmentPolicy(data.environmentCreationPolicy || 'ADMIN_ONLY');
      setProductionProtection(data.productionProtectionEnforced !== false);
    } catch (err) {
      setError(err.message || 'Failed to load workspace settings.');
    } finally {
      setIsLoading(false);
    }
  }, [activeWorkspace?.id, activeWorkspace?.name]);

  const fetchMembers = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsLoadingMembers(true);
    try {
      const data = await workspaceApi.listMembers(activeWorkspace.id);
      setMembers(data || []);
    } catch (err) {
      // Ignore or set error
    } finally {
      setIsLoadingMembers(false);
    }
  }, [activeWorkspace?.id]);

  const fetchInvitations = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsLoadingInvitations(true);
    try {
      const data = await workspaceApi.listInvitations(activeWorkspace.id);
      setInvitations(data || []);
    } catch (err) {
      // Ignore
    } finally {
      setIsLoadingInvitations(false);
    }
  }, [activeWorkspace?.id]);

  useEffect(() => {
    fetchSettings();
  }, [fetchSettings]);

  useEffect(() => {
    if (activeSubTab === 'members') {
      fetchMembers();
    } else if (activeSubTab === 'invitations') {
      fetchInvitations();
    }
  }, [activeSubTab, fetchMembers, fetchInvitations]);

  // Debounced User Lookup
  useEffect(() => {
    if (!lookupEmail || !lookupEmail.includes('@') || !isAdmin) {
      setLookupResult(null);
      return;
    }
    const timer = setTimeout(async () => {
      setIsLookingUp(true);
      try {
        const result = await workspaceApi.lookupUser(activeWorkspace.id, lookupEmail);
        setLookupResult(result);
      } catch (err) {
        setLookupResult(null);
      } finally {
        setIsLookingUp(false);
      }
    }, 350);
    return () => clearTimeout(timer);
  }, [lookupEmail, activeWorkspace?.id, isAdmin]);

  const handleCopy = (text, field) => {
    navigator.clipboard.writeText(text);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 2000);
  };

  const handleSaveGeneral = async (e) => {
    e.preventDefault();
    if (!isAdmin) return;
    setIsSaving(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await workspaceApi.updateSettings(activeWorkspace.id, {
        name: workspaceName.trim(),
        projectCreationPolicy: projectPolicy,
        environmentCreationPolicy: environmentPolicy,
        productionProtectionEnforced: productionProtection,
      });
      setSettings(updated);
      setSuccessMessage('Workspace general settings saved successfully.');
      await refreshWorkspaces();
    } catch (err) {
      setError(err.message || 'Failed to update workspace settings.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleSaveGovernance = async (e) => {
    e.preventDefault();
    if (!isAdmin) return;
    setIsSaving(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await workspaceApi.updateSettings(activeWorkspace.id, {
        name: workspaceName.trim(),
        projectCreationPolicy: projectPolicy,
        environmentCreationPolicy: environmentPolicy,
        productionProtectionEnforced: productionProtection,
      });
      setSettings(updated);
      setSuccessMessage('Workspace security governance policies updated and audited.');
    } catch (err) {
      setError(err.message || 'Failed to update governance policies.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleUpdateRole = async (targetUserId, newRole) => {
    if (!isAdmin) return;
    setUpdatingMemberId(targetUserId);
    setError(null);
    try {
      await workspaceApi.updateMemberRole(activeWorkspace.id, targetUserId, { role: newRole });
      await fetchMembers();
      setSuccessMessage('Member role updated successfully.');
    } catch (err) {
      setError(err.message || 'Failed to update member role.');
    } finally {
      setUpdatingMemberId(null);
    }
  };

  const handleRemoveMember = async (targetUserId, email) => {
    if (!window.confirm(`Are you sure you want to remove member ${email} from this workspace?`)) {
      return;
    }
    setUpdatingMemberId(targetUserId);
    setError(null);
    try {
      await workspaceApi.removeMember(activeWorkspace.id, targetUserId);
      await fetchMembers();
      setSuccessMessage(`Member ${email} removed from workspace.`);
    } catch (err) {
      setError(err.message || 'Failed to remove member.');
    } finally {
      setUpdatingMemberId(null);
    }
  };

  const handleSendInvite = async (e) => {
    e.preventDefault();
    if (!isAdmin || !lookupEmail) return;
    setIsSendingInvite(true);
    setInviteFeedback(null);
    try {
      await workspaceApi.createInvitation(activeWorkspace.id, {
        email: lookupEmail.trim(),
        role: inviteRole,
        expiresInDays: parseInt(inviteExpiresDays, 10) || 7,
      });
      setInviteFeedback({ type: 'success', message: `In-app invitation issued to ${lookupEmail}.` });
      setLookupEmail('');
      setLookupResult(null);
      await fetchInvitations();
    } catch (err) {
      setInviteFeedback({ type: 'error', message: err.message || 'Failed to send invitation.' });
    } finally {
      setIsSendingInvite(false);
    }
  };

  const handleRevokeInvite = async (invitationId) => {
    if (!window.confirm('Are you sure you want to revoke this pending invitation?')) return;
    try {
      await workspaceApi.revokeInvitation(activeWorkspace.id, invitationId);
      await fetchInvitations();
    } catch (err) {
      alert(err.message || 'Failed to revoke invitation.');
    }
  };

  const handleLeaveWorkspace = async () => {
    if (!window.confirm(`Are you sure you want to leave workspace "${activeWorkspace?.name}"?`)) {
      return;
    }
    try {
      await workspaceApi.removeMember(activeWorkspace.id, user.id);
      await refreshWorkspaces();
    } catch (err) {
      alert(err.message || 'Failed to leave workspace.');
    }
  };

  const subTabs = [
    { id: 'general', label: 'General', icon: <Building2 className="w-4 h-4" /> },
    { id: 'governance', label: 'Security & Governance', icon: <Shield className="w-4 h-4" /> },
    { id: 'members', label: 'Members', icon: <Users className="w-4 h-4" /> },
    { id: 'invitations', label: 'Invitations', icon: <MailCheck className="w-4 h-4" /> },
    { id: 'security', label: 'Security Overview', icon: <ShieldCheck className="w-4 h-4" /> },
    { id: 'danger', label: 'Danger Zone', icon: <AlertTriangle className="w-4 h-4 text-[#F87171]" /> },
  ];

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-16 gap-3 font-mono text-xs text-[#F4B5C8]">
        <RefreshCw className="w-6 h-6 animate-spin text-[#FF2D6D]" />
        <span>Loading workspace settings...</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col w-full gap-8 text-white font-body">
      {/* Workspace Settings Header Banner */}
      <section className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 relative">
        <div className="flex flex-col gap-2 max-w-3xl">
          <div className="flex items-center gap-2 text-xs font-mono tracking-widest text-[#A26377] uppercase font-semibold">
            <span>Settings</span>
            <span className="text-[#FF2D6D]">/</span>
            <span className="text-white font-bold">{activeWorkspace?.name || 'Workspace'}</span>
            <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] text-[#4ADE80] font-mono border border-[#4ADE80]/30">
              <span className="w-1.5 h-1.5 rounded-full bg-[#4ADE80] mr-1.5" />
              {activeWorkspace?.role || 'MEMBER'}
            </span>
          </div>

          <h1 className="text-3xl md:text-4xl font-headline font-bold tracking-tight text-white flex items-center gap-3">
            <span>Workspace Settings</span>
          </h1>

          <p className="text-xs md:text-sm text-[#F4B5C8] leading-relaxed">
            Manage organization workspace identity, project scoping policies, multi-tenant governance, member roles, and security invariants.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={fetchSettings}
            className="px-4 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white text-xs font-mono font-semibold flex items-center gap-2 transition-all border border-[#FFB4C8]/15"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </button>
        </div>
      </section>

      {/* Global Alerts */}
      {error && (
        <div className="flex items-center gap-3 p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6]">
          <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
          <span className="flex-1">{error}</span>
          <button onClick={() => setError(null)} className="text-xs underline text-[#FFDAD6]">
            Dismiss
          </button>
        </div>
      )}

      {successMessage && (
        <div className="flex items-center gap-3 p-4 rounded-2xl bg-[#00511C]/30 border border-[#4ADE80]/40 text-xs text-[#86EFAC]">
          <CheckCircle2 className="w-5 h-5 shrink-0 text-[#4ADE80]" />
          <span className="flex-1">{successMessage}</span>
          <button onClick={() => setSuccessMessage(null)} className="text-xs underline text-[#86EFAC]">
            Dismiss
          </button>
        </div>
      )}

      {/* Settings Navigation Tabs */}
      <section className="flex flex-wrap items-center gap-2 border-b border-[#FFB4C8]/15 pb-4">
        {subTabs.map((tab) => {
          const isActive = activeSubTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => {
                setActiveSubTab(tab.id);
                setError(null);
                setSuccessMessage(null);
              }}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-mono font-medium transition-all cursor-pointer ${
                isActive
                  ? 'bg-[#FF2D6D] text-white font-bold shadow-lg shadow-[#FF2D6D]/20'
                  : 'bg-[#30000F] text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] border border-[#FFB4C8]/15'
              }`}
            >
              <span>{tab.icon}</span>
              <span>{tab.label}</span>
            </button>
          );
        })}
      </section>

      {/* Tab 1: General Settings */}
      {activeSubTab === 'general' && (
        <div className="flex flex-col gap-6 max-w-3xl">
          <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-5">
            <div>
              <h2 className="text-base font-headline font-bold text-white">General Information</h2>
              <p className="text-xs text-[#A26377]">
                Basic identification properties for this workspace container.
              </p>
            </div>

            <form onSubmit={handleSaveGeneral} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Workspace Display Name</label>
                <input
                  type="text"
                  value={workspaceName}
                  onChange={(e) => setWorkspaceName(e.target.value)}
                  disabled={!isAdmin || isSaving}
                  required
                  placeholder="e.g. Acme Core Infrastructure"
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] text-xs text-white outline-none disabled:opacity-50"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Workspace URL Slug</label>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={settings?.slug || activeWorkspace?.slug || ''}
                    readOnly
                    className="flex-1 px-4 py-2.5 rounded-xl bg-[#30000F]/60 border border-[#FFB4C8]/10 text-xs font-mono text-[#A26377] outline-none cursor-not-allowed"
                  />
                  <button
                    type="button"
                    onClick={() => handleCopy(settings?.slug || activeWorkspace?.slug, 'slug')}
                    className="p-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-[#F4B5C8] hover:text-white"
                    title="Copy slug"
                  >
                    {copiedField === 'slug' ? <Check className="w-4 h-4 text-[#4ADE80]" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
                <span className="text-[10px] font-mono text-[#A26377]">
                  Used in CLI invocations and scoping URLs. Slugs are immutable after creation.
                </span>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Workspace ID (UUID)</label>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={activeWorkspace?.id || ''}
                    readOnly
                    className="flex-1 px-4 py-2.5 rounded-xl bg-[#30000F]/60 border border-[#FFB4C8]/10 text-xs font-mono text-[#A26377] outline-none cursor-not-allowed"
                  />
                  <button
                    type="button"
                    onClick={() => handleCopy(activeWorkspace?.id, 'id')}
                    className="p-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-[#F4B5C8] hover:text-white"
                    title="Copy UUID"
                  >
                    {copiedField === 'id' ? <Check className="w-4 h-4 text-[#4ADE80]" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {isAdmin && (
                <div className="pt-3 border-t border-[#FFB4C8]/10 flex items-center justify-end">
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-6 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-bold font-mono tracking-wider flex items-center gap-2 shadow-lg shadow-[#FF2D6D]/20 transition-all disabled:opacity-50"
                  >
                    <Save className="w-4 h-4" />
                    <span>{isSaving ? 'Saving...' : 'Save Workspace Changes'}</span>
                  </button>
                </div>
              )}
            </form>
          </div>
        </div>
      )}

      {/* Tab 2: Security & Governance */}
      {activeSubTab === 'governance' && (
        <div className="flex flex-col gap-6 max-w-3xl">
          <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6">
            <div>
              <h2 className="text-base font-headline font-bold text-white">Security Governance Policies</h2>
              <p className="text-xs text-[#A26377]">
                Configure zero-trust creation policies, elevated tier protection, and scope boundaries.
              </p>
            </div>

            <form onSubmit={handleSaveGovernance} className="flex flex-col gap-5">
              {/* Policy 1: Project Creation */}
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-headline font-bold text-white">
                    Project Creation Policy
                  </span>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#3F0016] text-[#FFB4C8]">
                    Phase 2 Policy
                  </span>
                </div>
                <p className="text-xs text-[#A26377]">
                  Defines which workspace members are authorized to create new project microservice containers.
                </p>
                <select
                  value={projectPolicy}
                  onChange={(e) => setProjectPolicy(e.target.value)}
                  disabled={!isAdmin || isSaving}
                  className="mt-2 px-3 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                >
                  <option value="ALL_MEMBERS">ALL_MEMBERS — Owners, Admins, and Developers can create projects</option>
                  <option value="ADMIN_ONLY">ADMIN_ONLY — Only Workspace Owners and Admins can create projects</option>
                  <option value="OWNER_ONLY">OWNER_ONLY — Strictly Workspace Owners can create projects</option>
                </select>
              </div>

              {/* Policy 2: Environment Creation */}
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-headline font-bold text-white">
                    Custom Environment Creation Policy
                  </span>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#3F0016] text-[#FFB4C8]">
                    Phase 2 Policy
                  </span>
                </div>
                <p className="text-xs text-[#A26377]">
                  Controls authorization for provisioning custom environment tiers beyond default dev/staging/prod.
                </p>
                <select
                  value={environmentPolicy}
                  onChange={(e) => setEnvironmentPolicy(e.target.value)}
                  disabled={!isAdmin || isSaving}
                  className="mt-2 px-3 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                >
                  <option value="ADMIN_ONLY">ADMIN_ONLY — Owners and Admins can provision custom environments</option>
                  <option value="OWNER_ONLY">OWNER_ONLY — Strictly Owners can provision custom environments</option>
                </select>
              </div>

              {/* Policy 3: Production Protection */}
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex items-center justify-between gap-4">
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-headline font-bold text-white">
                    Enforce Production Environment Protection
                  </span>
                  <p className="text-xs text-[#A26377]">
                    Enforces immutable versioning, AES-256-GCM context binding, and step-up authorization on production tiers.
                  </p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer shrink-0">
                  <input
                    type="checkbox"
                    checked={productionProtection}
                    onChange={(e) => setProductionProtection(e.target.checked)}
                    disabled={!isAdmin || isSaving}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-[#3F0016] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#FF2D6D]" />
                </label>
              </div>

              {isAdmin && (
                <div className="pt-3 border-t border-[#FFB4C8]/10 flex items-center justify-end">
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-6 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-bold font-mono tracking-wider flex items-center gap-2 shadow-lg shadow-[#FF2D6D]/20 transition-all disabled:opacity-50"
                  >
                    <Save className="w-4 h-4" />
                    <span>{isSaving ? 'Updating...' : 'Save Governance Policies'}</span>
                  </button>
                </div>
              )}
            </form>
          </div>
        </div>
      )}

      {/* Tab 3: Members */}
      {activeSubTab === 'members' && (
        <div className="flex flex-col gap-6">
          <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-headline font-bold text-white">Workspace Members</h2>
                <p className="text-xs text-[#A26377]">
                  Manage active team members and their baseline standing RBAC roles.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setActiveSubTab('invitations')}
                className="px-4 py-2 rounded-xl bg-[#FF2D6D] text-white text-xs font-bold font-mono flex items-center gap-2 shadow-md hover:bg-[#FF2D6D]/90"
              >
                <UserPlus className="w-4 h-4" />
                <span>+ Invite Member</span>
              </button>
            </div>

            {isLoadingMembers ? (
              <div className="flex items-center justify-center p-12 text-xs font-mono text-[#A26377]">
                <RefreshCw className="w-5 h-5 animate-spin mr-2 text-[#FF2D6D]" />
                <span>Loading workspace members...</span>
              </div>
            ) : (
              <div className="overflow-x-auto rounded-2xl border border-[#FFB4C8]/15">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-[#30000F] text-[#A26377] font-mono text-[10px] uppercase tracking-wider border-b border-[#FFB4C8]/15">
                      <th className="p-3.5">User</th>
                      <th className="p-3.5">Email</th>
                      <th className="p-3.5">Workspace Role</th>
                      <th className="p-3.5">Joined</th>
                      <th className="p-3.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#FFB4C8]/10 bg-[#1E000A]">
                    {members.map((member) => {
                      const isCurrentUser = member.userId === user?.id;
                      return (
                        <tr key={member.id} className="hover:bg-[#30000F]/60 transition-colors">
                          <td className="p-3.5 font-medium text-white">
                            <div className="flex items-center gap-2.5">
                              <div className="w-7 h-7 rounded-full bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center font-mono text-[10px] text-[#FF2D6D]">
                                {(member.fullName || member.email || 'U').slice(0, 2).toUpperCase()}
                              </div>
                              <span>{member.fullName || 'Registered User'}</span>
                              {isCurrentUser && (
                                <span className="px-1.5 py-0.2 rounded bg-[#3F0016] text-[9px] font-mono text-[#FF2D6D]">
                                  You
                                </span>
                              )}
                            </div>
                          </td>
                          <td className="p-3.5 font-mono text-[#A26377]">{member.email}</td>
                          <td className="p-3.5">
                            {isAdmin && !isCurrentUser ? (
                              <select
                                value={member.role}
                                onChange={(e) => handleUpdateRole(member.userId, e.target.value)}
                                disabled={updatingMemberId === member.userId}
                                className="px-2.5 py-1 rounded-lg bg-[#30000F] border border-[#FFB4C8]/20 text-[11px] font-mono text-white outline-none focus:border-[#FF2D6D]"
                              >
                                <option value="OWNER">OWNER</option>
                                <option value="ADMIN">ADMIN</option>
                                <option value="DEVELOPER">DEVELOPER</option>
                                <option value="VIEWER">VIEWER</option>
                              </select>
                            ) : (
                              <span className="px-2.5 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono font-bold text-[#4ADE80] border border-[#4ADE80]/30">
                                {member.role}
                              </span>
                            )}
                          </td>
                          <td className="p-3.5 font-mono text-[10px] text-[#A26377]">
                            {member.createdAt ? new Date(member.createdAt).toLocaleDateString() : 'N/A'}
                          </td>
                          <td className="p-3.5 text-right">
                            <div className="flex items-center justify-end gap-2">
                              {isAdmin && (
                                <button
                                  type="button"
                                  onClick={() => setSelectedMemberForAccess(member)}
                                  className="px-2.5 py-1 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#FFB4C8] hover:text-white text-[10px] font-mono border border-[#FFB4C8]/15 transition-all"
                                >
                                  Access Matrix
                                </button>
                              )}
                              {isAdmin && !isCurrentUser && (
                                <button
                                  type="button"
                                  onClick={() => handleRemoveMember(member.userId, member.email)}
                                  disabled={updatingMemberId === member.userId}
                                  className="p-1.5 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors"
                                  title="Remove Member"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
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
            )}
          </div>
        </div>
      )}

      {/* Tab 4: Invitations */}
      {activeSubTab === 'invitations' && (
        <div className="flex flex-col gap-6">
          {/* Invite Form */}
          {isAdmin && (
            <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-5">
              <div>
                <h2 className="text-base font-headline font-bold text-white">Send In-App Team Invitation</h2>
                <p className="text-xs text-[#A26377]">
                  Invite existing SecretVault users or pre-register new emails with role-based access.
                </p>
              </div>

              {inviteFeedback && (
                <div
                  className={`p-3 rounded-xl text-xs flex items-center gap-2 ${
                    inviteFeedback.type === 'success'
                      ? 'bg-[#00511C]/30 border border-[#4ADE80]/40 text-[#86EFAC]'
                      : 'bg-[#93000A]/30 border border-[#FFB4AB]/40 text-[#FFDAD6]'
                  }`}
                >
                  {inviteFeedback.type === 'success' ? (
                    <CheckCircle2 className="w-4 h-4 text-[#4ADE80]" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-[#FFB4AB]" />
                  )}
                  <span>{inviteFeedback.message}</span>
                </div>
              )}

              <form onSubmit={handleSendInvite} className="flex flex-col gap-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="flex flex-col gap-1.5 md:col-span-2">
                    <label className="text-xs font-mono text-[#F4B5C8]">Target User Email</label>
                    <input
                      type="email"
                      value={lookupEmail}
                      onChange={(e) => setLookupEmail(e.target.value)}
                      placeholder="colleague@company.com"
                      required
                      className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] text-xs text-white outline-none"
                    />
                    {isLookingUp && (
                      <span className="text-[10px] font-mono text-[#A26377] flex items-center gap-1.5">
                        <RefreshCw className="w-3 h-3 animate-spin text-[#FF2D6D]" />
                        Checking directory...
                      </span>
                    )}
                    {lookupResult && (
                      <div className="p-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-[11px] flex items-center justify-between">
                        <span className="text-[#F4B5C8]">
                          {lookupResult.exists ? `Found: ${lookupResult.user?.name || lookupResult.user?.email}` : 'No existing account (User can register to accept)'}
                        </span>
                        {lookupResult.isMember && (
                          <span className="text-[10px] font-mono text-[#FBBF24]">Already a member</span>
                        )}
                        {lookupResult.hasPendingInvitation && (
                          <span className="text-[10px] font-mono text-[#FF2D6D]">Invitation pending</span>
                        )}
                      </div>
                    )}
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-mono text-[#F4B5C8]">Assigned Role</label>
                    <select
                      value={inviteRole}
                      onChange={(e) => setInviteRole(e.target.value)}
                      className="px-3 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                    >
                      <option value="DEVELOPER">DEVELOPER</option>
                      <option value="VIEWER">VIEWER</option>
                      <option value="ADMIN">ADMIN</option>
                      {isOwner && <option value="OWNER">OWNER</option>}
                    </select>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-2">
                  <div className="flex items-center gap-2">
                    <label className="text-xs font-mono text-[#A26377]">Expires In:</label>
                    <select
                      value={inviteExpiresDays}
                      onChange={(e) => setInviteExpiresDays(e.target.value)}
                      className="px-2 py-1 rounded-lg bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                    >
                      <option value={1}>24 Hours</option>
                      <option value={7}>7 Days</option>
                      <option value={14}>14 Days</option>
                      <option value={30}>30 Days</option>
                    </select>
                  </div>

                  <button
                    type="submit"
                    disabled={isSendingInvite || (lookupResult?.isMember)}
                    className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-bold font-mono tracking-wider flex items-center gap-2 shadow-lg shadow-[#FF2D6D]/20 transition-all disabled:opacity-50"
                  >
                    <MailCheck className="w-4 h-4" />
                    <span>{isSendingInvite ? 'Issuing...' : 'Send In-App Invitation'}</span>
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Pending Invitations Table */}
          <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-5">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-headline font-bold text-white">Pending Invitations ({invitations.length})</h3>
                <p className="text-xs text-[#A26377]">Active cryptographic invitations pending recipient acceptance.</p>
              </div>
              <button
                type="button"
                onClick={fetchInvitations}
                className="p-2 rounded-xl bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isLoadingInvitations ? 'animate-spin text-[#FF2D6D]' : ''}`} />
              </button>
            </div>

            {invitations.length === 0 ? (
              <div className="p-8 rounded-2xl bg-[#30000F]/40 border border-[#FFB4C8]/10 text-center text-xs font-mono text-[#A26377]">
                No pending invitations for this workspace.
              </div>
            ) : (
              <div className="overflow-x-auto rounded-2xl border border-[#FFB4C8]/15">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-[#30000F] text-[#A26377] font-mono text-[10px] uppercase tracking-wider border-b border-[#FFB4C8]/15">
                      <th className="p-3.5">Recipient</th>
                      <th className="p-3.5">Assigned Role</th>
                      <th className="p-3.5">Status</th>
                      <th className="p-3.5">Expires</th>
                      <th className="p-3.5 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#FFB4C8]/10 bg-[#1E000A]">
                    {invitations.map((inv) => (
                      <tr key={inv.id} className="hover:bg-[#30000F]/60 transition-colors">
                        <td className="p-3.5 font-mono text-white">{inv.email}</td>
                        <td className="p-3.5 font-mono text-[#F4B5C8]">{inv.role}</td>
                        <td className="p-3.5">
                          <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#FFB4C8] border border-[#FFB4C8]/20">
                            {inv.status}
                          </span>
                        </td>
                        <td className="p-3.5 font-mono text-[10px] text-[#A26377]">
                          {inv.expiresAt ? new Date(inv.expiresAt).toLocaleDateString() : 'N/A'}
                        </td>
                        <td className="p-3.5 text-right">
                          {isAdmin && (
                            <button
                              type="button"
                              onClick={() => handleRevokeInvite(inv.id)}
                              className="px-2.5 py-1 rounded-lg bg-[#3F0016] hover:bg-[#93000A] text-[#F87171] hover:text-white text-[10px] font-mono border border-[#F87171]/30 transition-all"
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
        </div>
      )}

      {/* Tab 5: Security Overview */}
      {activeSubTab === 'security' && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {/* Control 1 */}
            <div className="p-5 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col justify-between gap-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono uppercase text-[#A26377]">Authentication</span>
                <Lock className="w-4 h-4 text-[#FF2D6D]" />
              </div>
              <div>
                <h3 className="text-sm font-headline font-bold text-white">Stateless JWT + MFA</h3>
                <p className="text-xs text-[#A26377] mt-1">
                  HMAC-SHA256 signature verification with rotating refresh tokens and step-up challenges.
                </p>
              </div>
              <span className="text-[10px] font-mono text-[#4ADE80]">Active &amp; Enforced</span>
            </div>

            {/* Control 2 */}
            <div className="p-5 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col justify-between gap-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono uppercase text-[#A26377]">Access Governance</span>
                <ShieldCheck className="w-4 h-4 text-[#4ADE80]" />
              </div>
              <div>
                <h3 className="text-sm font-headline font-bold text-white">JIT Access &amp; Reviews</h3>
                <p className="text-xs text-[#A26377] mt-1">
                  Dual-custody ephemeral elevations and periodic compliance certification campaigns.
                </p>
              </div>
              <button
                type="button"
                onClick={onNavigateToAccess}
                className="text-[11px] font-mono text-[#FF2D6D] hover:underline flex items-center gap-1"
              >
                <span>Open Access Center</span>
                <ChevronRight className="w-3 h-3" />
              </button>
            </div>

            {/* Control 3 */}
            <div className="p-5 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col justify-between gap-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono uppercase text-[#A26377]">Cryptography</span>
                <Sparkles className="w-4 h-4 text-[#FFB4C8]" />
              </div>
              <div>
                <h3 className="text-sm font-headline font-bold text-white">AES-256-GCM Envelope</h3>
                <p className="text-xs text-[#A26377] mt-1">
                  Unique ephemeral DEK and 96-bit nonce per version with AAD context binding.
                </p>
              </div>
              <span className="text-[10px] font-mono text-[#4ADE80]">Hardware Grade</span>
            </div>
          </div>
        </div>
      )}

      {/* Tab 6: Danger Zone */}
      {activeSubTab === 'danger' && (
        <div className="flex flex-col gap-6 max-w-3xl">
          <div className="p-6 rounded-3xl bg-[#93000A]/15 border border-[#FFB4AB]/30 flex flex-col gap-6">
            <div>
              <h2 className="text-base font-headline font-bold text-[#FFDAD6]">Workspace Danger Zone</h2>
              <p className="text-xs text-[#FFB4AB]/80">
                Destructive and boundary-altering actions for this workspace.
              </p>
            </div>

            {/* Action 1: Leave Workspace */}
            <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex flex-col gap-1">
                <span className="text-xs font-headline font-bold text-white">Leave This Workspace</span>
                <p className="text-xs text-[#A26377]">
                  Relinquish your membership in this workspace container. You will lose access to its projects.
                </p>
              </div>
              <button
                type="button"
                onClick={handleLeaveWorkspace}
                className="px-4 py-2 rounded-xl bg-[#3F0016] hover:bg-[#93000A] text-[#F87171] hover:text-white text-xs font-mono font-bold border border-[#F87171]/30 transition-all shrink-0"
              >
                Leave Workspace
              </button>
            </div>

            {/* Action 2: Delete Workspace */}
            <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col sm:flex-row sm:items-center justify-between gap-4 opacity-75">
              <div className="flex flex-col gap-1">
                <span className="text-xs font-headline font-bold text-white">Delete Entire Workspace</span>
                <p className="text-xs text-[#A26377]">
                  Workspace deletion requires cascading multi-tenant resource archival and is strictly governed by Organization Owners.
                </p>
              </div>
              <button
                type="button"
                disabled
                title="Workspace deletion requires multi-resource archival and is intentionally restricted"
                className="px-4 py-2 rounded-xl bg-[#30000F] text-[#A26377] text-xs font-mono font-bold border border-[#FFB4C8]/10 cursor-not-allowed shrink-0"
              >
                Delete Workspace
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Member Access Management Modal */}
      {selectedMemberForAccess && (
        <MemberAccessManagementDialog
          isOpen={Boolean(selectedMemberForAccess)}
          targetMember={selectedMemberForAccess}
          member={selectedMemberForAccess}
          workspaceId={activeWorkspace?.id}
          onClose={() => setSelectedMemberForAccess(null)}
          onAccessUpdated={() => fetchMembers()}
        />
      )}
    </div>
  );
};

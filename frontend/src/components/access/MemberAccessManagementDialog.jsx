import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from '../../context/AuthContext';
import { workspaceApi } from '../../api/workspaces';
import { accessApi } from '../../api/access';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { RoleBadge } from '../common/Badge';
import { Alert } from '../common/Alert';
import { EffectivePermissionInspector } from './EffectivePermissionInspector';
import {
  Shield,
  Layers,
  Key,
  Clock,
  Search,
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Lock,
  Unlock,
  Save,
  RotateCcw,
  Plus,
  Trash2,
  Eye,
  Info,
  Sliders,
  Table as TableIcon,
  ListTree,
  User,
  Sparkles,
  Loader2
} from 'lucide-react';

export const MemberAccessManagementDialog = ({
  isOpen,
  onClose,
  targetMember,
  onAccessUpdated
}) => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;
  const userId = targetMember?.userId || targetMember?.id;

  const [activeTab, setActiveTab] = useState('matrix'); // 'matrix' | 'grants' | 'effective' | 'jit'
  const [viewMode, setViewMode] = useState('tree'); // 'tree' | 'compact'
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Original and modified access state
  const [accessOverview, setAccessOverview] = useState(null);
  const [projectConfigs, setProjectConfigs] = useState({}); // { [projectId]: { role: 'VIEWER' | 'DEVELOPER' | 'ADMIN' | null, environments: { [envId]: 'READ' | 'WRITE' | 'MANAGE' | null } } }
  const [collapsedProjects, setCollapsedProjects] = useState({});
  const [searchQuery, setSearchQuery] = useState('');
  const [filterMode, setFilterMode] = useState('ALL'); // 'ALL' | 'OVERRIDDEN' | 'PROD_ACCESS'

  // Granular Grants form state
  const [isGrantModalOpen, setIsGrantModalOpen] = useState(false);
  const [newGrantScope, setNewGrantScope] = useState('PROJECT');
  const [newGrantProjectId, setNewGrantProjectId] = useState('');
  const [newGrantEnvId, setNewGrantEnvId] = useState('');
  const [newGrantPermission, setNewGrantPermission] = useState('SECRET_READ');
  const [isCreatingGrant, setIsCreatingGrant] = useState(false);

  // Load Member Access Data
  const loadMemberAccess = useCallback(async () => {
    if (!workspaceId || !userId) return;
    setIsLoading(true);
    setError(null);
    try {
      const res = await workspaceApi.getMemberAccess(workspaceId, userId);
      const data = res?.data || res;
      setAccessOverview(data);

      // Initialize local editable state from fetched access
      const initialConfigs = {};
      if (data?.projects) {
        data.projects.forEach((p) => {
          const envMap = {};
          if (p.environments) {
            p.environments.forEach((e) => {
              envMap[e.environmentId] = e.environmentPermission || e.explicitPermissionLevel || null;
            });
          }
          initialConfigs[p.projectId] = {
            role: p.projectRole || p.explicitProjectRole || null,
            environments: envMap,
          };
        });
      }
      setProjectConfigs(initialConfigs);
    } catch (err) {
      console.error('Failed to load member access configuration:', err);
      setError(err.payload?.message || err.response?.data?.message || err.message || 'Failed to load member access configuration.');
      setAccessOverview(null);
    } finally {
      setIsLoading(false);
    }
  }, [workspaceId, userId]);

  useEffect(() => {
    if (isOpen && workspaceId && userId) {
      loadMemberAccess();
      setActiveTab('matrix');
      setError(null);
      setSuccessMsg(null);
    }
  }, [isOpen, workspaceId, userId, loadMemberAccess]);

  // Determine dirty / modified state
  const hasChanges = useMemo(() => {
    if (!accessOverview?.projects) return false;
    for (const p of accessOverview.projects) {
      const current = projectConfigs[p.projectId];
      if (!current) continue;
      const originalProjRole = p.projectRole || p.explicitProjectRole || null;
      if ((current.role || null) !== originalProjRole) return true;

      for (const e of p.environments || []) {
        const currentEnvPerm = current.environments?.[e.environmentId] || null;
        const originalEnvPerm = e.environmentPermission || e.explicitPermissionLevel || null;
        if (currentEnvPerm !== originalEnvPerm) return true;
      }
    }
    return false;
  }, [accessOverview, projectConfigs]);

  // Handlers for Project & Environment Role Changes
  const handleProjectRoleChange = (projectId, newRoleValue) => {
    const role = newRoleValue === 'INHERIT' ? null : newRoleValue;
    setProjectConfigs((prev) => ({
      ...prev,
      [projectId]: {
        ...prev[projectId],
        role,
      },
    }));
  };

  const handleEnvironmentPermChange = (projectId, envId, newPermValue) => {
    const perm = newPermValue === 'INHERIT' ? null : newPermValue;
    setProjectConfigs((prev) => ({
      ...prev,
      [projectId]: {
        ...prev[projectId],
        environments: {
          ...prev[projectId]?.environments,
          [envId]: perm,
        },
      },
    }));
  };

  const toggleProjectCollapse = (projectId) => {
    setCollapsedProjects((prev) => ({
      ...prev,
      [projectId]: !prev[projectId],
    }));
  };

  const handleReset = () => {
    if (!accessOverview?.projects) return;
    const initialConfigs = {};
    accessOverview.projects.forEach((p) => {
      const envMap = {};
      if (p.environments) {
        p.environments.forEach((e) => {
          envMap[e.environmentId] = e.environmentPermission || e.explicitPermissionLevel || null;
        });
      }
      initialConfigs[p.projectId] = {
        role: p.projectRole || p.explicitProjectRole || null,
        environments: envMap,
      };
    });
    setProjectConfigs(initialConfigs);
    setError(null);
    setSuccessMsg(null);
  };

  const handleSaveChanges = async () => {
    if (!workspaceId || !userId) return;
    setIsSaving(true);
    setError(null);
    setSuccessMsg(null);

    try {
      // Format payload for backend batch update
      const formattedProjectConfigs = Object.entries(projectConfigs).map(([projId, config]) => {
        const envConfigs = Object.entries(config.environments || {})
          .map(([envId, perm]) => ({
            environmentId: envId,
            permissionLevel: perm === 'INHERIT' ? null : (perm || null),
          }));

        return {
          projectId: projId,
          role: config.role === 'INHERIT' ? null : (config.role || null),
          environmentConfigs: envConfigs,
        };
      });

      const payload = {
        projectConfigs: formattedProjectConfigs,
      };

      const updated = await workspaceApi.updateMemberAccess(workspaceId, userId, payload);
      setSuccessMsg('Access permissions updated successfully!');
      
      // Update data state
      const data = updated?.data || updated;
      setAccessOverview(data);

      const initialConfigs = {};
      if (data?.projects) {
        data.projects.forEach((p) => {
          const envMap = {};
          if (p.environments) {
            p.environments.forEach((e) => {
              envMap[e.environmentId] = e.environmentPermission || e.explicitPermissionLevel || null;
            });
          }
          initialConfigs[p.projectId] = {
            role: p.projectRole || p.explicitProjectRole || null,
            environments: envMap,
          };
        });
      }
      setProjectConfigs(initialConfigs);

      if (onAccessUpdated) {
        onAccessUpdated(data);
      }
    } catch (err) {
      console.error('Failed to save access changes:', err);
      setError(err.payload?.message || err.response?.data?.message || err.message || 'Failed to save access changes.');
    } finally {
      setIsSaving(false);
    }
  };

  // Compute live local effective role preview
  const getComputedEffectiveProjectRole = (proj) => {
    const wsRole = accessOverview?.member?.workspaceRole || targetMember?.role || 'DEVELOPER';
    const currentExplicitRole = projectConfigs[proj.projectId]?.role;
    if (!currentExplicitRole || currentExplicitRole === 'INHERIT') return wsRole;
    
    // Effective is min(workspaceRole, explicitRole)
    const roleRank = { VIEWER: 1, DEVELOPER: 2, ADMIN: 3, OWNER: 4 };
    const wsRank = roleRank[wsRole] || 1;
    const projRank = roleRank[currentExplicitRole] || 1;
    return projRank < wsRank ? currentExplicitRole : wsRole;
  };

  const getComputedEffectiveEnvPermission = (proj, env) => {
    const effectiveProjRole = getComputedEffectiveProjectRole(proj);
    const currentExplicitEnvPerm = projectConfigs[proj.projectId]?.environments?.[env.environmentId];

    if (effectiveProjRole === 'VIEWER') {
      return 'READ';
    }
    if (effectiveProjRole === 'DEVELOPER') {
      if (currentExplicitEnvPerm === 'MANAGE') return 'WRITE';
      return currentExplicitEnvPerm || 'WRITE';
    }
    return currentExplicitEnvPerm || 'MANAGE';
  };

  // Filter projects
  const filteredProjects = useMemo(() => {
    if (!accessOverview?.projects) return [];
    return accessOverview.projects.filter((p) => {
      const matchesSearch =
        p.projectName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        p.projectSlug.toLowerCase().includes(searchQuery.toLowerCase());
      if (!matchesSearch) return false;

      if (filterMode === 'OVERRIDDEN') {
        const config = projectConfigs[p.projectId];
        const hasProjOverride = Boolean(config?.role);
        const hasEnvOverride = Object.values(config?.environments || {}).some((perm) => perm !== null);
        return hasProjOverride || hasEnvOverride;
      }
      if (filterMode === 'PROD_ACCESS') {
        return p.environments?.some((e) => e.isProtected || e.envType === 'PRODUCTION');
      }
      return true;
    });
  }, [accessOverview, searchQuery, filterMode, projectConfigs]);

  // Handle Granular Grant Creation
  const handleCreateGranularGrant = async (e) => {
    e.preventDefault();
    if (!workspaceId || !userId) return;
    setIsCreatingGrant(true);
    setError(null);
    try {
      const payload = {
        userId,
        scope: newGrantScope,
        projectId: newGrantScope !== 'WORKSPACE' ? newGrantProjectId : null,
        environmentId: newGrantScope === 'ENVIRONMENT' || newGrantScope === 'SECRET' ? newGrantEnvId : null,
        permission: newGrantPermission,
      };
      await accessApi.createGrant(workspaceId, payload);
      setSuccessMsg(`Granular grant for ${newGrantPermission} created successfully.`);
      setIsGrantModalOpen(false);
      loadMemberAccess();
    } catch (err) {
      setError(err.payload?.message || err.response?.data?.message || err.message || 'Failed to create granular grant.');
    } finally {
      setIsCreatingGrant(false);
    }
  };

  const handleRevokeGranularGrant = async (grantId, permName) => {
    if (!window.confirm(`Revoke granular grant for ${permName}?`)) return;
    try {
      await accessApi.revokeGrant(workspaceId, grantId);
      setSuccessMsg(`Grant for ${permName} has been revoked.`);
      loadMemberAccess();
    } catch (err) {
      setError(err.payload?.message || err.response?.data?.message || err.message || 'Failed to revoke grant.');
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Member Access & Governance Management"
      description="Configure scoped project, environment, and granular permissions without altering global workspace identity."
      maxWidth="3xl"
    >
      <div className="flex flex-col gap-5 font-body text-white">
        {/* Target Member Profile Header Card */}
        <div className="p-4 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shadow-xl">
          <div className="flex items-center gap-3.5">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#FF2D6D] to-[#990033] border border-[#FF85A2]/30 flex items-center justify-center font-bold text-white text-base shadow-md">
              {targetMember?.fullName
                ? targetMember.fullName.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase()
                : targetMember?.email?.slice(0, 2).toUpperCase() || 'U'}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold font-headline text-white">
                  {targetMember?.fullName || 'Team Member'}
                </h3>
                <RoleBadge role={accessOverview?.member?.workspaceRole || targetMember?.role || 'DEVELOPER'} />
              </div>
              <p className="text-xs text-[#F4B5C8]/70 font-mono mt-0.5">
                {targetMember?.email || 'No email specified'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 self-stretch sm:self-auto justify-end">
            {hasChanges && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-[#FFD600]/15 text-[#FFD600] border border-[#FFD600]/30 animate-pulse">
                <AlertTriangle className="w-3.5 h-3.5" />
                <span>Unsaved Changes</span>
              </span>
            )}
            <Button
              variant="secondary"
              size="sm"
              onClick={handleReset}
              disabled={!hasChanges || isSaving}
              className="text-xs"
            >
              <RotateCcw className="w-3.5 h-3.5 mr-1" />
              Reset
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={handleSaveChanges}
              disabled={!hasChanges || isSaving}
              isLoading={isSaving}
              className="text-xs shadow-lg shadow-[#FF2D6D]/20"
            >
              <Save className="w-3.5 h-3.5 mr-1" />
              Save Changes
            </Button>
          </div>
        </div>

        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}
        {successMsg && <Alert variant="success" message={successMsg} onDismiss={() => setSuccessMsg(null)} />}

        {/* Tab Navigation */}
        <div className="flex items-center gap-2 p-1 rounded-xl bg-[#28000C] border border-[#FFB4C8]/15 text-xs font-semibold">
          <button
            onClick={() => setActiveTab('matrix')}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
              activeTab === 'matrix'
                ? 'bg-[#FF2D6D] text-white shadow-md'
                : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#3F0016]'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>Project & Environment Matrix</span>
          </button>

          <button
            onClick={() => setActiveTab('grants')}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
              activeTab === 'grants'
                ? 'bg-[#FF2D6D] text-white shadow-md'
                : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#3F0016]'
            }`}
          >
            <Key className="w-3.5 h-3.5" />
            <span>Granular Grants ({accessOverview?.granularGrants?.length || 0})</span>
          </button>

          <button
            onClick={() => setActiveTab('effective')}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
              activeTab === 'effective'
                ? 'bg-[#FF2D6D] text-white shadow-md'
                : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#3F0016]'
            }`}
          >
            <Shield className="w-3.5 h-3.5" />
            <span>Effective Access & Why-Access</span>
          </button>

          <button
            onClick={() => setActiveTab('jit')}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
              activeTab === 'jit'
                ? 'bg-[#FF2D6D] text-white shadow-md'
                : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#3F0016]'
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
            <span>JIT Activity ({accessOverview?.activeJitGrants?.length || 0})</span>
          </button>
        </div>

        {/* Tab 1: Project & Environment Matrix */}
        {activeTab === 'matrix' && (
          <div className="space-y-4">
            {/* Filter and View Mode Controls */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="relative flex-1 w-full">
                <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-[#F4B5C8]/50" />
                <input
                  type="text"
                  placeholder="Filter projects by name or slug..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full pl-9.5 pr-4 py-2 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/20 text-xs text-white placeholder:text-[#F4B5C8]/40 focus:outline-none focus:border-[#FF2D6D]"
                />
              </div>

              <div className="flex items-center gap-2 self-stretch sm:self-auto">
                <div className="flex items-center bg-[#1C000A] border border-[#FFB4C8]/20 rounded-xl p-0.5 text-xs">
                  <button
                    onClick={() => setFilterMode('ALL')}
                    className={`px-2.5 py-1 rounded-lg transition-all ${
                      filterMode === 'ALL' ? 'bg-[#FF2D6D] text-white font-semibold' : 'text-[#F4B5C8]/70 hover:text-white'
                    }`}
                  >
                    All ({accessOverview?.projects?.length || 0})
                  </button>
                  <button
                    onClick={() => setFilterMode('OVERRIDDEN')}
                    className={`px-2.5 py-1 rounded-lg transition-all ${
                      filterMode === 'OVERRIDDEN' ? 'bg-[#FF2D6D] text-white font-semibold' : 'text-[#F4B5C8]/70 hover:text-white'
                    }`}
                  >
                    Overridden
                  </button>
                  <button
                    onClick={() => setFilterMode('PROD_ACCESS')}
                    className={`px-2.5 py-1 rounded-lg transition-all ${
                      filterMode === 'PROD_ACCESS' ? 'bg-[#FF2D6D] text-white font-semibold' : 'text-[#F4B5C8]/70 hover:text-white'
                    }`}
                  >
                    Production
                  </button>
                </div>

                <div className="flex items-center bg-[#1C000A] border border-[#FFB4C8]/20 rounded-xl p-0.5 text-xs">
                  <button
                    onClick={() => setViewMode('tree')}
                    title="Tree Accordion View"
                    className={`p-1.5 rounded-lg transition-all ${
                      viewMode === 'tree' ? 'bg-[#3F0016] text-[#FF85A2]' : 'text-[#F4B5C8]/50 hover:text-white'
                    }`}
                  >
                    <ListTree className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => setViewMode('compact')}
                    title="Compact Table View"
                    className={`p-1.5 rounded-lg transition-all ${
                      viewMode === 'compact' ? 'bg-[#3F0016] text-[#FF85A2]' : 'text-[#F4B5C8]/50 hover:text-white'
                    }`}
                  >
                    <TableIcon className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>

            {isLoading ? (
              <div className="p-12 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 flex flex-col items-center justify-center gap-3">
                <Loader2 className="w-7 h-7 text-[#FF2D6D] animate-spin" />
                <span className="text-xs text-[#F4B5C8]/70 font-mono">Loading access matrix hierarchy...</span>
              </div>
            ) : error && !accessOverview ? (
              <div className="p-10 rounded-2xl bg-[#1C000A] border border-[#FF2D6D]/30 text-center space-y-3">
                <AlertTriangle className="w-8 h-8 text-[#FF2D6D] mx-auto" />
                <p className="text-sm font-semibold text-white">Failed to load member access matrix</p>
                <p className="text-xs text-[#F4B5C8]/70 max-w-md mx-auto">{error}</p>
                <Button variant="secondary" size="sm" onClick={loadMemberAccess} className="text-xs mx-auto">
                  <RotateCcw className="w-3.5 h-3.5 mr-1" />
                  Retry Loading
                </Button>
              </div>
            ) : filteredProjects.length === 0 ? (
              <div className="p-10 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center space-y-2">
                <Layers className="w-8 h-8 text-[#FF85A2]/40 mx-auto" />
                <p className="text-sm font-semibold text-white">No projects found</p>
                <p className="text-xs text-[#F4B5C8]/60">
                  {searchQuery || filterMode !== 'ALL'
                    ? 'Try adjusting your search query or filter.'
                    : 'Create projects in this workspace to configure access.'}
                </p>
              </div>
            ) : viewMode === 'tree' ? (
              /* Tree Accordion Matrix View */
              <div className="space-y-3 max-h-[500px] overflow-y-auto pr-1">
                {filteredProjects.map((proj) => {
                  const currentRole = projectConfigs[proj.projectId]?.role || 'INHERIT';
                  const effectiveRole = getComputedEffectiveProjectRole(proj);
                  const isOverridden = currentRole !== 'INHERIT';

                  return (
                    <div
                      key={proj.projectId}
                      className={`rounded-2xl border transition-all overflow-hidden ${
                        isOverridden
                          ? 'bg-[#20000C] border-[#FF2D6D]/40 shadow-lg shadow-[#FF2D6D]/5'
                          : 'bg-[#1C000A] border-[#FFB4C8]/15'
                      }`}
                    >
                      {/* Project Row Header */}
                      <div className="p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-[#26000F]/60">
                        <div
                          className="flex items-center gap-3 cursor-pointer select-none flex-1"
                          onClick={() => toggleProjectCollapse(proj.projectId)}
                        >
                          <button
                            type="button"
                            className="p-1 rounded-lg text-[#F4B5C8]/70 hover:text-white hover:bg-[#3F0016]"
                          >
                            {collapsedProjects[proj.projectId] ? (
                              <ChevronRight className="w-4 h-4" />
                            ) : (
                              <ChevronDown className="w-4 h-4" />
                            )}
                          </button>

                          <div className="w-8 h-8 rounded-xl bg-[#3E0018] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
                            <Layers className="w-4 h-4" />
                          </div>

                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-bold font-headline text-white">
                                {proj.projectName}
                              </span>
                              <span className="text-[11px] font-mono text-[#F4B5C8]/50">
                                /{proj.projectSlug}
                              </span>
                            </div>
                            <div className="flex items-center gap-2 mt-0.5 text-xs text-[#F4B5C8]/70">
                              <span>Effective Project Role:</span>
                              <span className="font-semibold text-[#FF85A2]">{effectiveRole}</span>
                              {isOverridden && (
                                <span className="text-[10px] px-1.5 py-0.2 rounded bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30">
                                  Explicit Override
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        {/* Project Role Selector Dropdown */}
                        <div className="flex items-center gap-2 self-end sm:self-auto">
                          <span className="text-xs text-[#F4B5C8]/70">Project Access:</span>
                          <select
                            value={currentRole}
                            onChange={(e) => handleProjectRoleChange(proj.projectId, e.target.value)}
                            className="px-3 py-1.5 rounded-xl bg-[#140007] border border-[#FFB4C8]/30 text-xs text-white font-semibold focus:outline-none focus:border-[#FF2D6D]"
                          >
                            <option value="INHERIT">Inherit Workspace ({accessOverview?.member?.workspaceRole || targetMember?.role})</option>
                            <option value="VIEWER">VIEWER (READ Only)</option>
                            <option value="DEVELOPER">DEVELOPER (READ / WRITE)</option>
                            <option value="ADMIN">ADMIN (MANAGE)</option>
                          </select>
                        </div>
                      </div>

                      {/* Nested Environments Container */}
                      {!collapsedProjects[proj.projectId] && (
                        <div className="p-4 bg-[#140007]/80 border-t border-[#FFB4C8]/10 space-y-2.5">
                          <div className="text-[11px] font-mono uppercase tracking-wider text-[#F4B5C8]/50 px-2 flex justify-between">
                            <span>Environment Hierarchy</span>
                            <span>Scoped Permission</span>
                          </div>

                          {proj.environments?.map((env) => {
                            const currentEnvPerm = projectConfigs[proj.projectId]?.environments?.[env.environmentId] || 'INHERIT';
                            const effectiveEnvPerm = getComputedEffectiveEnvPermission(proj, env);
                            const isEnvOverridden = currentEnvPerm !== 'INHERIT';
                            const isProtected = env.isProtected || env.isProduction || env.envType === 'PRODUCTION';

                            return (
                              <div
                                key={env.environmentId}
                                className={`p-3 rounded-xl border flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 transition-all ${
                                  isEnvOverridden
                                    ? 'bg-[#2A0012]/80 border-[#FF2D6D]/30'
                                    : 'bg-[#1C000A]/60 border-[#FFB4C8]/10'
                                }`}
                              >
                                <div className="flex items-center gap-2.5">
                                  <div className={`p-1.5 rounded-lg border ${
                                    isProtected
                                      ? 'bg-[#D50000]/15 border-[#D50000]/30 text-[#FF5252]'
                                      : 'bg-[#00C853]/15 border-[#00C853]/30 text-[#00E676]'
                                  }`}>
                                    {isProtected ? (
                                      <Lock className="w-3.5 h-3.5" />
                                    ) : (
                                      <Unlock className="w-3.5 h-3.5" />
                                    )}
                                  </div>

                                  <div>
                                    <div className="flex items-center gap-2">
                                      <span className="text-xs font-semibold text-white">
                                        {env.name}
                                      </span>
                                      <span className={`text-[10px] px-2 py-0.5 rounded font-mono font-medium ${
                                        env.envType === 'PRODUCTION'
                                          ? 'bg-[#D50000]/20 text-[#FF5252] border border-[#D50000]/30'
                                          : env.envType === 'STAGING'
                                          ? 'bg-[#FFD600]/20 text-[#FFD600] border border-[#FFD600]/30'
                                          : 'bg-[#00E676]/20 text-[#00E676] border border-[#00E676]/30'
                                      }`}>
                                        {env.envType}
                                      </span>
                                      {isProtected && (
                                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-[#FF2D6D]/20 text-[#FF85A2] font-mono">
                                          Protected
                                        </span>
                                      )}
                                    </div>
                                    <div className="text-[11px] text-[#F4B5C8]/60 mt-0.5">
                                      Effective: <span className="font-semibold text-white">{effectiveEnvPerm}</span>
                                    </div>
                                  </div>
                                </div>

                                {/* Environment Permission Selector */}
                                <div className="flex items-center gap-2 self-end sm:self-auto">
                                  <select
                                    value={currentEnvPerm}
                                    onChange={(e) => handleEnvironmentPermChange(proj.projectId, env.environmentId, e.target.value)}
                                    className="px-2.5 py-1 rounded-lg bg-[#20000C] border border-[#FFB4C8]/25 text-xs text-white font-medium focus:outline-none focus:border-[#FF2D6D]"
                                  >
                                    <option value="INHERIT">Inherit Project ({effectiveRole})</option>
                                    <option value="READ">READ (Metadata Only)</option>
                                    <option value="WRITE">WRITE (Read + Create + Update)</option>
                                    <option value="MANAGE">MANAGE (Administer Environment)</option>
                                  </select>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              /* Compact Matrix Table View */
              <div className="rounded-2xl border border-[#FFB4C8]/15 overflow-x-auto bg-[#1C000A]">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-[#28000C] border-b border-[#FFB4C8]/15 text-[#F4B5C8]/70 font-mono text-[11px] uppercase">
                      <th className="p-3">Project</th>
                      <th className="p-3">Project Role</th>
                      <th className="p-3">Environments & Effective Access</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#FFB4C8]/10">
                    {filteredProjects.map((proj) => {
                      const currentRole = projectConfigs[proj.projectId]?.role || 'INHERIT';
                      const effectiveRole = getComputedEffectiveProjectRole(proj);

                      return (
                        <tr key={proj.projectId} className="hover:bg-[#2C0012]/40 transition-colors">
                          <td className="p-3 align-top font-semibold text-white">
                            <div>{proj.projectName}</div>
                            <div className="text-[10px] font-mono text-[#F4B5C8]/50">/{proj.projectSlug}</div>
                          </td>
                          <td className="p-3 align-top">
                            <select
                              value={currentRole}
                              onChange={(e) => handleProjectRoleChange(proj.projectId, e.target.value)}
                              className="px-2 py-1 rounded-lg bg-[#20000C] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D]"
                            >
                              <option value="INHERIT">Inherit ({effectiveRole})</option>
                              <option value="VIEWER">VIEWER</option>
                              <option value="DEVELOPER">DEVELOPER</option>
                              <option value="ADMIN">ADMIN</option>
                            </select>
                          </td>
                          <td className="p-3 space-y-1.5">
                            {proj.environments?.map((env) => {
                              const currentEnvPerm = projectConfigs[proj.projectId]?.environments?.[env.environmentId] || 'INHERIT';
                              const effectiveEnvPerm = getComputedEffectiveEnvPermission(proj, env);

                              return (
                                <div key={env.environmentId} className="flex items-center justify-between gap-3 text-[11px]">
                                  <span className="font-mono text-[#F4B5C8]/80">{env.name} ({env.envType}):</span>
                                  <div className="flex items-center gap-2">
                                    <span className="px-1.5 py-0.5 rounded bg-[#3F0016] text-[#FF85A2] font-semibold text-[10px]">
                                      {effectiveEnvPerm}
                                    </span>
                                    <select
                                      value={currentEnvPerm}
                                      onChange={(e) => handleEnvironmentPermChange(proj.projectId, env.environmentId, e.target.value)}
                                      className="px-2 py-0.5 rounded bg-[#20000C] border border-[#FFB4C8]/20 text-[10px] text-white"
                                    >
                                      <option value="INHERIT">Inherit</option>
                                      <option value="READ">READ</option>
                                      <option value="WRITE">WRITE</option>
                                      <option value="MANAGE">MANAGE</option>
                                    </select>
                                  </div>
                                </div>
                              );
                            })}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Granular Access Grants */}
        {activeTab === 'grants' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3 p-4 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15">
              <div>
                <h4 className="text-sm font-bold text-white font-headline">Explicit Granular Grants</h4>
                <p className="text-xs text-[#F4B5C8]/70 mt-0.5">
                  Point-to-point permissions assigned directly to this user on specific resources.
                </p>
              </div>
              <Button
                variant="primary"
                size="sm"
                onClick={() => setIsGrantModalOpen(true)}
                className="text-xs"
              >
                <Plus className="w-3.5 h-3.5 mr-1" />
                Add Granular Grant
              </Button>
            </div>

            {/* Grant Creation Sub-Modal / Form */}
            {isGrantModalOpen && (
              <form onSubmit={handleCreateGranularGrant} className="p-4 rounded-2xl bg-[#26000F] border border-[#FF2D6D]/40 space-y-4 shadow-xl">
                <div className="flex items-center justify-between pb-2 border-b border-[#FFB4C8]/10">
                  <span className="text-xs font-bold font-headline text-white">Create New Granular Grant</span>
                  <button
                    type="button"
                    onClick={() => setIsGrantModalOpen(false)}
                    className="text-xs text-[#F4B5C8]/70 hover:text-white"
                  >
                    Cancel
                  </button>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                  <div>
                    <label className="block text-[#F4B5C8]/70 mb-1 font-mono uppercase text-[10px]">Scope</label>
                    <select
                      value={newGrantScope}
                      onChange={(e) => setNewGrantScope(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/25 text-white"
                    >
                      <option value="WORKSPACE">Workspace Scope</option>
                      <option value="PROJECT">Project Scope</option>
                      <option value="ENVIRONMENT">Environment Scope</option>
                    </select>
                  </div>

                  {newGrantScope !== 'WORKSPACE' && (
                    <div>
                      <label className="block text-[#F4B5C8]/70 mb-1 font-mono uppercase text-[10px]">Project Target</label>
                      <select
                        value={newGrantProjectId}
                        onChange={(e) => {
                          setNewGrantProjectId(e.target.value);
                          const proj = accessOverview?.projects?.find((p) => p.projectId === e.target.value);
                          if (proj?.environments?.length) {
                            setNewGrantEnvId(proj.environments[0].environmentId);
                          }
                        }}
                        className="w-full px-3 py-2 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/25 text-white"
                        required
                      >
                        <option value="">Select Project</option>
                        {accessOverview?.projects?.map((p) => (
                          <option key={p.projectId} value={p.projectId}>{p.projectName}</option>
                        ))}
                      </select>
                    </div>
                  )}

                  {newGrantScope === 'ENVIRONMENT' && (
                    <div>
                      <label className="block text-[#F4B5C8]/70 mb-1 font-mono uppercase text-[10px]">Environment Target</label>
                      <select
                        value={newGrantEnvId}
                        onChange={(e) => setNewGrantEnvId(e.target.value)}
                        className="w-full px-3 py-2 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/25 text-white"
                        required
                      >
                        <option value="">Select Environment</option>
                        {accessOverview?.projects
                          ?.find((p) => p.projectId === newGrantProjectId)
                          ?.environments?.map((e) => (
                            <option key={e.environmentId} value={e.environmentId}>{e.name} ({e.envType})</option>
                          ))}
                      </select>
                    </div>
                  )}

                  <div className={newGrantScope === 'WORKSPACE' ? 'sm:col-span-2' : ''}>
                    <label className="block text-[#F4B5C8]/70 mb-1 font-mono uppercase text-[10px]">Permission</label>
                    <select
                      value={newGrantPermission}
                      onChange={(e) => setNewGrantPermission(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/25 text-white"
                    >
                      <option value="SECRET_READ">SECRET_READ (View metadata)</option>
                      <option value="SECRET_REVEAL">SECRET_REVEAL (Decrypt plaintext)</option>
                      <option value="SECRET_CREATE">SECRET_CREATE (Add new secrets)</option>
                      <option value="SECRET_UPDATE">SECRET_UPDATE (Rotate / modify)</option>
                      <option value="SECRET_DELETE">SECRET_DELETE (Soft delete)</option>
                      <option value="SECRET_ROLLBACK">SECRET_ROLLBACK (Version restore)</option>
                      <option value="SECRET_BRANCH">SECRET_BRANCH (Feature branching)</option>
                      <option value="ENVIRONMENT_PROMOTE">ENVIRONMENT_PROMOTE (Cross-env promotion)</option>
                      <option value="ENVIRONMENT_MANAGE">ENVIRONMENT_MANAGE (Env config)</option>
                    </select>
                  </div>
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    isLoading={isCreatingGrant}
                    className="text-xs"
                  >
                    Authorize & Create Grant
                  </Button>
                </div>
              </form>
            )}

            {/* Existing Grants List */}
            {accessOverview?.granularGrants?.length === 0 ? (
              <div className="p-8 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center space-y-1 text-xs text-[#F4B5C8]/60">
                <Key className="w-6 h-6 text-[#FF85A2]/30 mx-auto" />
                <p>No explicit granular access grants assigned.</p>
              </div>
            ) : (
              <div className="space-y-2 max-h-[350px] overflow-y-auto pr-1">
                {accessOverview?.granularGrants?.map((grant) => (
                  <div
                    key={grant.id}
                    className="p-3 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 flex items-center justify-between gap-3 text-xs"
                  >
                    <div className="flex items-center gap-3">
                      <div className="p-2 rounded-lg bg-[#00C853]/10 border border-[#00C853]/30 text-[#00E676]">
                        <Key className="w-3.5 h-3.5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-white font-mono">{grant.permissionCode || grant.permission}</span>
                          <span className="text-[10px] px-2 py-0.5 rounded bg-[#2C0012] text-[#FF85A2] font-mono border border-[#FFB4C8]/20">
                            {grant.scope || grant.scopeType}
                          </span>
                        </div>
                        <div className="text-[11px] text-[#F4B5C8]/60 mt-0.5">
                          {grant.projectName && <span>Project: {grant.projectName} </span>}
                          {grant.environmentName && <span>• Env: {grant.environmentName} </span>}
                          {(grant.secretKey || grant.secretName) && <span>• Secret: {grant.secretKey || grant.secretName}</span>}
                        </div>
                      </div>
                    </div>

                    <button
                      onClick={() => handleRevokeGranularGrant(grant.id, grant.permissionCode || grant.permission)}
                      className="p-1.5 rounded-lg text-[#FF5252] hover:bg-[#D50000]/20 transition-colors"
                      title="Revoke Grant"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Effective Access & Why-Access Lineage */}
        {activeTab === 'effective' && (
          <div className="space-y-4">
            <div className="p-4 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 flex items-center gap-3">
              <div className="p-2 rounded-xl bg-[#3E0018] text-[#FF2D6D] border border-[#FF2D6D]/30">
                <Shield className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-white font-headline">
                  Live Effective Authorization Preview
                </h4>
                <p className="text-xs text-[#F4B5C8]/70">
                  Evaluated in real-time by the backend <span className="font-mono text-white">EffectiveAccessService</span> combining Workspace Role, Project Access, Environment Access, Granular Grants, and Active JIT.
                </p>
              </div>
            </div>

            {/* Embedded Live Inspector scoped to target member */}
            <EffectivePermissionInspector workspaceId={workspaceId} userId={userId} />
          </div>
        )}

        {/* Tab 4: Active JIT Grants */}
        {activeTab === 'jit' && (
          <div className="space-y-4">
            <div className="p-4 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 flex items-center gap-3">
              <div className="p-2 rounded-xl bg-[#FFD600]/15 text-[#FFD600] border border-[#FFD600]/30">
                <Clock className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-white font-headline">Just-In-Time (JIT) Temporary Access</h4>
                <p className="text-xs text-[#F4B5C8]/70">
                  Temporary elevated access grants with automatic expiration.
                </p>
              </div>
            </div>

            {accessOverview?.activeJitGrants?.length === 0 ? (
              <div className="p-8 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center space-y-1 text-xs text-[#F4B5C8]/60">
                <Clock className="w-6 h-6 text-[#FFD600]/30 mx-auto" />
                <p>No active JIT temporary grants currently open for this member.</p>
              </div>
            ) : (
              <div className="space-y-2 max-h-[350px] overflow-y-auto pr-1">
                {accessOverview?.activeJitGrants?.map((jit) => (
                  <div
                    key={jit.id}
                    className="p-4 rounded-xl bg-[#1C000A] border border-[#FFD600]/30 flex items-center justify-between gap-3 text-xs"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-[#FFD600] font-mono">{jit.permissionCode || jit.requestedPermission || jit.permission}</span>
                        <span className="text-[10px] px-2 py-0.5 rounded bg-[#FFD600]/20 text-[#FFD600] font-mono">
                          ACTIVE JIT
                        </span>
                      </div>
                      <p className="text-xs text-white">Reason: {jit.reason || jit.justification || 'Emergency elevation'}</p>
                      <p className="text-[11px] text-[#F4B5C8]/60 font-mono">
                        Expires: {new Date(jit.expiresAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
};

import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { projectApi } from '../../api/projects';
import { environmentApi } from '../../api/environments';
import { workspaceApi } from '../../api/workspaces';
import { MemberAccessManagementDialog } from '../access/MemberAccessManagementDialog';
import {
  FolderGit2,
  Layers,
  Users,
  Shield,
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
  Save,
  Trash2,
  Plus,
  Copy,
  Check,
  AlertCircle,
  Key,
  ExternalLink,
  ChevronDown,
  Edit2,
  Lock,
  Sparkles,
} from 'lucide-react';

export const ProjectSettingsView = ({ initialProjectId, onNavigateToSecrets, onNavigateToAccess }) => {
  const { activeWorkspace } = useAuth();

  const [projects, setProjects] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState(initialProjectId || null);
  const [selectedProject, setSelectedProject] = useState(null);
  const [activeSubTab, setActiveSubTab] = useState('general'); // 'general' | 'environments' | 'access' | 'security' | 'danger'
  
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // General Form
  const [projectName, setProjectName] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [projectStatus, setProjectStatus] = useState('ACTIVE');

  // Environments State
  const [environments, setEnvironments] = useState([]);
  const [isLoadingEnvs, setIsLoadingEnvs] = useState(false);
  const [isAddEnvModalOpen, setIsAddEnvModalOpen] = useState(false);
  const [editingEnv, setEditingEnv] = useState(null);
  const [envForm, setEnvForm] = useState({
    name: '',
    slug: '',
    envType: 'DEVELOPMENT',
    description: '',
    isProtected: false,
  });

  // Project Access Members State
  const [projectMembers, setProjectMembers] = useState([]);
  const [workspaceMembers, setWorkspaceMembers] = useState([]);
  const [isLoadingAccess, setIsLoadingAccess] = useState(false);
  const [isAddAccessModalOpen, setIsAddAccessModalOpen] = useState(false);
  const [selectedMemberForAccessMatrix, setSelectedMemberForAccessMatrix] = useState(null);
  const [accessForm, setAccessForm] = useState({
    userId: '',
    role: 'DEVELOPER',
  });

  // Danger Zone
  const [confirmDeleteInput, setConfirmDeleteInput] = useState('');
  const [isDeletingProject, setIsDeletingProject] = useState(false);

  // Copy state
  const [copiedField, setCopiedField] = useState(null);

  const isOwner = activeWorkspace?.role === 'OWNER';
  const isAdmin = activeWorkspace?.role === 'ADMIN' || isOwner;

  // 1. Fetch Projects List
  const fetchProjects = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsLoading(true);
    try {
      const data = await projectApi.list(activeWorkspace.id);
      setProjects(data || []);
      if (data && data.length > 0) {
        const found = data.find((p) => p.id === selectedProjectId) || data[0];
        setSelectedProjectId(found.id);
        setSelectedProject(found);
      }
    } catch (err) {
      setError(err.message || 'Failed to load workspace projects.');
    } finally {
      setIsLoading(false);
    }
  }, [activeWorkspace?.id, selectedProjectId]);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  // 2. Load Project Details when selection changes
  useEffect(() => {
    if (selectedProject) {
      setProjectName(selectedProject.name || '');
      setProjectDescription(selectedProject.description || '');
      setProjectStatus(selectedProject.status || 'ACTIVE');
      setEnvironments(selectedProject.environments || []);
    }
  }, [selectedProject]);

  const loadProjectDetails = useCallback(async (projId) => {
    if (!activeWorkspace?.id || !projId) return;
    try {
      const data = await projectApi.getById(activeWorkspace.id, projId);
      setSelectedProject(data);
      setEnvironments(data.environments || []);
    } catch (err) {
      setError(err.message || 'Failed to load project details.');
    }
  }, [activeWorkspace?.id]);

  // 3. Load Environments
  const fetchEnvironments = useCallback(async () => {
    if (!activeWorkspace?.id || !selectedProjectId) return;
    setIsLoadingEnvs(true);
    try {
      const data = await environmentApi.list(activeWorkspace.id, selectedProjectId);
      setEnvironments(data || []);
    } catch (err) {
      // fallback to project summary envs
    } finally {
      setIsLoadingEnvs(false);
    }
  }, [activeWorkspace?.id, selectedProjectId]);

  // 4. Load Project Members
  const fetchProjectMembers = useCallback(async () => {
    if (!activeWorkspace?.id || !selectedProjectId) return;
    setIsLoadingAccess(true);
    try {
      const [membersData, allWsMembers] = await Promise.all([
        projectApi.listMembers(activeWorkspace.id, selectedProjectId),
        workspaceApi.listMembers(activeWorkspace.id),
      ]);
      setProjectMembers(membersData || []);
      setWorkspaceMembers(allWsMembers || []);
    } catch (err) {
      // ignore
    } finally {
      setIsLoadingAccess(false);
    }
  }, [activeWorkspace?.id, selectedProjectId]);

  useEffect(() => {
    if (activeSubTab === 'environments') {
      fetchEnvironments();
    } else if (activeSubTab === 'access') {
      fetchProjectMembers();
    }
  }, [activeSubTab, fetchEnvironments, fetchProjectMembers]);

  const handleCopy = (text, field) => {
    navigator.clipboard.writeText(text);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 2000);
  };

  const handleSelectProject = (projId) => {
    setSelectedProjectId(projId);
    const found = projects.find((p) => p.id === projId);
    if (found) {
      setSelectedProject(found);
    }
    loadProjectDetails(projId);
    setError(null);
    setSuccessMessage(null);
  };

  const handleSaveGeneral = async (e) => {
    e.preventDefault();
    if (!isAdmin || !selectedProjectId) return;
    setIsSaving(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await projectApi.update(activeWorkspace.id, selectedProjectId, {
        name: projectName.trim(),
        description: projectDescription.trim(),
        status: projectStatus,
      });
      setSelectedProject(updated);
      setSuccessMessage('Project settings saved successfully.');
      await fetchProjects();
    } catch (err) {
      setError(err.message || 'Failed to update project settings.');
    } finally {
      setIsSaving(false);
    }
  };

  // Environment Actions
  const handleCreateEnvironment = async (e) => {
    e.preventDefault();
    if (!isAdmin || !selectedProjectId) return;
    setIsSaving(true);
    setError(null);
    try {
      await environmentApi.create(activeWorkspace.id, selectedProjectId, {
        name: envForm.name.trim(),
        slug: envForm.slug.trim() || undefined,
        envType: envForm.envType,
        description: envForm.description.trim() || undefined,
        isProtected: envForm.isProtected,
      });
      setIsAddEnvModalOpen(false);
      setEnvForm({ name: '', slug: '', envType: 'DEVELOPMENT', description: '', isProtected: false });
      setSuccessMessage('Environment created successfully.');
      await fetchEnvironments();
      await loadProjectDetails(selectedProjectId);
    } catch (err) {
      setError(err.message || 'Failed to create environment.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleUpdateEnvironment = async (e) => {
    e.preventDefault();
    if (!isAdmin || !selectedProjectId || !editingEnv) return;
    setIsSaving(true);
    setError(null);
    try {
      await environmentApi.update(activeWorkspace.id, selectedProjectId, editingEnv.id, {
        name: envForm.name.trim(),
        description: envForm.description.trim() || undefined,
        envType: envForm.envType,
        isProtected: envForm.isProtected,
      });
      setEditingEnv(null);
      setSuccessMessage('Environment updated successfully.');
      await fetchEnvironments();
      await loadProjectDetails(selectedProjectId);
    } catch (err) {
      setError(err.message || 'Failed to update environment.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteEnvironment = async (envId, isProtected) => {
    if (isProtected) {
      alert('Protected environments cannot be deleted directly. Remove protection tier first.');
      return;
    }
    if (!window.confirm('Are you sure you want to delete this environment?')) return;
    try {
      await environmentApi.delete(activeWorkspace.id, selectedProjectId, envId);
      setSuccessMessage('Environment deleted successfully.');
      await fetchEnvironments();
      await loadProjectDetails(selectedProjectId);
    } catch (err) {
      setError(err.message || 'Failed to delete environment.');
    }
  };

  // Project Access Actions
  const handleGrantProjectAccess = async (e) => {
    e.preventDefault();
    if (!isAdmin || !selectedProjectId || !accessForm.userId) return;
    setIsSaving(true);
    setError(null);
    try {
      await projectApi.grantMemberAccess(activeWorkspace.id, selectedProjectId, {
        userId: accessForm.userId,
        role: accessForm.role,
      });
      setIsAddAccessModalOpen(false);
      setAccessForm({ userId: '', role: 'DEVELOPER' });
      setSuccessMessage('Project access granted successfully.');
      await fetchProjectMembers();
    } catch (err) {
      setError(err.message || 'Failed to grant project access.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleUpdateProjectAccess = async (userId, role) => {
    if (!isAdmin || !selectedProjectId) return;
    try {
      await projectApi.updateMemberAccess(activeWorkspace.id, selectedProjectId, userId, { role });
      setSuccessMessage('Project member role updated.');
      await fetchProjectMembers();
    } catch (err) {
      setError(err.message || 'Failed to update project access.');
    }
  };

  const handleRemoveProjectAccess = async (userId) => {
    if (!window.confirm('Are you sure you want to revoke this user\'s scoped project access?')) return;
    try {
      await projectApi.removeMemberAccess(activeWorkspace.id, selectedProjectId, userId);
      setSuccessMessage('Project access revoked.');
      await fetchProjectMembers();
    } catch (err) {
      setError(err.message || 'Failed to revoke project access.');
    }
  };

  // Delete Project Action
  const handleDeleteProject = async (e) => {
    e.preventDefault();
    if (!isAdmin || !selectedProjectId) return;
    if (confirmDeleteInput.trim() !== selectedProject?.name?.trim()) {
      alert(`Please type "${selectedProject?.name}" exactly to confirm deletion.`);
      return;
    }
    setIsDeletingProject(true);
    setError(null);
    try {
      await projectApi.delete(activeWorkspace.id, selectedProjectId);
      alert('Project successfully deleted.');
      await fetchProjects();
    } catch (err) {
      setError(err.message || 'Failed to delete project.');
    } finally {
      setIsDeletingProject(false);
    }
  };

  const subTabs = [
    { id: 'general', label: 'General', icon: <FolderGit2 className="w-4 h-4" /> },
    { id: 'environments', label: 'Environments', icon: <Layers className="w-4 h-4" /> },
    { id: 'access', label: 'Access Control', icon: <Users className="w-4 h-4" /> },
    { id: 'security', label: 'Security Overview', icon: <ShieldCheck className="w-4 h-4" /> },
    { id: 'danger', label: 'Danger Zone', icon: <AlertTriangle className="w-4 h-4 text-[#F87171]" /> },
  ];

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-16 gap-3 font-mono text-xs text-[#F4B5C8]">
        <RefreshCw className="w-6 h-6 animate-spin text-[#FF2D6D]" />
        <span>Loading project settings...</span>
      </div>
    );
  }

  if (projects.length === 0) {
    return (
      <div className="p-12 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col items-center justify-center text-center gap-4">
        <FolderGit2 className="w-10 h-10 text-[#FF2D6D]" />
        <h3 className="text-base font-headline font-bold text-white">No projects found in this workspace</h3>
        <p className="text-xs text-[#A26377]">
          Create a project first before configuring project settings.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col w-full gap-8 text-white font-body">
      {/* Project Selector & Header Banner */}
      <section className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 relative">
        <div className="flex flex-col gap-2 max-w-3xl">
          <div className="flex items-center gap-2 text-xs font-mono tracking-widest text-[#A26377] uppercase font-semibold">
            <span>{activeWorkspace?.name || 'Workspace'}</span>
            <span className="text-[#FF2D6D]">/</span>
            <span className="text-white font-bold">{selectedProject?.name || 'Project'}</span>
            <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] text-[#4ADE80] font-mono border border-[#4ADE80]/30">
              {selectedProject?.status || 'ACTIVE'}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-3xl md:text-4xl font-headline font-bold tracking-tight text-white">
              Project Settings
            </h1>

            {/* Project Switcher Dropdown */}
            <div className="relative">
              <select
                value={selectedProjectId || ''}
                onChange={(e) => handleSelectProject(e.target.value)}
                className="px-3 py-1.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/25 text-xs font-mono text-[#F4B5C8] focus:border-[#FF2D6D] outline-none cursor-pointer"
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.slug})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <p className="text-xs md:text-sm text-[#F4B5C8] leading-relaxed">
            Configure application container metadata, environment deployment tiers, scoped RBAC access grants, and secret protection policies.
          </p>
        </div>

        <div className="flex items-center gap-3">
          {selectedProject && onNavigateToSecrets && (
            <button
              type="button"
              onClick={() => onNavigateToSecrets(selectedProject.id)}
              className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#FFB4C8] hover:text-white text-xs font-mono font-bold flex items-center gap-2 border border-[#FFB4C8]/20 transition-all shadow-sm"
            >
              <Key className="w-4 h-4 text-[#FF2D6D]" />
              <span>Open Secrets Vault</span>
            </button>
          )}
        </div>
      </section>

      {/* Global Feedback */}
      {error && (
        <div className="flex items-center gap-3 p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6]">
          <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
          <span className="flex-1">{error}</span>
          <button onClick={() => setError(null)} className="text-xs underline text-[#FFDAD6]">Dismiss</button>
        </div>
      )}

      {successMessage && (
        <div className="flex items-center gap-3 p-4 rounded-2xl bg-[#00511C]/30 border border-[#4ADE80]/40 text-xs text-[#86EFAC]">
          <CheckCircle2 className="w-5 h-5 shrink-0 text-[#4ADE80]" />
          <span className="flex-1">{successMessage}</span>
          <button onClick={() => setSuccessMessage(null)} className="text-xs underline text-[#86EFAC]">Dismiss</button>
        </div>
      )}

      {/* Sub Tabs Navigation */}
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
              <h2 className="text-base font-headline font-bold text-white">Project Information</h2>
              <p className="text-xs text-[#A26377]">
                Identification and lifecycle properties for this microservice application.
              </p>
            </div>

            <form onSubmit={handleSaveGeneral} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Project Name</label>
                <input
                  type="text"
                  value={projectName}
                  onChange={(e) => setProjectName(e.target.value)}
                  disabled={!isAdmin || isSaving}
                  required
                  placeholder="e.g. Payment Gateway API"
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] text-xs text-white outline-none disabled:opacity-50"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Project URL Slug</label>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={selectedProject?.slug || ''}
                    readOnly
                    className="flex-1 px-4 py-2.5 rounded-xl bg-[#30000F]/60 border border-[#FFB4C8]/10 text-xs font-mono text-[#A26377] outline-none cursor-not-allowed"
                  />
                  <button
                    type="button"
                    onClick={() => handleCopy(selectedProject?.slug, 'proj-slug')}
                    className="p-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-[#F4B5C8] hover:text-white"
                    title="Copy Slug"
                  >
                    {copiedField === 'proj-slug' ? <Check className="w-4 h-4 text-[#4ADE80]" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Description</label>
                <textarea
                  value={projectDescription}
                  onChange={(e) => setProjectDescription(e.target.value)}
                  disabled={!isAdmin || isSaving}
                  rows={3}
                  placeholder="Describe the application scope, architecture, or service dependencies..."
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] text-xs text-white outline-none resize-none disabled:opacity-50"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Lifecycle Status</label>
                <select
                  value={projectStatus}
                  onChange={(e) => setProjectStatus(e.target.value)}
                  disabled={!isAdmin || isSaving}
                  className="px-3 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                >
                  <option value="ACTIVE">ACTIVE — Microservice active and accepting secrets sync</option>
                  <option value="ARCHIVED">ARCHIVED — Read-only legacy archive; secrets mutation blocked</option>
                </select>
              </div>

              {isAdmin && (
                <div className="pt-3 border-t border-[#FFB4C8]/10 flex items-center justify-end">
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-6 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-bold font-mono tracking-wider flex items-center gap-2 shadow-lg shadow-[#FF2D6D]/20 transition-all disabled:opacity-50"
                  >
                    <Save className="w-4 h-4" />
                    <span>{isSaving ? 'Saving...' : 'Save Project Changes'}</span>
                  </button>
                </div>
              )}
            </form>
          </div>
        </div>
      )}

      {/* Tab 2: Environments */}
      {activeSubTab === 'environments' && (
        <div className="flex flex-col gap-6">
          <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-headline font-bold text-white">Deployment Environments</h2>
                <p className="text-xs text-[#A26377]">
                  Manage development, staging, production, and custom deployment tiers for this project.
                </p>
              </div>

              {isAdmin && (
                <button
                  type="button"
                  onClick={() => {
                    setEnvForm({ name: '', slug: '', envType: 'DEVELOPMENT', description: '', isProtected: false });
                    setIsAddEnvModalOpen(true);
                  }}
                  className="px-4 py-2 rounded-xl bg-[#FF2D6D] text-white text-xs font-bold font-mono flex items-center gap-2 shadow-md hover:bg-[#FF2D6D]/90"
                >
                  <Plus className="w-4 h-4" />
                  <span>+ Custom Environment</span>
                </button>
              )}
            </div>

            {isLoadingEnvs ? (
              <div className="flex items-center justify-center p-12 text-xs font-mono text-[#A26377]">
                <RefreshCw className="w-5 h-5 animate-spin mr-2 text-[#FF2D6D]" />
                <span>Loading deployment tiers...</span>
              </div>
            ) : environments.length === 0 ? (
              <div className="p-8 rounded-2xl bg-[#30000F]/40 border border-[#FFB4C8]/10 text-center text-xs font-mono text-[#A26377]">
                No environments provisioned for this project.
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                {environments.map((env) => {
                  const isProd = env.envType === 'PRODUCTION' || env.isProtected;
                  return (
                    <div
                      key={env.id}
                      className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FF2D6D]/50 flex flex-col justify-between gap-4 transition-all"
                    >
                      <div className="flex flex-col gap-2">
                        <div className="flex items-center justify-between">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border ${
                            isProd
                              ? 'bg-[#3F0016] text-[#F87171] border-[#F87171]/40'
                              : 'bg-[#3F0016] text-[#60A5FA] border-[#60A5FA]/40'
                          }`}>
                            {env.envType}
                          </span>

                          {isProd && (
                            <span className="flex items-center gap-1 text-[10px] font-mono text-[#F87171]">
                              <ShieldCheck className="w-3.5 h-3.5" />
                              <span>Protected</span>
                            </span>
                          )}
                        </div>

                        <div>
                          <h3 className="text-sm font-headline font-bold text-white">{env.name}</h3>
                          <span className="text-[11px] font-mono text-[#A26377]">{env.slug}</span>
                        </div>

                        {env.description && (
                          <p className="text-xs text-[#F4B5C8] line-clamp-2 mt-1">{env.description}</p>
                        )}
                      </div>

                      <div className="flex items-center justify-between pt-3 border-t border-[#FFB4C8]/10 text-xs">
                        {onNavigateToSecrets && (
                          <button
                            type="button"
                            onClick={() => onNavigateToSecrets(selectedProjectId, env.id)}
                            className="text-[11px] font-mono text-[#FF2D6D] hover:underline flex items-center gap-1"
                          >
                            <Key className="w-3.5 h-3.5" />
                            <span>Secrets Vault</span>
                          </button>
                        )}

                        <div className="flex items-center gap-2">
                          {isAdmin && (
                            <button
                              type="button"
                              onClick={() => {
                                setEditingEnv(env);
                                setEnvForm({
                                  name: env.name,
                                  slug: env.slug,
                                  envType: env.envType,
                                  description: env.description || '',
                                  isProtected: env.isProtected,
                                });
                              }}
                              className="p-1.5 rounded-lg text-[#A26377] hover:text-white hover:bg-[#3F0016]"
                              title="Edit Environment"
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                            </button>
                          )}

                          {isAdmin && !isProd && (
                            <button
                              type="button"
                              onClick={() => handleDeleteEnvironment(env.id, env.isProtected)}
                              className="p-1.5 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016]"
                              title="Delete Environment"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 3: Access Control */}
      {activeSubTab === 'access' && (
        <div className="flex flex-col gap-6">
          <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-headline font-bold text-white">Project Scoped Access</h2>
                <p className="text-xs text-[#A26377]">
                  Explicit ProjectAccess grants restricting member roles within this specific microservice.
                </p>
              </div>

              {isAdmin && (
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setIsAddAccessModalOpen(true)}
                    className="px-4 py-2 rounded-xl bg-[#FF2D6D] text-white text-xs font-bold font-mono flex items-center gap-2 shadow-md hover:bg-[#FF2D6D]/90"
                  >
                    <Plus className="w-4 h-4" />
                    <span>+ Grant Member Access</span>
                  </button>
                </div>
              )}
            </div>

            {isLoadingAccess ? (
              <div className="flex items-center justify-center p-12 text-xs font-mono text-[#A26377]">
                <RefreshCw className="w-5 h-5 animate-spin mr-2 text-[#FF2D6D]" />
                <span>Loading project access grants...</span>
              </div>
            ) : projectMembers.length === 0 ? (
              <div className="p-8 rounded-2xl bg-[#30000F]/40 border border-[#FFB4C8]/10 text-center text-xs font-mono text-[#A26377] flex flex-col items-center gap-2">
                <span>No explicit project-scoped access overrides configured.</span>
                <span className="text-[11px] text-[#F4B5C8]">Workspace standing roles (Owners, Admins, Developers) apply by default.</span>
              </div>
            ) : (
              <div className="overflow-x-auto rounded-2xl border border-[#FFB4C8]/15">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-[#30000F] text-[#A26377] font-mono text-[10px] uppercase tracking-wider border-b border-[#FFB4C8]/15">
                      <th className="p-3.5">Member</th>
                      <th className="p-3.5">Email</th>
                      <th className="p-3.5">Scoped Project Role</th>
                      <th className="p-3.5">Granted At</th>
                      <th className="p-3.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#FFB4C8]/10 bg-[#1E000A]">
                    {projectMembers.map((pm) => (
                      <tr key={pm.id} className="hover:bg-[#30000F]/60 transition-colors">
                        <td className="p-3.5 font-medium text-white">
                          <div className="flex items-center gap-2.5">
                            <div className="w-7 h-7 rounded-full bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center font-mono text-[10px] text-[#FF2D6D]">
                              {(pm.fullName || pm.email || 'U').slice(0, 2).toUpperCase()}
                            </div>
                            <span>{pm.fullName || 'Workspace Member'}</span>
                          </div>
                        </td>
                        <td className="p-3.5 font-mono text-[#A26377]">{pm.email}</td>
                        <td className="p-3.5">
                          {isAdmin ? (
                            <select
                              value={pm.role}
                              onChange={(e) => handleUpdateProjectAccess(pm.userId, e.target.value)}
                              className="px-2.5 py-1 rounded-lg bg-[#30000F] border border-[#FFB4C8]/20 text-[11px] font-mono text-white outline-none focus:border-[#FF2D6D]"
                            >
                              <option value="VIEWER">VIEWER (Read Only)</option>
                              <option value="DEVELOPER">DEVELOPER (Read / Write)</option>
                              <option value="ADMIN">ADMIN (Manage Project)</option>
                            </select>
                          ) : (
                            <span className="px-2.5 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono font-bold text-[#4ADE80] border border-[#4ADE80]/30">
                              {pm.role}
                            </span>
                          )}
                        </td>
                        <td className="p-3.5 font-mono text-[10px] text-[#A26377]">
                          {pm.createdAt ? new Date(pm.createdAt).toLocaleDateString() : 'N/A'}
                        </td>
                        <td className="p-3.5 text-right">
                          <div className="flex items-center justify-end gap-2">
                            {isAdmin && (
                              <button
                                type="button"
                                onClick={() => setSelectedMemberForAccessMatrix({ userId: pm.userId, email: pm.email, fullName: pm.fullName })}
                                className="px-2.5 py-1 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#FFB4C8] hover:text-white text-[10px] font-mono border border-[#FFB4C8]/15"
                              >
                                Governance Matrix
                              </button>
                            )}
                            {isAdmin && (
                              <button
                                type="button"
                                onClick={() => handleRemoveProjectAccess(pm.userId)}
                                className="p-1.5 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors"
                                title="Revoke Project Access"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            )}
                          </div>
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

      {/* Tab 4: Security Overview */}
      {activeSubTab === 'security' && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            <div className="p-5 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col justify-between gap-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono uppercase text-[#A26377]">Isolation Tier</span>
                <Lock className="w-4 h-4 text-[#FF2D6D]" />
              </div>
              <div>
                <h3 className="text-sm font-headline font-bold text-white">Hierarchical IDOR Guard</h3>
                <p className="text-xs text-[#A26377] mt-1">
                  Server-side authorization asserts project membership before returning secret keys or environments.
                </p>
              </div>
              <span className="text-[10px] font-mono text-[#4ADE80]">Protected</span>
            </div>

            <div className="p-5 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col justify-between gap-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono uppercase text-[#A26377]">Environment Promotion</span>
                <Sparkles className="w-4 h-4 text-[#FFB4C8]" />
              </div>
              <div>
                <h3 className="text-sm font-headline font-bold text-white">Cryptographic Promotion</h3>
                <p className="text-xs text-[#A26377] mt-1">
                  Promotions re-encrypt secret payloads using fresh destination DEKs and AAD context keys.
                </p>
              </div>
              <span className="text-[10px] font-mono text-[#4ADE80]">Active</span>
            </div>

            <div className="p-5 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col justify-between gap-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono uppercase text-[#A26377]">Access Reviews</span>
                <ShieldCheck className="w-4 h-4 text-[#4ADE80]" />
              </div>
              <div>
                <h3 className="text-sm font-headline font-bold text-white">Certification Snapshot</h3>
                <p className="text-xs text-[#A26377] mt-1">
                  Participates in workspace periodic access certification campaigns and JIT elevation auditing.
                </p>
              </div>
              {onNavigateToAccess && (
                <button
                  type="button"
                  onClick={onNavigateToAccess}
                  className="text-[11px] font-mono text-[#FF2D6D] hover:underline flex items-center gap-1"
                >
                  <span>Open Access Center</span>
                  <ExternalLink className="w-3 h-3" />
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Tab 5: Danger Zone */}
      {activeSubTab === 'danger' && (
        <div className="flex flex-col gap-6 max-w-3xl">
          <div className="p-6 rounded-3xl bg-[#93000A]/15 border border-[#FFB4AB]/30 flex flex-col gap-6">
            <div>
              <h2 className="text-base font-headline font-bold text-[#FFDAD6]">Project Danger Zone</h2>
              <p className="text-xs text-[#FFB4AB]/80">
                Irreversible destructive actions. Deleting a project permanently deletes all associated environments, secrets, and version tags.
              </p>
            </div>

            <form onSubmit={handleDeleteProject} className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-4">
              <div className="flex flex-col gap-1">
                <span className="text-xs font-headline font-bold text-white">
                  Delete Project "{selectedProject?.name}"
                </span>
                <p className="text-xs text-[#A26377]">
                  To confirm, type <strong className="text-white font-mono">{selectedProject?.name}</strong> below:
                </p>
              </div>

              <input
                type="text"
                value={confirmDeleteInput}
                onChange={(e) => setConfirmDeleteInput(e.target.value)}
                placeholder={selectedProject?.name}
                className="px-4 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4AB]/30 focus:border-[#F87171] text-xs text-white outline-none font-mono"
              />

              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={isDeletingProject || confirmDeleteInput.trim() !== selectedProject?.name?.trim()}
                  className="px-5 py-2 rounded-xl bg-[#93000A] hover:bg-[#BA1A1A] text-white text-xs font-bold font-mono tracking-wider flex items-center gap-2 shadow-lg shadow-[#93000A]/30 transition-all disabled:opacity-40"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>{isDeletingProject ? 'Deleting Project...' : 'I understand, delete this project'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 1: Add Custom Environment */}
      {isAddEnvModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
          <div className="w-full max-w-lg rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/25 p-6 shadow-2xl flex flex-col gap-5">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-headline font-bold text-white">Create Custom Environment</h3>
              <button onClick={() => setIsAddEnvModalOpen(false)} className="text-[#A26377] hover:text-white">✕</button>
            </div>

            <form onSubmit={handleCreateEnvironment} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Environment Name</label>
                <input
                  type="text"
                  value={envForm.name}
                  onChange={(e) => setEnvForm({ ...envForm, name: e.target.value })}
                  required
                  placeholder="e.g. QA Automation"
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Environment Tier Type</label>
                <select
                  value={envForm.envType}
                  onChange={(e) => setEnvForm({ ...envForm, envType: e.target.value })}
                  className="px-3 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                >
                  <option value="DEVELOPMENT">DEVELOPMENT</option>
                  <option value="STAGING">STAGING</option>
                  <option value="PRODUCTION">PRODUCTION</option>
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Description (Optional)</label>
                <input
                  type="text"
                  value={envForm.description}
                  onChange={(e) => setEnvForm({ ...envForm, description: e.target.value })}
                  placeholder="e.g. Automated end-to-end testing cluster"
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                />
              </div>

              <div className="flex items-center gap-3 p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15">
                <input
                  type="checkbox"
                  id="isProtectedCheck"
                  checked={envForm.isProtected}
                  onChange={(e) => setEnvForm({ ...envForm, isProtected: e.target.checked })}
                  className="w-4 h-4 accent-[#FF2D6D]"
                />
                <label htmlFor="isProtectedCheck" className="text-xs text-[#F4B5C8] cursor-pointer">
                  Mark as Protected Environment (Enforce step-up reveal and promotion checks)
                </label>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsAddEnvModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs text-[#A26377] hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] text-white text-xs font-bold font-mono shadow-lg shadow-[#FF2D6D]/20"
                >
                  {isSaving ? 'Creating...' : 'Create Environment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 2: Edit Environment */}
      {editingEnv && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
          <div className="w-full max-w-lg rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/25 p-6 shadow-2xl flex flex-col gap-5">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-headline font-bold text-white">Edit Environment: {editingEnv.name}</h3>
              <button onClick={() => setEditingEnv(null)} className="text-[#A26377] hover:text-white">✕</button>
            </div>

            <form onSubmit={handleUpdateEnvironment} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Environment Display Name</label>
                <input
                  type="text"
                  value={envForm.name}
                  onChange={(e) => setEnvForm({ ...envForm, name: e.target.value })}
                  required
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Tier Type</label>
                <select
                  value={envForm.envType}
                  onChange={(e) => setEnvForm({ ...envForm, envType: e.target.value })}
                  className="px-3 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                >
                  <option value="DEVELOPMENT">DEVELOPMENT</option>
                  <option value="STAGING">STAGING</option>
                  <option value="PRODUCTION">PRODUCTION</option>
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Description</label>
                <input
                  type="text"
                  value={envForm.description}
                  onChange={(e) => setEnvForm({ ...envForm, description: e.target.value })}
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                />
              </div>

              <div className="flex items-center gap-3 p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15">
                <input
                  type="checkbox"
                  id="editIsProtectedCheck"
                  checked={envForm.isProtected}
                  onChange={(e) => setEnvForm({ ...envForm, isProtected: e.target.checked })}
                  className="w-4 h-4 accent-[#FF2D6D]"
                />
                <label htmlFor="editIsProtectedCheck" className="text-xs text-[#F4B5C8] cursor-pointer">
                  Protected Status (Restricted mutations &amp; rollback auditing)
                </label>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setEditingEnv(null)}
                  className="px-4 py-2 rounded-xl text-xs text-[#A26377] hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] text-white text-xs font-bold font-mono shadow-lg shadow-[#FF2D6D]/20"
                >
                  {isSaving ? 'Updating...' : 'Save Environment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 3: Add Member Project Access */}
      {isAddAccessModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
          <div className="w-full max-w-lg rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/25 p-6 shadow-2xl flex flex-col gap-5">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-headline font-bold text-white">Grant Scoped Project Access</h3>
              <button onClick={() => setIsAddAccessModalOpen(false)} className="text-[#A26377] hover:text-white">✕</button>
            </div>

            <form onSubmit={handleGrantProjectAccess} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Select Workspace Member</label>
                <select
                  value={accessForm.userId}
                  onChange={(e) => setAccessForm({ ...accessForm, userId: e.target.value })}
                  required
                  className="px-3 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none focus:border-[#FF2D6D]"
                >
                  <option value="">-- Choose Member --</option>
                  {workspaceMembers.map((m) => (
                    <option key={m.userId} value={m.userId}>
                      {m.fullName || m.email} ({m.email}) - Workspace {m.role}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono text-[#F4B5C8]">Scoped Project Role</label>
                <select
                  value={accessForm.role}
                  onChange={(e) => setAccessForm({ ...accessForm, role: e.target.value })}
                  className="px-3 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                >
                  <option value="VIEWER">VIEWER — Read secret metadata only</option>
                  <option value="DEVELOPER">DEVELOPER — Read and Write secrets in allowed environments</option>
                  <option value="ADMIN">ADMIN — Full project and environment management</option>
                </select>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsAddAccessModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs text-[#A26377] hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving || !accessForm.userId}
                  className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] text-white text-xs font-bold font-mono shadow-lg shadow-[#FF2D6D]/20 disabled:opacity-50"
                >
                  {isSaving ? 'Granting...' : 'Grant Scoped Access'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Member Access Management Dialog */}
      {selectedMemberForAccessMatrix && (
        <MemberAccessManagementDialog
          isOpen={Boolean(selectedMemberForAccessMatrix)}
          member={selectedMemberForAccessMatrix}
          workspaceId={activeWorkspace?.id}
          onClose={() => setSelectedMemberForAccessMatrix(null)}
          onAccessUpdated={() => fetchProjectMembers()}
        />
      )}
    </div>
  );
};

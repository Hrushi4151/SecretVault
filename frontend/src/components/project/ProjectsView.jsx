import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { projectApi } from '../../api/projects';
import { CreateProjectModal } from './CreateProjectModal';
import {
  FolderGit2,
  Plus,
  RefreshCw,
  Search,
  Layers,
  ShieldCheck,
  Shield,
  Trash2,
  AlertCircle,
  ExternalLink,
  ChevronRight,
  Server,
  Lock,
  Sparkles,
  Key,
  ArrowRight,
} from 'lucide-react';

export const ProjectsView = ({ onNavigateToSecrets }) => {
  const { activeWorkspace } = useAuth();
  const [projects, setProjects] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [deletingProjectId, setDeletingProjectId] = useState(null);

  const fetchProjects = async (showLoading = true) => {
    if (!activeWorkspace?.id) return;
    if (showLoading) setIsLoading(true);
    setError(null);
    try {
      const data = await projectApi.list(activeWorkspace.id);
      setProjects(data || []);
    } catch (err) {
      setError(err.message || 'Failed to load projects for this workspace.');
    } finally {
      if (showLoading) setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchProjects(true);
  }, [activeWorkspace?.id]);

  const handleRefresh = () => {
    setIsRefreshing(true);
    fetchProjects(false);
  };

  const handleProjectCreated = (newProject) => {
    setProjects((prev) => [newProject, ...prev]);
  };

  const handleDeleteProject = async (projectId, e) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this project and all its environments?')) {
      return;
    }
    setDeletingProjectId(projectId);
    try {
      await projectApi.delete(activeWorkspace.id, projectId);
      setProjects((prev) => prev.filter((p) => p.id !== projectId));
    } catch (err) {
      alert(err.message || 'Failed to delete project');
    } finally {
      setDeletingProjectId(null);
    }
  };

  const handleProjectClick = (projectId, defaultEnvId) => {
    if (onNavigateToSecrets) {
      onNavigateToSecrets(projectId, defaultEnvId);
    }
  };

  const handleEnvironmentClick = (projectId, envId, e) => {
    e.stopPropagation();
    if (onNavigateToSecrets) {
      onNavigateToSecrets(projectId, envId);
    }
  };

  const filteredProjects = projects.filter((p) => {
    const q = searchQuery.toLowerCase();
    return (
      p.name?.toLowerCase().includes(q) ||
      p.slug?.toLowerCase().includes(q) ||
      p.description?.toLowerCase().includes(q)
    );
  });

  const totalEnvironments = projects.reduce(
    (acc, p) => acc + (p.environments?.length || 0),
    0
  );
  const protectedEnvironments = projects.reduce(
    (acc, p) =>
      acc + (p.environments?.filter((e) => e.isProtected)?.length || 0),
    0
  );

  return (
    <div className="flex flex-col w-full gap-8 text-white font-body">
      {/* Top Banner Header */}
      <section className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 relative">
        <div className="flex flex-col gap-2 max-w-3xl">
          <div className="flex items-center gap-2 text-xs font-mono tracking-widest text-[#A26377] uppercase font-semibold">
            <span>{activeWorkspace?.name || 'Workspace'}</span>
            <span className="text-[#FF2D6D]">/</span>
            <span className="text-white font-bold">Projects</span>
            <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] text-[#FFB4C8] font-mono border border-[#FFB4C8]/20">
              <span className="w-1.5 h-1.5 rounded-full bg-[#FF2D6D] mr-1.5 animate-pulse" />
              FEDERATED KMS ACTIVE
            </span>
          </div>

          <h1 className="text-3xl md:text-4xl font-headline font-bold tracking-tight text-white">
            Projects &amp; Infrastructure Workspaces
          </h1>

          <p className="text-xs md:text-sm text-[#F4B5C8] leading-relaxed">
            Manage containerized microservices, environment secrets mapping, and federated cloud infrastructure across clusters with zero-trust key isolation.
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white text-xs font-mono font-semibold tracking-wide flex items-center gap-2 transition-all border border-[#FFB4C8]/15 active:scale-95 shadow-sm"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-[#FF2D6D]' : ''}`} />
            <span>Sync Registry</span>
          </button>

          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-mono font-bold tracking-wider uppercase flex items-center gap-2 transition-all shadow-lg shadow-[#FF2D6D]/20 active:scale-95 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>+ New Project</span>
          </button>
        </div>
      </section>

      {/* Metrics Summary Grid */}
      <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Metric 1: Total Projects */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Total Projects
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
              <FolderGit2 className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-headline font-bold text-white">
              {projects.length}
            </span>
            <span className="text-[10px] font-mono text-[#4ADE80]">Active</span>
          </div>
        </div>

        {/* Metric 2: Provisioned Environments */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Active Environments
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
              <Layers className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-headline font-bold text-white">
              {totalEnvironments}
            </span>
            <span className="text-[10px] font-mono text-[#F4B5C8]">Across tiers</span>
          </div>
        </div>

        {/* Metric 3: Protected Envs */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Protected Enclaves
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#F87171]/40 flex items-center justify-center text-[#F87171]">
              <ShieldCheck className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-headline font-bold text-[#F87171]">
              {protectedEnvironments}
            </span>
            <span className="text-[10px] font-mono text-[#A26377]">Production</span>
          </div>
        </div>

        {/* Metric 4: RBAC Governance */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Zero-Trust Policy
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#4ADE80]/30 flex items-center justify-center text-[#4ADE80]">
              <Lock className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-lg font-headline font-bold text-[#4ADE80]">
              ENFORCED
            </span>
            <span className="text-[10px] font-mono text-[#A26377]">Hierarchical</span>
          </div>
        </div>
      </section>

      {/* Filter and Search Bar */}
      <section className="flex flex-col sm:flex-row items-center justify-between gap-4 p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#A26377]" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search projects by name or slug..."
            className="w-full pl-9 pr-4 py-2 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs text-white placeholder-[#A26377] outline-none transition-all"
          />
        </div>

        <div className="flex items-center gap-2 text-xs font-mono text-[#A26377]">
          <span>Showing {filteredProjects.length} of {projects.length} projects</span>
        </div>
      </section>

      {/* Error Banner */}
      {error && (
        <div className="flex items-center gap-3 p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6]">
          <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
          <span>{error}</span>
        </div>
      )}

      {/* Projects Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map((idx) => (
            <div
              key={idx}
              className="p-6 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-4 animate-pulse"
            >
              <div className="flex items-center justify-between">
                <div className="w-10 h-10 rounded-xl bg-[#3F0016]" />
                <div className="w-16 h-5 rounded-full bg-[#3F0016]" />
              </div>
              <div className="w-3/4 h-5 rounded bg-[#3F0016]" />
              <div className="w-full h-10 rounded bg-[#3F0016]" />
              <div className="flex gap-2 pt-2">
                <div className="w-16 h-6 rounded-full bg-[#3F0016]" />
                <div className="w-16 h-6 rounded-full bg-[#3F0016]" />
                <div className="w-16 h-6 rounded-full bg-[#3F0016]" />
              </div>
            </div>
          ))}
        </div>
      ) : filteredProjects.length === 0 ? (
        <div className="p-12 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col items-center justify-center text-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
            <FolderGit2 className="w-8 h-8" />
          </div>
          <div className="flex flex-col gap-1 max-w-md">
            <h3 className="text-base font-headline font-bold text-white">
              {searchQuery ? 'No matching projects found' : 'No projects in this workspace yet'}
            </h3>
            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              {searchQuery
                ? 'Try adjusting your search terms or create a new project.'
                : 'Projects group application microservices and their deployment environments (development, staging, production).'}
            </p>
          </div>
          {!searchQuery && (
            <button
              onClick={() => setIsCreateModalOpen(true)}
              className="mt-2 px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-bold shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-2 active:scale-95"
            >
              <Plus className="w-4 h-4" />
              <span>Create First Project</span>
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredProjects.map((project) => {
            const firstEnvId = project.environments?.[0]?.id;

            return (
              <div
                key={project.id}
                onClick={() => handleProjectClick(project.id, firstEnvId)}
                className="p-6 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FF2D6D]/60 shadow-sm hover:shadow-2xl hover:shadow-black/60 flex flex-col justify-between gap-5 transition-all group cursor-pointer hover:-translate-y-1"
              >
                {/* Card Top */}
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] group-hover:scale-105 group-hover:border-[#FF2D6D] transition-all shadow-sm">
                        <Server className="w-5 h-5" />
                      </div>
                      <div>
                        <h3 className="text-sm font-headline font-bold text-white group-hover:text-[#FFB4C8] transition-colors flex items-center gap-1.5">
                          <span>{project.name}</span>
                          <ArrowRight className="w-3.5 h-3.5 text-[#FF2D6D] opacity-0 group-hover:opacity-100 transition-opacity transform group-hover:translate-x-0.5" />
                        </h3>
                        <span className="text-[11px] font-mono text-[#A26377]">
                          {project.slug}
                        </span>
                      </div>
                    </div>

                    <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#4ADE80] border border-[#4ADE80]/30 font-semibold">
                      {project.status || 'ACTIVE'}
                    </span>
                  </div>

                  <p className="text-xs text-[#F4B5C8] leading-relaxed line-clamp-2">
                    {project.description || 'Application microservice running on SecretVault zero-trust infrastructure.'}
                  </p>
                </div>

                {/* Environments Tier Pills (Clickable to jump directly into specific env secrets) */}
                <div className="flex flex-col gap-2 pt-2 border-t border-[#FFB4C8]/10">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono uppercase tracking-wider text-[#A26377]">
                      Environments ({project.environments?.length || 0})
                    </span>
                    <span className="text-[10px] font-mono text-[#FF2D6D] opacity-0 group-hover:opacity-100 transition-opacity">
                      Click tier to open vault →
                    </span>
                  </div>
                  <div className="flex flex-wrap items-center gap-1.5">
                    {project.environments && project.environments.length > 0 ? (
                      project.environments.map((env) => {
                        const isProd = env.envType === 'PRODUCTION' || env.isProtected;
                        const isStaging = env.envType === 'STAGING';
                        return (
                          <button
                            key={env.id}
                            type="button"
                            onClick={(e) => handleEnvironmentClick(project.id, env.id, e)}
                            title={`Open ${env.name} secrets`}
                            className={`px-2.5 py-1 rounded-xl text-[10px] font-mono font-medium flex items-center gap-1.5 border transition-all cursor-pointer hover:scale-105 active:scale-95 ${
                              isProd
                                ? 'bg-[#3F0016] text-[#F87171] border-[#F87171]/40 hover:border-[#F87171] shadow-sm'
                                : isStaging
                                ? 'bg-[#3F0016] text-[#FBBF24] border-[#FBBF24]/40 hover:border-[#FBBF24]'
                                : 'bg-[#3F0016] text-[#60A5FA] border-[#60A5FA]/40 hover:border-[#60A5FA]'
                            }`}
                          >
                            {isProd ? (
                              <ShieldCheck className="w-3 h-3 text-[#F87171]" />
                            ) : (
                              <Key className="w-3 h-3 text-[#FF2D6D]" />
                            )}
                            <span className="font-semibold">{env.slug || env.name}</span>
                          </button>
                        );
                      })
                    ) : (
                      <span className="text-[10px] font-mono text-[#A26377]">
                        No environments
                      </span>
                    )}
                  </div>
                </div>

                {/* Card Footer Actions */}
                <div className="flex items-center justify-between pt-3 border-t border-[#FFB4C8]/10 text-xs">
                  <button
                    type="button"
                    onClick={() => handleProjectClick(project.id, firstEnvId)}
                    className="px-3 py-1.5 rounded-xl bg-[#3F0016] hover:bg-[#FF2D6D] text-[#FFB4C8] hover:text-white text-[11px] font-mono font-bold transition-all flex items-center gap-1.5 shadow-sm"
                  >
                    <Key className="w-3.5 h-3.5 text-[#FF2D6D] group-hover:text-white" />
                    <span>Open Secrets Vault</span>
                  </button>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={(e) => handleDeleteProject(project.id, e)}
                      disabled={deletingProjectId === project.id}
                      title="Delete Project"
                      className="p-1.5 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create Project Modal */}
      <CreateProjectModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onProjectCreated={handleProjectCreated}
      />
    </div>
  );
};

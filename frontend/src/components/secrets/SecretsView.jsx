import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { projectApi } from '../../api/projects';
import { secretApi } from '../../api/secrets';
import { CreateSecretModal } from './CreateSecretModal';
import { SecretDetailsModal } from './SecretDetailsModal';
import {
  Key,
  Plus,
  RefreshCw,
  Search,
  Lock,
  Unlock,
  ShieldCheck,
  Layers,
  Sparkles,
  AlertCircle,
  Eye,
  Trash2,
  FolderGit2,
  Check,
  Copy,
  Clock,
  Shield,
  ChevronRight,
  Server,
  Filter,
} from 'lucide-react';

export const SecretsView = () => {
  const { activeWorkspace } = useAuth();
  const [projects, setProjects] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState(null);
  const [selectedEnvironmentId, setSelectedEnvironmentId] = useState(null);
  const [secrets, setSecrets] = useState([]);
  const [isLoadingProjects, setIsLoadingProjects] = useState(true);
  const [isLoadingSecrets, setIsLoadingSecrets] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState(null);

  // Modals
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [selectedSecretForDetails, setSelectedSecretForDetails] = useState(null);
  const [copiedKeyName, setCopiedKeyName] = useState(null);

  // Quick reveal state per secret row
  const [quickRevealedSecrets, setQuickRevealedSecrets] = useState({}); // { [secretId]: { value, expiresAt } }
  const [quickRevealingId, setQuickRevealingId] = useState(null);

  // Fetch Projects on workspace change
  useEffect(() => {
    const fetchProjects = async () => {
      if (!activeWorkspace?.id) return;
      setIsLoadingProjects(true);
      setError(null);
      try {
        const response = await projectApi.list(activeWorkspace.id);
        const data = response?.data || response;
        const projectList = Array.isArray(data) ? data : [];
        setProjects(projectList);

        if (projectList.length > 0) {
          const firstProj = projectList[0];
          setSelectedProjectId(firstProj.id);
          if (firstProj.environments && firstProj.environments.length > 0) {
            setSelectedEnvironmentId(firstProj.environments[0].id);
          } else {
            setSelectedEnvironmentId(null);
          }
        } else {
          setSelectedProjectId(null);
          setSelectedEnvironmentId(null);
        }
      } catch (err) {
        setError(err.message || 'Failed to load projects');
      } finally {
        setIsLoadingProjects(false);
      }
    };

    fetchProjects();
  }, [activeWorkspace?.id]);

  // When selected project changes, update default selected environment
  const currentProject = projects.find((p) => p.id === selectedProjectId);
  const currentEnvironments = currentProject?.environments || [];
  const currentEnvironment = currentEnvironments.find((e) => e.id === selectedEnvironmentId);

  useEffect(() => {
    if (currentEnvironments.length > 0 && !currentEnvironments.some((e) => e.id === selectedEnvironmentId)) {
      setSelectedEnvironmentId(currentEnvironments[0].id);
    }
  }, [selectedProjectId, currentEnvironments]);

  // Fetch secrets when project or environment changes
  const fetchSecrets = async (showLoading = true) => {
    if (!activeWorkspace?.id || !selectedProjectId || !selectedEnvironmentId) {
      setSecrets([]);
      return;
    }

    if (showLoading) setIsLoadingSecrets(true);
    setError(null);

    try {
      const response = await secretApi.list(
        activeWorkspace.id,
        selectedProjectId,
        selectedEnvironmentId,
        { search: searchQuery || undefined }
      );
      const data = response?.data || response;
      setSecrets(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || 'Failed to fetch secrets for this environment.');
    } finally {
      if (showLoading) setIsLoadingSecrets(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchSecrets(true);
  }, [activeWorkspace?.id, selectedProjectId, selectedEnvironmentId, searchQuery]);

  // Refresh handler
  const handleRefresh = () => {
    setIsRefreshing(true);
    fetchSecrets(false);
  };

  // Quick reveal handler
  const handleQuickReveal = async (secretId) => {
    if (quickRevealedSecrets[secretId]) {
      // Hide if already revealed
      setQuickRevealedSecrets((prev) => {
        const next = { ...prev };
        delete next[secretId];
        return next;
      });
      return;
    }

    setQuickRevealingId(secretId);
    try {
      const response = await secretApi.reveal(
        activeWorkspace.id,
        selectedProjectId,
        selectedEnvironmentId,
        secretId
      );
      const data = response?.data || response;
      setQuickRevealedSecrets((prev) => ({
        ...prev,
        [secretId]: {
          value: data.value,
          expiresAt: Date.now() + 15000,
        },
      }));

      // Auto-hide after 15 seconds
      setTimeout(() => {
        setQuickRevealedSecrets((prev) => {
          const next = { ...prev };
          delete next[secretId];
          return next;
        });
      }, 15000);
    } catch (err) {
      alert(err.message || 'Failed to reveal secret. Ensure you have proper permissions.');
    } finally {
      setQuickRevealingId(null);
    }
  };

  const handleCopyKeyName = (keyName) => {
    navigator.clipboard.writeText(keyName);
    setCopiedKeyName(keyName);
    setTimeout(() => setCopiedKeyName(null), 2000);
  };

  const handleSecretCreated = (newSecretOrBatch) => {
    fetchSecrets(false);
  };

  const handleSecretUpdated = (updatedSecret) => {
    setSecrets((prev) =>
      prev.map((s) => (s.id === updatedSecret.id ? updatedSecret : s))
    );
    if (selectedSecretForDetails?.id === updatedSecret.id) {
      setSelectedSecretForDetails(updatedSecret);
    }
  };

  const handleSecretDeleted = (deletedSecretId) => {
    setSecrets((prev) => prev.filter((s) => s.id !== deletedSecretId));
    if (selectedSecretForDetails?.id === deletedSecretId) {
      setSelectedSecretForDetails(null);
    }
  };

  return (
    <div className="flex flex-col w-full gap-8 text-white font-body">
      {/* Top Banner Header */}
      <section className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 relative">
        <div className="flex flex-col gap-2 max-w-3xl">
          <div className="flex items-center gap-2 text-xs font-mono tracking-widest text-[#A26377] uppercase font-semibold">
            <span>{activeWorkspace?.name || 'Workspace'}</span>
            <span className="text-[#FF2D6D]">/</span>
            <span>{currentProject?.name || 'Project'}</span>
            <span className="text-[#FF2D6D]">/</span>
            <span className="text-white font-bold">{currentEnvironment?.name || 'Secrets'}</span>
            <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] text-[#FFB4C8] font-mono border border-[#FFB4C8]/20">
              <span className="w-1.5 h-1.5 rounded-full bg-[#FF2D6D] mr-1.5 animate-pulse" />
              AES-256-GCM ENVELOPE ENCRYPTION
            </span>
          </div>

          <h1 className="text-3xl md:text-4xl font-headline font-bold tracking-tight text-white">
            Core Secret Management
          </h1>

          <p className="text-xs md:text-sm text-[#F4B5C8] leading-relaxed">
            Zero-knowledge, hardware KMS-wrapped envelope encrypted application secrets with immutable versioning, tamper-proof AAD authentication, and audit telemetry.
          </p>
        </div>

        {/* Top Controls */}
        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white text-xs font-mono font-semibold tracking-wide flex items-center gap-2 transition-all border border-[#FFB4C8]/15 active:scale-95 shadow-sm"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-[#FF2D6D]' : ''}`} />
            <span>Sync Vault</span>
          </button>

          <button
            type="button"
            disabled={!selectedEnvironmentId}
            onClick={() => setIsCreateModalOpen(true)}
            className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-white text-xs font-mono font-bold tracking-wider uppercase flex items-center gap-2 transition-all shadow-lg shadow-[#FF2D6D]/20 active:scale-95 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>+ New Secret</span>
          </button>
        </div>
      </section>

      {/* Project & Environment Hierarchical Selectors */}
      <section className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4 p-4 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/20 shadow-md">
        {/* Project Dropdown / Pills */}
        <div className="flex items-center gap-3 overflow-x-auto pb-1 md:pb-0">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-xs text-[#A26377] font-mono shrink-0">
            <Server className="w-4 h-4 text-[#FF2D6D]" />
            <span>Project:</span>
          </div>

          <div className="flex items-center gap-1.5">
            {projects.map((proj) => {
              const isSelected = proj.id === selectedProjectId;
              return (
                <button
                  key={proj.id}
                  onClick={() => setSelectedProjectId(proj.id)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-mono font-semibold transition-all whitespace-nowrap cursor-pointer ${
                    isSelected
                      ? 'bg-[#FF2D6D] text-white shadow-md shadow-[#FF2D6D]/20'
                      : 'bg-[#30000F] text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] border border-[#FFB4C8]/15'
                  }`}
                >
                  {proj.name}
                </button>
              );
            })}
          </div>
        </div>

        {/* Environment Tiers */}
        {currentEnvironments.length > 0 && (
          <div className="flex items-center gap-2 overflow-x-auto pt-2 md:pt-0 border-t md:border-t-0 border-[#FFB4C8]/10">
            <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-xs text-[#A26377] font-mono shrink-0">
              <Layers className="w-4 h-4 text-[#FF2D6D]" />
              <span>Tier:</span>
            </div>

            <div className="flex items-center gap-1.5">
              {currentEnvironments.map((env) => {
                const isSelected = env.id === selectedEnvironmentId;
                const isProd = env.envType === 'PRODUCTION' || env.isProtected;
                const isStaging = env.envType === 'STAGING';

                return (
                  <button
                    key={env.id}
                    onClick={() => setSelectedEnvironmentId(env.id)}
                    className={`px-3 py-1.5 rounded-xl text-xs font-mono font-medium transition-all flex items-center gap-1.5 whitespace-nowrap cursor-pointer ${
                      isSelected
                        ? 'bg-[#3F0016] text-white border-2 border-[#FF2D6D] shadow-md shadow-[#FF2D6D]/20 font-bold'
                        : 'bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15'
                    }`}
                  >
                    {isProd ? (
                      <ShieldCheck className="w-3.5 h-3.5 text-[#F87171]" />
                    ) : isStaging ? (
                      <span className="w-2 h-2 rounded-full bg-[#FBBF24]" />
                    ) : (
                      <span className="w-2 h-2 rounded-full bg-[#60A5FA]" />
                    )}
                    <span>{env.name}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </section>

      {/* Cryptographic Metrics Banner */}
      <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Metric 1 */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Active Secrets
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
              <Key className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-headline font-bold text-white">
              {secrets.length}
            </span>
            <span className="text-[10px] font-mono text-[#4ADE80]">Encrypted</span>
          </div>
        </div>

        {/* Metric 2 */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Cipher Standard
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
              <Lock className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-xl font-headline font-bold text-white">
              AES-256-GCM
            </span>
            <span className="text-[10px] font-mono text-[#F4B5C8]">+ AAD</span>
          </div>
        </div>

        {/* Metric 3 */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Key Wrap Scheme
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
              <ShieldCheck className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-xl font-headline font-bold text-white">
              RFC 3394
            </span>
            <span className="text-[10px] font-mono text-[#A26377]">KMS Wrap</span>
          </div>
        </div>

        {/* Metric 4 */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 shadow-sm flex flex-col justify-between hover:border-[#FFB4C8]/30 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
              Audit Guarantee
            </span>
            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#4ADE80]/30 flex items-center justify-center text-[#4ADE80]">
              <Sparkles className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-lg font-headline font-bold text-[#4ADE80]">
              ZERO-LEAK
            </span>
            <span className="text-[10px] font-mono text-[#A26377]">Telemetry</span>
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
            placeholder="Search secrets by key name..."
            className="w-full pl-9 pr-4 py-2 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs font-mono text-white placeholder-[#A26377] outline-none transition-all"
          />
        </div>

        <div className="flex items-center gap-2 text-xs font-mono text-[#A26377]">
          <span>Showing {secrets.length} encrypted secret{secrets.length === 1 ? '' : 's'}</span>
        </div>
      </section>

      {/* Error Banner */}
      {error && (
        <div className="flex items-center gap-3 p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6]">
          <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
          <span>{error}</span>
        </div>
      )}

      {/* Secrets Table / List */}
      {isLoadingSecrets || isLoadingProjects ? (
        <div className="p-8 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-3 animate-pulse">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-14 rounded-2xl bg-[#30000F]" />
          ))}
        </div>
      ) : secrets.length === 0 ? (
        <div className="p-12 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col items-center justify-center text-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
            <Key className="w-8 h-8" />
          </div>
          <div className="flex flex-col gap-1 max-w-md">
            <h3 className="text-base font-headline font-bold text-white">
              {searchQuery ? 'No secrets match your search' : 'No secrets stored in this environment yet'}
            </h3>
            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              {searchQuery
                ? 'Try a different search query or clear the filter.'
                : 'Secrets added here will be protected with AES-256-GCM envelope encryption and isolated within this environment.'}
            </p>
          </div>
          {!searchQuery && selectedEnvironmentId && (
            <button
              onClick={() => setIsCreateModalOpen(true)}
              className="mt-2 px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-bold shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-2 active:scale-95 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Add First Secret</span>
            </button>
          )}
        </div>
      ) : (
        <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/20 shadow-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[#FFB4C8]/15 bg-[#140007] text-[11px] font-mono text-[#A26377] uppercase tracking-wider">
                  <th className="py-3.5 px-6">Secret Key Name</th>
                  <th className="py-3.5 px-6">Encrypted Value</th>
                  <th className="py-3.5 px-6">Version</th>
                  <th className="py-3.5 px-6">Status</th>
                  <th className="py-3.5 px-6">Last Updated</th>
                  <th className="py-3.5 px-6 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#FFB4C8]/10 text-xs">
                {secrets.map((s) => {
                  const revealed = quickRevealedSecrets[s.id];
                  const isRevealing = quickRevealingId === s.id;

                  return (
                    <tr
                      key={s.id}
                      className="hover:bg-[#30000F]/60 transition-colors group cursor-pointer"
                      onClick={() => setSelectedSecretForDetails(s)}
                    >
                      {/* Key Name */}
                      <td className="py-4 px-6 font-mono font-bold text-white">
                        <div className="flex items-center gap-2.5">
                          <div className="w-7 h-7 rounded-lg bg-[#30000F] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shrink-0">
                            <Key className="w-3.5 h-3.5" />
                          </div>
                          <div className="flex flex-col">
                            <div className="flex items-center gap-2">
                              <span className="hover:text-[#FFB4C8] transition-colors">{s.name}</span>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleCopyKeyName(s.name);
                                }}
                                className="p-1 rounded text-[#A26377] hover:text-white opacity-0 group-hover:opacity-100 transition-opacity"
                                title="Copy Key Name"
                              >
                                {copiedKeyName === s.name ? (
                                  <Check className="w-3 h-3 text-[#4ADE80]" />
                                ) : (
                                  <Copy className="w-3 h-3" />
                                )}
                              </button>
                            </div>
                            {s.description && (
                              <span className="text-[10px] font-sans text-[#A26377] font-normal truncate max-w-xs">
                                {s.description}
                              </span>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Value / Masked */}
                      <td className="py-4 px-6 font-mono">
                        <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                          {revealed ? (
                            <span className="text-[#4ADE80] font-semibold select-all">
                              {revealed.value}
                            </span>
                          ) : (
                            <span className="text-[#A26377] tracking-widest text-xs select-none">
                              ••••••••••••••••
                            </span>
                          )}

                          <button
                            onClick={() => handleQuickReveal(s.id)}
                            disabled={isRevealing}
                            className="p-1.5 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all"
                            title={revealed ? 'Hide' : 'Quick Reveal'}
                          >
                            {revealed ? (
                              <Unlock className="w-3 h-3 text-[#4ADE80]" />
                            ) : (
                              <Lock className="w-3 h-3" />
                            )}
                          </button>
                        </div>
                      </td>

                      {/* Version Badge */}
                      <td className="py-4 px-6 font-mono">
                        <span className="px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] text-[#FFB4C8] border border-[#FFB4C8]/20 font-semibold">
                          v{s.currentVersionNumber || 1}
                        </span>
                      </td>

                      {/* Status */}
                      <td className="py-4 px-6 font-mono">
                        <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] text-[#4ADE80] border border-[#4ADE80]/30 font-semibold">
                          {s.status || 'ACTIVE'}
                        </span>
                      </td>

                      {/* Updated Date */}
                      <td className="py-4 px-6 font-mono text-[#A26377] text-[11px]">
                        {s.updatedAt ? new Date(s.updatedAt).toLocaleDateString() : 'N/A'}
                      </td>

                      {/* Actions */}
                      <td className="py-4 px-6 text-right">
                        <div className="flex items-center justify-end gap-2" onClick={(e) => e.stopPropagation()}>
                          <button
                            onClick={() => setSelectedSecretForDetails(s)}
                            className="px-3 py-1 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 text-xs font-mono font-semibold transition-all flex items-center gap-1.5"
                          >
                            <Eye className="w-3 h-3 text-[#FF2D6D]" />
                            <span>Details</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create Secret Modal (With Single & Bulk .env import modes) */}
      {selectedEnvironmentId && (
        <CreateSecretModal
          isOpen={isCreateModalOpen}
          onClose={() => setIsCreateModalOpen(false)}
          workspaceId={activeWorkspace?.id}
          projectId={selectedProjectId}
          environmentId={selectedEnvironmentId}
          environmentName={currentEnvironment?.name || 'Production'}
          onSecretCreated={handleSecretCreated}
        />
      )}

      {/* Secret Details & Reveal Modal */}
      {selectedSecretForDetails && selectedEnvironmentId && (
        <SecretDetailsModal
          isOpen={!!selectedSecretForDetails}
          onClose={() => setSelectedSecretForDetails(null)}
          secret={selectedSecretForDetails}
          workspaceId={activeWorkspace?.id}
          projectId={selectedProjectId}
          environmentId={selectedEnvironmentId}
          environmentName={currentEnvironment?.name || 'Production'}
          onSecretUpdated={handleSecretUpdated}
          onSecretDeleted={handleSecretDeleted}
        />
      )}
    </div>
  );
};

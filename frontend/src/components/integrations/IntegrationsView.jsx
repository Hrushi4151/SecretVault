import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from '../../context/AuthContext';
import { integrationsApi } from '../../api/integrations';
import { projectApi } from '../../api/projects';
import { environmentApi } from '../../api/environments';
import { secretApi } from '../../api/secrets';
import {
  getProviderMeta,
  AVAILABLE_PROVIDERS_LIST,
} from '../../utils/providerRegistry';
import {
  Network,
  Plus,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Key,
  FolderGit2,
  Globe,
  Trash2,
  Unlink,
  UploadCloud,
  Eye,
  Shield,
  Loader2,
  ExternalLink,
  Layers,
  Check,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  Info,
  Server,
  Zap,
  Search,
  Filter,
  ArrowRight,
  Cloud,
  CheckSquare,
  ShieldCheck,
} from 'lucide-react';

export const IntegrationsView = ({ initialProjectId = null }) => {
  const { activeWorkspace } = useAuth();
  const [activeTab, setActiveTab] = useState(initialProjectId ? 'MAPPINGS' : 'PROVIDERS'); // 'MAPPINGS' | 'PROVIDERS'
  const [mappingViewMode, setMappingViewMode] = useState('PROJECT_WISE'); // 'PROJECT_WISE' | 'TABLE'
  const [selectedProjectFilter, setSelectedProjectFilter] = useState(initialProjectId || 'ALL');
  const [mappingSearch, setMappingSearch] = useState('');
  const [expandedProjectIds, setExpandedProjectIds] = useState(new Set());
  const [providerCategoryFilter, setProviderCategoryFilter] = useState('ALL');
  const [providerSearchQuery, setProviderSearchQuery] = useState('');

  const [integrations, setIntegrations] = useState([]);
  const [mappings, setMappings] = useState([]);
  const [projects, setProjects] = useState([]);
  const [environmentsMap, setEnvironmentsMap] = useState({}); // projectId -> environments[]
  const [isLoading, setIsLoading] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [feedback, setFeedback] = useState(null); // { type: 'success' | 'error' | 'info', message: string }

  // Modals state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isMappingModalOpen, setIsMappingModalOpen] = useState(false);
  const [isPushModalOpen, setIsPushModalOpen] = useState(false);
  const [isInspectModalOpen, setIsInspectModalOpen] = useState(false);
  const [selectedIntegrationForMapping, setSelectedIntegrationForMapping] = useState(null);
  const [selectedMappingForPush, setSelectedMappingForPush] = useState(null);
  const [selectedMappingForInspect, setSelectedMappingForInspect] = useState(null);

  // Form states
  const [providerForm, setProviderForm] = useState({
    providerType: 'VERCEL',
    displayName: '',
    credential: '',
    configuration: '',
  });
  const [mappingForm, setMappingForm] = useState({
    integrationId: '',
    projectId: '',
    environmentId: '',
    providerResourceType: 'PROJECT',
    providerResourceId: '',
    providerResourceName: '',
    providerEnvironment: 'production',
    syncEnabled: true,
  });

  // Discovery & Remote state
  const [discoveredResources, setDiscoveredResources] = useState([]);
  const [isDiscovering, setIsDiscovering] = useState(false);
  const [remoteSecrets, setRemoteSecrets] = useState([]);
  const [isLoadingRemoteSecrets, setIsLoadingRemoteSecrets] = useState(false);
  const [availableSecretsToPush, setAvailableSecretsToPush] = useState([]);
  const [isLoadingSecretsToPush, setIsLoadingSecretsToPush] = useState(false);

  const showFeedback = (type, message) => {
    setFeedback({ type, message });
    setTimeout(() => setFeedback(null), 6000);
  };

  const loadData = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    try {
      setIsLoading(true);
      const [intRes, projRes] = await Promise.all([
        integrationsApi.list(activeWorkspace.id),
        projectApi.list(activeWorkspace.id),
      ]);

      const rawList = intRes?.items || intRes?.content || (Array.isArray(intRes) ? intRes : []);
      // Keep code repository integrations (e.g. GitHub/GitLab used in Settings) completely separate
      // from cloud hosting runtime provider integrations (e.g. Vercel, Render)
      const intList = rawList.filter(
        (i) => i.providerType !== 'GITHUB' && i.providerType !== 'GITLAB'
      );
      setIntegrations(intList);

      const projList = Array.isArray(projRes) ? projRes : (projRes?.items || projRes?.content || []);
      setProjects(projList);

      // Load mappings across all integrations
      const allMappings = [];
      for (const integration of intList) {
        try {
          const mRes = await integrationsApi.listMappings(activeWorkspace.id, integration.id);
          const mList = Array.isArray(mRes) ? mRes : (mRes?.items || mRes?.content || []);
          mList.forEach((m) => {
            allMappings.push({ ...m, integrationName: integration.displayName, providerType: integration.providerType });
          });
        } catch (err) {
          console.error(`Failed to load mappings for integration ${integration.id}`, err);
        }
      }
      setMappings(allMappings);

      // Preload environments for projects
      const envMap = {};
      for (const p of projList) {
        try {
          const eRes = await environmentApi.list(activeWorkspace.id, p.id);
          envMap[p.id] = Array.isArray(eRes) ? eRes : (eRes?.items || eRes?.content || []);
        } catch (err) {
          envMap[p.id] = [];
        }
      }
      setEnvironmentsMap(envMap);
    } catch (err) {
      showFeedback('error', err.message || 'Failed to load provider integrations.');
    } finally {
      setIsLoading(false);
    }
  }, [activeWorkspace?.id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (initialProjectId) {
      setActiveTab('MAPPINGS');
      setSelectedProjectFilter(initialProjectId);
      setExpandedProjectIds((prev) => new Set([...prev, initialProjectId]));
    }
  }, [initialProjectId]);

  useEffect(() => {
    if (projects.length > 0) {
      if (initialProjectId) {
        setExpandedProjectIds(new Set([initialProjectId]));
      } else {
        setExpandedProjectIds(new Set(projects.map((p) => p.id)));
      }
    }
  }, [projects, initialProjectId]);

  const toggleProjectExpand = (projId) => {
    setExpandedProjectIds((prev) => {
      const next = new Set(prev);
      if (next.has(projId)) {
        next.delete(projId);
      } else {
        next.add(projId);
      }
      return next;
    });
  };

  const expandAllProjects = () => {
    setExpandedProjectIds(new Set(projects.map((p) => p.id)));
  };

  const collapseAllProjects = () => {
    setExpandedProjectIds(new Set());
  };

  const handleOpenMappingModalForProject = (project) => {
    const defaultInt = integrations[0];
    setSelectedIntegrationForMapping(defaultInt || null);
    const envList = environmentsMap[project.id] || [];
    setMappingForm({
      integrationId: defaultInt ? defaultInt.id : '',
      projectId: project.id,
      environmentId: envList[0]?.id || '',
      providerResourceType: defaultInt ? getProviderMeta(defaultInt.providerType).defaultResourceType : 'PROJECT',
      providerResourceId: '',
      providerResourceName: '',
      providerEnvironment: 'production',
      syncEnabled: true,
    });
    setIsMappingModalOpen(true);
    if (defaultInt) {
      handleDiscoverResources(defaultInt.id);
    }
  };

  const handleValidateIntegration = async (integrationId) => {
    const target = integrations.find((i) => i.id === integrationId);
    if (target?.providerType === 'GITHUB' || target?.providerType === 'GITLAB') {
      showFeedback('error', 'Repository integrations cannot be validated from Provider Integrations. Manage them in Settings.');
      return;
    }
    try {
      setActionLoadingId(integrationId);
      const res = await integrationsApi.validate(activeWorkspace.id, integrationId);
      if (res?.valid) {
        showFeedback('success', `Connection successful: ${res.message || 'Provider API authenticated successfully.'}`);
      } else {
        showFeedback('error', `Validation failed: ${res?.message || 'Invalid API token or connection error.'}`);
      }
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Validation request failed.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleDeleteIntegration = async (integrationId, name) => {
    const target = integrations.find((i) => i.id === integrationId);
    if (target?.providerType === 'GITHUB' || target?.providerType === 'GITLAB') {
      showFeedback('error', 'Repository integrations cannot be deleted from Provider Integrations. Manage them in Settings.');
      return;
    }
    if (!window.confirm(`Are you sure you want to delete integration "${name}" and all associated resource mappings?`)) {
      return;
    }
    try {
      setActionLoadingId(integrationId);
      await integrationsApi.delete(activeWorkspace.id, integrationId);
      showFeedback('success', `Integration "${name}" deleted successfully.`);
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Failed to delete provider integration.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleDisconnectIntegration = async (integrationId, name) => {
    const target = integrations.find((i) => i.id === integrationId);
    if (target?.providerType === 'GITHUB' || target?.providerType === 'GITLAB') {
      showFeedback('error', 'Repository integrations cannot be disconnected from Provider Integrations. Manage them in Settings.');
      return;
    }
    if (!window.confirm(`Are you sure you want to disconnect integration "${name}"? Existing resource mappings will be preserved, but synchronization will be suspended until re-connected.`)) {
      return;
    }
    try {
      setActionLoadingId(integrationId);
      await integrationsApi.disconnect(activeWorkspace.id, integrationId);
      showFeedback('success', `Integration "${name}" disconnected. Resource mappings preserved.`);
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Failed to disconnect provider integration.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleCreateIntegration = async (e) => {
    e.preventDefault();
    if (!providerForm.displayName.trim() || !providerForm.credential.trim()) {
      showFeedback('error', 'Display Name and API Token are required.');
      return;
    }

    try {
      setIsLoading(true);
      let configObj = null;
      if (providerForm.configuration.trim()) {
        try {
          configObj = JSON.parse(providerForm.configuration);
        } catch (parseErr) {
          showFeedback('error', 'Configuration must be valid JSON format (e.g. {"teamId": "team_123"}).');
          setIsLoading(false);
          return;
        }
      }

      await integrationsApi.create(activeWorkspace.id, {
        providerType: providerForm.providerType,
        displayName: providerForm.displayName.trim(),
        credential: providerForm.credential.trim(),
        configuration: configObj,
      });

      showFeedback('success', `Provider integration "${providerForm.displayName}" created and validated!`);
      setIsCreateModalOpen(false);
      setProviderForm({ providerType: 'VERCEL', displayName: '', credential: '', configuration: '' });
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Failed to create provider integration.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleDiscoverResources = async (integrationId) => {
    try {
      setIsDiscovering(true);
      const res = await integrationsApi.discoverResources(activeWorkspace.id, integrationId);
      const list = Array.isArray(res) ? res : (res?.items || []);
      setDiscoveredResources(list);
      showFeedback('info', `Discovered ${list.length} remote resource(s) from provider.`);
    } catch (err) {
      showFeedback('error', err.message || 'Failed to auto-discover remote resources.');
    } finally {
      setIsDiscovering(false);
    }
  };

  const handleCreateMapping = async (e) => {
    e.preventDefault();
    if (!mappingForm.integrationId || !mappingForm.projectId || !mappingForm.environmentId || !mappingForm.providerResourceId) {
      showFeedback('error', 'Please fill in all required mapping fields.');
      return;
    }

    try {
      setIsLoading(true);
      await integrationsApi.createMapping(activeWorkspace.id, mappingForm.integrationId, {
        projectId: mappingForm.projectId,
        environmentId: mappingForm.environmentId,
        providerResourceType: mappingForm.providerResourceType,
        providerResourceId: mappingForm.providerResourceId.trim(),
        providerResourceName: mappingForm.providerResourceName.trim() || mappingForm.providerResourceId.trim(),
        providerEnvironment: mappingForm.providerEnvironment.trim(),
        syncEnabled: mappingForm.syncEnabled,
      });

      showFeedback('success', 'Resource mapping successfully bound to external provider environment!');
      setIsMappingModalOpen(false);
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Failed to create resource mapping.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeleteMapping = async (mapping) => {
    if (!window.confirm(`Delete mapping for "${mapping.providerResourceName}" (${mapping.providerEnvironment})?`)) {
      return;
    }
    try {
      setActionLoadingId(mapping.id);
      await integrationsApi.deleteMapping(activeWorkspace.id, mapping.providerIntegrationId, mapping.id);
      showFeedback('success', 'Resource mapping deleted successfully.');
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Failed to delete mapping.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleToggleSync = async (mapping) => {
    try {
      setActionLoadingId(mapping.id);
      await integrationsApi.updateMapping(activeWorkspace.id, mapping.providerIntegrationId, mapping.id, {
        syncEnabled: !mapping.syncEnabled,
      });
      showFeedback('success', `Sync ${!mapping.syncEnabled ? 'enabled' : 'disabled'} for ${mapping.providerResourceName}`);
      loadData();
    } catch (err) {
      showFeedback('error', err.message || 'Failed to update sync status.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleOpenPushModal = async (mapping) => {
    setSelectedMappingForPush(mapping);
    setIsPushModalOpen(true);
    try {
      setIsLoadingSecretsToPush(true);
      const res = await secretApi.list(activeWorkspace.id, mapping.projectId, mapping.environmentId);
      const list = Array.isArray(res) ? res : (res?.items || res?.content || []);
      setAvailableSecretsToPush(list);
    } catch (err) {
      showFeedback('error', 'Failed to load secrets for this mapping.');
    } finally {
      setIsLoadingSecretsToPush(false);
    }
  };

  const handleExecutePushSecret = async (secretId, secretKey) => {
    if (!selectedMappingForPush) return;
    try {
      setActionLoadingId(secretId);
      const res = await integrationsApi.pushSecret(
        activeWorkspace.id,
        selectedMappingForPush.providerIntegrationId,
        selectedMappingForPush.id,
        secretId
      );
      showFeedback(
        'success',
        `Secret "${secretKey}" pushed to ${selectedMappingForPush.providerType} (${res?.providerSecretKey || secretKey})!`
      );
    } catch (err) {
      showFeedback('error', err.message || `Failed to push secret "${secretKey}".`);
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleOpenInspectModal = async (mapping) => {
    setSelectedMappingForInspect(mapping);
    setIsInspectModalOpen(true);
    try {
      setIsLoadingRemoteSecrets(true);
      const res = await integrationsApi.listProviderSecrets(
        activeWorkspace.id,
        mapping.providerIntegrationId,
        mapping.id
      );
      setRemoteSecrets(Array.isArray(res) ? res : []);
    } catch (err) {
      showFeedback('error', err.message || 'Failed to inspect remote provider secrets.');
    } finally {
      setIsLoadingRemoteSecrets(false);
    }
  };

  const activeIntegrationsCount = integrations.filter((i) => i.status === 'ACTIVE').length;

  return (
    <div className="flex flex-col gap-6 max-w-7xl mx-auto pb-16 font-body text-white animate-fade-in">
      {/* Top Banner & Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-[#30000F] border border-[#FFB4C8]/15 p-6 rounded-2xl shadow-xl">
        <div className="flex items-start gap-4">
          <div className="w-12 h-12 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/15 shrink-0">
            <Network className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-headline font-bold text-white">Provider Integrations</h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30 uppercase tracking-wide">
                Phase 7 Provider Framework
              </span>
            </div>
            <p className="text-xs text-[#F4B5C8] mt-1 max-w-2xl leading-relaxed">
              Connect external cloud and hosting platforms (Vercel, Render) with envelope encryption to push secrets and synchronize environment variables directly into remote runtime environments.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={loadData}
            disabled={isLoading}
            className="flex items-center gap-2 px-3 py-2 rounded-xl bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30 transition-all text-xs font-semibold cursor-pointer disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setProviderForm({ providerType: 'VERCEL', displayName: '', credential: '', configuration: '' });
              setIsCreateModalOpen(true);
            }}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] shadow-lg shadow-[#FF2D6D]/25 transition-all text-xs font-bold cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Connect Provider</span>
          </button>
        </div>
      </div>

      {/* Feedback Toast */}
      {feedback && (
        <div
          className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-xs animate-fade-in ${
            feedback.type === 'success'
              ? 'bg-[#34D399]/10 border-[#34D399]/30 text-[#34D399]'
              : feedback.type === 'info'
              ? 'bg-[#818CF8]/10 border-[#818CF8]/30 text-[#818CF8]'
              : 'bg-[#F87171]/10 border-[#F87171]/30 text-[#F87171]'
          }`}
        >
          {feedback.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 shrink-0" />
          ) : feedback.type === 'info' ? (
            <Info className="w-4 h-4 shrink-0" />
          ) : (
            <AlertTriangle className="w-4 h-4 shrink-0" />
          )}
          <span className="flex-1 font-medium">{feedback.message}</span>
          <button type="button" onClick={() => setFeedback(null)} className="text-current opacity-70 hover:opacity-100">
            ×
          </button>
        </div>
      )}

      {/* KPI Overview Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Connected Providers</span>
            <Server className="w-4 h-4 text-[#FF2D6D]" />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-headline font-bold text-white">{integrations.length}</span>
            <span className="text-[11px] text-[#34D399] font-mono">{activeIntegrationsCount} Active</span>
          </div>
        </div>

        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Resource Mappings</span>
            <Layers className="w-4 h-4 text-[#818CF8]" />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-headline font-bold text-white">{mappings.length}</span>
            <span className="text-[11px] text-[#F4B5C8] font-mono">Bound Envs</span>
          </div>
        </div>

        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Encryption Layer</span>
            <Shield className="w-4 h-4 text-[#34D399]" />
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-sm font-headline font-bold text-white">AES-256-GCM</span>
            <span className="text-[10px] text-[#A26377] font-mono">Envelope Mode</span>
          </div>
        </div>

        <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl p-4 flex flex-col justify-between">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider">Supported Connectors</span>
            <Zap className="w-4 h-4 text-[#FBBF24]" />
          </div>
          <div className="mt-3 flex items-center gap-1.5 flex-wrap">
            <span className="px-2 py-0.5 rounded bg-[#3F0016] text-[10px] font-mono text-white border border-[#FFB4C8]/15">
              {AVAILABLE_PROVIDERS_LIST.length}+ Platforms
            </span>
            <span className="text-[10px] text-[#A26377] font-mono">
              AWS, Vercel, K8s, Render...
            </span>
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex items-center gap-2 border-b border-[#FFB4C8]/15 pb-2">
        <button
          type="button"
          onClick={() => setActiveTab('MAPPINGS')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
            activeTab === 'MAPPINGS'
              ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
              : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
          }`}
        >
          <Layers className="w-3.5 h-3.5" />
          <span>Resource Mappings (Project-Wise) ({mappings.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('PROVIDERS')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
            activeTab === 'PROVIDERS'
              ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
              : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
          }`}
        >
          <Server className="w-3.5 h-3.5" />
          <span>Connected Providers ({integrations.length})</span>
        </button>
      </div>

      {/* TAB 1: CONNECTED PROVIDERS */}
      {activeTab === 'PROVIDERS' && (
        <div className="flex flex-col gap-4">
          {integrations.length === 0 && !isLoading ? (
            <div className="flex flex-col items-center justify-center p-12 bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl text-center gap-3">
              <div className="w-14 h-14 rounded-2xl bg-[#3F0016] border border-[#FFB4C8]/20 flex items-center justify-center text-[#FF2D6D]">
                <Network className="w-7 h-7 opacity-70" />
              </div>
              <h3 className="text-base font-bold text-white">No Provider Integrations Connected</h3>
              <p className="text-xs text-[#F4B5C8] max-w-md">
                Connect your Vercel or Render account using an API token to enable bidirectional secret management and sync automation.
              </p>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(true)}
                className="mt-2 flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold shadow-lg shadow-[#FF2D6D]/25 transition-all cursor-pointer"
              >
                <Plus className="w-4 h-4" />
                <span>Connect First Provider</span>
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {integrations.map((item) => {
                const meta = getProviderMeta(item.providerType);
                const isActive = item.status === 'ACTIVE';
                const isRevoked = item.status === 'REVOKED';
                const isError = item.status === 'ERROR';
                const itemMappings = mappings.filter((m) => m.providerIntegrationId === item.id);
                const linkedProjectIds = [...new Set(itemMappings.map((m) => m.projectId))];
                const linkedProjects = projects.filter((p) => linkedProjectIds.includes(p.id));

                return (
                  <div
                    key={item.id}
                    className="bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30 rounded-2xl p-5 flex flex-col justify-between gap-4 transition-all shadow-lg"
                  >
                    <div className="flex flex-col gap-3">
                      {/* Top Row: Provider Logo & Status */}
                      <div className="flex items-start justify-between">
                        <div className="flex items-center gap-3">
                          <div className={`w-10 h-10 rounded-xl border flex items-center justify-center text-lg font-bold shadow-sm ${meta.badgeBg} ${meta.badgeBorder} ${meta.badgeText}`}>
                            {meta.iconSymbol}
                          </div>
                          <div className="flex flex-col">
                            <span className="text-sm font-bold text-white">{item.displayName}</span>
                            <span className="text-[10px] font-mono text-[#A26377]">
                              {meta.displayName} ({meta.category}) • {itemMappings.length} mapping{itemMappings.length !== 1 ? 's' : ''}
                            </span>
                          </div>
                        </div>

                        <span
                          className={`px-2.5 py-1 rounded-full text-[10px] font-mono font-bold uppercase flex items-center gap-1.5 ${
                            isActive
                              ? 'bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30'
                              : isRevoked
                              ? 'bg-[#9CA3AF]/15 text-[#9CA3AF] border border-[#9CA3AF]/30'
                              : isError
                              ? 'bg-[#F87171]/15 text-[#F87171] border border-[#F87171]/30'
                              : 'bg-[#FBBF24]/15 text-[#FBBF24] border border-[#FBBF24]/30'
                          }`}
                        >
                          <span
                            className={`w-1.5 h-1.5 rounded-full ${
                              isActive ? 'bg-[#34D399]' : isRevoked ? 'bg-[#9CA3AF]' : isError ? 'bg-[#F87171]' : 'bg-[#FBBF24] animate-pulse'
                            }`}
                          />
                          {item.status}
                        </span>
                      </div>

                      {/* Credential Hint */}
                      <div className="flex items-center justify-between px-3 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 text-xs font-mono text-[#F4B5C8]">
                        <div className="flex items-center gap-2">
                          <Key className="w-3.5 h-3.5 text-[#FF2D6D]" />
                          <span>Token:</span>
                          <span className="text-white">{item.redactedCredentialHint || '••••••••••••••••'}</span>
                        </div>
                        <span className="text-[10px] text-[#34D399]">Encrypted</span>
                      </div>

                      {/* Error Banner if any */}
                      {item.lastErrorMessage && (
                        <div className="px-3 py-2 rounded-xl bg-[#F87171]/10 border border-[#F87171]/20 text-[11px] text-[#F87171] flex items-center gap-2">
                          <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                          <span className="truncate">{item.lastErrorMessage}</span>
                        </div>
                      )}

                      {/* Metadata / Timestamps */}
                      <div className="grid grid-cols-2 gap-2 text-[10px] font-mono text-[#A26377]">
                        <div>
                          <span>Created: </span>
                          <span className="text-[#F4B5C8]">{new Date(item.createdAt).toLocaleDateString()}</span>
                        </div>
                        <div>
                          <span>Last Validated: </span>
                          <span className="text-[#F4B5C8]">
                            {item.lastValidatedAt ? new Date(item.lastValidatedAt).toLocaleTimeString() : 'Never'}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Bottom Action Buttons */}
                    <div className="flex items-center justify-between border-t border-[#FFB4C8]/10 pt-3 gap-2">
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => handleValidateIntegration(item.id)}
                          disabled={actionLoadingId === item.id}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#3F0016] text-xs font-semibold text-white hover:bg-[#4A001C] border border-[#FFB4C8]/15 transition-all cursor-pointer disabled:opacity-50"
                        >
                          {actionLoadingId === item.id ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin text-[#FF2D6D]" />
                          ) : (
                            <CheckCircle2 className="w-3.5 h-3.5 text-[#34D399]" />
                          )}
                          <span>Test Connection</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => {
                            setSelectedIntegrationForMapping(item);
                            setMappingForm((prev) => ({
                              ...prev,
                              integrationId: item.id,
                              projectId: projects[0]?.id || '',
                              environmentId: projects[0] ? environmentsMap[projects[0].id]?.[0]?.id || '' : '',
                            }));
                            setIsMappingModalOpen(true);
                            handleDiscoverResources(item.id);
                          }}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#FF2D6D]/15 text-xs font-semibold text-[#FF85A2] hover:bg-[#FF2D6D]/25 border border-[#FF2D6D]/30 transition-all cursor-pointer"
                        >
                          <Plus className="w-3.5 h-3.5" />
                          <span>Add Mapping</span>
                        </button>
                      </div>

                      <div className="flex items-center gap-1">
                        {item.status !== 'REVOKED' && (
                          <button
                            type="button"
                            onClick={() => handleDisconnectIntegration(item.id, item.displayName)}
                            disabled={actionLoadingId === item.id}
                            className="p-2 rounded-xl text-[#A26377] hover:text-[#FBBF24] hover:bg-[#3F0016] transition-colors cursor-pointer disabled:opacity-50"
                            title="Disconnect Integration (Preserve Mappings)"
                          >
                            <Unlink className="w-4 h-4" />
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => handleDeleteIntegration(item.id, item.displayName)}
                          disabled={actionLoadingId === item.id}
                          className="p-2 rounded-xl text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors cursor-pointer disabled:opacity-50"
                          title="Delete Integration"
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
        </div>
      )}

      {/* TAB: RESOURCE MAPPINGS (PROJECT-WISE & DYNAMIC PLATFORMS) */}
      {activeTab === 'MAPPINGS' && (
        <div className="flex flex-col gap-5">
          {/* Section Header Card */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#30000F] border border-[#FFB4C8]/15 p-5 rounded-2xl shadow-sm">
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <Layers className="w-4 h-4 text-[#FF2D6D]" />
                <h2 className="text-base font-bold text-white">Project-Centric Resource Mappings</h2>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#FF2D6D]/15 text-[#FF85A2] border border-[#FF2D6D]/30">
                  {mappings.length} Active Routes
                </span>
              </div>
              <p className="text-xs text-[#F4B5C8]">
                Organized project-by-project. Identify exactly which local project environment is sharing secrets to which external cloud platform.
              </p>
            </div>

            {integrations.length > 0 && (
              <button
                type="button"
                onClick={() => {
                  const defaultInt = integrations[0];
                  setSelectedIntegrationForMapping(defaultInt);
                  setMappingForm((prev) => ({
                    ...prev,
                    integrationId: defaultInt.id,
                    projectId: projects[0]?.id || '',
                    environmentId: projects[0] ? environmentsMap[projects[0].id]?.[0]?.id || '' : '',
                    providerResourceType: getProviderMeta(defaultInt.providerType).defaultResourceType,
                  }));
                  setIsMappingModalOpen(true);
                  handleDiscoverResources(defaultInt.id);
                }}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold cursor-pointer transition-all shadow-md shadow-[#FF2D6D]/20 self-start sm:self-auto"
              >
                <Plus className="w-4 h-4" />
                <span>New Resource Mapping</span>
              </button>
            )}
          </div>

          {/* Filtering and Controls Toolbar */}
          <div className="flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-3 p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15">
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 flex-1">
              {/* Search Bar */}
              <div className="relative flex-1 min-w-[240px]">
                <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#A26377]" />
                <input
                  type="text"
                  value={mappingSearch}
                  onChange={(e) => setMappingSearch(e.target.value)}
                  placeholder="Filter by project, environment, or target platform..."
                  className="w-full pl-9 pr-4 py-2 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs text-white placeholder-[#A26377] outline-none transition-all"
                />
              </div>

              {/* Project Filter Dropdown */}
              <div className="flex items-center gap-2">
                <Filter className="w-3.5 h-3.5 text-[#A26377] shrink-0" />
                <select
                  value={selectedProjectFilter}
                  onChange={(e) => setSelectedProjectFilter(e.target.value)}
                  className="px-3 py-2 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] cursor-pointer"
                >
                  <option value="ALL">All Projects ({projects.length})</option>
                  {projects.map((p) => {
                    const count = mappings.filter((m) => m.projectId === p.id).length;
                    return (
                      <option key={p.id} value={p.id}>
                        {p.name} ({count} mapping{count !== 1 ? 's' : ''})
                      </option>
                    );
                  })}
                </select>
              </div>
            </div>

            {/* View Mode & Expand/Collapse Toggles */}
            <div className="flex items-center justify-between sm:justify-end gap-2 pt-2 lg:pt-0 border-t lg:border-t-0 border-[#FFB4C8]/10">
              {mappingViewMode === 'PROJECT_WISE' && (
                <div className="flex items-center gap-1.5 mr-2">
                  <button
                    type="button"
                    onClick={expandAllProjects}
                    className="px-2.5 py-1.5 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#A26377] hover:text-white text-[11px] font-mono transition-colors cursor-pointer"
                  >
                    Expand All
                  </button>
                  <button
                    type="button"
                    onClick={collapseAllProjects}
                    className="px-2.5 py-1.5 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#A26377] hover:text-white text-[11px] font-mono transition-colors cursor-pointer"
                  >
                    Collapse All
                  </button>
                </div>
              )}

              <div className="flex items-center rounded-xl bg-[#30000F] p-1 border border-[#FFB4C8]/15 text-xs">
                <button
                  type="button"
                  onClick={() => setMappingViewMode('PROJECT_WISE')}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                    mappingViewMode === 'PROJECT_WISE'
                      ? 'bg-[#FF2D6D] text-white shadow-sm font-bold'
                      : 'text-[#A26377] hover:text-white'
                  }`}
                  title="Group mappings project by project"
                >
                  <FolderGit2 className="w-3.5 h-3.5" />
                  <span>By Project</span>
                </button>
                <button
                  type="button"
                  onClick={() => setMappingViewMode('TABLE')}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                    mappingViewMode === 'TABLE'
                      ? 'bg-[#FF2D6D] text-white shadow-sm font-bold'
                      : 'text-[#A26377] hover:text-white'
                  }`}
                  title="View all mappings in a tabular list"
                >
                  <Layers className="w-3.5 h-3.5" />
                  <span>Table View</span>
                </button>
              </div>
            </div>
          </div>

          {/* VIEW MODE 1: PROJECT-WISE ACCORDION CARDS */}
          {mappingViewMode === 'PROJECT_WISE' && (() => {
            const displayedProjects = projects.filter((p) => {
              if (selectedProjectFilter !== 'ALL' && p.id !== selectedProjectFilter) {
                return false;
              }
              if (!mappingSearch.trim()) return true;
              const q = mappingSearch.toLowerCase();
              const pMatch = p.name?.toLowerCase().includes(q) || p.slug?.toLowerCase().includes(q);
              const pMaps = mappings.filter((m) => m.projectId === p.id);
              const mapMatch = pMaps.some(
                (m) =>
                  m.providerResourceName?.toLowerCase().includes(q) ||
                  m.providerType?.toLowerCase().includes(q) ||
                  m.providerEnvironment?.toLowerCase().includes(q) ||
                  m.integrationName?.toLowerCase().includes(q)
              );
              return pMatch || mapMatch;
            });

            if (displayedProjects.length === 0) {
              return (
                <div className="p-12 bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl text-center flex flex-col items-center gap-3">
                  <FolderGit2 className="w-10 h-10 text-[#A26377]" />
                  <h3 className="text-sm font-bold text-white">No Projects Match Your Filter</h3>
                  <p className="text-xs text-[#F4B5C8] max-w-sm">
                    {mappingSearch ? 'Try clearing your search terms to see all project mappings.' : 'No projects found in this workspace.'}
                  </p>
                </div>
              );
            }

            return (
              <div className="flex flex-col gap-4">
                {displayedProjects.map((proj) => {
                  const projMappings = mappings.filter((m) => m.projectId === proj.id);
                  const isExpanded = expandedProjectIds.has(proj.id);
                  const uniqueProviderTypes = [...new Set(projMappings.map((m) => m.providerType))];
                  const activeSyncCount = projMappings.filter((m) => m.syncEnabled).length;
                  const envList = environmentsMap[proj.id] || [];

                  return (
                    <div
                      key={proj.id}
                      className="bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30 rounded-2xl overflow-hidden transition-all shadow-md"
                    >
                      {/* Project Header Bar */}
                      <div
                        onClick={() => toggleProjectExpand(proj.id)}
                        className="p-4 sm:p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 cursor-pointer select-none bg-[#30000F] hover:bg-[#380012] transition-colors"
                      >
                        {/* Left: Project Identity */}
                        <div className="flex items-center gap-3.5">
                          <div className="w-10 h-10 rounded-xl bg-[#1E000A] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shrink-0">
                            <FolderGit2 className="w-5 h-5" />
                          </div>
                          <div className="flex flex-col">
                            <div className="flex items-center gap-2">
                              <h3 className="text-sm font-bold text-white">{proj.name}</h3>
                              <span className="text-[10px] font-mono text-[#A26377]">({proj.slug})</span>
                            </div>
                            <div className="flex items-center gap-2 mt-0.5">
                              <span className="text-[11px] font-mono text-[#FF85A2]">
                                {envList.length} local env{envList.length !== 1 ? 's' : ''}
                              </span>
                              <span className="text-[#A26377]">•</span>
                              <span className="text-[11px] font-mono text-[#34D399]">
                                {activeSyncCount} active sync{activeSyncCount !== 1 ? 's' : ''}
                              </span>
                            </div>
                          </div>
                        </div>

                        {/* Center: Connected External Platform Badges */}
                        <div className="flex items-center gap-2 flex-wrap">
                          {uniqueProviderTypes.length === 0 ? (
                            <span className="text-[10px] font-mono text-[#A26377] italic">
                              No external platforms connected
                            </span>
                          ) : (
                            uniqueProviderTypes.map((pType) => {
                              const meta = getProviderMeta(pType);
                              const count = projMappings.filter((m) => m.providerType === pType).length;
                              return (
                                <div
                                  key={pType}
                                  title={`${meta.displayName} (${count} resource mapping${count !== 1 ? 's' : ''})`}
                                  className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-mono font-bold border ${meta.badgeBg} ${meta.badgeBorder} ${meta.badgeText}`}
                                >
                                  <span>{meta.iconSymbol}</span>
                                  <span>{meta.shortName}</span>
                                  <span className="opacity-75 text-[9px]">({count})</span>
                                </div>
                              );
                            })
                          )}
                        </div>

                        {/* Right: Actions & Expand Chevron */}
                        <div className="flex items-center gap-2 self-end md:self-auto" onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            onClick={() => handleOpenMappingModalForProject(proj)}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#FF2D6D]/15 hover:bg-[#FF2D6D] text-[#FF85A2] hover:text-white border border-[#FF2D6D]/30 text-xs font-bold transition-all cursor-pointer shadow-sm"
                            title="Add an external cloud platform mapping to this project"
                          >
                            <Plus className="w-3.5 h-3.5" />
                            <span>Map Platform</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => toggleProjectExpand(proj.id)}
                            className="p-1.5 rounded-xl text-[#A26377] hover:text-white hover:bg-[#1E000A] transition-colors cursor-pointer"
                            title={isExpanded ? 'Collapse project' : 'Expand project'}
                          >
                            {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                          </button>
                        </div>
                      </div>

                      {/* Project Body (Expanded) */}
                      {isExpanded && (
                        <div className="border-t border-[#FFB4C8]/10 bg-[#1E000A]/60 p-4 sm:p-5 flex flex-col gap-3">
                          {projMappings.length === 0 ? (
                            <div className="p-6 rounded-xl border border-dashed border-[#FFB4C8]/20 flex flex-col sm:flex-row items-center justify-between gap-4 text-center sm:text-left bg-[#30000F]/30">
                              <div className="flex items-center gap-3">
                                <div className="w-9 h-9 rounded-lg bg-[#3F0016] text-[#FF2D6D] flex items-center justify-center shrink-0">
                                  <Cloud className="w-5 h-5" />
                                </div>
                                <div className="flex flex-col">
                                  <span className="text-xs font-bold text-white">No external platforms mapped to {proj.name}</span>
                                  <span className="text-[11px] text-[#A26377]">
                                    Bind any local environment to Vercel, AWS, Render, Railway, or another cloud service to automate secret distribution.
                                  </span>
                                </div>
                              </div>
                              <button
                                type="button"
                                onClick={() => handleOpenMappingModalForProject(proj)}
                                className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF4D85] text-white text-xs font-bold transition-all cursor-pointer shadow-sm shrink-0"
                              >
                                <Plus className="w-3.5 h-3.5" />
                                <span>Connect First Platform</span>
                              </button>
                            </div>
                          ) : (
                            <div className="grid grid-cols-1 gap-3">
                              {projMappings.map((m) => {
                                const meta = getProviderMeta(m.providerType);
                                const env = envList.find((e) => e.id === m.environmentId);
                                const isSyncing = actionLoadingId === m.id;
                                const isProdEnv = env?.envType === 'PRODUCTION' || env?.isProtected;
                                const isStagingEnv = env?.envType === 'STAGING';

                                return (
                                  <div
                                    key={m.id}
                                    className="p-4 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FF2D6D]/40 transition-all flex flex-col lg:flex-row lg:items-center justify-between gap-4 shadow-sm"
                                  >
                                    {/* Pipeline Visual Route */}
                                    <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3 sm:gap-5 flex-1">
                                      {/* Local Source */}
                                      <div className="flex items-center gap-2.5 min-w-[180px]">
                                        <div className="w-8 h-8 rounded-lg bg-[#1E000A] border border-[#FFB4C8]/20 flex items-center justify-center text-[#FF2D6D] shrink-0 font-bold text-xs">
                                          SV
                                        </div>
                                        <div className="flex flex-col">
                                          <span className="text-[10px] font-mono text-[#A26377] uppercase font-bold">Local Source</span>
                                          <div className="flex items-center gap-1.5 mt-0.5">
                                            <span
                                              className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold border flex items-center gap-1 ${
                                                isProdEnv
                                                  ? 'bg-[#3F0016] text-[#F87171] border-[#F87171]/40'
                                                  : isStagingEnv
                                                  ? 'bg-[#3F0016] text-[#FBBF24] border-[#FBBF24]/40'
                                                  : 'bg-[#3F0016] text-[#60A5FA] border-[#60A5FA]/40'
                                              }`}
                                            >
                                              {isProdEnv && <ShieldCheck className="w-3 h-3 text-[#F87171]" />}
                                              <span>{env?.name || m.environmentId}</span>
                                            </span>
                                          </div>
                                        </div>
                                      </div>

                                      {/* Sync Engine Arrow */}
                                      <div className="flex items-center gap-2 text-[#A26377] font-mono text-[10px] py-1 sm:py-0">
                                        <div className="hidden sm:flex items-center gap-1">
                                          <div className="w-6 h-0.5 bg-[#FF2D6D]/40" />
                                          <ArrowRight className="w-3.5 h-3.5 text-[#FF2D6D]" />
                                        </div>
                                        <button
                                          type="button"
                                          onClick={() => handleToggleSync(m)}
                                          disabled={isSyncing}
                                          className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold transition-all cursor-pointer ${
                                            m.syncEnabled
                                              ? 'bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30 hover:bg-[#34D399]/25'
                                              : 'bg-[#A26377]/15 text-[#A26377] border border-[#A26377]/30 hover:bg-[#A26377]/25'
                                          }`}
                                          title="Toggle automated secret sync on update"
                                        >
                                          <span className={`w-1.5 h-1.5 rounded-full ${m.syncEnabled ? 'bg-[#34D399]' : 'bg-[#A26377]'}`} />
                                          <span>{m.syncEnabled ? 'SYNC ACTIVE' : 'DISABLED'}</span>
                                        </button>
                                      </div>

                                      {/* External Target */}
                                      <div className="flex items-center gap-2.5 flex-1 min-w-[200px]">
                                        <div
                                          className={`w-8 h-8 rounded-lg border flex items-center justify-center font-bold text-xs shrink-0 ${meta.badgeBg} ${meta.badgeBorder} ${meta.badgeText}`}
                                        >
                                          {meta.iconSymbol}
                                        </div>
                                        <div className="flex flex-col">
                                          <div className="flex items-center gap-2">
                                            <span className="text-[10px] font-mono text-[#A26377] uppercase font-bold">
                                              {meta.displayName} Target
                                            </span>
                                            <span className="px-1.5 py-0.2 rounded text-[9px] font-mono font-bold bg-[#818CF8]/20 text-[#818CF8] border border-[#818CF8]/30">
                                              {m.providerEnvironment}
                                            </span>
                                          </div>
                                          <div className="flex items-center gap-2 mt-0.5">
                                            <span className="text-xs font-mono font-bold text-white">
                                              {m.providerResourceName || m.providerResourceId}
                                            </span>
                                            <span className="text-[10px] font-mono text-[#A26377] truncate max-w-[140px]">
                                              (ID: {m.providerResourceId})
                                            </span>
                                          </div>
                                        </div>
                                      </div>
                                    </div>

                                    {/* Action Buttons */}
                                    <div className="flex items-center gap-2 border-t sm:border-t-0 border-[#FFB4C8]/10 pt-2 sm:pt-0 justify-end shrink-0">
                                      <button
                                        type="button"
                                        onClick={() => handleOpenPushModal(m)}
                                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#FF2D6D]/15 text-[#FF85A2] hover:bg-[#FF2D6D] hover:text-white border border-[#FF2D6D]/30 text-xs font-semibold transition-all cursor-pointer shadow-sm"
                                        title="Push secrets to this remote target"
                                      >
                                        <UploadCloud className="w-3.5 h-3.5" />
                                        <span>Push Secrets</span>
                                      </button>

                                      <button
                                        type="button"
                                        onClick={() => handleOpenInspectModal(m)}
                                        className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-[#1E000A] text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] border border-[#FFB4C8]/15 text-xs transition-colors cursor-pointer"
                                        title="Inspect remote secrets on this platform"
                                      >
                                        <Eye className="w-3.5 h-3.5" />
                                        <span>Inspect</span>
                                      </button>

                                      <button
                                        type="button"
                                        onClick={() => handleDeleteMapping(m)}
                                        disabled={isSyncing}
                                        className="p-2 rounded-xl text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors cursor-pointer disabled:opacity-50"
                                        title="Unlink mapping"
                                      >
                                        <Trash2 className="w-3.5 h-3.5" />
                                      </button>
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            );
          })()}

          {/* VIEW MODE 2: FLAT TABLE VIEW */}
          {mappingViewMode === 'TABLE' && (
            <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl overflow-hidden shadow-xl">
              {mappings.length === 0 ? (
                <div className="p-8 text-center flex flex-col items-center gap-2">
                  <Layers className="w-8 h-8 text-[#A26377]" />
                  <span className="text-sm font-bold text-white">No Resource Mappings Configured</span>
                  <span className="text-xs text-[#F4B5C8] max-w-sm">
                    Map a SecretVault project environment to an external cloud platform to push and synchronize secrets.
                  </span>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-[#1E000A] text-[#A26377] font-mono text-[10px] uppercase border-b border-[#FFB4C8]/15 tracking-wider">
                      <tr>
                        <th className="py-3 px-4">Local Project &amp; Env</th>
                        <th className="py-3 px-4">Platform Connector</th>
                        <th className="py-3 px-4">Remote Target Resource</th>
                        <th className="py-3 px-4">Remote Env</th>
                        <th className="py-3 px-4">Sync Engine</th>
                        <th className="py-3 px-4 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#FFB4C8]/10 text-white font-body">
                      {mappings
                        .filter((m) => {
                          if (selectedProjectFilter !== 'ALL' && m.projectId !== selectedProjectFilter) return false;
                          if (!mappingSearch.trim()) return true;
                          const q = mappingSearch.toLowerCase();
                          return (
                            m.providerResourceName?.toLowerCase().includes(q) ||
                            m.providerType?.toLowerCase().includes(q) ||
                            m.providerEnvironment?.toLowerCase().includes(q) ||
                            m.integrationName?.toLowerCase().includes(q)
                          );
                        })
                        .map((m) => {
                          const proj = projects.find((p) => p.id === m.projectId);
                          const envList = environmentsMap[m.projectId] || [];
                          const env = envList.find((e) => e.id === m.environmentId);
                          const meta = getProviderMeta(m.providerType);
                          const isSyncing = actionLoadingId === m.id;

                          return (
                            <tr key={m.id} className="hover:bg-[#3F0016]/50 transition-colors">
                              <td className="py-3 px-4">
                                <div className="flex flex-col">
                                  <span className="font-semibold text-white">{proj?.name || m.projectId}</span>
                                  <span className="text-[10px] font-mono text-[#FF85A2]">{env?.name || m.environmentId}</span>
                                </div>
                              </td>

                              <td className="py-3 px-4">
                                <div className="flex items-center gap-2">
                                  <span className={`w-5 h-5 rounded flex items-center justify-center font-bold text-[10px] border ${meta.badgeBg} ${meta.badgeBorder} ${meta.badgeText}`}>
                                    {meta.iconSymbol}
                                  </span>
                                  <span className="font-medium text-xs text-white">{m.integrationName || meta.displayName}</span>
                                </div>
                              </td>

                              <td className="py-3 px-4">
                                <div className="flex flex-col">
                                  <span className="font-mono text-xs font-semibold text-white">{m.providerResourceName || m.providerResourceId}</span>
                                  <span className="text-[10px] font-mono text-[#A26377]">ID: {m.providerResourceId}</span>
                                </div>
                              </td>

                              <td className="py-3 px-4">
                                <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#818CF8]/20 text-[#818CF8] border border-[#818CF8]/30">
                                  {m.providerEnvironment}
                                </span>
                              </td>

                              <td className="py-3 px-4">
                                <button
                                  type="button"
                                  onClick={() => handleToggleSync(m)}
                                  disabled={isSyncing}
                                  className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold transition-all cursor-pointer ${
                                    m.syncEnabled
                                      ? 'bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30 hover:bg-[#34D399]/25'
                                      : 'bg-[#A26377]/15 text-[#A26377] border border-[#A26377]/30 hover:bg-[#A26377]/25'
                                  }`}
                                >
                                  <span className={`w-1.5 h-1.5 rounded-full ${m.syncEnabled ? 'bg-[#34D399]' : 'bg-[#A26377]'}`} />
                                  <span>{m.syncEnabled ? 'SYNC ON' : 'DISABLED'}</span>
                                </button>
                              </td>

                              <td className="py-3 px-4 text-right">
                                <div className="flex items-center justify-end gap-2">
                                  <button
                                    type="button"
                                    onClick={() => handleOpenPushModal(m)}
                                    className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#FF2D6D]/15 text-[#FF85A2] hover:bg-[#FF2D6D]/25 border border-[#FF2D6D]/30 text-[11px] font-semibold transition-colors cursor-pointer"
                                    title="Push secrets to this provider target"
                                  >
                                    <UploadCloud className="w-3.5 h-3.5" />
                                    <span>Push</span>
                                  </button>

                                  <button
                                    type="button"
                                    onClick={() => handleOpenInspectModal(m)}
                                    className="p-1.5 rounded-lg bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-colors cursor-pointer"
                                    title="Inspect remote secrets"
                                  >
                                    <Eye className="w-3.5 h-3.5" />
                                  </button>

                                  <button
                                    type="button"
                                    onClick={() => handleDeleteMapping(m)}
                                    disabled={isSyncing}
                                    className="p-1.5 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors cursor-pointer"
                                    title="Delete Mapping"
                                  >
                                    <Trash2 className="w-3.5 h-3.5" />
                                  </button>
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
          )}
        </div>
      )}

      {/* MODAL 1: CONNECT PROVIDER */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#30000F] border border-[#FFB4C8]/25 rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-hidden shadow-2xl animate-scale-in flex flex-col">
            <div className="px-6 py-4 border-b border-[#FFB4C8]/15 flex items-center justify-between shrink-0">
              <div className="flex items-center gap-2">
                <Network className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-bold text-white">Connect External Cloud Provider</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="text-[#A26377] hover:text-white text-lg font-bold cursor-pointer"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateIntegration} className="p-6 flex flex-col gap-4 text-xs overflow-y-auto">
              {/* Dynamic Extensible Provider Selection */}
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                    Select External Platform ({AVAILABLE_PROVIDERS_LIST.length} Available)
                  </label>
                  <span className="text-[10px] font-mono text-[#34D399]">Extensible Registry</span>
                </div>

                {/* Search Bar for Providers */}
                <div className="relative">
                  <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-[#A26377]" />
                  <input
                    type="text"
                    value={providerSearchQuery}
                    onChange={(e) => setProviderSearchQuery(e.target.value)}
                    placeholder="Search cloud platform (e.g. AWS, Vercel, Render, Railway, Fly.io, Azure, GCP)..."
                    className="w-full pl-8 pr-3 py-1.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 text-xs text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D]"
                  />
                </div>

                {/* Provider Scrollable Grid */}
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 max-h-52 overflow-y-auto p-1 rounded-xl bg-[#1E000A]/60 border border-[#FFB4C8]/10">
                  {AVAILABLE_PROVIDERS_LIST.filter((p) => {
                    if (!providerSearchQuery.trim()) return true;
                    const q = providerSearchQuery.toLowerCase();
                    return (
                      p.displayName.toLowerCase().includes(q) ||
                      p.id.toLowerCase().includes(q) ||
                      p.category.toLowerCase().includes(q) ||
                      p.description.toLowerCase().includes(q)
                    );
                  }).map((p) => {
                    const isSelected = providerForm.providerType === p.id;
                    return (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => {
                          setProviderForm({
                            ...providerForm,
                            providerType: p.id,
                            displayName: providerForm.displayName || `${p.displayName} Production`,
                          });
                        }}
                        className={`p-2.5 rounded-xl border text-left cursor-pointer transition-all flex flex-col gap-1.5 relative ${
                          isSelected
                            ? 'bg-[#FF2D6D]/15 border-[#FF2D6D] text-white shadow-sm ring-1 ring-[#FF2D6D]'
                            : 'bg-[#1E000A] border-[#FFB4C8]/15 text-[#F4B5C8] hover:border-[#FFB4C8]/30 hover:bg-[#3F0016]/40'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div className={`w-7 h-7 rounded-lg border flex items-center justify-center font-bold text-xs ${p.badgeBg} ${p.badgeBorder} ${p.badgeText}`}>
                            {p.iconSymbol}
                          </div>
                          <span className="text-[9px] font-mono text-[#A26377] truncate max-w-[80px]">
                            {p.category.split(' ')[0]}
                          </span>
                        </div>
                        <div className="flex flex-col">
                          <span className="font-bold text-xs text-white">{p.displayName}</span>
                          <span className="text-[10px] text-[#A26377] line-clamp-1">{p.description}</span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Display Name */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  Connection Display Name
                </label>
                <input
                  type="text"
                  placeholder="e.g. Production AWS Org, Main Vercel Team, Staging Render"
                  value={providerForm.displayName}
                  onChange={(e) => setProviderForm({ ...providerForm, displayName: e.target.value })}
                  className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D]"
                  required
                />
              </div>

              {/* API Token / Credential */}
              {(() => {
                const curMeta = getProviderMeta(providerForm.providerType);
                return (
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between">
                      <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                        {curMeta.displayName} API Token / Access Key
                      </label>
                      <span className="text-[10px] font-mono text-[#34D399]">AES-256 Envelope Encrypted</span>
                    </div>
                    <input
                      type="password"
                      placeholder={`Paste ${curMeta.displayName} API key or access token...`}
                      value={providerForm.credential}
                      onChange={(e) => setProviderForm({ ...providerForm, credential: e.target.value })}
                      className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] font-mono focus:outline-none focus:border-[#FF2D6D]"
                      required
                    />
                  </div>
                );
              })()}

              {/* Optional Config JSON */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  Optional Provider Configuration (JSON)
                </label>
                <input
                  type="text"
                  placeholder='e.g. {"teamId": "team_abc123", "region": "us-east-1"}'
                  value={providerForm.configuration}
                  onChange={(e) => setProviderForm({ ...providerForm, configuration: e.target.value })}
                  className="px-3.5 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] font-mono text-[11px] focus:outline-none focus:border-[#FF2D6D]"
                />
              </div>

              {/* Buttons */}
              <div className="flex items-center justify-end gap-3 mt-2 pt-3 border-t border-[#FFB4C8]/15">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-[#F4B5C8] hover:bg-[#3F0016] text-xs font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isLoading}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold shadow-lg shadow-[#FF2D6D]/25 transition-all cursor-pointer disabled:opacity-50"
                >
                  {isLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Save &amp; Connect Provider</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: ADD RESOURCE MAPPING (PROJECT-CENTRIC) */}
      {isMappingModalOpen && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#30000F] border border-[#FFB4C8]/25 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-scale-in">
            <div className="px-6 py-4 border-b border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Layers className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-bold text-white">Create Project Resource Mapping</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsMappingModalOpen(false)}
                className="text-[#A26377] hover:text-white text-lg font-bold cursor-pointer"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateMapping} className="p-6 flex flex-col gap-4 text-xs">
              {/* Visual Step Pipeline Breadcrumbs */}
              <div className="flex items-center justify-between p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 text-[11px] font-mono">
                <div className="flex items-center gap-1.5 text-white font-bold">
                  <span className="w-5 h-5 rounded-full bg-[#FF2D6D] text-white flex items-center justify-center text-[10px]">1</span>
                  <span>Project &amp; Env</span>
                </div>
                <ArrowRight className="w-3.5 h-3.5 text-[#A26377]" />
                <div className="flex items-center gap-1.5 text-white font-bold">
                  <span className="w-5 h-5 rounded-full bg-[#FF2D6D] text-white flex items-center justify-center text-[10px]">2</span>
                  <span>External Platform</span>
                </div>
                <ArrowRight className="w-3.5 h-3.5 text-[#A26377]" />
                <div className="flex items-center gap-1.5 text-white font-bold">
                  <span className="w-5 h-5 rounded-full bg-[#FF2D6D] text-white flex items-center justify-center text-[10px]">3</span>
                  <span>Target Resource</span>
                </div>
              </div>

              {/* Step 1: Project & Environment */}
              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                    1. SecretVault Project
                  </label>
                  <select
                    value={mappingForm.projectId}
                    onChange={(e) => {
                      const pId = e.target.value;
                      const envList = environmentsMap[pId] || [];
                      setMappingForm({
                        ...mappingForm,
                        projectId: pId,
                        environmentId: envList[0]?.id || '',
                      });
                    }}
                    className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] cursor-pointer"
                  >
                    {projects.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                    Local Environment
                  </label>
                  <select
                    value={mappingForm.environmentId}
                    onChange={(e) => setMappingForm({ ...mappingForm, environmentId: e.target.value })}
                    className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] cursor-pointer"
                  >
                    {(environmentsMap[mappingForm.projectId] || []).map((env) => (
                      <option key={env.id} value={env.id}>
                        {env.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Step 2: Target Integration Provider */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  2. External Platform Provider
                </label>
                <select
                  value={mappingForm.integrationId}
                  onChange={(e) => {
                    const intId = e.target.value;
                    setMappingForm({ ...mappingForm, integrationId: intId });
                    const found = integrations.find((i) => i.id === intId);
                    setSelectedIntegrationForMapping(found);
                    if (found) {
                      setMappingForm((prev) => ({
                        ...prev,
                        providerResourceType: getProviderMeta(found.providerType).defaultResourceType,
                      }));
                      handleDiscoverResources(found.id);
                    }
                  }}
                  className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] cursor-pointer"
                >
                  {integrations.map((i) => {
                    const meta = getProviderMeta(i.providerType);
                    return (
                      <option key={i.id} value={i.id}>
                        {meta.iconSymbol} {i.displayName} ({meta.displayName})
                      </option>
                    );
                  })}
                </select>
              </div>

              {/* Step 3: Provider Discovered Resource Picker or Manual */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                    3. Target Remote Resource (Service / App / Secret Store)
                  </label>
                  {isDiscovering && (
                    <span className="flex items-center gap-1 text-[10px] text-[#818CF8] font-mono">
                      <Loader2 className="w-3 h-3 animate-spin" /> Auto-discovering...
                    </span>
                  )}
                </div>

                {discoveredResources.length > 0 ? (
                  <select
                    value={mappingForm.providerResourceId}
                    onChange={(e) => {
                      const selected = discoveredResources.find((r) => r.id === e.target.value);
                      setMappingForm({
                        ...mappingForm,
                        providerResourceId: e.target.value,
                        providerResourceName: selected?.name || e.target.value,
                      });
                    }}
                    className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] cursor-pointer"
                  >
                    <option value="">-- Choose Discovered Remote Resource --</option>
                    {discoveredResources.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.name} ({r.id})
                      </option>
                    ))}
                  </select>
                ) : (
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="text"
                      placeholder="Target Resource ID (e.g. prj_123 or srv-abc)"
                      value={mappingForm.providerResourceId}
                      onChange={(e) => setMappingForm({ ...mappingForm, providerResourceId: e.target.value })}
                      className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] font-mono text-[11px]"
                      required
                    />
                    <input
                      type="text"
                      placeholder="Resource Name (optional)"
                      value={mappingForm.providerResourceName}
                      onChange={(e) => setMappingForm({ ...mappingForm, providerResourceName: e.target.value })}
                      className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] text-[11px]"
                    />
                  </div>
                )}
              </div>

              {/* Target Environment */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  Target Provider Environment
                </label>
                <select
                  value={mappingForm.providerEnvironment}
                  onChange={(e) => setMappingForm({ ...mappingForm, providerEnvironment: e.target.value })}
                  className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] cursor-pointer"
                >
                  <option value="production">production</option>
                  <option value="staging">staging</option>
                  <option value="preview">preview</option>
                  <option value="development">development</option>
                </select>
              </div>

              {/* Sync Toggle */}
              <label className="flex items-center gap-3 p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 cursor-pointer">
                <input
                  type="checkbox"
                  checked={mappingForm.syncEnabled}
                  onChange={(e) => setMappingForm({ ...mappingForm, syncEnabled: e.target.checked })}
                  className="rounded text-[#FF2D6D] focus:ring-0 cursor-pointer"
                />
                <div className="flex flex-col">
                  <span className="font-semibold text-white">Enable Automated Sync Engine</span>
                  <span className="text-[10px] text-[#A26377]">
                    Automatically synchronize secrets to this remote target when updated in SecretVault.
                  </span>
                </div>
              </label>

              {/* Mapping Route Preview Summary */}
              {(() => {
                const selectedProj = projects.find((p) => p.id === mappingForm.projectId);
                const selectedEnv = (environmentsMap[mappingForm.projectId] || []).find((e) => e.id === mappingForm.environmentId);
                const selectedInt = integrations.find((i) => i.id === mappingForm.integrationId);
                const intMeta = selectedInt ? getProviderMeta(selectedInt.providerType) : null;

                return (
                  <div className="p-3 rounded-xl bg-[#1E000A] border border-[#FF2D6D]/30 flex flex-col gap-1 text-[11px]">
                    <span className="font-mono text-[#A26377] uppercase text-[9px] font-bold">Route Preview Summary</span>
                    <div className="flex items-center gap-2 text-white font-mono flex-wrap">
                      <span className="font-bold text-[#FF85A2]">{selectedProj?.name || 'Project'}</span>
                      <span className="text-[#A26377]">({selectedEnv?.name || 'Environment'})</span>
                      <span className="text-[#34D399]">──(Sync)──▶</span>
                      <span className="font-bold text-white flex items-center gap-1">
                        {intMeta?.iconSymbol} {selectedInt?.displayName || 'Provider'}
                      </span>
                      <span className="text-[#818CF8]">[{mappingForm.providerResourceName || mappingForm.providerResourceId || 'Target'} / {mappingForm.providerEnvironment}]</span>
                    </div>
                  </div>
                );
              })()}

              {/* Buttons */}
              <div className="flex items-center justify-end gap-3 mt-1 pt-3 border-t border-[#FFB4C8]/15">
                <button
                  type="button"
                  onClick={() => setIsMappingModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-[#F4B5C8] hover:bg-[#3F0016] text-xs font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isLoading}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold shadow-lg shadow-[#FF2D6D]/25 transition-all cursor-pointer disabled:opacity-50"
                >
                  {isLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Create Resource Mapping</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 3: PUSH SECRET MODAL */}
      {isPushModalOpen && selectedMappingForPush && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#30000F] border border-[#FFB4C8]/25 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-scale-in">
            <div className="px-6 py-4 border-b border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <UploadCloud className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-bold text-white">Push Secret to External Provider</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsPushModalOpen(false)}
                className="text-[#A26377] hover:text-white text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <div className="p-6 flex flex-col gap-4 text-xs">
              <div className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <span className="text-[10px] font-mono text-[#A26377] uppercase font-bold">Target Destination</span>
                <span className="font-semibold text-white">
                  {selectedMappingForPush.providerType} — {selectedMappingForPush.providerResourceName} (
                  {selectedMappingForPush.providerEnvironment})
                </span>
              </div>

              <div className="flex flex-col gap-2">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  Select Secret from SecretVault
                </label>

                {isLoadingSecretsToPush ? (
                  <div className="p-6 text-center text-[#A26377] flex items-center justify-center gap-2">
                    <Loader2 className="w-4 h-4 animate-spin text-[#FF2D6D]" />
                    <span>Loading vault secrets...</span>
                  </div>
                ) : availableSecretsToPush.length === 0 ? (
                  <div className="p-4 bg-[#1E000A] rounded-xl text-center text-[#F4B5C8]">
                    No secrets found in this project environment.
                  </div>
                ) : (
                  <div className="max-h-60 overflow-y-auto flex flex-col gap-1.5 pr-1">
                    {availableSecretsToPush.map((sec) => (
                      <div
                        key={sec.id}
                        className="flex items-center justify-between p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 hover:border-[#FFB4C8]/30 transition-all"
                      >
                        <div className="flex items-center gap-2.5">
                          <Key className="w-4 h-4 text-[#FF2D6D]" />
                          <div className="flex flex-col">
                            <span className="font-mono font-bold text-white">{sec.key}</span>
                            <span className="text-[10px] text-[#A26377]">Type: {sec.type || 'STRING'}</span>
                          </div>
                        </div>

                        <button
                          type="button"
                          onClick={() => handleExecutePushSecret(sec.id, sec.key)}
                          disabled={actionLoadingId === sec.id}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold transition-all cursor-pointer disabled:opacity-50"
                        >
                          {actionLoadingId === sec.id ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          ) : (
                            <UploadCloud className="w-3.5 h-3.5" />
                          )}
                          <span>Push</span>
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex justify-end pt-3 border-t border-[#FFB4C8]/15">
                <button
                  type="button"
                  onClick={() => setIsPushModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-[#3F0016] text-[#F4B5C8] hover:text-white text-xs font-semibold cursor-pointer"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* MODAL 4: INSPECT REMOTE SECRETS */}
      {isInspectModalOpen && selectedMappingForInspect && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#30000F] border border-[#FFB4C8]/25 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-scale-in">
            <div className="px-6 py-4 border-b border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Eye className="w-5 h-5 text-[#818CF8]" />
                <h3 className="text-base font-bold text-white">Remote Provider Environment Variables</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsInspectModalOpen(false)}
                className="text-[#A26377] hover:text-white text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <div className="p-6 flex flex-col gap-4 text-xs">
              <div className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <span className="text-[10px] font-mono text-[#A26377] uppercase font-bold">Target Resource</span>
                <span className="font-semibold text-white">
                  {selectedMappingForInspect.providerResourceName} ({selectedMappingForInspect.providerEnvironment})
                </span>
              </div>

              {isLoadingRemoteSecrets ? (
                <div className="p-8 text-center text-[#A26377] flex items-center justify-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin text-[#818CF8]" />
                  <span>Scanning remote provider environment variables...</span>
                </div>
              ) : remoteSecrets.length === 0 ? (
                <div className="p-6 bg-[#1E000A] rounded-xl text-center text-[#F4B5C8]">
                  No remote environment variables found on this provider target.
                </div>
              ) : (
                <div className="max-h-60 overflow-y-auto flex flex-col gap-2 pr-1">
                  {remoteSecrets.map((rSec, idx) => (
                    <div
                      key={idx}
                      className="p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 flex items-center justify-between"
                    >
                      <div className="flex flex-col">
                        <span className="font-mono font-bold text-white">{rSec.key}</span>
                        <span className="text-[10px] font-mono text-[#A26377]">
                          ID: {rSec.id || 'N/A'} • Target: {rSec.target || selectedMappingForInspect.providerEnvironment}
                        </span>
                      </div>
                      <span className="px-2 py-0.5 rounded bg-[#30000F] text-[10px] font-mono text-[#34D399] border border-[#34D399]/30">
                        Live on Provider
                      </span>
                    </div>
                  ))}
                </div>
              )}

              <div className="flex justify-end pt-3 border-t border-[#FFB4C8]/15">
                <button
                  type="button"
                  onClick={() => setIsInspectModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-[#3F0016] text-[#F4B5C8] hover:text-white text-xs font-semibold cursor-pointer"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

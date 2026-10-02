import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { integrationsApi } from '../../api/integrations';
import { projectApi } from '../../api/projects';
import { environmentApi } from '../../api/environments';
import { secretApi } from '../../api/secrets';
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
  UploadCloud,
  Eye,
  Shield,
  Loader2,
  ExternalLink,
  Layers,
  Check,
  ChevronRight,
  Info,
  Server,
  Zap,
} from 'lucide-react';

export const IntegrationsView = () => {
  const { activeWorkspace } = useAuth();
  const [activeTab, setActiveTab] = useState('PROVIDERS'); // 'PROVIDERS' | 'MAPPINGS'
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

      const intList = intRes?.items || intRes?.content || (Array.isArray(intRes) ? intRes : []);
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

  const handleValidateIntegration = async (integrationId) => {
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
          <div className="mt-3 flex items-center gap-2">
            <span className="px-2 py-0.5 rounded bg-[#3F0016] text-[10px] font-mono text-white border border-[#FFB4C8]/15">
              ▲ Vercel
            </span>
            <span className="px-2 py-0.5 rounded bg-[#3F0016] text-[10px] font-mono text-white border border-[#FFB4C8]/15">
              ⬡ Render
            </span>
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex items-center gap-2 border-b border-[#FFB4C8]/15 pb-2">
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
          <span>Resource Mappings ({mappings.length})</span>
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
                const isVercel = item.providerType === 'VERCEL';
                const isActive = item.status === 'ACTIVE';
                const isError = item.status === 'ERROR';
                const itemMappings = mappings.filter((m) => m.providerIntegrationId === item.id);

                return (
                  <div
                    key={item.id}
                    className="bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30 rounded-2xl p-5 flex flex-col justify-between gap-4 transition-all shadow-lg"
                  >
                    <div className="flex flex-col gap-3">
                      {/* Top Row: Provider Logo & Status */}
                      <div className="flex items-start justify-between">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 flex items-center justify-center text-lg font-bold">
                            {isVercel ? '▲' : '⬡'}
                          </div>
                          <div className="flex flex-col">
                            <span className="text-sm font-bold text-white">{item.displayName}</span>
                            <span className="text-[10px] font-mono text-[#A26377]">
                              {item.providerType} • {itemMappings.length} mapping(s)
                            </span>
                          </div>
                        </div>

                        <span
                          className={`px-2.5 py-1 rounded-full text-[10px] font-mono font-bold uppercase flex items-center gap-1.5 ${
                            isActive
                              ? 'bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30'
                              : isError
                              ? 'bg-[#F87171]/15 text-[#F87171] border border-[#F87171]/30'
                              : 'bg-[#FBBF24]/15 text-[#FBBF24] border border-[#FBBF24]/30'
                          }`}
                        >
                          <span
                            className={`w-1.5 h-1.5 rounded-full ${
                              isActive ? 'bg-[#34D399]' : isError ? 'bg-[#F87171]' : 'bg-[#FBBF24] animate-pulse'
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
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: RESOURCE MAPPINGS */}
      {activeTab === 'MAPPINGS' && (
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between bg-[#30000F] border border-[#FFB4C8]/15 p-4 rounded-2xl">
            <div>
              <h2 className="text-sm font-bold text-white">Configured Resource Mappings</h2>
              <p className="text-[11px] text-[#F4B5C8]">
                Binds a SecretVault Project &amp; Environment to an external provider project &amp; deployment target.
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
                  }));
                  setIsMappingModalOpen(true);
                  handleDiscoverResources(defaultInt.id);
                }}
                className="flex items-center gap-2 px-3 py-2 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold cursor-pointer transition-all shadow-md shadow-[#FF2D6D]/20"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>New Mapping</span>
              </button>
            )}
          </div>

          {mappings.length === 0 ? (
            <div className="p-8 bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl text-center flex flex-col items-center gap-2">
              <Layers className="w-8 h-8 text-[#A26377]" />
              <span className="text-sm font-bold text-white">No Resource Mappings Configured</span>
              <span className="text-xs text-[#F4B5C8] max-w-sm">
                Map a SecretVault project environment to a remote Vercel/Render project to push and synchronize secrets.
              </span>
            </div>
          ) : (
            <div className="bg-[#30000F] border border-[#FFB4C8]/15 rounded-2xl overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[#1E000A] text-[#A26377] font-mono text-[10px] uppercase border-b border-[#FFB4C8]/15 tracking-wider">
                    <tr>
                      <th className="py-3 px-4">Local Project &amp; Env</th>
                      <th className="py-3 px-4">Provider</th>
                      <th className="py-3 px-4">Remote Target Resource</th>
                      <th className="py-3 px-4">Remote Target Env</th>
                      <th className="py-3 px-4">Sync Engine</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#FFB4C8]/10 text-white font-body">
                    {mappings.map((m) => {
                      const proj = projects.find((p) => p.id === m.projectId);
                      const envList = environmentsMap[m.projectId] || [];
                      const env = envList.find((e) => e.id === m.environmentId);
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
                              <span className="w-5 h-5 rounded bg-[#1E000A] border border-[#FFB4C8]/20 flex items-center justify-center font-bold text-[10px]">
                                {m.providerType === 'VERCEL' ? '▲' : '⬡'}
                              </span>
                              <span className="font-medium text-xs text-white">{m.integrationName || m.providerType}</span>
                            </div>
                          </td>

                          <td className="py-3 px-4">
                            <div className="flex flex-col">
                              <span className="font-mono text-xs font-semibold text-white">{m.providerResourceName}</span>
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
                                title="Push a secret directly to this provider target"
                              >
                                <UploadCloud className="w-3.5 h-3.5" />
                                <span>Push</span>
                              </button>

                              <button
                                type="button"
                                onClick={() => handleOpenInspectModal(m)}
                                className="p-1.5 rounded-lg bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-colors cursor-pointer"
                                title="Inspect remote environment variables"
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
            </div>
          )}
        </div>
      )}

      {/* MODAL 1: CONNECT PROVIDER */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#30000F] border border-[#FFB4C8]/25 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-scale-in">
            <div className="px-6 py-4 border-b border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Network className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-bold text-white">Connect External Provider</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="text-[#A26377] hover:text-white text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateIntegration} className="p-6 flex flex-col gap-4 text-xs">
              {/* Provider Selection */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">Select Provider</label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => setProviderForm({ ...providerForm, providerType: 'VERCEL' })}
                    className={`flex items-center gap-3 p-3 rounded-xl border text-left cursor-pointer transition-all ${
                      providerForm.providerType === 'VERCEL'
                        ? 'bg-[#FF2D6D]/15 border-[#FF2D6D] text-white'
                        : 'bg-[#1E000A] border-[#FFB4C8]/15 text-[#F4B5C8] hover:border-[#FFB4C8]/30'
                    }`}
                  >
                    <div className="w-8 h-8 rounded-lg bg-[#30000F] flex items-center justify-center font-bold text-sm">
                      ▲
                    </div>
                    <div className="flex flex-col">
                      <span className="font-bold text-xs">Vercel</span>
                      <span className="text-[10px] text-[#A26377]">Deployments &amp; Serverless</span>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setProviderForm({ ...providerForm, providerType: 'RENDER' })}
                    className={`flex items-center gap-3 p-3 rounded-xl border text-left cursor-pointer transition-all ${
                      providerForm.providerType === 'RENDER'
                        ? 'bg-[#FF2D6D]/15 border-[#FF2D6D] text-white'
                        : 'bg-[#1E000A] border-[#FFB4C8]/15 text-[#F4B5C8] hover:border-[#FFB4C8]/30'
                    }`}
                  >
                    <div className="w-8 h-8 rounded-lg bg-[#30000F] flex items-center justify-center font-bold text-sm">
                      ⬡
                    </div>
                    <div className="flex flex-col">
                      <span className="font-bold text-xs">Render</span>
                      <span className="text-[10px] text-[#A26377]">Web &amp; Background Services</span>
                    </div>
                  </button>
                </div>
              </div>

              {/* Display Name */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  Connection Display Name
                </label>
                <input
                  type="text"
                  placeholder="e.g. Production Vercel Org or Main Render Account"
                  value={providerForm.displayName}
                  onChange={(e) => setProviderForm({ ...providerForm, displayName: e.target.value })}
                  className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D]"
                  required
                />
              </div>

              {/* API Token / Credential */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                    Provider API Token / Secret Key
                  </label>
                  <span className="text-[10px] font-mono text-[#34D399]">AES-256 Envelope Encrypted</span>
                </div>
                <input
                  type="password"
                  placeholder="Paste external platform personal/team access token..."
                  value={providerForm.credential}
                  onChange={(e) => setProviderForm({ ...providerForm, credential: e.target.value })}
                  className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] font-mono focus:outline-none focus:border-[#FF2D6D]"
                  required
                />
              </div>

              {/* Optional Config JSON */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                  Optional Provider Configuration (JSON)
                </label>
                <input
                  type="text"
                  placeholder='e.g. {"teamId": "team_abc123"}'
                  value={providerForm.configuration}
                  onChange={(e) => setProviderForm({ ...providerForm, configuration: e.target.value })}
                  className="px-3.5 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] font-mono text-[11px] focus:outline-none focus:border-[#FF2D6D]"
                />
              </div>

              {/* Buttons */}
              <div className="flex items-center justify-end gap-3 mt-4 pt-3 border-t border-[#FFB4C8]/15">
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
                  className="flex items-center gap-2 px-5 py-2 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold shadow-lg shadow-[#FF2D6D]/25 transition-all cursor-pointer disabled:opacity-50"
                >
                  {isLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Save &amp; Connect</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: ADD RESOURCE MAPPING */}
      {isMappingModalOpen && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#30000F] border border-[#FFB4C8]/25 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-scale-in">
            <div className="px-6 py-4 border-b border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Layers className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-bold text-white">Create Resource Mapping</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsMappingModalOpen(false)}
                className="text-[#A26377] hover:text-white text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateMapping} className="p-6 flex flex-col gap-4 text-xs">
              {/* Integration Selection */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">Provider Integration</label>
                <select
                  value={mappingForm.integrationId}
                  onChange={(e) => {
                    const intId = e.target.value;
                    setMappingForm({ ...mappingForm, integrationId: intId });
                    const found = integrations.find((i) => i.id === intId);
                    setSelectedIntegrationForMapping(found);
                    if (found) handleDiscoverResources(found.id);
                  }}
                  className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
                >
                  {integrations.map((i) => (
                    <option key={i.id} value={i.id}>
                      {i.displayName} ({i.providerType})
                    </option>
                  ))}
                </select>
              </div>

              {/* Project & Environment */}
              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">SecretVault Project</label>
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
                    className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
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
                    SecretVault Environment
                  </label>
                  <select
                    value={mappingForm.environmentId}
                    onChange={(e) => setMappingForm({ ...mappingForm, environmentId: e.target.value })}
                    className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
                  >
                    {(environmentsMap[mappingForm.projectId] || []).map((env) => (
                      <option key={env.id} value={env.id}>
                        {env.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Provider Discovered Resource Picker or Manual */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-[#A26377] font-mono uppercase font-bold text-[10px]">
                    Target Remote Resource (Project / Service)
                  </label>
                  {isDiscovering && (
                    <span className="flex items-center gap-1 text-[10px] text-[#818CF8] font-mono">
                      <Loader2 className="w-3 h-3 animate-spin" /> Discovering...
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
                    className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
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
                  className="px-3.5 py-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D]"
                >
                  <option value="production">production</option>
                  <option value="preview">preview</option>
                  <option value="development">development</option>
                  <option value="staging">staging</option>
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
                  <span className="font-semibold text-white">Enable Automated Sync</span>
                  <span className="text-[10px] text-[#A26377]">
                    Automatically synchronize secrets to this provider when created or modified.
                  </span>
                </div>
              </label>

              {/* Buttons */}
              <div className="flex items-center justify-end gap-3 mt-4 pt-3 border-t border-[#FFB4C8]/15">
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
                  className="flex items-center gap-2 px-5 py-2 rounded-xl bg-[#FF2D6D] text-white hover:bg-[#FF4D85] text-xs font-bold shadow-lg shadow-[#FF2D6D]/25 transition-all cursor-pointer disabled:opacity-50"
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

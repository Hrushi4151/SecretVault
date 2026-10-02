import React, { useState, useEffect } from 'react';
import { oidcApi } from '../../api/oidc';
import {
  Server,
  Plus,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  ExternalLink,
  Shield,
  Loader2,
  Trash2,
  Activity,
  KeyRound,
  Sparkles,
  Github,
  Gitlab,
  Globe
} from 'lucide-react';

export const OidcProvidersView = ({ workspaceId, onBack }) => {
  const [providers, setProviders] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Modal State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [testingProviderId, setTestingProviderId] = useState(null);
  const [refreshingProviderId, setRefreshingProviderId] = useState(null);
  const [testResult, setTestResult] = useState(null);

  // Form State
  const [providerTypePreset, setProviderTypePreset] = useState('GITHUB_ACTIONS');
  const [formData, setFormData] = useState({
    name: 'GitHub Actions',
    providerType: 'GITHUB_ACTIONS',
    issuer: 'https://token.actions.githubusercontent.com',
    audience: 'https://github.com/secretvault',
    discoveryUrl: 'https://token.actions.githubusercontent.com/.well-known/openid-configuration',
    jwksUrl: 'https://token.actions.githubusercontent.com/.well-known/jwks',
    enabled: true,
  });
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (workspaceId) {
      loadProviders();
    }
  }, [workspaceId]);

  const loadProviders = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const res = await oidcApi.listProviders(workspaceId);
      const data = res.data?.data || res.data || [];
      setProviders(Array.isArray(data) ? data : []);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to load OIDC providers');
    } finally {
      setIsLoading(false);
    }
  };

  const handlePresetChange = (preset) => {
    setProviderTypePreset(preset);
    if (preset === 'GITHUB_ACTIONS') {
      setFormData({
        name: 'GitHub Actions',
        providerType: 'GITHUB_ACTIONS',
        issuer: 'https://token.actions.githubusercontent.com',
        audience: 'https://github.com/secretvault',
        discoveryUrl: 'https://token.actions.githubusercontent.com/.well-known/openid-configuration',
        jwksUrl: 'https://token.actions.githubusercontent.com/.well-known/jwks',
        enabled: true,
      });
    } else if (preset === 'GITLAB_CI') {
      setFormData({
        name: 'GitLab CI/CD',
        providerType: 'GITLAB_CI',
        issuer: 'https://gitlab.com',
        audience: 'https://gitlab.com',
        discoveryUrl: 'https://gitlab.com/.well-known/openid-configuration',
        jwksUrl: 'https://gitlab.com/oauth/discovery/keys',
        enabled: true,
      });
    } else {
      setFormData({
        name: 'Generic OIDC Provider',
        providerType: 'GENERIC_OIDC',
        issuer: 'https://oidc.example.com',
        audience: 'secretvault',
        discoveryUrl: 'https://oidc.example.com/.well-known/openid-configuration',
        jwksUrl: '',
        enabled: true,
      });
    }
  };

  const handleCreateProvider = async (e) => {
    e.preventDefault();
    try {
      setIsSubmitting(true);
      setErrorMessage(null);
      await oidcApi.createProvider(workspaceId, formData);
      setSuccessMessage(`OIDC Provider "${formData.name}" registered successfully.`);
      setIsCreateModalOpen(false);
      loadProviders();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to create OIDC provider');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleTestConnection = async (providerId) => {
    try {
      setTestingProviderId(providerId);
      setTestResult(null);
      const res = await oidcApi.testConnection(workspaceId, providerId);
      const resultData = res.data?.data || res.data;
      setTestResult({ providerId, ...resultData });
    } catch (err) {
      setTestResult({
        providerId,
        success: false,
        message: err.response?.data?.message || err.message || 'Connection test failed',
      });
    } finally {
      setTestingProviderId(null);
    }
  };

  const handleRefreshJwks = async (providerId) => {
    try {
      setRefreshingProviderId(providerId);
      await oidcApi.refreshJwks(workspaceId, providerId);
      setSuccessMessage('JWKS keys successfully refreshed and cached in memory.');
      loadProviders();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to refresh JWKS');
    } finally {
      setRefreshingProviderId(null);
    }
  };

  const handleDeleteProvider = async (providerId, providerName) => {
    if (!window.confirm(`Are you sure you want to delete OIDC provider "${providerName}"? Trust policies referencing this provider will be disabled.`)) {
      return;
    }
    try {
      await oidcApi.deleteProvider(workspaceId, providerId);
      setSuccessMessage(`OIDC Provider "${providerName}" deleted.`);
      loadProviders();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Failed to delete provider');
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            {onBack && (
              <button
                onClick={onBack}
                className="text-xs text-brand-primary hover:underline flex items-center gap-1 font-mono"
              >
                ← Back to Machine Identities
              </button>
            )}
          </div>
          <h2 className="text-xl font-semibold text-text-primary flex items-center gap-2 mt-1">
            <Server className="w-5 h-5 text-brand-primary" />
            OIDC Workload Providers
            <span className="px-2 py-0.5 text-xs font-mono rounded bg-brand-primary/10 text-brand-primary border border-brand-primary/20">
              Phase 9
            </span>
          </h2>
          <p className="text-xs text-text-secondary mt-0.5">
            Configure OpenID Connect issuers (GitHub Actions, GitLab CI, Generic OIDC) for cryptographic key resolution and token exchange.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={loadProviders}
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
            Register OIDC Provider
          </button>
        </div>
      </div>

      {/* Status Banners */}
      {errorMessage && (
        <div className="p-3 rounded-lg bg-status-error/10 border border-status-error/20 flex items-start gap-2 text-xs text-status-error">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <div className="flex-1">{errorMessage}</div>
          <button onClick={() => setErrorMessage(null)} className="hover:opacity-75">✕</button>
        </div>
      )}

      {successMessage && (
        <div className="p-3 rounded-lg bg-status-success/10 border border-status-success/20 flex items-start gap-2 text-xs text-status-success">
          <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
          <div className="flex-1">{successMessage}</div>
          <button onClick={() => setSuccessMessage(null)} className="hover:opacity-75">✕</button>
        </div>
      )}

      {/* Providers Grid */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center p-12 bg-surface-container rounded-xl border border-outline-variant">
          <Loader2 className="w-6 h-6 animate-spin text-brand-primary mb-2" />
          <span className="text-xs text-text-secondary font-mono">Resolving workspace OIDC providers...</span>
        </div>
      ) : providers.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 bg-surface-container/50 rounded-xl border border-dashed border-outline-variant text-center">
          <Server className="w-10 h-10 text-text-tertiary mb-3" />
          <h3 className="text-sm font-medium text-text-primary">No OIDC Providers Configured</h3>
          <p className="text-xs text-text-secondary max-w-md mt-1 mb-4">
            Register GitHub Actions or GitLab CI to enable seamless key rotation, discovery validation, and token exchanges for your CI/CD pipelines.
          </p>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="px-3.5 py-1.5 text-xs font-medium rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-surface-container-lowest transition-colors flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            Register First Provider
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {providers.map((p) => {
            const isGitHub = p.providerType === 'GITHUB_ACTIONS' || p.issuer?.includes('github');
            const isGitLab = p.providerType === 'GITLAB_CI' || p.issuer?.includes('gitlab');
            const isTesting = testingProviderId === p.id;
            const isRefreshing = refreshingProviderId === p.id;

            return (
              <div
                key={p.id}
                className="bg-surface-container rounded-xl border border-outline-variant p-4 space-y-4 hover:border-outline transition-colors"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-lg bg-surface-container-high border border-outline-variant flex items-center justify-center text-text-primary">
                      {isGitHub ? (
                        <Github className="w-5 h-5 text-[#FF2D6D]" />
                      ) : isGitLab ? (
                        <Gitlab className="w-5 h-5 text-[#FF85A2]" />
                      ) : (
                        <Globe className="w-5 h-5 text-brand-primary" />
                      )}
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-text-primary flex items-center gap-2">
                        {p.name}
                        <span className={`px-1.5 py-0.5 text-[10px] font-mono rounded ${
                          p.status === 'ACTIVE'
                            ? 'bg-status-success/10 text-status-success border border-status-success/20'
                            : 'bg-status-warning/10 text-status-warning border border-status-warning/20'
                        }`}>
                          {p.status}
                        </span>
                      </h4>
                      <span className="text-[11px] font-mono text-text-tertiary">{p.providerType}</span>
                    </div>
                  </div>

                  <button
                    onClick={() => handleDeleteProvider(p.id, p.name)}
                    className="p-1.5 text-text-tertiary hover:text-status-error hover:bg-surface-container-high rounded transition-colors"
                    title="Delete Provider"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>

                {/* Details */}
                <div className="space-y-1.5 text-xs font-mono bg-surface-container-lowest/60 rounded-lg p-3 border border-outline-variant/60">
                  <div className="flex items-center justify-between">
                    <span className="text-text-tertiary">Issuer:</span>
                    <span className="text-text-primary truncate max-w-[240px]" title={p.issuer}>
                      {p.issuer}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-text-tertiary">Audience:</span>
                    <span className="text-text-primary truncate max-w-[240px]" title={p.audience}>
                      {p.audience || 'Default'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-text-tertiary">Algorithms:</span>
                    <span className="text-brand-primary font-semibold">
                      {Array.isArray(p.allowedAlgorithms) ? p.allowedAlgorithms.join(', ') : 'RS256, ES256'}
                    </span>
                  </div>
                </div>

                {/* Test Connection Output */}
                {testResult && testResult.providerId === p.id && (
                  <div className={`p-2.5 rounded-lg text-xs font-mono border ${
                    testResult.success
                      ? 'bg-status-success/10 text-status-success border-status-success/20'
                      : 'bg-status-error/10 text-status-error border-status-error/20'
                  }`}>
                    <div className="flex items-center gap-1.5 font-semibold mb-1">
                      {testResult.success ? <CheckCircle2 className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
                      {testResult.success ? 'Discovery & JWKS Reachable' : 'Discovery Test Failed'}
                    </div>
                    <div className="text-[11px] opacity-90">{testResult.message}</div>
                    {testResult.keysCount !== undefined && (
                      <div className="text-[10px] text-text-secondary mt-1">
                        Discovered Public Keys: {testResult.keysCount} RSA/EC Keys
                      </div>
                    )}
                  </div>
                )}

                {/* Actions */}
                <div className="flex items-center justify-between pt-2 border-t border-outline-variant/60">
                  <button
                    onClick={() => handleTestConnection(p.id)}
                    disabled={isTesting}
                    className="px-2.5 py-1 text-xs font-medium rounded border border-outline-variant bg-surface-container hover:bg-surface-container-high text-text-secondary hover:text-text-primary transition-colors flex items-center gap-1"
                  >
                    {isTesting ? <Loader2 className="w-3 h-3 animate-spin text-brand-primary" /> : <Activity className="w-3 h-3 text-brand-primary" />}
                    Test Discovery & JWKS
                  </button>

                  <button
                    onClick={() => handleRefreshJwks(p.id)}
                    disabled={isRefreshing}
                    className="px-2.5 py-1 text-xs font-medium rounded border border-outline-variant bg-surface-container hover:bg-surface-container-high text-text-secondary hover:text-text-primary transition-colors flex items-center gap-1"
                  >
                    {isRefreshing ? <Loader2 className="w-3 h-3 animate-spin" /> : <KeyRound className="w-3 h-3 text-text-tertiary" />}
                    Rotate / Refresh Keys
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Register Provider Modal */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-container-lowest/80 backdrop-blur-sm">
          <div className="w-full max-w-lg bg-surface-container rounded-2xl border border-outline-variant shadow-2xl p-6 space-y-5 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-semibold text-text-primary flex items-center gap-2">
                  <Server className="w-5 h-5 text-brand-primary" />
                  Register OIDC Workload Provider
                </h3>
                <p className="text-xs text-text-secondary mt-0.5">
                  Configure trusted identity issuer for workload authentication.
                </p>
              </div>
              <button
                onClick={() => setIsCreateModalOpen(false)}
                className="text-text-tertiary hover:text-text-primary text-sm"
              >
                ✕
              </button>
            </div>

            {/* Provider Type Selector */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-text-secondary">Provider Profile Preset</label>
              <div className="grid grid-cols-3 gap-2">
                <button
                  type="button"
                  onClick={() => handlePresetChange('GITHUB_ACTIONS')}
                  className={`p-2.5 rounded-xl border text-left flex flex-col gap-1 transition-all ${
                    providerTypePreset === 'GITHUB_ACTIONS'
                      ? 'border-brand-primary bg-brand-primary/10 text-brand-primary shadow-sm'
                      : 'border-outline-variant bg-surface-container-high text-text-secondary hover:text-text-primary'
                  }`}
                >
                  <Github className="w-4 h-4" />
                  <span className="text-xs font-semibold">GitHub Actions</span>
                  <span className="text-[10px] opacity-75">id-token: write</span>
                </button>

                <button
                  type="button"
                  onClick={() => handlePresetChange('GITLAB_CI')}
                  className={`p-2.5 rounded-xl border text-left flex flex-col gap-1 transition-all ${
                    providerTypePreset === 'GITLAB_CI'
                      ? 'border-brand-primary bg-brand-primary/10 text-brand-primary shadow-sm'
                      : 'border-outline-variant bg-surface-container-high text-text-secondary hover:text-text-primary'
                  }`}
                >
                  <Gitlab className="w-4 h-4" />
                  <span className="text-xs font-semibold">GitLab CI/CD</span>
                  <span className="text-[10px] opacity-75">CI_JOB_JWT_V2</span>
                </button>

                <button
                  type="button"
                  onClick={() => handlePresetChange('GENERIC_OIDC')}
                  className={`p-2.5 rounded-xl border text-left flex flex-col gap-1 transition-all ${
                    providerTypePreset === 'GENERIC_OIDC'
                      ? 'border-brand-primary bg-brand-primary/10 text-brand-primary shadow-sm'
                      : 'border-outline-variant bg-surface-container-high text-text-secondary hover:text-text-primary'
                  }`}
                >
                  <Globe className="w-4 h-4" />
                  <span className="text-xs font-semibold">Generic OIDC</span>
                  <span className="text-[10px] opacity-75">Custom Issuer</span>
                </button>
              </div>
            </div>

            <form onSubmit={handleCreateProvider} className="space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-medium text-text-secondary">Provider Name</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g. GitHub Actions Production"
                  className="w-full px-3 py-2 text-xs rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-text-secondary">Issuer URL (Strict HTTPS)</label>
                <input
                  type="url"
                  required
                  value={formData.issuer}
                  onChange={(e) => setFormData({ ...formData, issuer: e.target.value })}
                  placeholder="https://token.actions.githubusercontent.com"
                  className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-text-secondary">Audience (aud)</label>
                <input
                  type="text"
                  value={formData.audience}
                  onChange={(e) => setFormData({ ...formData, audience: e.target.value })}
                  placeholder="e.g. https://github.com/secretvault or secretvault"
                  className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-secondary">Discovery URL (Optional)</label>
                  <input
                    type="url"
                    value={formData.discoveryUrl}
                    onChange={(e) => setFormData({ ...formData, discoveryUrl: e.target.value })}
                    placeholder="https://.../.well-known/openid-configuration"
                    className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-medium text-text-secondary">JWKS URL (Optional)</label>
                  <input
                    type="url"
                    value={formData.jwksUrl}
                    onChange={(e) => setFormData({ ...formData, jwksUrl: e.target.value })}
                    placeholder="https://.../.well-known/jwks"
                    className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-outline-variant bg-surface-container-lowest text-text-primary focus:outline-none focus:border-brand-primary"
                  />
                </div>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-lowest border border-outline-variant text-[11px] text-text-secondary space-y-1">
                <div className="flex items-center gap-1.5 text-brand-primary font-semibold">
                  <Shield className="w-3.5 h-3.5" />
                  SSRF & Cryptographic Security
                </div>
                <div>
                  Issuer URLs are protected against SSRF, DNS rebinding, and private network IPs. RSA-256 and EC-256 public keys are automatically rotated and cached.
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
                  disabled={isSubmitting}
                  className="px-4 py-1.5 text-xs font-medium rounded-lg bg-brand-primary hover:bg-brand-primary-hover text-surface-container-lowest flex items-center gap-1.5"
                >
                  {isSubmitting && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  Register Provider
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

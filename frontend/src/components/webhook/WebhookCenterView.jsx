import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { webhooksApi } from '../../api/webhooks';
import {
  Webhook,
  RotateCw,
  Plus,
  Key,
  ShieldAlert,
  Send,
  CheckCircle2,
  XCircle,
  ExternalLink,
  Lock,
  Copy,
  Check,
  Play
} from 'lucide-react';

export const WebhookCenterView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [endpoints, setEndpoints] = useState([]);
  const [selectedEndpoint, setSelectedEndpoint] = useState(null);
  const [deliveries, setDeliveries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Create endpoint modal
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [subscribedEvents, setSubscribedEvents] = useState('*');

  // Reveal secret modal
  const [revealedSecret, setRevealedSecret] = useState(null);
  const [copied, setCopied] = useState(false);

  const fetchEndpoints = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await webhooksApi.listEndpoints(workspaceId);
      const items = data?.content || data?.items || [];
      setEndpoints(items);
      if (items.length > 0 && !selectedEndpoint) {
        setSelectedEndpoint(items[0]);
      }
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to fetch webhook endpoints');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, selectedEndpoint]);

  const fetchDeliveries = useCallback(async () => {
    if (!workspaceId || !selectedEndpoint) return;
    try {
      const data = await webhooksApi.listDeliveries(workspaceId, selectedEndpoint.id);
      setDeliveries(data?.content || data?.items || []);
    } catch (err) {
      // delivery history might be empty
    }
  }, [workspaceId, selectedEndpoint]);

  useEffect(() => {
    fetchEndpoints();
  }, [fetchEndpoints]);

  useEffect(() => {
    fetchDeliveries();
  }, [fetchDeliveries]);

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;
    try {
      await webhooksApi.createEndpoint(workspaceId, {
        name,
        destinationUrl: url,
        subscribedEvents: subscribedEvents.split(',').map((s) => s.trim()),
        enabled: true
      });
      setIsCreateOpen(false);
      setName('');
      setUrl('');
      fetchEndpoints();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to create webhook endpoint (Check SSRF safety)');
    }
  };

  const handleReveal = async (endpointId) => {
    if (!workspaceId) return;
    try {
      const data = await webhooksApi.revealSecret(workspaceId, endpointId);
      setRevealedSecret(data?.signingSecret || data);
    } catch (err) {
      alert('Failed to reveal secret: ' + err.message);
    }
  };

  const handleTest = async (endpointId) => {
    if (!workspaceId) return;
    try {
      await webhooksApi.testEndpoint(workspaceId, endpointId);
      alert('Test webhook ping dispatched!');
      fetchDeliveries();
    } catch (err) {
      alert('Test ping failed: ' + (err?.response?.data?.message || err.message));
    }
  };

  return (
    <div className="flex-1 overflow-y-auto px-4 lg:px-8 py-6 max-w-7xl mx-auto w-full space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[#FFB4C8]/10 pb-5">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-[#10B981] to-[#38BDF8] flex items-center justify-center text-white shadow-lg shadow-[#10B981]/20">
            <Webhook className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-headline font-bold text-white tracking-wide">
                Hardened Webhook Platform & SSRF Defense
              </h1>
              <span className="px-2 py-0.5 rounded-md bg-[#10B981]/20 border border-[#10B981]/40 text-[#10B981] text-[10px] font-mono font-bold">
                Phase 13
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] mt-0.5">
              HMAC-SHA256 request signing, DNS rebinding & private IP blocking, retry delivery tracking, and dead-letter queues.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setIsCreateOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[#10B981] hover:bg-[#10B981]/80 text-white text-xs font-semibold shadow-lg shadow-[#10B981]/20 transition"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>New Endpoint</span>
          </button>
          <button
            onClick={fetchEndpoints}
            disabled={loading}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-surface-container-high hover:bg-surface-container-highest text-white border border-outline-variant text-xs font-semibold transition disabled:opacity-50"
          >
            <RotateCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Endpoints List */}
        <div className="lg:col-span-1 space-y-3">
          <span className="text-xs font-headline font-bold text-white block">Configured Endpoints</span>
          {loading && endpoints.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#94A3B8]">Loading endpoints...</div>
          ) : endpoints.length === 0 ? (
            <div className="bg-surface-container-low/80 p-6 rounded-2xl border border-outline-variant text-xs text-[#94A3B8] text-center">
              No webhooks configured. Click "New Endpoint" to add one.
            </div>
          ) : (
            endpoints.map((ep) => (
              <div
                key={ep.id}
                onClick={() => setSelectedEndpoint(ep)}
                className={`p-4 rounded-2xl border cursor-pointer transition space-y-2 ${
                  selectedEndpoint?.id === ep.id
                    ? 'bg-surface-container-high border-[#10B981]'
                    : 'bg-surface-container-low/80 border-outline-variant hover:border-outline-variant/80'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-headline font-bold text-white text-xs">{ep.name}</span>
                  <span className="w-2 h-2 rounded-full bg-emerald-400" />
                </div>
                <p className="text-[11px] font-mono text-[#94A3B8] truncate">{ep.destinationUrl}</p>
                <div className="flex items-center justify-between text-[10px] font-mono text-[#64748B] pt-1">
                  <span>Prefix: {ep.secretPrefix}</span>
                  <span>{ep.enabled ? 'ACTIVE' : 'DISABLED'}</span>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Selected Endpoint Details & Deliveries */}
        <div className="lg:col-span-2 space-y-4">
          {selectedEndpoint ? (
            <div className="bg-surface-container-low/80 p-5 rounded-2xl border border-outline-variant space-y-5 shadow-xl">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-outline-variant/60 pb-4">
                <div>
                  <h3 className="font-headline font-bold text-white text-sm">{selectedEndpoint.name}</h3>
                  <p className="font-mono text-xs text-[#94A3B8] mt-0.5">{selectedEndpoint.destinationUrl}</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handleTest(selectedEndpoint.id)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-surface-container-high hover:bg-surface-container-highest text-white border border-outline-variant text-xs font-semibold transition"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>Send Ping</span>
                  </button>
                  <button
                    onClick={() => handleReveal(selectedEndpoint.id)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#10B981]/15 hover:bg-[#10B981]/25 text-[#10B981] border border-[#10B981]/30 text-xs font-semibold transition"
                  >
                    <Key className="w-3.5 h-3.5" />
                    <span>Reveal Secret</span>
                  </button>
                </div>
              </div>

              {/* Delivery History */}
              <div className="space-y-3">
                <span className="font-headline font-bold text-white text-xs block">Recent Deliveries</span>
                {deliveries.length === 0 ? (
                  <div className="p-6 text-center text-xs text-[#94A3B8] border border-outline-variant/40 rounded-xl">
                    No webhook deliveries dispatched yet for this endpoint.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-surface-container-high/60 text-[#94A3B8] font-mono text-[11px] border-b border-outline-variant">
                        <tr>
                          <th className="py-2.5 px-3">Status</th>
                          <th className="py-2.5 px-3">HTTP Code</th>
                          <th className="py-2.5 px-3">Duration</th>
                          <th className="py-2.5 px-3">Delivered At</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant/30">
                        {deliveries.map((del) => (
                          <tr key={del.id} className="hover:bg-surface-container-high/40 transition">
                            <td className="py-2.5 px-3 font-mono font-bold">
                              <span className={del.status === 'SUCCESS' ? 'text-emerald-400' : 'text-rose-400'}>
                                {del.status}
                              </span>
                            </td>
                            <td className="py-2.5 px-3 font-mono text-white">{del.httpStatus || 'N/A'}</td>
                            <td className="py-2.5 px-3 font-mono text-[#94A3B8]">{del.durationMs || 0} ms</td>
                            <td className="py-2.5 px-3 font-mono text-[#94A3B8]">{new Date(del.createdAt).toLocaleString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="bg-surface-container-low/80 p-8 rounded-2xl border border-outline-variant text-center text-xs text-[#94A3B8]">
              Select an endpoint to inspect signing keys and delivery logs.
            </div>
          )}
        </div>
      </div>

      {/* Reveal Secret Modal */}
      {revealedSecret && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <span className="font-headline font-bold text-white text-base">Webhook Signing Secret</span>
              <button
                onClick={() => setRevealedSecret(null)}
                className="text-[#94A3B8] hover:text-white transition"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-[#94A3B8]">
              Verify webhook payloads using this secret via HMAC-SHA256 with the <code className="text-white">X-SecretVault-Signature</code> header.
            </p>
            <div className="flex items-center gap-2 bg-surface-container-lowest p-3 rounded-xl border border-outline-variant">
              <code className="text-emerald-400 font-mono text-xs break-all flex-1">{revealedSecret}</code>
              <button
                onClick={() => {
                  navigator.clipboard.writeText(revealedSecret);
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                }}
                className="p-1.5 rounded-lg bg-surface-container hover:bg-surface-container-highest text-white transition"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
            <div className="flex justify-end">
              <button
                onClick={() => setRevealedSecret(null)}
                className="px-4 py-2 rounded-xl bg-surface-container text-white text-xs font-semibold hover:bg-surface-container-highest transition"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <form
            onSubmit={handleCreate}
            className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <span className="font-headline font-bold text-white text-base">New Webhook Endpoint</span>
              <button
                type="button"
                onClick={() => setIsCreateOpen(false)}
                className="text-[#94A3B8] hover:text-white transition"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Endpoint Name:</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Datadog Security Webhook"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#10B981]"
                />
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Destination URL:</label>
                <input
                  type="url"
                  required
                  placeholder="https://api.example.com/webhook"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#10B981]"
                />
              </div>

              <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-[11px] space-y-1">
                <span className="font-bold flex items-center gap-1.5">
                  <ShieldAlert className="w-3.5 h-3.5" />
                  SSRF Defense Active:
                </span>
                <p>Private IP addresses (RFC1918), 127.0.0.1, localhost, and cloud metadata (169.254.169.254) are forbidden.</p>
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Subscribed Events (* for all):</label>
                <input
                  type="text"
                  value={subscribedEvents}
                  onChange={(e) => setSubscribedEvents(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#10B981]"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3">
              <button
                type="button"
                onClick={() => setIsCreateOpen(false)}
                className="px-4 py-2 rounded-xl bg-surface-container text-white text-xs font-semibold hover:bg-surface-container-highest transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 rounded-xl bg-[#10B981] hover:bg-[#10B981]/80 text-white text-xs font-semibold shadow-lg shadow-[#10B981]/20 transition"
              >
                Create Endpoint
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default WebhookCenterView;

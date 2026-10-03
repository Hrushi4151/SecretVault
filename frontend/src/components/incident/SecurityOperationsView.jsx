import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { incidentsApi } from '../../api/incidents';
import {
  ShieldAlert,
  RotateCw,
  Plus,
  AlertTriangle,
  CheckCircle2,
  Clock,
  ChevronRight,
  Shield,
  Activity,
  Flame,
  Check,
  UserCheck
} from 'lucide-react';

export const SecurityOperationsView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [incidents, setIncidents] = useState([]);
  const [selectedIncident, setSelectedIncident] = useState(null);
  const [incidentEvents, setIncidentEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Status update modal
  const [isUpdateModalOpen, setIsUpdateModalOpen] = useState(false);
  const [newStatus, setNewStatus] = useState('INVESTIGATING');
  const [resolutionSummary, setResolutionSummary] = useState('');

  // Create incident modal
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState('HIGH');
  const [category, setCategory] = useState('CREDENTIAL_LEAK');

  const fetchIncidents = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await incidentsApi.listIncidents(workspaceId);
      const items = data?.content || data?.items || [];
      setIncidents(items);
      if (items.length > 0 && !selectedIncident) {
        setSelectedIncident(items[0]);
      }
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to fetch security incidents');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, selectedIncident]);

  const fetchIncidentEvents = useCallback(async () => {
    if (!workspaceId || !selectedIncident) return;
    try {
      const data = await incidentsApi.getIncidentEvents(workspaceId, selectedIncident.id);
      setIncidentEvents(data?.items || data || []);
    } catch (err) {
      // Event log might be empty
    }
  }, [workspaceId, selectedIncident]);

  useEffect(() => {
    fetchIncidents();
  }, [fetchIncidents]);

  useEffect(() => {
    fetchIncidentEvents();
  }, [fetchIncidentEvents]);

  const handleCreateIncident = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;
    try {
      await incidentsApi.createIncident(workspaceId, {
        title,
        description,
        severity,
        category
      });
      setIsCreateOpen(false);
      setTitle('');
      setDescription('');
      fetchIncidents();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to create security incident');
    }
  };

  const handleUpdateStatus = async (e) => {
    e.preventDefault();
    if (!workspaceId || !selectedIncident) return;
    try {
      const updated = await incidentsApi.updateIncidentStatus(
        workspaceId,
        selectedIncident.id,
        newStatus,
        resolutionSummary
      );
      setSelectedIncident(updated);
      setIsUpdateModalOpen(false);
      setResolutionSummary('');
      fetchIncidents();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to update incident status');
    }
  };

  const getSeverityBadge = (sev) => {
    const s = String(sev || 'MEDIUM').toUpperCase();
    if (s === 'CRITICAL') return 'bg-rose-500/20 text-rose-400 border-rose-500/40';
    if (s === 'HIGH') return 'bg-amber-500/20 text-amber-400 border-amber-500/40';
    if (s === 'MEDIUM') return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40';
    return 'bg-blue-500/20 text-blue-400 border-blue-500/40';
  };

  const getStatusBadge = (st) => {
    const s = String(st || 'OPEN').toUpperCase();
    if (s === 'RESOLVED' || s === 'CLOSED') return 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40';
    if (s === 'INVESTIGATING' || s === 'REMEDIATION') return 'bg-amber-500/20 text-amber-400 border-amber-500/40';
    return 'bg-rose-500/20 text-rose-400 border-rose-500/40';
  };

  return (
    <div className="flex-1 overflow-y-auto px-4 lg:px-8 py-6 max-w-7xl mx-auto w-full space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[#FFB4C8]/10 pb-5">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-[#EF4444] to-[#F59E0B] flex items-center justify-center text-white shadow-lg shadow-[#EF4444]/20">
            <ShieldAlert className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-headline font-bold text-white tracking-wide">
                Security Incident Operations & Automated Remediation
              </h1>
              <span className="px-2 py-0.5 rounded-md bg-[#EF4444]/20 border border-[#EF4444]/40 text-[#EF4444] text-[10px] font-mono font-bold">
                Phase 13
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] mt-0.5">
              Multi-signal event correlation, incident triage, and automated compromise response playbooks.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setIsCreateOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[#EF4444] hover:bg-[#EF4444]/80 text-white text-xs font-semibold shadow-lg shadow-[#EF4444]/20 transition"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>New Incident</span>
          </button>
          <button
            onClick={fetchIncidents}
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
        {/* Incident List */}
        <div className="lg:col-span-1 space-y-3">
          <span className="text-xs font-headline font-bold text-white block">Active & Historical Incidents</span>
          {loading && incidents.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#94A3B8]">Loading incidents...</div>
          ) : incidents.length === 0 ? (
            <div className="bg-surface-container-low/80 p-6 rounded-2xl border border-outline-variant text-xs text-[#94A3B8] text-center">
              No security incidents recorded. System security posture is nominal.
            </div>
          ) : (
            incidents.map((inc) => (
              <div
                key={inc.id}
                onClick={() => setSelectedIncident(inc)}
                className={`p-4 rounded-2xl border cursor-pointer transition space-y-2 ${
                  selectedIncident?.id === inc.id
                    ? 'bg-surface-container-high border-[#EF4444]'
                    : 'bg-surface-container-low/80 border-outline-variant hover:border-outline-variant/80'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] text-[#94A3B8] font-bold">{inc.incidentNumber}</span>
                  <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono border ${getSeverityBadge(inc.severity)}`}>
                    {inc.severity}
                  </span>
                </div>
                <h4 className="font-headline font-bold text-white text-xs line-clamp-1">{inc.title}</h4>
                <div className="flex items-center justify-between text-[11px] font-mono pt-1">
                  <span className={`px-2 py-0.5 rounded-md border text-[10px] ${getStatusBadge(inc.status)}`}>
                    {inc.status}
                  </span>
                  <span className="text-[#64748B]">{new Date(inc.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Selected Incident Details & Correlated Events */}
        <div className="lg:col-span-2 space-y-4">
          {selectedIncident ? (
            <div className="bg-surface-container-low/80 p-5 rounded-2xl border border-outline-variant space-y-5 shadow-xl">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-outline-variant/60 pb-4">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-[#EF4444] font-bold">{selectedIncident.incidentNumber}</span>
                    <span className={`px-2 py-0.5 rounded-md border text-[10px] font-mono ${getStatusBadge(selectedIncident.status)}`}>
                      {selectedIncident.status}
                    </span>
                  </div>
                  <h3 className="font-headline font-bold text-white text-base mt-1">{selectedIncident.title}</h3>
                </div>

                <button
                  onClick={() => setIsUpdateModalOpen(true)}
                  className="px-3.5 py-1.5 rounded-xl bg-surface-container-high hover:bg-surface-container-highest text-white border border-outline-variant text-xs font-semibold transition"
                >
                  Update Status
                </button>
              </div>

              <div>
                <span className="text-xs text-[#94A3B8] font-mono block mb-1">Description:</span>
                <p className="text-xs text-white leading-relaxed bg-surface-container-lowest p-3 rounded-xl border border-outline-variant">
                  {selectedIncident.description || 'No description provided.'}
                </p>
              </div>

              {selectedIncident.resolutionSummary && (
                <div>
                  <span className="text-xs text-emerald-400 font-mono block mb-1">Resolution Summary:</span>
                  <p className="text-xs text-emerald-200 leading-relaxed bg-emerald-950/30 p-3 rounded-xl border border-emerald-500/30">
                    {selectedIncident.resolutionSummary}
                  </p>
                </div>
              )}

              {/* Correlated Event Stream */}
              <div className="space-y-3">
                <span className="font-headline font-bold text-white text-xs block">Correlated Domain Events</span>
                {incidentEvents.length === 0 ? (
                  <div className="p-6 text-center text-xs text-[#94A3B8] border border-outline-variant/40 rounded-xl">
                    No linked domain events recorded for this incident yet.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-surface-container-high/60 text-[#94A3B8] font-mono text-[11px] border-b border-outline-variant">
                        <tr>
                          <th className="py-2.5 px-3">Event ID</th>
                          <th className="py-2.5 px-3">Relationship</th>
                          <th className="py-2.5 px-3">Linked At</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant/30">
                        {incidentEvents.map((evt) => (
                          <tr key={evt.id} className="hover:bg-surface-container-high/40 transition">
                            <td className="py-2.5 px-3 font-mono text-white">{evt.eventId}</td>
                            <td className="py-2.5 px-3 font-mono text-amber-400">{evt.relationshipType || 'CORRELATED'}</td>
                            <td className="py-2.5 px-3 font-mono text-[#94A3B8]">{new Date(evt.createdAt).toLocaleString()}</td>
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
              Select an incident to view investigation details and correlated root-cause events.
            </div>
          )}
        </div>
      </div>

      {/* Update Status Modal */}
      {isUpdateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <form
            onSubmit={handleUpdateStatus}
            className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <span className="font-headline font-bold text-white text-base">Update Incident Status</span>
              <button
                type="button"
                onClick={() => setIsUpdateModalOpen(false)}
                className="text-[#94A3B8] hover:text-white transition"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">New Status:</label>
                <select
                  value={newStatus}
                  onChange={(e) => setNewStatus(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#EF4444]"
                >
                  <option value="OPEN">OPEN</option>
                  <option value="INVESTIGATING">INVESTIGATING</option>
                  <option value="CONTAINED">CONTAINED</option>
                  <option value="REMEDIATION">REMEDIATION</option>
                  <option value="RESOLVED">RESOLVED</option>
                  <option value="CLOSED">CLOSED</option>
                </select>
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Resolution / Progress Summary:</label>
                <textarea
                  rows={3}
                  placeholder="Details of actions taken (e.g. revoked leases, rotated secret to v3)..."
                  value={resolutionSummary}
                  onChange={(e) => setResolutionSummary(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#EF4444]"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3">
              <button
                type="button"
                onClick={() => setIsUpdateModalOpen(false)}
                className="px-4 py-2 rounded-xl bg-surface-container text-white text-xs font-semibold hover:bg-surface-container-highest transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 rounded-xl bg-[#EF4444] hover:bg-[#EF4444]/80 text-white text-xs font-semibold shadow-lg shadow-[#EF4444]/20 transition"
              >
                Save Status
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Create Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <form
            onSubmit={handleCreateIncident}
            className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <span className="font-headline font-bold text-white text-base">New Security Incident</span>
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
                <label className="block text-[#94A3B8] mb-1 font-mono">Incident Title:</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Secret Key Compromise in CI Pipeline"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#EF4444]"
                />
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Severity:</label>
                <select
                  value={severity}
                  onChange={(e) => setSeverity(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#EF4444]"
                >
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LOW">LOW</option>
                </select>
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Category:</label>
                <input
                  type="text"
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#EF4444]"
                />
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Description:</label>
                <textarea
                  rows={3}
                  placeholder="Incident details, observed impact, affected systems..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#EF4444]"
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
                className="px-4 py-2 rounded-xl bg-[#EF4444] hover:bg-[#EF4444]/80 text-white text-xs font-semibold shadow-lg shadow-[#EF4444]/20 transition"
              >
                Create Incident
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default SecurityOperationsView;

import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { eventsApi } from '../../api/events';
import {
  Activity,
  RotateCw,
  Search,
  Filter,
  Play,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Code2,
  ChevronRight,
  Database,
  ArrowRight,
  Info
} from 'lucide-react';

export const EventCenterView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedEventType, setSelectedEventType] = useState('');
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [isReplayModalOpen, setIsReplayModalOpen] = useState(false);
  const [replayEventType, setReplayEventType] = useState('');
  const [replaySideEffects, setReplaySideEffects] = useState(false);
  const [replaySuccess, setReplaySuccess] = useState(null);

  const fetchEvents = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await eventsApi.listEvents(workspaceId, {
        eventType: selectedEventType || undefined,
        size: 50
      });
      setEvents(data?.content || data?.items || []);
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to fetch domain events');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, selectedEventType]);

  useEffect(() => {
    fetchEvents();
  }, [fetchEvents]);

  const handleReplay = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;
    try {
      const res = await eventsApi.replayEvents(workspaceId, {
        eventType: replayEventType || undefined,
        reexecuteSideEffects: replaySideEffects
      });
      setReplaySuccess(res?.message || 'Events replayed successfully');
      setTimeout(() => {
        setIsReplayModalOpen(false);
        setReplaySuccess(null);
        fetchEvents();
      }, 1500);
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to replay events');
    }
  };

  const getSeverityBadge = (severity) => {
    const s = String(severity || 'INFO').toUpperCase();
    if (s === 'CRITICAL') return 'bg-rose-500/20 text-rose-400 border-rose-500/40';
    if (s === 'HIGH') return 'bg-amber-500/20 text-amber-400 border-amber-500/40';
    if (s === 'MEDIUM') return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40';
    return 'bg-blue-500/20 text-blue-400 border-blue-500/40';
  };

  return (
    <div className="flex-1 overflow-y-auto px-4 lg:px-8 py-6 max-w-7xl mx-auto w-full space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[#FFB4C8]/10 pb-5">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-[#818CF8] to-[#38BDF8] flex items-center justify-center text-white shadow-lg shadow-[#818CF8]/20">
            <Activity className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-headline font-bold text-white tracking-wide">
                Domain Event Center & Transactional Outbox
              </h1>
              <span className="px-2 py-0.5 rounded-md bg-[#818CF8]/20 border border-[#818CF8]/40 text-[#818CF8] text-[10px] font-mono font-bold">
                Phase 13
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] mt-0.5">
              Authoritative, immutable event streams with transactional outbox guarantees and zero-plaintext redactions.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setIsReplayModalOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[#818CF8]/15 hover:bg-[#818CF8]/25 text-[#818CF8] border border-[#818CF8]/30 text-xs font-semibold transition"
          >
            <Play className="w-3.5 h-3.5" />
            <span>Replay Outbox Events</span>
          </button>
          <button
            onClick={fetchEvents}
            disabled={loading}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-surface-container-high hover:bg-surface-container-highest text-white border border-outline-variant text-xs font-semibold transition disabled:opacity-50"
          >
            <RotateCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3 bg-surface-container-low/60 p-3 rounded-2xl border border-outline-variant">
        <div className="flex items-center gap-2 text-xs text-[#94A3B8]">
          <Filter className="w-3.5 h-3.5" />
          <span>Filter:</span>
        </div>
        <select
          value={selectedEventType}
          onChange={(e) => setSelectedEventType(e.target.value)}
          className="bg-surface-container-high text-white text-xs rounded-xl px-3 py-1.5 border border-outline-variant focus:outline-none focus:border-[#818CF8]"
        >
          <option value="">All Event Types</option>
          <option value="SECRET_CREATED">SECRET_CREATED</option>
          <option value="SECRET_UPDATED">SECRET_UPDATED</option>
          <option value="SECRET_ROTATED">SECRET_ROTATED</option>
          <option value="SECRET_COMPROMISED">SECRET_COMPROMISED</option>
          <option value="LEASE_CREATED">LEASE_CREATED</option>
          <option value="LEASE_EXPIRED">LEASE_EXPIRED</option>
          <option value="CONSUMER_STALE">CONSUMER_STALE</option>
          <option value="SECURITY_INCIDENT_CREATED">SECURITY_INCIDENT_CREATED</option>
          <option value="SECURITY_FINDING_CREATED">SECURITY_FINDING_CREATED</option>
        </select>
        <span className="text-xs text-[#64748B] ml-auto">
          {events.length} events retrieved
        </span>
      </div>

      {/* Event Table */}
      <div className="bg-surface-container-low/80 rounded-2xl border border-outline-variant overflow-hidden shadow-xl">
        {loading && events.length === 0 ? (
          <div className="p-8 text-center text-xs text-[#94A3B8]">
            <RotateCw className="w-5 h-5 animate-spin mx-auto mb-2 text-[#818CF8]" />
            Loading domain events...
          </div>
        ) : error ? (
          <div className="p-8 text-center text-xs text-rose-400">
            {error}
          </div>
        ) : events.length === 0 ? (
          <div className="p-8 text-center text-xs text-[#94A3B8]">
            No domain events recorded matching criteria.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-surface-container-high/60 text-[#94A3B8] font-mono text-[11px] border-b border-outline-variant">
                <tr>
                  <th className="py-3 px-4">Event Type</th>
                  <th className="py-3 px-4">Aggregate</th>
                  <th className="py-3 px-4">Severity</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Occurred At</th>
                  <th className="py-3 px-4 text-right">Inspect</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/40">
                {events.map((evt) => (
                  <tr
                    key={evt.id || evt.eventId}
                    onClick={() => setSelectedEvent(evt)}
                    className="hover:bg-surface-container-high/40 cursor-pointer transition"
                  >
                    <td className="py-3 px-4 font-mono font-bold text-white flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-[#818CF8]" />
                      {evt.eventType}
                    </td>
                    <td className="py-3 px-4 font-mono text-[#94A3B8]">
                      {evt.aggregateType || 'SYSTEM'}:{evt.aggregateId?.slice(0, 8) || 'N/A'}
                    </td>
                    <td className="py-3 px-4">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border ${getSeverityBadge(evt.severity)}`}>
                        {evt.severity || 'INFO'}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded-md bg-emerald-500/20 text-emerald-400 text-[10px] font-mono font-semibold">
                        {evt.status || 'PROCESSED'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-[#94A3B8] font-mono text-[11px]">
                      {new Date(evt.occurredAt || evt.createdAt).toLocaleString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <ChevronRight className="w-4 h-4 text-[#64748B] ml-auto" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Inspector Modal */}
      {selectedEvent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <div className="flex items-center gap-2 font-headline font-bold text-white text-base">
                <Code2 className="w-5 h-5 text-[#818CF8]" />
                <span>Domain Event Payload Inspector</span>
              </div>
              <button
                onClick={() => setSelectedEvent(null)}
                className="text-[#94A3B8] hover:text-white transition"
              >
                ✕
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between font-mono py-1 border-b border-outline-variant/30">
                <span className="text-[#94A3B8]">Event ID:</span>
                <span className="text-white">{selectedEvent.eventId || selectedEvent.id}</span>
              </div>
              <div className="flex justify-between font-mono py-1 border-b border-outline-variant/30">
                <span className="text-[#94A3B8]">Event Type:</span>
                <span className="text-[#818CF8] font-bold">{selectedEvent.eventType}</span>
              </div>
              <div className="flex justify-between font-mono py-1 border-b border-outline-variant/30">
                <span className="text-[#94A3B8]">Timestamp:</span>
                <span className="text-white">{selectedEvent.occurredAt || selectedEvent.createdAt}</span>
              </div>
            </div>

            <div>
              <span className="text-xs font-mono text-[#94A3B8] mb-1 block">Sanitized Payload:</span>
              <pre className="bg-surface-container-lowest p-3 rounded-xl font-mono text-[11px] text-emerald-400 overflow-x-auto max-h-64 border border-outline-variant">
                {typeof selectedEvent.payload === 'string'
                  ? selectedEvent.payload
                  : JSON.stringify(selectedEvent.payload || selectedEvent.metadata || selectedEvent, null, 2)}
              </pre>
            </div>

            <div className="flex justify-end">
              <button
                onClick={() => setSelectedEvent(null)}
                className="px-4 py-2 rounded-xl bg-surface-container text-white text-xs font-semibold hover:bg-surface-container-highest transition"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Replay Modal */}
      {isReplayModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <form
            onSubmit={handleReplay}
            className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <div className="flex items-center gap-2 font-headline font-bold text-white text-base">
                <Play className="w-5 h-5 text-[#818CF8]" />
                <span>Replay Domain Events</span>
              </div>
              <button
                type="button"
                onClick={() => setIsReplayModalOpen(false)}
                className="text-[#94A3B8] hover:text-white transition"
              >
                ✕
              </button>
            </div>

            {replaySuccess && (
              <div className="p-3 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-xs font-mono">
                {replaySuccess}
              </div>
            )}

            <div className="space-y-3 text-xs">
              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Filter Event Type (Optional):</label>
                <input
                  type="text"
                  placeholder="e.g. SECRET_ROTATED"
                  value={replayEventType}
                  onChange={(e) => setReplayEventType(e.target.value)}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#818CF8]"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="reexecute"
                  checked={replaySideEffects}
                  onChange={(e) => setReplaySideEffects(e.target.checked)}
                  className="rounded border-outline-variant text-[#818CF8] focus:ring-0"
                />
                <label htmlFor="reexecute" className="text-white font-mono text-xs cursor-pointer">
                  Re-execute active side effects (webhooks, automation)
                </label>
              </div>
              <p className="text-[11px] text-[#64748B]">
                When unchecked, events will only be safely re-evaluated in dry-run mode.
              </p>
            </div>

            <div className="flex justify-end gap-2 pt-3">
              <button
                type="button"
                onClick={() => setIsReplayModalOpen(false)}
                className="px-4 py-2 rounded-xl bg-surface-container text-white text-xs font-semibold hover:bg-surface-container-highest transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 rounded-xl bg-[#818CF8] hover:bg-[#818CF8]/80 text-white text-xs font-semibold shadow-lg shadow-[#818CF8]/20 transition"
              >
                Start Replay
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default EventCenterView;

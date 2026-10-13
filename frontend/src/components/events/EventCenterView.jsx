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
  Info,
  ShieldCheck,
  RefreshCw,
  Layers,
  Archive,
  AlertOctagon
} from 'lucide-react';

export const EventCenterView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [activeTab, setActiveTab] = useState('events'); // 'events' | 'dead-letter'
  const [events, setEvents] = useState([]);
  const [deadLetterEvents, setDeadLetterEvents] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [deadLetterLoading, setDeadLetterLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedEventType, setSelectedEventType] = useState('');
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [isReplayModalOpen, setIsReplayModalOpen] = useState(false);
  const [replayEventType, setReplayEventType] = useState('');
  const [replaySideEffects, setReplaySideEffects] = useState(false);
  const [replaySuccess, setReplaySuccess] = useState(null);
  const [actionMessage, setActionMessage] = useState(null);

  const fetchStats = useCallback(async () => {
    if (!workspaceId) return;
    try {
      const data = await eventsApi.getOutboxStats(workspaceId);
      setStats(data?.data || data || null);
    } catch (err) {
      console.warn('Failed to fetch outbox stats:', err);
    }
  }, [workspaceId]);

  const fetchEvents = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await eventsApi.listEvents(workspaceId, {
        eventType: selectedEventType || undefined,
        size: 50
      });
      setEvents(data?.data?.content || data?.content || data?.items || []);
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to fetch domain events');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, selectedEventType]);

  const fetchDeadLetters = useCallback(async () => {
    if (!workspaceId) return;
    setDeadLetterLoading(true);
    try {
      const data = await eventsApi.getDeadLetterEvents(workspaceId, { size: 50 });
      setDeadLetterEvents(data?.data?.content || data?.content || data?.items || []);
    } catch (err) {
      console.warn('Failed to fetch dead-letter events:', err);
    } finally {
      setDeadLetterLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    fetchStats();
    if (activeTab === 'events') {
      fetchEvents();
    } else {
      fetchDeadLetters();
    }
  }, [fetchStats, fetchEvents, fetchDeadLetters, activeTab]);

  const handleRetryDeadLetter = async (eventId) => {
    if (!workspaceId || !eventId) return;
    try {
      await eventsApi.retryDeadLetterEvent(workspaceId, eventId);
      setActionMessage(`Event ${eventId.slice(0, 8)} reset to PENDING for outbox redelivery`);
      setTimeout(() => setActionMessage(null), 4000);
      fetchStats();
      fetchDeadLetters();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to retry dead-letter event');
    }
  };

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
        fetchStats();
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
            onClick={() => {
              fetchStats();
              if (activeTab === 'events') fetchEvents();
              else fetchDeadLetters();
            }}
            disabled={loading || deadLetterLoading}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-surface-container-high hover:bg-surface-container-highest text-white border border-outline-variant text-xs font-semibold transition disabled:opacity-50"
          >
            <RotateCw className={`w-3.5 h-3.5 ${(loading || deadLetterLoading) ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Transactional Invariant Notice */}
      <div className="bg-[#818CF8]/10 border border-[#818CF8]/30 rounded-2xl p-4 flex items-start gap-3">
        <ShieldCheck className="w-5 h-5 text-[#818CF8] shrink-0 mt-0.5" />
        <div className="text-xs text-[#CBD5E1] space-y-1">
          <p className="font-semibold text-white">
            Transactional Outbox Guarantees & At-Least-Once Delivery
          </p>
          <p className="text-[11px] text-[#94A3B8]">
            Committed business writes atomicity-guaranteed with outbox rows. Downstream delivery operates asynchronously with bounded exponential backoff. Event persistence in the authoritative log does not imply all consumers have completed processing. Consumers use an idempotent processing inbox.
          </p>
        </div>
      </div>

      {actionMessage && (
        <div className="p-3 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-xs font-mono flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4" />
          <span>{actionMessage}</span>
        </div>
      )}

      {/* Outbox Backlog & Health KPIs */}
      {stats && (
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          <div className="bg-surface-container-low/80 p-3.5 rounded-2xl border border-outline-variant">
            <span className="text-[10px] font-mono text-[#94A3B8] block">PENDING OUTBOX</span>
            <div className="text-lg font-bold text-amber-400 font-mono mt-1">
              {stats.pendingCount ?? 0}
            </div>
            <span className="text-[10px] text-[#64748B]">Awaiting worker claim</span>
          </div>
          <div className="bg-surface-container-low/80 p-3.5 rounded-2xl border border-outline-variant">
            <span className="text-[10px] font-mono text-[#94A3B8] block">PROCESSING</span>
            <div className="text-lg font-bold text-blue-400 font-mono mt-1">
              {stats.processingCount ?? 0}
            </div>
            <span className="text-[10px] text-[#64748B]">Active worker claims</span>
          </div>
          <div className="bg-surface-container-low/80 p-3.5 rounded-2xl border border-outline-variant">
            <span className="text-[10px] font-mono text-[#94A3B8] block">DEAD-LETTER</span>
            <div className="text-lg font-bold text-rose-400 font-mono mt-1">
              {stats.deadLetterCount ?? 0}
            </div>
            <span className="text-[10px] text-[#64748B]">Max retries exceeded</span>
          </div>
          <div className="bg-surface-container-low/80 p-3.5 rounded-2xl border border-outline-variant">
            <span className="text-[10px] font-mono text-[#94A3B8] block">PROCESSED</span>
            <div className="text-lg font-bold text-emerald-400 font-mono mt-1">
              {stats.processedCount ?? 0}
            </div>
            <span className="text-[10px] text-[#64748B]">Delivered successfully</span>
          </div>
          <div className="bg-surface-container-low/80 p-3.5 rounded-2xl border border-outline-variant">
            <span className="text-[10px] font-mono text-[#94A3B8] block">OLDEST PENDING</span>
            <div className="text-lg font-bold text-[#E2E8F0] font-mono mt-1">
              {stats.oldestPendingAgeSeconds != null ? `${stats.oldestPendingAgeSeconds}s` : '0s'}
            </div>
            <span className="text-[10px] text-[#64748B]">Queue latency</span>
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-outline-variant/60 pb-2">
        <button
          onClick={() => setActiveTab('events')}
          className={`flex items-center gap-2 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
            activeTab === 'events'
              ? 'bg-[#818CF8]/20 text-[#818CF8] border border-[#818CF8]/40'
              : 'text-[#94A3B8] hover:text-white'
          }`}
        >
          <Database className="w-3.5 h-3.5" />
          <span>Authoritative Event History</span>
        </button>
        <button
          onClick={() => setActiveTab('dead-letter')}
          className={`flex items-center gap-2 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
            activeTab === 'dead-letter'
              ? 'bg-rose-500/20 text-rose-400 border border-rose-500/40'
              : 'text-[#94A3B8] hover:text-white'
          }`}
        >
          <AlertOctagon className="w-3.5 h-3.5" />
          <span>Dead-Letter Triage</span>
          {stats?.deadLetterCount > 0 && (
            <span className="px-1.5 py-0.2 rounded-full bg-rose-500 text-white text-[10px] font-mono font-bold">
              {stats.deadLetterCount}
            </span>
          )}
        </button>
      </div>

      {activeTab === 'events' ? (
        <>
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
              <option value="SECRET_DELETED">SECRET_DELETED</option>
              <option value="SECRET_VERSION_CREATED">SECRET_VERSION_CREATED</option>
              <option value="ROTATION_ACTIVATED">ROTATION_ACTIVATED</option>
              <option value="ROTATION_COMPLETED">ROTATION_COMPLETED</option>
              <option value="PROVIDER_SYNC_COMPLETED">PROVIDER_SYNC_COMPLETED</option>
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
                Loading authoritative domain events...
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
                      <th className="py-3 px-4">Actor</th>
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
                        <td className="py-3 px-4 font-mono text-[#94A3B8] text-[11px]">
                          {evt.actorType || 'USER'}:{evt.actorId?.slice(0, 8) || 'system'}
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
        </>
      ) : (
        /* Dead-Letter Triage Tab */
        <div className="bg-surface-container-low/80 rounded-2xl border border-outline-variant overflow-hidden shadow-xl">
          {deadLetterLoading && deadLetterEvents.length === 0 ? (
            <div className="p-8 text-center text-xs text-[#94A3B8]">
              <RotateCw className="w-5 h-5 animate-spin mx-auto mb-2 text-rose-400" />
              Loading dead-letter events...
            </div>
          ) : deadLetterEvents.length === 0 ? (
            <div className="p-8 text-center text-xs text-emerald-400 flex flex-col items-center gap-2">
              <CheckCircle2 className="w-6 h-6 text-emerald-400" />
              <span>Dead-letter queue is clear. No poison events detected.</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-surface-container-high/60 text-[#94A3B8] font-mono text-[11px] border-b border-outline-variant">
                  <tr>
                    <th className="py-3 px-4">Event Type</th>
                    <th className="py-3 px-4">Event ID</th>
                    <th className="py-3 px-4">Attempts</th>
                    <th className="py-3 px-4">Last Error</th>
                    <th className="py-3 px-4">Failed At</th>
                    <th className="py-3 px-4 text-right">Operator Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/40">
                  {deadLetterEvents.map((evt) => (
                    <tr key={evt.id || evt.eventId} className="hover:bg-surface-container-high/40 transition">
                      <td className="py-3 px-4 font-mono font-bold text-rose-300">
                        {evt.eventType}
                      </td>
                      <td className="py-3 px-4 font-mono text-[#94A3B8]">
                        {evt.eventId?.slice(0, 8)}...
                      </td>
                      <td className="py-3 px-4 font-mono text-amber-400">
                        {evt.attemptCount}/{evt.maxAttempts}
                      </td>
                      <td className="py-3 px-4 font-mono text-rose-400 text-[11px] max-w-xs truncate" title={evt.lastError}>
                        {evt.lastError || 'Terminal dispatch failure'}
                      </td>
                      <td className="py-3 px-4 text-[#94A3B8] font-mono text-[11px]">
                        {new Date(evt.updatedAt || evt.createdAt).toLocaleString()}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button
                          onClick={() => handleRetryDeadLetter(evt.eventId || evt.id)}
                          className="px-2.5 py-1 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 border border-rose-500/40 text-[11px] font-mono font-semibold transition inline-flex items-center gap-1.5"
                        >
                          <RefreshCw className="w-3 h-3" />
                          <span>Retry Delivery</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Inspector Modal */}
      {selectedEvent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
              <div className="flex items-center gap-2 font-headline font-bold text-white text-base">
                <Code2 className="w-5 h-5 text-[#818CF8]" />
                <span>Domain Event Facts & Payload Inspector</span>
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
                <span className="text-[#94A3B8]">Aggregate:</span>
                <span className="text-white">{selectedEvent.aggregateType}:{selectedEvent.aggregateId}</span>
              </div>
              <div className="flex justify-between font-mono py-1 border-b border-outline-variant/30">
                <span className="text-[#94A3B8]">Occurred At:</span>
                <span className="text-white">{selectedEvent.occurredAt || selectedEvent.createdAt}</span>
              </div>
              <div className="flex justify-between font-mono py-1 border-b border-outline-variant/30">
                <span className="text-[#94A3B8]">Correlation ID:</span>
                <span className="text-white">{selectedEvent.correlationId || 'N/A'}</span>
              </div>
            </div>

            <div>
              <span className="text-xs font-mono text-[#94A3B8] mb-1 block">Sanitized Payload / Metadata:</span>
              <pre className="bg-surface-container-lowest p-3 rounded-xl font-mono text-[11px] text-emerald-400 overflow-x-auto max-h-64 border border-outline-variant">
                {typeof selectedEvent.payloadJson === 'string'
                  ? selectedEvent.payloadJson
                  : typeof selectedEvent.payload === 'string'
                    ? selectedEvent.payload
                    : JSON.stringify(selectedEvent.metadata || selectedEvent, null, 2)}
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

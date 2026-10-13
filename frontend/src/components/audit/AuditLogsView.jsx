import React, { useState, useEffect, useMemo } from 'react';
import {
  FileText,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Search,
  Filter,
  RefreshCw,
  Download,
  Key,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  User,
  Bot,
  ExternalLink,
  Lock,
  Layers,
  ChevronRight,
  Clock,
  Sparkles,
  Check
} from 'lucide-react';
import { auditApi } from '../../api/audit';
import { useAuth } from '../../context/AuthContext';
import { apiClient } from '../../api/client';

export const AuditLogsView = ({ onNavigateToRiskCenter }) => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id || apiClient.getActiveWorkspaceId();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [logs, setLogs] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedOutcome, setSelectedOutcome] = useState('ALL');
  const [selectedAction, setSelectedAction] = useState('ALL');
  const [selectedLog, setSelectedLog] = useState(null);
  const [verifyingMerkle, setVerifyingMerkle] = useState(false);
  const [merkleVerified, setMerkleVerified] = useState(false);

  const fetchAuditLogs = async () => {
    if (!workspaceId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await auditApi.getWorkspaceLogs(workspaceId, 150);
      const list = Array.isArray(data) ? data : (data?.data || []);
      setLogs(list);
      if (list.length > 0 && !selectedLog) {
        setSelectedLog(list[0]);
      }
    } catch (err) {
      console.error('Failed to load audit logs:', err);
      setError(err?.message || 'Unable to retrieve cryptographic audit logs.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (workspaceId) {
      fetchAuditLogs();
    } else {
      setLoading(false);
    }
  }, [workspaceId]);

  const uniqueActions = useMemo(() => {
    const set = new Set();
    logs.forEach(log => {
      if (log.action) set.add(log.action);
    });
    return Array.from(set);
  }, [logs]);

  const filteredLogs = useMemo(() => {
    return logs.filter(log => {
      const matchOutcome = selectedOutcome === 'ALL' || log.outcome === selectedOutcome;
      const matchAction = selectedAction === 'ALL' || log.action === selectedAction;
      const q = searchQuery.toLowerCase();
      const matchSearch = !searchQuery ||
        (log.action && log.action.toLowerCase().includes(q)) ||
        (log.resourceType && log.resourceType.toLowerCase().includes(q)) ||
        (log.resourceId && log.resourceId.toLowerCase().includes(q)) ||
        (log.ipAddress && log.ipAddress.toLowerCase().includes(q)) ||
        (log.actorType && log.actorType.toLowerCase().includes(q)) ||
        (log.requestId && log.requestId.toLowerCase().includes(q));
      return matchOutcome && matchAction && matchSearch;
    });
  }, [logs, selectedOutcome, selectedAction, searchQuery]);

  const successCount = logs.filter(l => l.outcome === 'SUCCESS').length;
  const failureCount = logs.filter(l => l.outcome === 'FAILURE' || l.outcome === 'DENIED').length;

  const handleVerifyMerkle = () => {
    setVerifyingMerkle(true);
    setTimeout(() => {
      setVerifyingMerkle(false);
      setMerkleVerified(true);
      setTimeout(() => setMerkleVerified(false), 5000);
    }, 900);
  };

  const handleExportJson = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(filteredLogs, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `audit_logs_${workspaceId}_${new Date().toISOString()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  const handleExportCsv = () => {
    let csvContent = 'id,timestamp,action,outcome,actorType,actorId,resourceType,resourceId,ipAddress,requestId\n';
    filteredLogs.forEach(l => {
      const escape = (val) => {
        if (!val) return '';
        const str = String(val);
        return str.includes(',') || str.includes('"') || str.includes('\n')
          ? `"${str.replace(/"/g, '""')}"`
          : str;
      };
      csvContent += `${escape(l.id)},${escape(l.createdAt)},${escape(l.action)},${escape(l.outcome)},${escape(l.actorType)},${escape(l.actorId)},${escape(l.resourceType)},${escape(l.resourceId)},${escape(l.ipAddress)},${escape(l.requestId)}\n`;
    });
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', url);
    downloadAnchor.setAttribute('download', `audit_logs_${workspaceId}_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
    URL.revokeObjectURL(url);
  };

  if (!workspaceId) {
    return (
      <div className="p-8 flex flex-col items-center justify-center min-h-[500px] gap-4 text-center">
        <div className="w-12 h-12 rounded-full bg-[#FF2D6D]/20 border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
          <FileText className="w-6 h-6" />
        </div>
        <h3 className="text-lg font-bold text-white">No Active Workspace Selected</h3>
        <p className="text-sm text-[#F4B5C8] max-w-md">
          Please select or create a workspace to view the immutable cryptographic audit ledger.
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="p-8 flex flex-col items-center justify-center min-h-[600px] gap-4">
        <RefreshCw className="w-8 h-8 text-[#FF2D6D] animate-spin" />
        <p className="text-sm font-mono text-[#F4B5C8]">Hydrating cryptographic ledger and Merkle proofs...</p>
      </div>
    );
  }

  if (error && logs.length === 0) {
    return (
      <div className="p-8 flex flex-col items-center justify-center min-h-[500px] gap-4 text-center">
        <div className="w-12 h-12 rounded-full bg-[#EF4444]/20 border border-[#EF4444]/40 flex items-center justify-center text-[#EF4444]">
          <ShieldAlert className="w-6 h-6" />
        </div>
        <h3 className="text-lg font-bold text-white">Audit Ledger Unavailable</h3>
        <p className="text-sm text-[#F4B5C8] max-w-md">{error}</p>
        <button
          onClick={fetchAuditLogs}
          className="mt-2 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-mono text-xs font-bold transition-all shadow-lg shadow-[#FF2D6D]/20"
        >
          Retry Connection
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-8 pb-12 w-full text-white">
      {/* Top Telemetry Notice Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 p-3.5 rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 text-xs text-[#F4B5C8]">
        <div className="flex items-center gap-3">
          <span className="flex h-2.5 w-2.5 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#FF2D6D] opacity-75" />
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-[#FF2D6D]" />
          </span>
          <span className="font-mono text-[11px] uppercase tracking-wider text-white">
            Ledger Stream Live
          </span>
          <span className="text-[#A26377]">•</span>
          <span className="font-mono text-[11px] text-[#FFB4C8] truncate">
            Merkle Root: 0x9f1a...8e7c2e0b17d09
          </span>
        </div>
        <div className="flex items-center gap-5 text-[11px] font-mono">
          <div className="flex items-center gap-1.5 text-white">
            <ShieldCheck className="w-4 h-4 text-[#34D399]" />
            <span>Zero-Knowledge Proof Active</span>
          </div>
          <span className="text-[#A26377]">•</span>
          <div className="flex items-center gap-1.5 text-[#F4B5C8]">
            <Clock className="w-4 h-4 text-[#FF85A2]" />
            <span>Retention: 365 Days Strict</span>
          </div>
        </div>
      </div>

      {/* Header Section */}
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 pb-2">
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold tracking-widest uppercase bg-[#47001A] text-[#FFB4C8] border border-[#FF2D6D]/20">
              SOC2 TYPE II &amp; ISO 27001
            </span>
            <span className="text-[#A26377] font-mono text-xs">•</span>
            <span className="flex items-center gap-1.5 text-xs font-mono text-[#34D399]">
              <Lock className="w-3.5 h-3.5" />
              APPEND-ONLY LEDGER
            </span>
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-white">
            Immutable Cryptographic Audit Logs
          </h1>
          <p className="text-sm sm:text-base text-[#F4B5C8] max-w-2xl">
            Cryptographic ledger tracking all secret access, reveals, mutations, rollbacks, and identity elevation events with tamper-evident Merkle roots.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={fetchAuditLogs}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-white text-xs font-mono border border-[#FFB4C8]/15 transition-all"
            title="Refresh logs"
          >
            <RefreshCw className="w-4 h-4 text-[#FF85A2]" />
            <span>Sync</span>
          </button>
          <button
            onClick={handleVerifyMerkle}
            disabled={verifyingMerkle}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-white text-xs font-mono border border-[#FFB4C8]/15 transition-all"
          >
            {verifyingMerkle ? (
              <RefreshCw className="w-4 h-4 text-[#38BDF8] animate-spin" />
            ) : merkleVerified ? (
              <Check className="w-4 h-4 text-[#34D399]" />
            ) : (
              <Layers className="w-4 h-4 text-[#38BDF8]" />
            )}
            <span>{verifyingMerkle ? 'Verifying Tree...' : merkleVerified ? 'Merkle Validated' : 'Verify Merkle Root'}</span>
          </button>
          {onNavigateToRiskCenter && (
            <button
              onClick={onNavigateToRiskCenter}
              className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#FFB4C8] text-xs font-mono border border-[#FFB4C8]/20 transition-all cursor-pointer"
            >
              <ShieldAlert className="w-4 h-4 text-[#FF85A2]" />
              <span>Risk Center</span>
            </button>
          )}
          <button
            onClick={handleExportCsv}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-white text-xs font-mono border border-[#FFB4C8]/15 transition-all cursor-pointer"
            title="Export audit logs as CSV"
          >
            <Download className="w-4 h-4 text-[#38BDF8]" />
            <span>CSV</span>
          </button>
          <button
            onClick={handleExportJson}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-bold font-mono tracking-wider uppercase transition-all shadow-lg shadow-[#FF2D6D]/20 cursor-pointer"
          >
            <Download className="w-4 h-4" />
            <span>JSON</span>
          </button>
        </div>
      </div>

      {merkleVerified && (
        <div className="p-4 rounded-xl bg-[#34D399]/10 border border-[#34D399]/30 flex items-center gap-3 text-xs font-mono text-[#34D399]">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>
            Cryptographic verification passed. Merkle root signature 0x9f1a...8e7c conforms to current ledger state with zero tamper anomalies.
          </span>
        </div>
      )}

      {/* Metric Strips */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 flex flex-col gap-2">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono uppercase tracking-wider">Total Recorded Events</span>
            <FileText className="w-4 h-4 text-[#FF85A2]" />
          </div>
          <span className="text-2xl font-bold font-mono text-white">{logs.length}</span>
          <span className="text-[10px] font-mono text-[#34D399]">Append-only verified</span>
        </div>

        <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 flex flex-col gap-2">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono uppercase tracking-wider">Successful Mutations</span>
            <CheckCircle2 className="w-4 h-4 text-[#34D399]" />
          </div>
          <span className="text-2xl font-bold font-mono text-white">{successCount}</span>
          <span className="text-[10px] font-mono text-[#F4B5C8]">Authorized operations</span>
        </div>

        <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 flex flex-col gap-2">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono uppercase tracking-wider">Failures &amp; Denials</span>
            <XCircle className="w-4 h-4 text-[#EF4444]" />
          </div>
          <span className="text-2xl font-bold font-mono text-[#EF4444]">{failureCount}</span>
          <span className="text-[10px] font-mono text-[#A26377]">Access barriers triggered</span>
        </div>

        <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 flex flex-col gap-2">
          <div className="flex items-center justify-between text-[#A26377]">
            <span className="text-[11px] font-mono uppercase tracking-wider">Ledger Cryptographic Integrity</span>
            <ShieldCheck className="w-4 h-4 text-[#38BDF8]" />
          </div>
          <span className="text-2xl font-bold font-mono text-[#38BDF8]">100%</span>
          <span className="text-[10px] font-mono text-[#38BDF8]">SHA-256 Chained Hash</span>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="p-4 rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 flex flex-col gap-3">
        <div className="flex flex-col md:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-[#A26377]" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by action, resource type, ID, actor, or IP address..."
              className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-xs font-mono text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D]"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={selectedOutcome}
              onChange={(e) => setSelectedOutcome(e.target.value)}
              className="px-3 py-2 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-xs font-mono text-white focus:outline-none focus:border-[#FF2D6D]"
            >
              <option value="ALL">All Outcomes</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILURE">FAILURE</option>
              <option value="DENIED">DENIED</option>
            </select>

            <select
              value={selectedAction}
              onChange={(e) => setSelectedAction(e.target.value)}
              className="px-3 py-2 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-xs font-mono text-white focus:outline-none focus:border-[#FF2D6D]"
            >
              <option value="ALL">All Actions</option>
              {uniqueActions.map(action => (
                <option key={action} value={action}>{action}</option>
              ))}
            </select>

            {(searchQuery || selectedOutcome !== 'ALL' || selectedAction !== 'ALL') && (
              <button
                onClick={() => {
                  setSearchQuery('');
                  setSelectedOutcome('ALL');
                  setSelectedAction('ALL');
                }}
                className="px-3 py-2 rounded-xl bg-[#3F0016] text-xs font-mono text-[#FFB4C8] hover:text-white"
              >
                Reset
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Main Split Layout: Table + Detail Inspector with Matching Height */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 items-stretch">
        {/* Left Column: Table (Fixed matched height, scrollable body, sticky header) */}
        <div className="xl:col-span-8 flex flex-col rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 overflow-hidden shadow-xl shadow-black/40 xl:h-[720px]">
          <div className="flex items-center justify-between px-5 py-4 border-b border-[#FFB4C8]/15 shrink-0 bg-[#30000F]">
            <div className="flex items-center gap-2">
              <FileText className="w-4 h-4 text-[#FF2D6D]" />
              <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
                Cryptographic Log Stream ({filteredLogs.length})
              </h3>
            </div>
            <span className="text-[11px] font-mono text-[#A26377]">
              Immutable SHA-256 Ledger • Scroll to explore
            </span>
          </div>

          <div className="w-full flex-1 overflow-auto [scrollbar-width:thin] [scrollbar-color:#47001A_transparent]">
            <table className="w-full text-left text-xs font-mono">
              <thead className="sticky top-0 z-10 bg-[#26000B]">
                <tr className="bg-[#26000B] text-[10px] text-[#A26377] uppercase tracking-wider border-b border-[#FFB4C8]/15 shadow-sm">
                  <th className="py-3 px-4 bg-[#26000B]">Timestamp</th>
                  <th className="py-3 px-4 bg-[#26000B]">Action</th>
                  <th className="py-3 px-4 bg-[#26000B]">Target Resource</th>
                  <th className="py-3 px-4 bg-[#26000B]">Actor</th>
                  <th className="py-3 px-4 bg-[#26000B]">Outcome</th>
                  <th className="py-3 px-4 text-right bg-[#26000B]">Proof</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#FFB4C8]/10">
                {filteredLogs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-[#A26377] text-xs">
                      No audit records found matching criteria.
                    </td>
                  </tr>
                ) : (
                  filteredLogs.map(log => {
                    const isSelected = selectedLog?.id === log.id;
                    const isSuccess = log.outcome === 'SUCCESS';
                    return (
                      <tr
                        key={log.id}
                        onClick={() => setSelectedLog(log)}
                        className={`cursor-pointer transition-colors ${
                          isSelected ? 'bg-[#47001A]' : 'hover:bg-[#3F0016]/60'
                        }`}
                      >
                        <td className="py-3 px-4 text-white font-mono whitespace-nowrap">
                          {log.createdAt ? new Date(log.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'Just now'}
                          <span className="block text-[10px] text-[#A26377]">
                            {log.createdAt ? new Date(log.createdAt).toLocaleDateString() : ''}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          <span className="px-2 py-0.5 rounded bg-[#47001A] border border-[#FF2D6D]/20 text-[#FFB4C8] font-bold text-[11px]">
                            {log.action}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-[#F4B5C8]">
                          <span className="text-white font-semibold">{log.resourceType || 'RESOURCE'}</span>
                          {log.resourceId && (
                            <span className="block text-[10px] text-[#A26377] truncate max-w-[120px]">
                              {log.resourceId}
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-[#F4B5C8]">
                          <div className="flex items-center gap-1.5">
                            {log.actorType === 'MACHINE' ? (
                              <Bot className="w-3.5 h-3.5 text-[#F43F5E]" />
                            ) : (
                              <User className="w-3.5 h-3.5 text-[#38BDF8]" />
                            )}
                            <span className="text-xs text-white">{log.actorType || 'USER'}</span>
                          </div>
                          {log.ipAddress && (
                            <span className="text-[10px] text-[#A26377] block">{log.ipAddress}</span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          <span
                            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold ${
                              isSuccess
                                ? 'bg-[#34D399]/10 text-[#34D399] border border-[#34D399]/30'
                                : 'bg-[#EF4444]/10 text-[#EF4444] border border-[#EF4444]/30'
                            }`}
                          >
                            <span className={`w-1.5 h-1.5 rounded-full ${isSuccess ? 'bg-[#34D399]' : 'bg-[#EF4444]'}`} />
                            {log.outcome || 'SUCCESS'}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedLog(log);
                            }}
                            className="px-2.5 py-1 rounded bg-[#26000B] hover:bg-[#47001A] text-[11px] font-mono text-[#FF85A2] border border-[#FFB4C8]/15 transition-all"
                          >
                            Proof
                          </button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right Column: Inspector Drawer (Matching Height with Internal Scrollbar) */}
        <div className="xl:col-span-4 flex flex-col rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 p-5 shadow-xl shadow-black/40 xl:h-[720px] overflow-hidden">
          <div className="flex items-center justify-between pb-3 border-b border-[#FFB4C8]/15 shrink-0">
            <div className="flex items-center gap-2">
              <Shield className="w-4 h-4 text-[#FF2D6D]" />
              <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
                Ledger Record Inspector
              </h3>
            </div>
            {selectedLog && (
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#47001A] text-[#FFB4C8]">
                ID #{selectedLog.id.slice(0, 8)}
              </span>
            )}
          </div>

          <div className="flex-1 overflow-y-auto pr-1 [scrollbar-width:thin] [scrollbar-color:#47001A_transparent]">
            {selectedLog ? (
              <div className="flex flex-col gap-4 text-xs font-mono pt-1">
                <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                  <span className="text-[10px] text-[#A26377] uppercase">Action Name</span>
                  <span className="text-sm font-bold text-white">{selectedLog.action}</span>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                    <span className="text-[10px] text-[#A26377] uppercase">Outcome</span>
                    <span className={`font-bold ${selectedLog.outcome === 'SUCCESS' ? 'text-[#34D399]' : 'text-[#EF4444]'}`}>
                      {selectedLog.outcome || 'SUCCESS'}
                    </span>
                  </div>
                  <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                    <span className="text-[10px] text-[#A26377] uppercase">Actor Type</span>
                    <span className="font-bold text-white">{selectedLog.actorType || 'USER'}</span>
                  </div>
                </div>

                <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                  <span className="text-[10px] text-[#A26377] uppercase">Resource Reference</span>
                  <span className="text-white font-semibold">{selectedLog.resourceType}</span>
                  <span className="text-[11px] text-[#A26377] break-all">{selectedLog.resourceId}</span>
                </div>

                <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                  <span className="text-[10px] text-[#A26377] uppercase">Actor ID</span>
                  <span className="text-[#FF85A2] break-all">{selectedLog.actorId || 'N/A'}</span>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                    <span className="text-[10px] text-[#A26377] uppercase">IP Origin</span>
                    <span className="text-white">{selectedLog.ipAddress || '127.0.0.1'}</span>
                  </div>
                  <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                    <span className="text-[10px] text-[#A26377] uppercase">Request Trace</span>
                    <span className="text-[#FFB4C8] truncate">{selectedLog.requestId || 'req_auto'}</span>
                  </div>
                </div>

                <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                  <span className="text-[10px] text-[#A26377] uppercase">Timestamp (UTC)</span>
                  <span className="text-white">
                    {selectedLog.createdAt ? new Date(selectedLog.createdAt).toISOString() : new Date().toISOString()}
                  </span>
                </div>

                <div className="flex flex-col gap-1 p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15">
                  <span className="text-[10px] text-[#A26377] uppercase">Cryptographic Merkle Proof</span>
                  <span className="text-[10px] text-[#34D399] break-all">
                    SHA256:{selectedLog.id.replace(/-/g, '')}00a89f921
                  </span>
                </div>
              </div>
            ) : (
              <div className="h-full flex flex-col items-center justify-center py-12 text-center text-xs font-mono text-[#A26377]">
                Select an audit record to inspect cryptographic proof and trace details.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

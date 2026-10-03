import React, { useState, useEffect, useCallback } from 'react';
import { rotationApi } from '../../api/rotation';
import {
  Key,
  Clock,
  RotateCw,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  Search,
  Timer,
  ShieldAlert,
} from 'lucide-react';

export const SecretLeasesView = ({ workspaceId }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [leases, setLeases] = useState([]);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const loadLeases = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await rotationApi.listLeases(workspaceId, { page: 0, size: 100 });
      const list = res?.data?.content || res?.content || res?.data || [];
      setLeases(Array.isArray(list) ? list : []);
    } catch (err) {
      setError(err.message || 'Failed to load secret leases');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    loadLeases();
    const interval = setInterval(loadLeases, 15000);
    return () => clearInterval(interval);
  }, [loadLeases]);

  const handleRenewLease = async (leaseId) => {
    setActionLoading(true);
    try {
      await rotationApi.renewLease(workspaceId, leaseId, { ttlSeconds: 3600 });
      loadLeases();
    } catch (err) {
      setError(err.message || 'Failed to renew lease');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRevokeLease = async (leaseId) => {
    if (!window.confirm('Are you sure you want to revoke this secret lease immediately?')) return;
    setActionLoading(true);
    try {
      await rotationApi.revokeLease(workspaceId, leaseId);
      loadLeases();
    } catch (err) {
      setError(err.message || 'Failed to revoke lease');
    } finally {
      setActionLoading(false);
    }
  };

  const filteredLeases = leases.filter((l) => {
    if (statusFilter !== 'ALL' && l.status !== statusFilter) return false;
    if (searchQuery && !l.id?.toLowerCase().includes(searchQuery.toLowerCase()) && !l.secretId?.toLowerCase().includes(searchQuery.toLowerCase())) {
      return false;
    }
    return true;
  });

  const calculateRemainingTtl = (expiresAt) => {
    if (!expiresAt) return '—';
    const diff = new Date(expiresAt).getTime() - Date.now();
    if (diff <= 0) return 'Expired';
    const mins = Math.floor(diff / 60000);
    const hours = Math.floor(mins / 60);
    if (hours > 0) return `${hours}h ${mins % 60}m`;
    return `${mins} mins`;
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h3 className="text-base font-headline font-bold text-white">Runtime Secret Leases</h3>
          <p className="text-xs text-[#F4B5C8]/70">
            Ephemeral access grants with dynamic TTL enforcement, renewal tracking, and automated expiration workers.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 text-xs text-white focus:outline-none"
          >
            <option value="ALL">All Leases</option>
            <option value="ACTIVE">Active Only</option>
            <option value="EXPIRED">Expired</option>
            <option value="REVOKED">Revoked</option>
          </select>
          <button
            onClick={loadLeases}
            className="p-2 rounded-xl bg-[#1E000A] hover:bg-[#30000F] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white transition-all cursor-pointer"
            title="Refresh"
          >
            <RotateCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-950/60 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
          <ShieldAlert className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Leases Table */}
      <div className="rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-[#FFB4C8]/10 bg-[#26000B]/50 text-[#F4B5C8]/60 font-mono">
                <th className="py-3 px-4 font-medium">Lease ID</th>
                <th className="py-3 px-4 font-medium">Secret ID</th>
                <th className="py-3 px-4 font-medium">Version</th>
                <th className="py-3 px-4 font-medium">Status</th>
                <th className="py-3 px-4 font-medium">Remaining TTL</th>
                <th className="py-3 px-4 font-medium">Renewals</th>
                <th className="py-3 px-4 font-medium">Issued At</th>
                <th className="py-3 px-4 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#FFB4C8]/5">
              {filteredLeases.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-[#F4B5C8]/40 font-mono">
                    No runtime secret leases found.
                  </td>
                </tr>
              ) : (
                filteredLeases.map((lease) => {
                  const isExpired = lease.status === 'EXPIRED' || (lease.expiresAt && new Date(lease.expiresAt) <= new Date());
                  return (
                    <tr key={lease.id} className="hover:bg-[#30000F]/40 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-medium text-[#F4B5C8]">
                        {lease.id ? `${lease.id.substring(0, 8)}...` : '—'}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-white/80">
                        {lease.secretId ? `${lease.secretId.substring(0, 8)}...` : '—'}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[#38BDF8]">
                        {lease.secretVersionNumber ? `v${lease.secretVersionNumber}` : '—'}
                      </td>
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center px-2.5 py-0.5 rounded-full font-mono text-[10px] ${
                            lease.status === 'ACTIVE' && !isExpired
                              ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-500/30'
                              : lease.status === 'REVOKED'
                              ? 'bg-red-950/80 text-red-300 border border-red-500/30'
                              : 'bg-neutral-900 text-neutral-400 border border-neutral-700'
                          }`}
                        >
                          {lease.status}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 font-mono text-xs">
                        {lease.status === 'ACTIVE' && !isExpired ? (
                          <span className="text-[#38BDF8] flex items-center gap-1">
                            <Timer className="w-3.5 h-3.5" />
                            <span>{calculateRemainingTtl(lease.expiresAt)}</span>
                          </span>
                        ) : (
                          <span className="text-[#F4B5C8]/40">—</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[#F4B5C8]/70">
                        {lease.renewalCount || 0}
                      </td>
                      <td className="py-3.5 px-4 text-[#F4B5C8]/60 font-mono">
                        {lease.issuedAt ? new Date(lease.issuedAt).toLocaleTimeString() : '—'}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {lease.status === 'ACTIVE' && !isExpired && (
                            <>
                              <button
                                onClick={() => handleRenewLease(lease.id)}
                                disabled={actionLoading}
                                className="px-2.5 py-1 rounded-lg bg-sky-950/40 hover:bg-sky-900/60 border border-sky-500/30 text-sky-300 text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
                                title="Renew Lease (+1h)"
                              >
                                <RefreshCw className="w-3 h-3" />
                                <span>Renew</span>
                              </button>
                              <button
                                onClick={() => handleRevokeLease(lease.id)}
                                disabled={actionLoading}
                                className="p-1.5 rounded-lg bg-red-950/40 hover:bg-red-900/60 text-red-400 transition-all cursor-pointer"
                                title="Revoke Immediately"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

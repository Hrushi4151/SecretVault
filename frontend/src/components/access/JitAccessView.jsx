import React, { useState, useEffect } from 'react';
import { jitApi } from '../../api/jit';
import { useAuth } from '../../context/AuthContext';
import { RequestJitModal } from './RequestJitModal';
import { JitApprovalModal } from './JitApprovalModal';
import {
  Clock,
  Plus,
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  RotateCcw,
  User,
  Key,
  Server,
  Loader2,
  Calendar,
  Lock,
  Timer
} from 'lucide-react';

export const JitAccessView = () => {
  const { activeWorkspace, user } = useAuth();
  const [requests, setRequests] = useState([]);
  const [activeTab, setActiveTab] = useState('ALL'); // 'ALL' | 'ACTIVE' | 'PENDING' | 'HISTORY'
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);

  const [isRequestModalOpen, setIsRequestModalOpen] = useState(false);
  const [selectedRequestForApproval, setSelectedRequestForApproval] = useState(null);
  const [currentTime, setCurrentTime] = useState(Date.now());

  useEffect(() => {
    if (activeWorkspace?.id) {
      loadRequests();
    }
  }, [activeWorkspace?.id, activeTab]);

  // Live timer tick for active countdowns
  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentTime(Date.now());
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  const loadRequests = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const statusParam = activeTab === 'PENDING' ? 'PENDING' : activeTab === 'ACTIVE' ? 'APPROVED' : null;
      const res = await jitApi.listRequests(activeWorkspace.id, statusParam);
      const list = res.data?.data || res.data || [];
      setRequests(list);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to load JIT requests');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRevoke = async (requestId) => {
    if (!window.confirm('Are you sure you want to revoke this active JIT elevation immediately?')) {
      return;
    }
    try {
      await jitApi.revokeGrant(activeWorkspace.id, requestId);
      loadRequests();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to revoke JIT grant');
    }
  };

  const handleCancel = async (requestId) => {
    try {
      await jitApi.cancelRequest(activeWorkspace.id, requestId);
      loadRequests();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to cancel JIT request');
    }
  };

  const formatRemainingTime = (expiresAt) => {
    if (!expiresAt) return '00:00';
    const diff = new Date(expiresAt).getTime() - currentTime;
    if (diff <= 0) return 'Expired';
    const totalSecs = Math.floor(diff / 1000);
    const mins = Math.floor(totalSecs / 60);
    const secs = totalSecs % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const filteredRequests = requests.filter((r) => {
    if (activeTab === 'ACTIVE') {
      return r.isActive && new Date(r.expiresAt).getTime() > currentTime;
    }
    if (activeTab === 'PENDING') {
      return r.status === 'PENDING';
    }
    if (activeTab === 'HISTORY') {
      return r.status !== 'PENDING' && (!r.isActive || new Date(r.expiresAt).getTime() <= currentTime);
    }
    return true;
  });

  const activeCount = requests.filter(r => r.isActive && new Date(r.expiresAt).getTime() > currentTime).length;
  const pendingCount = requests.filter(r => r.status === 'PENDING').length;

  return (
    <div className="space-y-6 font-body">
      {/* Top Header & Action */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-6 rounded-2xl bg-gradient-to-r from-[#2C0012] via-[#1E000A] to-[#1C000A] border border-[#FFB4C8]/15 shadow-xl">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h2 className="font-headline font-bold text-xl text-white">Just-In-Time (JIT) Temporary Access</h2>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-mono font-medium bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30">
              Zero Standing Privilege
            </span>
          </div>
          <p className="text-xs text-[#F4B5C8]/70">
            Request and approve ephemeral privilege elevations with automated TTL expiry and dual-custody governance.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => loadRequests()}
            className="p-2.5 rounded-xl bg-[#26000F] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white hover:border-[#FFB4C8]/30 transition-all"
            title="Refresh requests"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
          <button
            onClick={() => setIsRequestModalOpen(true)}
            className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#FF4D82] text-white text-xs font-semibold shadow-lg shadow-[#FF2D6D]/20 hover:opacity-95 flex items-center gap-2 transition-all"
          >
            <Plus className="w-4 h-4" />
            <span>Request JIT Elevation</span>
          </button>
        </div>
      </div>

      {/* Metric Counters */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FF2D6D]/30 flex items-center justify-between">
          <div>
            <span className="text-[11px] font-medium text-[#F4B5C8]/70 uppercase tracking-wider">Active Elevations</span>
            <div className="text-2xl font-bold font-headline text-white mt-1">{activeCount}</div>
          </div>
          <div className="w-10 h-10 rounded-xl bg-[#3E0018] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
            <Timer className="w-5 h-5 animate-pulse" />
          </div>
        </div>

        <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 flex items-center justify-between">
          <div>
            <span className="text-[11px] font-medium text-[#F4B5C8]/70 uppercase tracking-wider">Pending Approvals</span>
            <div className="text-2xl font-bold font-headline text-[#FF9900] mt-1">{pendingCount}</div>
          </div>
          <div className="w-10 h-10 rounded-xl bg-[#360014] border border-[#FF9900]/30 flex items-center justify-center text-[#FF9900]">
            <ShieldAlert className="w-5 h-5" />
          </div>
        </div>

        <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 flex items-center justify-between">
          <div>
            <span className="text-[11px] font-medium text-[#F4B5C8]/70 uppercase tracking-wider">Total Requests</span>
            <div className="text-2xl font-bold font-headline text-white mt-1">{requests.length}</div>
          </div>
          <div className="w-10 h-10 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 flex items-center justify-center text-[#F4B5C8]">
            <Clock className="w-5 h-5" />
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-[#FFB4C8]/15 pb-2">
        {[
          { id: 'ALL', label: 'All Requests' },
          { id: 'ACTIVE', label: `Active Elevations (${activeCount})` },
          { id: 'PENDING', label: `Pending Approval (${pendingCount})` },
          { id: 'HISTORY', label: 'History & Audits' },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
              activeTab === tab.id
                ? 'bg-[#3E0018] text-white border border-[#FF2D6D]/40 font-semibold shadow-inner'
                : 'text-[#F4B5C8]/60 hover:text-white hover:bg-[#2C0012]'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Request Table / List */}
      {isLoading ? (
        <div className="p-12 flex flex-col items-center justify-center gap-3 text-[#F4B5C8]/60">
          <Loader2 className="w-6 h-6 animate-spin text-[#FF2D6D]" />
          <span className="text-xs font-mono">Loading JIT temporary access ledger...</span>
        </div>
      ) : filteredRequests.length === 0 ? (
        <div className="p-12 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center space-y-3">
          <div className="w-12 h-12 rounded-2xl bg-[#2C0012] border border-[#FFB4C8]/15 mx-auto flex items-center justify-center text-[#F4B5C8]/40">
            <Clock className="w-6 h-6" />
          </div>
          <p className="text-xs text-[#F4B5C8]/70">No JIT requests match this filter.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredRequests.map((req) => {
            const isCurrentlyActive = req.isActive && new Date(req.expiresAt).getTime() > currentTime;
            const remainingFormatted = isCurrentlyActive ? formatRemainingTime(req.expiresAt) : null;
            const isSelf = user?.id === req.userId;

            return (
              <div
                key={req.id}
                className={`p-4 rounded-xl border transition-all ${
                  isCurrentlyActive
                    ? 'bg-gradient-to-r from-[#2C0012] to-[#1C000A] border-[#00C853]/40 shadow-lg shadow-[#00C853]/5'
                    : req.status === 'PENDING'
                    ? 'bg-[#1C000A] border-[#FF9900]/40'
                    : 'bg-[#180008] border-[#FFB4C8]/10 opacity-80'
                }`}
              >
                <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4">
                  {/* Left Metadata */}
                  <div className="space-y-2">
                    <div className="flex items-center gap-2.5 flex-wrap">
                      <span className="font-semibold text-sm text-white flex items-center gap-1.5">
                        <User className="w-3.5 h-3.5 text-[#FF2D6D]" />
                        {req.userFullName}
                      </span>
                      <span className="text-[11px] text-[#F4B5C8]/50 font-mono">({req.userEmail})</span>

                      {/* Status Badge */}
                      {isCurrentlyActive ? (
                        <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#00C853]/20 text-[#00E676] border border-[#00C853]/40 flex items-center gap-1">
                          <span className="w-1.5 h-1.5 rounded-full bg-[#00E676] animate-pulse" />
                          ACTIVE ({remainingFormatted})
                        </span>
                      ) : (
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-medium border ${
                            req.status === 'PENDING'
                              ? 'bg-[#FF9900]/15 text-[#FFB84D] border-[#FF9900]/30'
                              : req.status === 'REJECTED'
                              ? 'bg-[#FF2D6D]/15 text-[#FF85A2] border-[#FF2D6D]/30'
                              : req.status === 'REVOKED'
                              ? 'bg-gray-500/20 text-gray-300 border-gray-500/30'
                              : 'bg-white/5 text-[#F4B5C8]/60 border-white/10'
                          }`}
                        >
                          {req.status}
                        </span>
                      )}

                      {req.isProtectedEnvironment && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-[#FF9900]/10 text-[#FFB84D] border border-[#FF9900]/20">
                          PROD ENCLAVE
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-4 text-xs text-[#F4B5C8]/70 flex-wrap">
                      <span className="flex items-center gap-1">
                        <Server className="w-3 h-3 text-[#FF2D6D]" />
                        {req.projectName ? `${req.projectName} / ` : ''}
                        <strong className="text-white">{req.environmentName}</strong>
                      </span>

                      <span className="flex items-center gap-1 font-mono">
                        <Key className="w-3 h-3 text-[#FF2D6D]" />
                        Permission: <strong className="text-[#FF85A2]">{req.permissionCode}</strong>
                      </span>

                      <span className="flex items-center gap-1 font-mono">
                        <Clock className="w-3 h-3 text-[#FF2D6D]" />
                        TTL: {req.durationMinutes}m
                      </span>

                      {req.approverEmail && (
                        <span className="text-[11px] text-[#F4B5C8]/50">
                          Approved by: {req.approverEmail}
                        </span>
                      )}
                    </div>

                    {/* Operational Reason */}
                    <p className="text-xs text-[#F4B5C8]/80 font-mono bg-[#120006] p-2 rounded-lg border border-[#FFB4C8]/10">
                      "{req.reason}"
                      {req.reviewerNotes && (
                        <span className="block text-[11px] text-[#F4B5C8]/50 mt-1 italic">
                          Reviewer note: "{req.reviewerNotes}"
                        </span>
                      )}
                    </p>
                  </div>

                  {/* Action Triggers */}
                  <div className="flex items-center gap-2 shrink-0 self-end lg:self-center">
                    {req.status === 'PENDING' && (
                      <>
                        {isSelf ? (
                          <button
                            onClick={() => handleCancel(req.id)}
                            className="px-3 py-1.5 rounded-lg bg-[#2C0012] hover:bg-[#3E0018] text-xs text-[#F4B5C8] border border-[#FFB4C8]/20 transition-all"
                          >
                            Cancel Request
                          </button>
                        ) : (
                          <button
                            onClick={() => setSelectedRequestForApproval(req)}
                            className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#FF4D82] text-white text-xs font-semibold shadow-md shadow-[#FF2D6D]/20 hover:opacity-95 flex items-center gap-1.5 transition-all"
                          >
                            <ShieldCheck className="w-3.5 h-3.5" />
                            <span>Review & Decide</span>
                          </button>
                        )}
                      </>
                    )}

                    {isCurrentlyActive && (
                      <button
                        onClick={() => handleRevoke(req.id)}
                        className="px-3 py-1.5 rounded-lg bg-[#360014] hover:bg-[#4A0018] text-[#FF4D82] border border-[#FF2D6D]/30 text-xs font-medium transition-all flex items-center gap-1.5"
                      >
                        <XCircle className="w-3.5 h-3.5" />
                        <span>Revoke Access</span>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modals */}
      <RequestJitModal
        workspaceId={activeWorkspace?.id}
        isOpen={isRequestModalOpen}
        onClose={() => setIsRequestModalOpen(false)}
        onSuccess={() => loadRequests()}
      />

      <JitApprovalModal
        workspaceId={activeWorkspace?.id}
        request={selectedRequestForApproval}
        isOpen={!!selectedRequestForApproval}
        onClose={() => setSelectedRequestForApproval(null)}
        onSuccess={() => loadRequests()}
      />
    </div>
  );
};

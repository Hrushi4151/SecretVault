import React, { useState, useEffect } from 'react';
import { privilegedAccessApi } from '../../api/privilegedAccess';
import { useAuth } from '../../context/AuthContext';
import {
  ShieldAlert,
  Flame,
  Users,
  Clock,
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  RotateCcw,
  Plus,
  Loader2,
  Lock,
  Timer,
  FileText,
  Sliders,
  Eye,
  Key,
  KeyRound,
  Trash2,
  ChevronRight,
  Sparkles
} from 'lucide-react';

export const PrivilegedAccessCenter = () => {
  const { activeWorkspace, user } = useAuth();
  const [activeTab, setActiveTab] = useState('REQUESTS'); // 'REQUESTS' | 'AWAITING_APPROVAL' | 'ELEVATIONS' | 'BREAK_GLASS' | 'POLICIES'
  const [statusFilter, setStatusFilter] = useState('ALL');

  const [requests, setRequests] = useState([]);
  const [elevations, setElevations] = useState([]);
  const [policies, setPolicies] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Modals state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [selectedRequestForApproval, setSelectedRequestForApproval] = useState(null);
  const [selectedPolicyForEdit, setSelectedPolicyForEdit] = useState(null);

  // Break-glass form state
  const [breakGlassForm, setBreakGlassForm] = useState({
    projectId: '',
    environmentId: '',
    secretId: '',
    action: 'BREAK_GLASS_REQUEST',
    requestedPermissions: 'secret.reveal',
    durationMinutes: 30,
    justification: '',
    stepUpProof: '',
  });
  const [isSubmittingBreakGlass, setIsSubmittingBreakGlass] = useState(false);

  // Current time ticker for live countdowns
  const [currentTime, setCurrentTime] = useState(Date.now());

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (activeWorkspace?.id) {
      loadData();
    }
  }, [activeWorkspace?.id, activeTab, statusFilter]);

  const loadData = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);

      if (activeTab === 'REQUESTS') {
        const statusParam = statusFilter !== 'ALL' ? statusFilter : null;
        const res = await privilegedAccessApi.listRequests(activeWorkspace.id, { status: statusParam });
        setRequests(res.data?.data || res.data || []);
      } else if (activeTab === 'AWAITING_APPROVAL') {
        const res = await privilegedAccessApi.listRequests(activeWorkspace.id, { awaitingMyApprovalOnly: true });
        setRequests(res.data?.data || res.data || []);
      } else if (activeTab === 'ELEVATIONS') {
        const res = await privilegedAccessApi.listElevations(activeWorkspace.id, { activeOnly: false });
        setElevations(res.data?.data || res.data || []);
      } else if (activeTab === 'POLICIES') {
        const res = await privilegedAccessApi.listPolicies(activeWorkspace.id);
        setPolicies(res.data?.data || res.data || []);
      }
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to load privileged access records');
    } finally {
      setIsLoading(false);
    }
  };

  const handleApprove = async (requestId, decision, notes, stepUpProof) => {
    try {
      await privilegedAccessApi.approveRequest(activeWorkspace.id, requestId, {
        decision,
        notes,
        stepUpProof,
      });
      setSelectedRequestForApproval(null);
      setSuccessMessage(`Request successfully ${decision === 'APPROVED' ? 'approved' : 'rejected'}`);
      loadData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to record approval decision');
    }
  };

  const handleRevokeElevation = async (elevationId) => {
    if (!window.confirm('Are you sure you want to revoke this privileged elevation immediately?')) {
      return;
    }
    try {
      await privilegedAccessApi.revokeElevation(activeWorkspace.id, elevationId, {
        reason: 'Revoked by administrator via Privileged Access Center',
      });
      setSuccessMessage('Privileged elevation grant revoked');
      loadData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to revoke elevation');
    }
  };

  const handleExecuteBreakGlass = async (e) => {
    e.preventDefault();
    if (breakGlassForm.justification.trim().length < 20) {
      alert('Break-glass emergency access requires detailed justification of at least 20 characters.');
      return;
    }
    if (!breakGlassForm.stepUpProof.trim()) {
      alert('Step-Up Authentication proof / Passkey token is required for Break-Glass emergency access.');
      return;
    }

    try {
      setIsSubmittingBreakGlass(true);
      await privilegedAccessApi.breakGlass(activeWorkspace.id, {
        projectId: breakGlassForm.projectId || null,
        environmentId: breakGlassForm.environmentId || null,
        secretId: breakGlassForm.secretId || null,
        action: breakGlassForm.action,
        requestedPermissions: breakGlassForm.requestedPermissions,
        durationMinutes: parseInt(breakGlassForm.durationMinutes, 10),
        justification: breakGlassForm.justification,
        stepUpProof: breakGlassForm.stepUpProof,
      });
      setSuccessMessage('EMERGENCY BREAK-GLASS ELEVATION ACTIVATED. All actions are heavily audited.');
      setBreakGlassForm({
        projectId: '',
        environmentId: '',
        secretId: '',
        action: 'BREAK_GLASS_REQUEST',
        requestedPermissions: 'secret.reveal',
        durationMinutes: 30,
        justification: '',
        stepUpProof: '',
      });
      setActiveTab('ELEVATIONS');
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to execute Break-Glass access');
    } finally {
      setIsSubmittingBreakGlass(false);
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

  return (
    <div className="p-6 max-w-7xl mx-auto space-y-6 font-body animate-fade-in">
      {/* Top Banner */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-[#2C0012] via-[#1E000A] to-[#120006] border border-[#FF2D6D]/20 shadow-2xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-[#FF2D6D]/20 text-[#FF2D6D]">
              <ShieldAlert className="w-6 h-6" />
            </div>
            <h1 className="font-headline font-bold text-2xl text-white">Privileged Access Security</h1>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-mono font-medium bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30">
              Four-Eyes & Break-Glass
            </span>
          </div>
          <p className="text-xs text-[#F4B5C8]/70">
            Enforces Dual Approval Quorum, real-time temporary elevation, anti-self-approval barriers, and strictly audited Emergency Break-Glass protocols.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="px-4 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF4D82] text-white text-xs font-semibold flex items-center gap-2 transition-all shadow-lg shadow-[#FF2D6D]/25"
          >
            <Plus className="w-4 h-4" />
            <span>Request Elevation</span>
          </button>
          <button
            onClick={() => setActiveTab('BREAK_GLASS')}
            className="px-4 py-2.5 rounded-xl bg-[#D50000] hover:bg-[#FF1744] text-white text-xs font-semibold flex items-center gap-2 transition-all shadow-lg shadow-[#D50000]/30 animate-pulse"
          >
            <Flame className="w-4 h-4" />
            <span>Break-Glass Emergency</span>
          </button>
          <button
            onClick={loadData}
            className="p-2.5 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white transition-all"
            title="Refresh"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Messages */}
      {successMessage && (
        <div className="p-4 rounded-xl bg-[#00C853]/15 border border-[#00C853]/30 text-[#00E676] text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{successMessage}</span>
          </div>
          <button onClick={() => setSuccessMessage(null)} className="text-[#00E676]/70 hover:text-[#00E676]">✕</button>
        </div>
      )}

      {errorMessage && (
        <div className="p-4 rounded-xl bg-[#FF1744]/15 border border-[#FF1744]/30 text-[#FF5252] text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
          <button onClick={() => setErrorMessage(null)} className="text-[#FF5252]/70 hover:text-[#FF5252]">✕</button>
        </div>
      )}

      {/* Subtabs Bar */}
      <div className="flex items-center gap-2 p-1.5 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 w-fit overflow-x-auto max-w-full">
        {[
          { id: 'REQUESTS', label: 'All Requests', icon: <FileText className="w-4 h-4" /> },
          { id: 'AWAITING_APPROVAL', label: 'Awaiting My Approval', icon: <Users className="w-4 h-4" /> },
          { id: 'ELEVATIONS', label: 'Active Elevations', icon: <Clock className="w-4 h-4" /> },
          { id: 'BREAK_GLASS', label: 'Break-Glass Console', icon: <Flame className="w-4 h-4" /> },
          { id: 'POLICIES', label: 'Governance Policies', icon: <Sliders className="w-4 h-4" /> },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 rounded-xl text-xs font-medium flex items-center gap-2 transition-all whitespace-nowrap ${
              activeTab === tab.id
                ? 'bg-gradient-to-r from-[#FF2D6D] to-[#FF4D82] text-white shadow-lg shadow-[#FF2D6D]/20 font-semibold'
                : 'text-[#F4B5C8]/70 hover:text-white hover:bg-[#2C0012]'
            }`}
          >
            {tab.icon}
            <span>{tab.label}</span>
          </button>
        ))}
      </div>

      {/* Content Area */}
      {isLoading ? (
        <div className="p-12 text-center text-[#F4B5C8]/60 flex flex-col items-center justify-center gap-3">
          <Loader2 className="w-8 h-8 animate-spin text-[#FF2D6D]" />
          <span className="text-xs">Loading privileged access control plane...</span>
        </div>
      ) : (
        <>
          {/* TAB 1: ALL REQUESTS */}
          {activeTab === 'REQUESTS' && (
            <div className="space-y-4">
              {/* Filter pills */}
              <div className="flex items-center gap-2">
                {['ALL', 'PENDING', 'APPROVED', 'EXECUTED', 'REJECTED', 'EXPIRED', 'REVOKED'].map((st) => (
                  <button
                    key={st}
                    onClick={() => setStatusFilter(st)}
                    className={`px-3 py-1 rounded-lg text-[11px] font-mono transition-all ${
                      statusFilter === st
                        ? 'bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/40 font-semibold'
                        : 'text-[#F4B5C8]/50 hover:text-white bg-[#1C000A]'
                    }`}
                  >
                    {st}
                  </button>
                ))}
              </div>

              {requests.length === 0 ? (
                <div className="p-12 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center text-[#F4B5C8]/50 text-xs">
                  No privileged access requests found for the selected filter.
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-3">
                  {requests.map((req) => (
                    <div
                      key={req.id}
                      className="p-5 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 hover:border-[#FF2D6D]/30 transition-all space-y-3"
                    >
                      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold ${
                            req.status === 'APPROVED' ? 'bg-[#00C853]/20 text-[#00E676] border border-[#00C853]/30' :
                            req.status === 'PENDING' ? 'bg-[#FFD600]/20 text-[#FFD600] border border-[#FFD600]/30' :
                            req.status === 'EXECUTED' ? 'bg-[#2979FF]/20 text-[#2979FF] border border-[#2979FF]/30' :
                            req.status === 'REJECTED' ? 'bg-[#FF1744]/20 text-[#FF5252] border border-[#FF1744]/30' :
                            'bg-[#757575]/20 text-[#BDBDBD] border border-[#757575]/30'
                          }`}>
                            {req.status}
                          </span>

                          {req.isBreakGlass && (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#D50000]/30 text-[#FF5252] border border-[#D50000]/50 flex items-center gap-1">
                              <Flame className="w-3 h-3" /> BREAK-GLASS
                            </span>
                          )}

                          <span className="text-xs font-mono font-semibold text-white">
                            {req.action}
                          </span>
                          <span className="text-[11px] text-[#F4B5C8]/50 font-mono">
                            ({req.scopeType})
                          </span>
                        </div>

                        <div className="text-[11px] font-mono text-[#F4B5C8]/60">
                          Quorum: {req.currentApprovalsCount}/{req.requiredQuorum} Approvals
                        </div>
                      </div>

                      <p className="text-xs text-[#F4B5C8]/80 bg-[#120006] p-3 rounded-xl border border-[#FFB4C8]/5">
                        {req.justification}
                      </p>

                      <div className="flex flex-wrap items-center justify-between text-[11px] text-[#F4B5C8]/60 gap-2 pt-2 border-t border-[#FFB4C8]/10">
                        <div className="flex items-center gap-4">
                          <span>Requester: <strong className="text-white">{req.requesterEmail || req.requesterName}</strong></span>
                          <span>Target: <strong className="text-white">{req.targetUserEmail || req.targetUserName}</strong></span>
                          <span>Duration: <strong className="text-white">{req.durationMinutes}m</strong></span>
                        </div>

                        <div className="flex items-center gap-2">
                          {req.canApprove && (
                            <button
                              onClick={() => setSelectedRequestForApproval(req)}
                              className="px-3 py-1.5 rounded-lg bg-[#00C853] hover:bg-[#00E676] text-black font-semibold text-[11px] flex items-center gap-1 shadow"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Review & Decide</span>
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 2: AWAITING MY APPROVAL */}
          {activeTab === 'AWAITING_APPROVAL' && (
            <div className="space-y-4">
              <div className="p-4 rounded-xl bg-[#FFD600]/10 border border-[#FFD600]/25 text-[#FFD600] text-xs flex items-center gap-2">
                <Users className="w-4 h-4 shrink-0" />
                <span>Four-Eyes Governance: You are an eligible approver. Anti-self-approval strictly prevents requesters from approving their own actions.</span>
              </div>

              {requests.length === 0 ? (
                <div className="p-12 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center text-[#F4B5C8]/50 text-xs">
                  You have no pending privileged requests awaiting your approval decision.
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-3">
                  {requests.map((req) => (
                    <div
                      key={req.id}
                      className="p-5 rounded-2xl bg-[#1C000A] border border-[#FFD600]/30 shadow-lg space-y-3"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#FFD600]/20 text-[#FFD600] border border-[#FFD600]/40">
                            QUORUM NEEDED: {req.currentApprovalsCount}/{req.requiredQuorum}
                          </span>
                          <span className="text-xs font-mono font-bold text-white">{req.action}</span>
                        </div>
                        <span className="text-[11px] font-mono text-[#F4B5C8]/60">Expires in: {formatRemainingTime(req.expiresAt)}</span>
                      </div>

                      <p className="text-xs text-[#F4B5C8]/90 bg-[#120006] p-3 rounded-xl border border-[#FFB4C8]/10">
                        {req.justification}
                      </p>

                      <div className="flex items-center justify-between pt-2 border-t border-[#FFB4C8]/10">
                        <div className="text-[11px] text-[#F4B5C8]/60">
                          Submitted by <strong className="text-white">{req.requesterEmail}</strong> for target <strong className="text-white">{req.targetUserEmail}</strong>
                        </div>
                        <button
                          onClick={() => setSelectedRequestForApproval(req)}
                          className="px-4 py-1.5 rounded-xl bg-[#00C853] hover:bg-[#00E676] text-black text-xs font-bold flex items-center gap-1.5 shadow-lg shadow-[#00C853]/20"
                        >
                          <CheckCircle2 className="w-4 h-4" />
                          <span>Approve / Reject</span>
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: ACTIVE ELEVATIONS */}
          {activeTab === 'ELEVATIONS' && (
            <div className="space-y-4">
              {elevations.length === 0 ? (
                <div className="p-12 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center text-[#F4B5C8]/50 text-xs">
                  No active or historical privileged elevations recorded in this workspace.
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {elevations.map((elev) => {
                    const isStillActive = elev.active && new Date(elev.expiresAt).getTime() > currentTime;
                    return (
                      <div
                        key={elev.id}
                        className={`p-5 rounded-2xl bg-[#1C000A] border transition-all space-y-3 ${
                          isStillActive
                            ? elev.isBreakGlass
                              ? 'border-[#FF1744]/50 shadow-xl shadow-[#FF1744]/10'
                              : 'border-[#00E676]/40 shadow-xl shadow-[#00E676]/10'
                            : 'border-[#FFB4C8]/10 opacity-70'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            {elev.isBreakGlass ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#D50000]/30 text-[#FF5252] border border-[#D50000]/50 flex items-center gap-1">
                                <Flame className="w-3 h-3" /> BREAK-GLASS
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#00C853]/20 text-[#00E676] border border-[#00C853]/30 flex items-center gap-1">
                                <ShieldCheck className="w-3 h-3" /> ELEVATION
                              </span>
                            )}
                            <span className="text-xs font-mono font-bold text-white">{elev.action}</span>
                          </div>

                          <span className={`px-2 py-0.5 rounded-md text-[10px] font-mono font-bold ${
                            isStillActive ? 'bg-[#00E676]/20 text-[#00E676]' : 'bg-[#757575]/20 text-[#BDBDBD]'
                          }`}>
                            {isStillActive ? 'ACTIVE' : elev.status}
                          </span>
                        </div>

                        <div className="p-3 rounded-xl bg-[#120006] border border-[#FFB4C8]/5 space-y-1 text-xs">
                          <div className="flex justify-between">
                            <span className="text-[#F4B5C8]/60">Permission Granted:</span>
                            <span className="font-mono text-white font-semibold">{elev.grantedPermission}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-[#F4B5C8]/60">Scope:</span>
                            <span className="font-mono text-white">{elev.scopeType}</span>
                          </div>
                          <div className="flex justify-between items-center pt-1 border-t border-[#FFB4C8]/10">
                            <span className="text-[#F4B5C8]/60 flex items-center gap-1">
                              <Timer className="w-3.5 h-3.5 text-[#FFD600]" /> Time Remaining:
                            </span>
                            <span className="font-mono text-sm font-bold text-[#FFD600]">
                              {isStillActive ? formatRemainingTime(elev.expiresAt) : 'Expired'}
                            </span>
                          </div>
                        </div>

                        {isStillActive && (
                          <button
                            onClick={() => handleRevokeElevation(elev.id)}
                            className="w-full py-2 rounded-xl bg-[#FF1744]/20 hover:bg-[#FF1744]/30 text-[#FF5252] border border-[#FF1744]/40 text-xs font-semibold flex items-center justify-center gap-1.5 transition-all"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                            <span>Revoke Elevation Immediately</span>
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* TAB 4: BREAK-GLASS CONSOLE */}
          {activeTab === 'BREAK_GLASS' && (
            <div className="max-w-3xl mx-auto space-y-6">
              <div className="p-6 rounded-2xl bg-gradient-to-r from-[#3D000A] to-[#1F0005] border-2 border-[#FF1744] shadow-2xl space-y-4">
                <div className="flex items-center gap-3">
                  <div className="p-3 rounded-xl bg-[#FF1744]/20 text-[#FF1744] animate-pulse">
                    <Flame className="w-8 h-8" />
                  </div>
                  <div>
                    <h2 className="font-headline font-bold text-xl text-white">Emergency Break-Glass Access Console</h2>
                    <p className="text-xs text-[#FF8A80]">
                      Emergency override for catastrophic outage remediation. Not an unrestricted owner role. Explicitly scoped and heavily audited.
                    </p>
                  </div>
                </div>

                <form onSubmit={handleExecuteBreakGlass} className="space-y-4 pt-2">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-mono text-[#F4B5C8]/70 mb-1">Target Project ID (Optional UUID)</label>
                      <input
                        type="text"
                        placeholder="Leave empty for workspace wide"
                        value={breakGlassForm.projectId}
                        onChange={(e) => setBreakGlassForm({ ...breakGlassForm, projectId: e.target.value })}
                        className="w-full px-3.5 py-2.5 rounded-xl bg-[#120006] border border-[#FFB4C8]/15 text-white text-xs font-mono focus:border-[#FF1744] focus:outline-none"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-mono text-[#F4B5C8]/70 mb-1">Target Environment ID (Optional UUID)</label>
                      <input
                        type="text"
                        placeholder="Leave empty for project wide"
                        value={breakGlassForm.environmentId}
                        onChange={(e) => setBreakGlassForm({ ...breakGlassForm, environmentId: e.target.value })}
                        className="w-full px-3.5 py-2.5 rounded-xl bg-[#120006] border border-[#FFB4C8]/15 text-white text-xs font-mono focus:border-[#FF1744] focus:outline-none"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-mono text-[#F4B5C8]/70 mb-1">Emergency Duration (Minutes)</label>
                      <select
                        value={breakGlassForm.durationMinutes}
                        onChange={(e) => setBreakGlassForm({ ...breakGlassForm, durationMinutes: e.target.value })}
                        className="w-full px-3.5 py-2.5 rounded-xl bg-[#120006] border border-[#FFB4C8]/15 text-white text-xs font-mono focus:border-[#FF1744] focus:outline-none"
                      >
                        <option value={15}>15 Minutes</option>
                        <option value={30}>30 Minutes (Recommended)</option>
                        <option value={60}>60 Minutes (Max standard)</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-mono text-[#F4B5C8]/70 mb-1">Emergency Action</label>
                      <input
                        type="text"
                        disabled
                        value="BREAK_GLASS_REQUEST (secret.reveal)"
                        className="w-full px-3.5 py-2.5 rounded-xl bg-[#120006]/50 border border-[#FFB4C8]/10 text-[#F4B5C8]/50 text-xs font-mono"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-mono text-[#F4B5C8]/70 mb-1">
                      Detailed Incident Justification (Min 20 characters) *
                    </label>
                    <textarea
                      required
                      rows={3}
                      placeholder="Comprehensive description of the active incident, impact, and remediation plan..."
                      value={breakGlassForm.justification}
                      onChange={(e) => setBreakGlassForm({ ...breakGlassForm, justification: e.target.value })}
                      className="w-full px-3.5 py-2.5 rounded-xl bg-[#120006] border border-[#FFB4C8]/15 text-white text-xs focus:border-[#FF1744] focus:outline-none"
                    />
                    <span className="text-[10px] text-[#F4B5C8]/50 font-mono">
                      {breakGlassForm.justification.length} / 20 characters minimum
                    </span>
                  </div>

                  <div>
                    <label className="block text-xs font-mono text-[#F4B5C8]/70 mb-1">
                      Step-Up Authentication Proof / Passkey Token *
                    </label>
                    <input
                      required
                      type="password"
                      placeholder="Enter verified Step-Up Proof token (WebAuthn/Passkey/MFA)"
                      value={breakGlassForm.stepUpProof}
                      onChange={(e) => setBreakGlassForm({ ...breakGlassForm, stepUpProof: e.target.value })}
                      className="w-full px-3.5 py-2.5 rounded-xl bg-[#120006] border border-[#FFB4C8]/15 text-white text-xs font-mono focus:border-[#FF1744] focus:outline-none"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={isSubmittingBreakGlass}
                    className="w-full py-3.5 rounded-xl bg-gradient-to-r from-[#D50000] to-[#FF1744] hover:from-[#FF1744] hover:to-[#FF5252] text-white font-bold text-sm shadow-xl shadow-[#D50000]/40 flex items-center justify-center gap-2 transition-all"
                  >
                    {isSubmittingBreakGlass ? (
                      <Loader2 className="w-5 h-5 animate-spin" />
                    ) : (
                      <>
                        <Flame className="w-5 h-5" />
                        <span>ACTIVATE EMERGENCY BREAK-GLASS ACCESS</span>
                      </>
                    )}
                  </button>
                </form>
              </div>
            </div>
          )}

          {/* TAB 5: GOVERNANCE POLICIES */}
          {activeTab === 'POLICIES' && (
            <div className="space-y-4">
              <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 text-xs text-[#F4B5C8]/70 flex items-center justify-between">
                <span>Configure dual-approval quorum requirements, mandatory step-up authentication, and emergency duration caps.</span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {policies.map((pol) => (
                  <div
                    key={pol.id}
                    className="p-5 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 space-y-3"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-mono text-xs font-bold text-white">{pol.action || 'GLOBAL'}</span>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono ${
                        pol.enabled ? 'bg-[#00C853]/20 text-[#00E676]' : 'bg-[#757575]/20 text-[#BDBDBD]'
                      }`}>
                        {pol.enabled ? 'ENABLED' : 'DISABLED'}
                      </span>
                    </div>

                    <div className="p-3 rounded-xl bg-[#120006] border border-[#FFB4C8]/5 space-y-1.5 text-xs font-mono">
                      <div className="flex justify-between">
                        <span className="text-[#F4B5C8]/60">Scope:</span>
                        <span className="text-white">{pol.scopeType}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-[#F4B5C8]/60">Quorum:</span>
                        <span className="text-white font-bold">{pol.approvalQuorum} Approvers</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-[#F4B5C8]/60">Step-Up Required:</span>
                        <span className={pol.requireStepUp ? 'text-[#00E676]' : 'text-[#F4B5C8]/50'}>
                          {pol.requireStepUp ? 'YES' : 'NO'}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-[#F4B5C8]/60">Anti-Self-Approval:</span>
                        <span className={pol.preventSelfApproval ? 'text-[#00E676]' : 'text-[#F4B5C8]/50'}>
                          {pol.preventSelfApproval ? 'ENFORCED' : 'DISABLED'}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-[#F4B5C8]/60">Break-Glass Allowed:</span>
                        <span className={pol.breakGlassAllowed ? 'text-[#FF5252]' : 'text-[#F4B5C8]/50'}>
                          {pol.breakGlassAllowed ? 'YES' : 'NO'}
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}

      {/* MODAL: REVIEW & DECIDE (APPROVAL / REJECTION) */}
      {selectedRequestForApproval && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-lg p-6 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/20 shadow-2xl space-y-4 animate-scale-in">
            <div className="flex items-center justify-between pb-3 border-b border-[#FFB4C8]/10">
              <h3 className="font-headline font-bold text-lg text-white flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-[#00E676]" />
                Privileged Access Decision
              </h3>
              <button
                onClick={() => setSelectedRequestForApproval(null)}
                className="text-[#F4B5C8]/60 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div className="p-3 rounded-xl bg-[#120006] border border-[#FFB4C8]/5 space-y-1">
                <div><strong className="text-[#F4B5C8]/70">Action:</strong> <span className="font-mono text-white">{selectedRequestForApproval.action}</span></div>
                <div><strong className="text-[#F4B5C8]/70">Requester:</strong> <span className="text-white">{selectedRequestForApproval.requesterEmail}</span></div>
                <div><strong className="text-[#F4B5C8]/70">Duration:</strong> <span className="text-white">{selectedRequestForApproval.durationMinutes} minutes</span></div>
                <div><strong className="text-[#F4B5C8]/70">Justification:</strong> <span className="text-white">{selectedRequestForApproval.justification}</span></div>
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  onClick={() => handleApprove(selectedRequestForApproval.id, 'APPROVED', 'Approved by authorized reviewer', null)}
                  className="flex-1 py-3 rounded-xl bg-[#00C853] hover:bg-[#00E676] text-black font-bold text-xs shadow-lg transition-all"
                >
                  Approve Request
                </button>
                <button
                  onClick={() => handleApprove(selectedRequestForApproval.id, 'REJECTED', 'Rejected by reviewer', null)}
                  className="flex-1 py-3 rounded-xl bg-[#FF1744]/20 hover:bg-[#FF1744]/30 text-[#FF5252] border border-[#FF1744]/40 font-bold text-xs transition-all"
                >
                  Reject Request
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

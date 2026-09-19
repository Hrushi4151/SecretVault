import React, { useState } from 'react';
import { jitApi } from '../../api/jit';
import { useAuth } from '../../context/AuthContext';
import {
  X,
  ShieldCheck,
  ShieldAlert,
  Clock,
  User,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Loader2,
  FileText,
  Lock
} from 'lucide-react';

export const JitApprovalModal = ({ workspaceId, request, isOpen, onClose, onSuccess }) => {
  const { user } = useAuth();
  const [reviewerNotes, setReviewerNotes] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  if (!isOpen || !request) return null;

  const isSelf = user?.id === request.userId;

  const handleApprove = async () => {
    if (isSelf) {
      setErrorMessage('Anti-Self-Approval Violation: You cannot approve your own JIT access request.');
      return;
    }

    try {
      setIsProcessing(true);
      setErrorMessage(null);
      await jitApi.approveRequest(workspaceId, request.id, {
        reviewerNotes: reviewerNotes.trim() || undefined
      });
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to approve JIT request');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleReject = async () => {
    if (!reviewerNotes || reviewerNotes.trim().length < 5) {
      setErrorMessage('Please provide a brief reason for rejection (at least 5 characters)');
      return;
    }

    try {
      setIsProcessing(true);
      setErrorMessage(null);
      await jitApi.rejectRequest(workspaceId, request.id, {
        reviewerNotes: reviewerNotes.trim()
      });
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to reject JIT request');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in font-body">
      <div className="relative w-full max-w-lg rounded-2xl bg-[#1C000A] border border-[#FF2D6D]/30 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#FFB4C8]/15 bg-gradient-to-r from-[#2C0012] to-[#1C000A]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#3E0018] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-headline font-bold text-base text-white">
                Dual-Custody JIT Review
              </h3>
              <p className="text-xs text-[#F4B5C8]/70 font-mono">
                Request ID: {request.id.slice(0, 8)}...
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-[#F4B5C8]/60 hover:text-white hover:bg-[#3E0018]/50 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-4">
          {errorMessage && (
            <div className="p-3.5 rounded-xl bg-[#4A0018]/80 border border-[#FF2D6D]/50 text-xs text-[#FFCCD6] flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-[#FF2D6D] shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          {isSelf && (
            <div className="p-3.5 rounded-xl bg-[#360014]/60 border border-[#FF9900]/50 text-xs text-[#FFE8C2] flex items-start gap-2.5">
              <Lock className="w-4 h-4 text-[#FF9900] shrink-0 mt-0.5" />
              <div>
                <span className="font-semibold text-white">Anti-Self-Approval Restriction:</span>
                <p className="text-[11px] text-[#FFD699] mt-0.5">
                  You submitted this request. To maintain zero-trust dual custody, another administrator or project maintainer must approve it.
                </p>
              </div>
            </div>
          )}

          {/* Request Metadata Card */}
          <div className="p-4 rounded-xl bg-[#26000F] border border-[#FFB4C8]/15 space-y-3 text-xs">
            <div className="flex items-center justify-between pb-2.5 border-b border-[#FFB4C8]/10">
              <span className="text-[#F4B5C8]/70 flex items-center gap-1.5">
                <User className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Requester
              </span>
              <div className="text-right">
                <span className="font-semibold text-white">{request.userFullName}</span>
                <span className="block text-[10px] text-[#F4B5C8]/50 font-mono">{request.userEmail}</span>
              </div>
            </div>

            <div className="flex items-center justify-between pb-2.5 border-b border-[#FFB4C8]/10">
              <span className="text-[#F4B5C8]/70">Target Enclave</span>
              <span className="font-mono text-white">
                {request.environmentName} {request.projectName ? `(${request.projectName})` : ''}
              </span>
            </div>

            <div className="flex items-center justify-between pb-2.5 border-b border-[#FFB4C8]/10">
              <span className="text-[#F4B5C8]/70">Permission</span>
              <span className="px-2 py-0.5 rounded font-mono font-medium bg-[#FF2D6D]/15 text-[#FF85A2] border border-[#FF2D6D]/30">
                {request.permissionCode}
              </span>
            </div>

            <div className="flex items-center justify-between pb-2.5 border-b border-[#FFB4C8]/10">
              <span className="text-[#F4B5C8]/70 flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Requested Duration
              </span>
              <span className="font-mono font-bold text-white">
                {request.durationMinutes} minutes
              </span>
            </div>

            <div>
              <span className="text-[#F4B5C8]/70 flex items-center gap-1.5 mb-1">
                <FileText className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Operational Justification
              </span>
              <div className="p-2.5 rounded-lg bg-[#1C000A] border border-[#FFB4C8]/10 text-xs text-[#F4B5C8]/90 font-mono">
                "{request.reason}"
              </div>
            </div>
          </div>

          {/* Reviewer Notes Input */}
          <div>
            <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5">
              Reviewer Notes / Decision Audit Trail
            </label>
            <textarea
              value={reviewerNotes}
              onChange={(e) => setReviewerNotes(e.target.value)}
              placeholder="e.g. 'Approved in accordance with Emergency Patch Ticket SEC-2910.'"
              rows={2}
              className="w-full p-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white placeholder-[#F4B5C8]/30 focus:outline-none focus:border-[#FF2D6D] transition-colors resize-none"
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
            <button
              type="button"
              onClick={handleReject}
              disabled={isProcessing}
              className="px-4 py-2.5 rounded-xl bg-[#360014] text-[#FF4D82] border border-[#FF2D6D]/30 text-xs font-medium hover:bg-[#4A0018] flex items-center gap-1.5 transition-colors disabled:opacity-50"
            >
              <XCircle className="w-4 h-4" />
              <span>Reject Request</span>
            </button>
            <button
              type="button"
              onClick={handleApprove}
              disabled={isProcessing || isSelf}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-[#00A86B] to-[#00C853] text-white text-xs font-medium shadow-lg shadow-[#00C853]/20 hover:opacity-95 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5 transition-all"
            >
              {isProcessing ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Processing...</span>
                </>
              ) : (
                <>
                  <CheckCircle className="w-4 h-4" />
                  <span>Approve & Grant Elevation</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

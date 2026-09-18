import React, { useState } from 'react';
import { versionsApi } from '../../api/versions';
import {
  X,
  RotateCcw,
  ShieldCheck,
  AlertCircle,
  Loader2,
  Lock,
  Layers,
  Sparkles
} from 'lucide-react';

export default function SecretRollbackModal({
  isOpen,
  onClose,
  secret,
  targetVersion,
  workspaceId,
  projectId,
  environmentId,
  onRollbackSuccess
}) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  if (!isOpen || !secret || !targetVersion) return null;

  const currentVersion = secret.currentVersionNumber || 1;
  const nextVersion = currentVersion + 1;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const res = await versionsApi.rollbackSecret(
        workspaceId,
        projectId,
        environmentId,
        secret.id,
        {
          targetVersion: targetVersion.versionNumber || targetVersion,
          expectedCurrentVersion: currentVersion,
          reason: reason.trim() || `Rollback to version ${targetVersion.versionNumber || targetVersion}`
        }
      );

      const data = res?.data !== undefined ? res.data : res;
      if (onRollbackSuccess) {
        onRollbackSuccess(data);
      }
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to rollback secret');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in font-body">
      <div className="relative w-full max-w-lg bg-[#1E000A] border border-[#FFB4C8]/25 rounded-3xl shadow-2xl p-6 md:p-8 flex flex-col gap-6 text-white max-h-[90vh] overflow-y-auto">
        
        {/* Header */}
        <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
              <RotateCcw className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg md:text-xl font-headline font-bold text-white tracking-tight">
                Rollback Secret
              </h2>
              <span className="text-xs font-mono text-[#A26377]">
                {secret.name}
              </span>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-xl text-[#A26377] hover:text-white hover:bg-[#30000F] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content & Form */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {error && (
            <div className="p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6] flex items-center gap-3 font-mono">
              <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
              <span>{error}</span>
            </div>
          )}

          {/* Security Invariant Notice */}
          <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-2">
            <div className="flex items-center gap-2 text-xs font-headline font-bold text-[#FF2D6D] uppercase tracking-wider">
              <ShieldCheck className="w-4 h-4" />
              <span>Zero-Downtime Rollover Invariant</span>
            </div>
            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              Rollback will create a brand new version (<strong>v{nextVersion}</strong>) containing the payload of target version (<strong>v{targetVersion.versionNumber || targetVersion}</strong>), encrypted with fresh 256-bit DEK & 96-bit IV. Previous versions (v1..v{currentVersion}) remain permanently immutable.
            </p>
          </div>

          {/* Version Transition Matrix */}
          <div className="grid grid-cols-2 gap-3 font-mono text-xs">
            <div className="p-3.5 rounded-2xl bg-[#140007] border border-[#FFB4C8]/15 flex flex-col gap-1">
              <span className="text-[#A26377] text-[10px] uppercase">Active Version</span>
              <span className="text-sm font-bold text-white">v{currentVersion}</span>
            </div>
            <div className="p-3.5 rounded-2xl bg-[#140007] border border-[#FF2D6D]/40 flex flex-col gap-1">
              <span className="text-[#FF2D6D] text-[10px] uppercase font-semibold">Target Rollback</span>
              <span className="text-sm font-bold text-[#FF2D6D]">v{targetVersion.versionNumber || targetVersion}</span>
            </div>
          </div>

          {/* Reason Input */}
          <div className="flex flex-col gap-1.5 font-mono text-xs">
            <label className="text-[11px] font-bold text-[#A26377] uppercase tracking-wider">
              Rollback Reason (Audited)
            </label>
            <textarea
              rows="3"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Rolled back after webhook latency regression"
              className="w-full p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] transition-colors text-xs font-mono resize-none"
            />
          </div>

          {/* Action Buttons */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-headline font-bold text-xs transition-all shadow-lg shadow-[#FF2D6D]/20 disabled:opacity-50 cursor-pointer active:scale-95"
            >
              {submitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Rolling back...</span>
                </>
              ) : (
                <>
                  <RotateCcw className="w-4 h-4" />
                  <span>Rollback to v{targetVersion.versionNumber || targetVersion}</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

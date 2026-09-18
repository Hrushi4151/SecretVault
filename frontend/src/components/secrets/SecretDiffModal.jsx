import React, { useState, useEffect } from 'react';
import { versionsApi } from '../../api/versions';
import {
  X,
  ArrowRightLeft,
  Lock,
  Unlock,
  Eye,
  EyeOff,
  AlertCircle,
  Loader2,
  Clock,
  RotateCcw,
  Sparkles,
  Layers,
  ShieldCheck,
  Check
} from 'lucide-react';

export default function SecretDiffModal({
  isOpen,
  onClose,
  secret,
  fromVersion,
  toVersion,
  workspaceId,
  projectId,
  environmentId,
  onTriggerRollback
}) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [diffData, setDiffData] = useState(null);
  const [valueDiffData, setValueDiffData] = useState(null);
  const [revealingValues, setRevealingValues] = useState(false);
  const [autoMaskSeconds, setAutoMaskSeconds] = useState(null);

  useEffect(() => {
    if (!isOpen || !secret || !fromVersion || !toVersion) return;

    let timer;
    if (autoMaskSeconds !== null && autoMaskSeconds > 0) {
      timer = setInterval(() => {
        setAutoMaskSeconds(s => (s > 1 ? s - 1 : null));
      }, 1000);
    } else if (autoMaskSeconds === 0) {
      setValueDiffData(null);
      setAutoMaskSeconds(null);
    }

    return () => {
      if (timer) clearInterval(timer);
    };
  }, [isOpen, autoMaskSeconds, secret, fromVersion, toVersion]);

  useEffect(() => {
    if (!isOpen || !secret || !fromVersion || !toVersion) return;

    const loadMetadataDiff = async () => {
      setLoading(true);
      setError(null);
      setValueDiffData(null);
      setAutoMaskSeconds(null);
      try {
        const res = await versionsApi.compareVersions(
          workspaceId,
          projectId,
          environmentId,
          secret.id,
          fromVersion,
          toVersion
        );
        const data = res?.data !== undefined ? res.data : res;
        setDiffData(data);
      } catch (err) {
        setError(err.response?.data?.message || err.message || 'Failed to compare secret versions');
      } finally {
        setLoading(false);
      }
    };

    loadMetadataDiff();
  }, [isOpen, secret, fromVersion, toVersion, workspaceId, projectId, environmentId]);

  const handleRevealValueDiff = async () => {
    if (valueDiffData) {
      setValueDiffData(null);
      setAutoMaskSeconds(null);
      return;
    }

    setRevealingValues(true);
    try {
      const res = await versionsApi.computeValueDiff(
        workspaceId,
        projectId,
        environmentId,
        secret.id,
        fromVersion,
        toVersion
      );
      const data = res?.data !== undefined ? res.data : res;
      setValueDiffData(data);
      setAutoMaskSeconds(30); // 30-second security auto-masking timer
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to reveal value-level diff');
    } finally {
      setRevealingValues(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in font-body">
      <div className="relative w-full max-w-4xl bg-[#1E000A] border border-[#FFB4C8]/25 rounded-3xl shadow-2xl p-6 md:p-8 flex flex-col gap-6 text-white max-h-[90vh] overflow-y-auto">
        
        {/* Header */}
        <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
              <ArrowRightLeft className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg md:text-xl font-headline font-bold text-white tracking-tight">
                  Comparing v{fromVersion} ⇄ v{toVersion}
                </h2>
                <span className="px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono text-[#FFB4C8] border border-[#FFB4C8]/20">
                  {secret.name}
                </span>
              </div>
              <p className="text-xs font-mono text-[#A26377]">
                Cryptographic divergence and Shannon entropy delta inspector
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-xl text-[#A26377] hover:text-white hover:bg-[#30000F] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Area */}
        <div className="flex flex-col gap-6">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-16 gap-3 text-[#A26377] font-mono text-xs">
              <Loader2 className="w-8 h-8 animate-spin text-[#FF2D6D]" />
              <span>Cryptographically inspecting version divergence...</span>
            </div>
          ) : error ? (
            <div className="p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6] flex items-center gap-3 font-mono">
              <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
              <span>{error}</span>
            </div>
          ) : (
            <>
              {/* Divergence Status Banner */}
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/20 flex flex-wrap items-center justify-between gap-4 font-mono text-xs">
                <div className="flex items-center gap-3">
                  <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider border ${
                    diffData?.isEqual
                      ? 'bg-[#4ADE80]/20 text-[#4ADE80] border-[#4ADE80]/30'
                      : 'bg-[#FBBF24]/20 text-[#FBBF24] border-[#FBBF24]/30'
                  }`}>
                    {diffData?.isEqual ? 'IDENTICAL PAYLOADS' : `MODIFIED (${diffData?.diffType || 'MODIFIED'})`}
                  </span>
                  <span className="text-[#A26377]">
                    {diffData?.message}
                  </span>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={handleRevealValueDiff}
                    disabled={revealingValues}
                    className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-[#1E000A] hover:bg-[#3F0016] text-[#FFB4C8] hover:text-white border border-[#FFB4C8]/20 text-xs font-mono font-semibold transition-all cursor-pointer"
                  >
                    {revealingValues ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin text-[#FF2D6D]" />
                    ) : valueDiffData ? (
                      <EyeOff className="w-3.5 h-3.5 text-[#FF2D6D]" />
                    ) : (
                      <Eye className="w-3.5 h-3.5 text-[#FF2D6D]" />
                    )}
                    <span>
                      {valueDiffData
                        ? `Hide Plaintext (${autoMaskSeconds}s)`
                        : revealingValues
                        ? 'Decrypting...'
                        : 'Reveal Value Diff'}
                    </span>
                  </button>

                  {onTriggerRollback && (
                    <button
                      onClick={() => onTriggerRollback(fromVersion)}
                      className="flex items-center gap-2 px-4 py-1.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-headline font-bold text-xs transition-all shadow-md shadow-[#FF2D6D]/20 cursor-pointer active:scale-95"
                    >
                      <RotateCcw className="w-3.5 h-3.5" />
                      <span>Rollback to v{fromVersion}</span>
                    </button>
                  )}
                </div>
              </div>

              {/* Side-by-Side Metadata Cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 font-mono text-xs">
                
                {/* Source Version Card */}
                <div className="flex flex-col rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 overflow-hidden">
                  <div className="p-3.5 bg-[#1E000A] border-b border-[#FFB4C8]/15 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 rounded bg-[#3F0016] text-[#FFB4C8] font-bold">
                        v{diffData?.fromMetadata?.versionNumber}
                      </span>
                      <span className="text-[#A26377] text-[11px]">
                        {diffData?.fromMetadata?.versionType}
                      </span>
                    </div>
                    <span className="text-[11px] text-[#FF2D6D] font-semibold">
                      Entropy: {diffData?.entropyScoreA || 0} bits/char
                    </span>
                  </div>
                  <div className="p-4 flex flex-col gap-2.5">
                    <div className="flex justify-between text-[#A26377]">
                      <span>Reason:</span>
                      <span className="text-white">{diffData?.fromMetadata?.reason || '—'}</span>
                    </div>
                    <div className="flex justify-between text-[#A26377]">
                      <span>Created At:</span>
                      <span className="text-white">
                        {diffData?.fromMetadata?.createdAt ? new Date(diffData.fromMetadata.createdAt).toLocaleString() : '—'}
                      </span>
                    </div>
                    <div className="flex justify-between text-[#A26377]">
                      <span>Key Reference:</span>
                      <span className="text-white">{diffData?.fromMetadata?.keyReference}</span>
                    </div>
                  </div>
                </div>

                {/* Target Version Card */}
                <div className="flex flex-col rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 overflow-hidden">
                  <div className="p-3.5 bg-[#1E000A] border-b border-[#FFB4C8]/15 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 rounded bg-[#FF2D6D] text-white font-bold">
                        v{diffData?.toMetadata?.versionNumber}
                      </span>
                      <span className="text-[#A26377] text-[11px]">
                        {diffData?.toMetadata?.versionType}
                        {diffData?.toMetadata?.isCurrent && ' (Current)'}
                      </span>
                    </div>
                    <span className="text-[11px] text-[#FF2D6D] font-semibold">
                      Entropy: {diffData?.entropyScoreB || 0} bits/char
                    </span>
                  </div>
                  <div className="p-4 flex flex-col gap-2.5">
                    <div className="flex justify-between text-[#A26377]">
                      <span>Reason:</span>
                      <span className="text-white">{diffData?.toMetadata?.reason || '—'}</span>
                    </div>
                    <div className="flex justify-between text-[#A26377]">
                      <span>Created At:</span>
                      <span className="text-white">
                        {diffData?.toMetadata?.createdAt ? new Date(diffData.toMetadata.createdAt).toLocaleString() : '—'}
                      </span>
                    </div>
                    <div className="flex justify-between text-[#A26377]">
                      <span>Key Reference:</span>
                      <span className="text-white">{diffData?.toMetadata?.keyReference}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Value Diff Viewer (If Revealed) */}
              {valueDiffData && (
                <div className="flex flex-col gap-2 rounded-2xl bg-[#140007] border border-[#FFB4C8]/25 p-4 font-mono text-xs overflow-hidden animate-fade-in">
                  <div className="flex items-center justify-between pb-2 border-b border-[#FFB4C8]/15 text-[#A26377]">
                    <span className="font-bold text-white">Decrypted Plaintext Diff Stream</span>
                    <span className="text-[#FBBF24] text-[11px] flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5" />
                      Auto-mask in {autoMaskSeconds}s
                    </span>
                  </div>
                  <div className="max-h-60 overflow-y-auto flex flex-col gap-1 py-2">
                    {(valueDiffData.diffLines || []).map((line, idx) => (
                      <div
                        key={idx}
                        className={`px-3 py-1.5 rounded-lg flex items-center gap-3 ${
                          line.type === 'ADDED'
                            ? 'bg-[#4ADE80]/10 text-[#4ADE80] border-l-2 border-[#4ADE80]'
                            : line.type === 'REMOVED'
                            ? 'bg-[#F87171]/10 text-[#F87171] border-l-2 border-[#F87171] line-through opacity-80'
                            : 'text-[#A26377]'
                        }`}
                      >
                        <span className="w-4 font-bold select-none">
                          {line.type === 'ADDED' ? '+' : line.type === 'REMOVED' ? '-' : ' '}
                        </span>
                        <span className="font-mono break-all">{line.text}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end pt-4 border-t border-[#FFB4C8]/15 mt-2">
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all cursor-pointer"
          >
            Close Diff Inspector
          </button>
        </div>
      </div>
    </div>
  );
}

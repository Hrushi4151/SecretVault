import React, { useState, useEffect } from 'react';
import { promotionApi } from '../../api/promotion';
import {
  X,
  ArrowRightLeft,
  Shield,
  ShieldCheck,
  Check,
  AlertCircle,
  Loader2,
  CheckCircle2,
  RefreshCw,
  Sparkles,
  Layers,
  Eye,
  Key
} from 'lucide-react';

export default function EnvironmentPromotionModal({
  isOpen,
  onClose,
  environments = [],
  currentEnvironmentId,
  workspaceId,
  projectId,
  onPromotionCompleted
}) {
  const [sourceEnvId, setSourceEnvId] = useState(currentEnvironmentId || '');
  const [destEnvId, setDestEnvId] = useState('');
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [error, setError] = useState(null);
  const [previewData, setPreviewData] = useState(null);
  const [selectedSecrets, setSelectedSecrets] = useState([]);
  const [reason, setReason] = useState('');
  const [resultData, setResultData] = useState(null);

  useEffect(() => {
    if (currentEnvironmentId) {
      setSourceEnvId(currentEnvironmentId);
    }
  }, [currentEnvironmentId]);

  useEffect(() => {
    // Pick default destination environment if available
    if (environments.length > 1 && !destEnvId) {
      const other = environments.find(e => e.id !== sourceEnvId);
      if (other) setDestEnvId(other.id);
    }
  }, [environments, sourceEnvId, destEnvId]);

  if (!isOpen) return null;

  const handlePreview = async () => {
    if (!sourceEnvId || !destEnvId || sourceEnvId === destEnvId) {
      setError('Please select two different environments to promote secrets.');
      return;
    }

    setLoadingPreview(true);
    setError(null);
    setPreviewData(null);
    setResultData(null);

    try {
      const res = await promotionApi.previewPromotion(workspaceId, projectId, sourceEnvId, {
        destinationEnvironmentId: destEnvId
      });
      const data = res?.data !== undefined ? res.data : res;
      setPreviewData(data);

      // Select all ADDED and MODIFIED by default
      const eligible = (data?.items || [])
        .filter(item => item.status === 'ADDED' || item.status === 'MODIFIED')
        .map(item => item.secretName);
      setSelectedSecrets(eligible);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to generate promotion preview');
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleExecute = async () => {
    if (!selectedSecrets.length) {
      setError('Please select at least one secret to promote');
      return;
    }

    setExecuting(true);
    setError(null);

    try {
      const res = await promotionApi.executePromotion(workspaceId, projectId, sourceEnvId, {
        destinationEnvironmentId: destEnvId,
        secretNames: selectedSecrets,
        reason: reason.trim() || `Promoted from ${previewData?.sourceEnvironmentName || 'source'} to ${previewData?.destinationEnvironmentName || 'destination'}`
      });

      const data = res?.data !== undefined ? res.data : res;
      setResultData(data);
      if (onPromotionCompleted) {
        onPromotionCompleted(data);
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Promotion execution failed');
    } finally {
      setExecuting(false);
    }
  };

  const toggleSelectSecret = (name) => {
    setSelectedSecrets(prev =>
      prev.includes(name) ? prev.filter(n => n !== name) : [...prev, name]
    );
  };

  const toggleSelectAll = () => {
    if (!previewData?.items) return;
    const eligible = previewData.items
      .filter(item => item.status === 'ADDED' || item.status === 'MODIFIED')
      .map(item => item.secretName);

    if (selectedSecrets.length === eligible.length) {
      setSelectedSecrets([]);
    } else {
      setSelectedSecrets(eligible);
    }
  };

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
              <h2 className="text-lg md:text-xl font-headline font-bold text-white tracking-tight">
                Cross-Environment Secret Promotion
              </h2>
              <p className="text-xs font-mono text-[#A26377]">
                Promote verified secrets across isolated enclave environments
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
          {error && (
            <div className="p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6] flex items-center gap-3 font-mono">
              <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
              <span>{error}</span>
            </div>
          )}

          {/* Environment Pickers */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 font-mono text-xs">
            <div className="flex flex-col gap-1.5">
              <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">
                Source Environment
              </label>
              <select
                value={sourceEnvId}
                onChange={(e) => { setSourceEnvId(e.target.value); setPreviewData(null); }}
                className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] text-xs"
              >
                {environments.map(e => (
                  <option key={e.id} value={e.id}>{e.name} ({e.slug})</option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">
                Destination Environment
              </label>
              <select
                value={destEnvId}
                onChange={(e) => { setDestEnvId(e.target.value); setPreviewData(null); }}
                className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] text-xs"
              >
                {environments.map(e => (
                  <option key={e.id} value={e.id} disabled={e.id === sourceEnvId}>
                    {e.name} {e.isProtected ? '🛡️ (Protected)' : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex items-center justify-between">
            <button
              onClick={handlePreview}
              disabled={loadingPreview || sourceEnvId === destEnvId}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/20 font-mono font-semibold text-xs transition-all disabled:opacity-50 cursor-pointer"
            >
              {loadingPreview ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin text-[#FF2D6D]" />
                  <span>Analyzing Invariants...</span>
                </>
              ) : (
                <>
                  <Eye className="w-4 h-4 text-[#FF2D6D]" />
                  <span>Preview Promotion (Dry-Run)</span>
                </>
              )}
            </button>
          </div>

          {/* Dry Run Preview Summary & Items Table */}
          {previewData && (
            <div className="flex flex-col gap-5 font-mono text-xs animate-fade-in">
              
              {/* Protected Notice */}
              {previewData.isDestinationProtected && (
                <div className="p-3.5 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/40 text-[#FFB4C8] flex items-center gap-2.5">
                  <Shield className="w-5 h-5 text-[#FF2D6D] shrink-0" />
                  <span><strong>Protected Destination:</strong> Target environment enforces production isolation controls.</span>
                </div>
              )}

              {/* Summary Counts */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                  <span className="text-[10px] text-[#A26377] uppercase tracking-wider">Total Evaluated</span>
                  <span className="text-xl font-headline font-bold text-white">{previewData.totalCandidates || 0}</span>
                </div>
                <div className="p-4 rounded-2xl bg-[#30000F] border border-[#4ADE80]/30 flex flex-col gap-1">
                  <span className="text-[10px] text-[#4ADE80] uppercase tracking-wider">New Secrets</span>
                  <span className="text-xl font-headline font-bold text-[#4ADE80]">+{previewData.addedCount || 0}</span>
                </div>
                <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FBBF24]/30 flex flex-col gap-1">
                  <span className="text-[10px] text-[#FBBF24] uppercase tracking-wider">Value Updates</span>
                  <span className="text-xl font-headline font-bold text-[#FBBF24]">~{previewData.modifiedCount || 0}</span>
                </div>
                <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                  <span className="text-[10px] text-[#A26377] uppercase tracking-wider">Unchanged</span>
                  <span className="text-xl font-headline font-bold text-[#A26377]">{previewData.unchangedCount || 0}</span>
                </div>
              </div>

              {/* Candidate Secrets Table */}
              <div className="rounded-2xl bg-[#140007] border border-[#FFB4C8]/20 overflow-hidden">
                <div className="p-3.5 bg-[#30000F] border-b border-[#FFB4C8]/15 flex items-center justify-between text-[11px]">
                  <div className="flex items-center gap-3">
                    <input
                      type="checkbox"
                      checked={selectedSecrets.length > 0 && selectedSecrets.length === (previewData.items || []).filter(i => i.status === 'ADDED' || i.status === 'MODIFIED').length}
                      onChange={toggleSelectAll}
                      className="rounded bg-[#1E000A] border-[#FFB4C8]/30 text-[#FF2D6D] focus:ring-0 cursor-pointer"
                    />
                    <span className="font-bold text-white uppercase">Select Candidates for Promotion</span>
                  </div>
                  <span className="text-[#A26377]">{selectedSecrets.length} selected</span>
                </div>

                <div className="divide-y divide-[#FFB4C8]/10 max-h-60 overflow-y-auto">
                  {(previewData.items || []).length === 0 ? (
                    <div className="p-6 text-center text-[#A26377]">
                      No secrets found in source environment.
                    </div>
                  ) : (
                    previewData.items.map((item, idx) => {
                      const isSelectable = item.status === 'ADDED' || item.status === 'MODIFIED';
                      const isChecked = selectedSecrets.includes(item.secretName);

                      return (
                        <div
                          key={idx}
                          className={`p-3.5 flex items-center justify-between gap-4 transition-colors ${
                            isChecked ? 'bg-[#30000F]/60' : 'hover:bg-[#30000F]/30'
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <input
                              type="checkbox"
                              disabled={!isSelectable}
                              checked={isChecked}
                              onChange={() => toggleSelectSecret(item.secretName)}
                              className="rounded bg-[#1E000A] border-[#FFB4C8]/30 text-[#FF2D6D] focus:ring-0 disabled:opacity-30 cursor-pointer"
                            />
                            <div className="flex flex-col">
                              <span className="font-bold text-white">{item.secretName}</span>
                              <span className="text-[11px] text-[#A26377]">{item.message}</span>
                            </div>
                          </div>

                          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase border ${
                            item.status === 'ADDED'
                              ? 'bg-[#4ADE80]/20 text-[#4ADE80] border-[#4ADE80]/30'
                              : item.status === 'MODIFIED'
                              ? 'bg-[#FBBF24]/20 text-[#FBBF24] border-[#FBBF24]/30'
                              : item.status === 'UNCHANGED'
                              ? 'bg-[#30000F] text-[#A26377] border-[#FFB4C8]/15'
                              : 'bg-[#F87171]/20 text-[#F87171] border-[#F87171]/30'
                          }`}>
                            {item.status}
                          </span>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>

              {/* Execution Reason */}
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">
                  Promotion Audit Reason
                </label>
                <input
                  type="text"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="e.g. Release 2026.09 Production Sync"
                  className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] text-xs"
                />
              </div>

              {/* Execution Trigger Button */}
              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  onClick={handleExecute}
                  disabled={executing || selectedSecrets.length === 0}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-headline font-bold text-xs transition-all shadow-lg shadow-[#FF2D6D]/20 disabled:opacity-50 cursor-pointer active:scale-95"
                >
                  {executing ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Executing Promotion...</span>
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4" />
                      <span>Promote {selectedSecrets.length} Secret{selectedSecrets.length === 1 ? '' : 's'}</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          )}

          {/* Execution Result Banner */}
          {resultData && (
            <div className="p-4 rounded-2xl bg-[#4ADE80]/15 border border-[#4ADE80]/30 text-[#4ADE80] flex flex-col gap-2 font-mono text-xs animate-fade-in">
              <div className="flex items-center gap-2 font-bold text-sm">
                <CheckCircle2 className="w-5 h-5" />
                <span>Promotion Completed Successfully!</span>
              </div>
              <p className="text-white/80">
                Promoted {resultData.promotedCount || 0} secret(s) ({resultData.skippedUnchangedCount || 0} skipped unchanged, {resultData.failedCount || 0} failed).
              </p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end pt-4 border-t border-[#FFB4C8]/15 mt-2">
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

import React, { useState, useEffect } from 'react';
import { secretApi } from '../../api/secrets';
import { versionsApi } from '../../api/versions';
import SecretDiffModal from './SecretDiffModal';
import SecretRollbackModal from './SecretRollbackModal';
import { StepUpAuthenticationModal } from '../auth/StepUpAuthenticationModal';
import {
  X,
  Key,
  Lock,
  Unlock,
  Copy,
  Check,
  Clock,
  History,
  ShieldCheck,
  ShieldAlert,
  AlertCircle,
  Loader2,
  RefreshCw,
  Trash2,
  EyeOff,
  Sparkles,
  Tag,
  ArrowRightLeft,
  RotateCcw,
  Plus,
  FileText,
  Ban
} from 'lucide-react';

export const SecretDetailsModal = ({
  isOpen,
  onClose,
  secret,
  workspaceId,
  projectId,
  environmentId,
  environmentName = 'Production',
  environmentType = 'DEVELOPMENT',
  onSecretUpdated,
  onSecretDeleted,
}) => {
  const [activeTab, setActiveTab] = useState('overview'); // 'overview' | 'versions' | 'rotate'
  const [versions, setVersions] = useState([]);
  const [isLoadingVersions, setIsLoadingVersions] = useState(false);

  // Effective Policy State
  const [revealPolicy, setRevealPolicy] = useState(null);
  const [isLoadingPolicy, setIsLoadingPolicy] = useState(false);

  // Reveal state
  const [revealedValue, setRevealedValue] = useState(null);
  const [revealedVersion, setRevealedVersion] = useState(null);
  const [isRevealing, setIsRevealing] = useState(false);
  const [revealError, setRevealError] = useState(null);
  const [autoMaskSeconds, setAutoMaskSeconds] = useState(0);
  const [isCopied, setIsCopied] = useState(false);
  const [copyAllowed, setCopyAllowed] = useState(true);
  const [clipboardTimeoutSeconds, setClipboardTimeoutSeconds] = useState(15);
  const [policyLevel, setPolicyLevel] = useState('DEFAULT');

  // Step-Up & Justification State
  const [isStepUpOpen, setIsStepUpOpen] = useState(false);
  const [isReasonModalOpen, setIsReasonModalOpen] = useState(false);
  const [justificationReason, setJustificationReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [pendingVersionNumber, setPendingVersionNumber] = useState(null);
  const [pendingStepUpProof, setPendingStepUpProof] = useState(null);

  // Rotate state
  const [newValue, setNewValue] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [isRotating, setIsRotating] = useState(false);
  const [rotateError, setRotateError] = useState(null);

  // Tagging state
  const [newTagName, setNewTagName] = useState('');
  const [taggingVersionNumber, setTaggingVersionNumber] = useState(null);
  const [isTagging, setIsTagging] = useState(false);

  // Modals for Diff & Rollback
  const [diffParams, setDiffParams] = useState(null); // { from, to }
  const [rollbackTargetVersion, setRollbackTargetVersion] = useState(null);

  // Delete state
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    if (isOpen && secret?.id) {
      setActiveTab('overview');
      setRevealedValue(null);
      setRevealedVersion(null);
      setAutoMaskSeconds(0);
      setRevealError(null);
      setNewValue('');
      setNewDescription(secret.description || '');
      setDiffParams(null);
      setRollbackTargetVersion(null);
      setIsReasonModalOpen(false);
      setJustificationReason('');
      setReasonError(null);
      setPendingVersionNumber(null);
      setPendingStepUpProof(null);
      fetchVersions();
      fetchRevealPolicy();
    }
  }, [isOpen, secret?.id]);

  // Auto-mask countdown timer
  useEffect(() => {
    let timer;
    if (autoMaskSeconds > 0) {
      timer = setInterval(() => {
        setAutoMaskSeconds((prev) => {
          if (prev <= 1) {
            setRevealedValue(null);
            setRevealedVersion(null);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [autoMaskSeconds]);

  if (!isOpen || !secret) return null;

  const fetchVersions = async () => {
    setIsLoadingVersions(true);
    try {
      const response = await versionsApi.getVersions(workspaceId, projectId, environmentId, secret.id, 0, 50);
      const data = response?.data?.content || response?.data || response;
      setVersions(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch versions:', err);
    } finally {
      setIsLoadingVersions(false);
    }
  };

  const fetchRevealPolicy = async () => {
    setIsLoadingPolicy(true);
    try {
      const response = await secretApi.getRevealPolicy(workspaceId, projectId, environmentId, secret.id);
      const data = response?.data || response;
      setRevealPolicy(data);
      if (data.policyLevel) {
        setPolicyLevel(data.policyLevel);
      }
      if (typeof data.copyAllowed === 'boolean') {
        setCopyAllowed(data.copyAllowed);
      }
      if (data.clipboardTimeoutSeconds) {
        setClipboardTimeoutSeconds(data.clipboardTimeoutSeconds);
      }
    } catch (err) {
      console.error('Failed to fetch reveal policy:', err);
    } finally {
      setIsLoadingPolicy(false);
    }
  };

  const handleReveal = async (versionNumber = null, stepUpProof = null, reason = null) => {
    const targetVer = versionNumber || secret.currentVersionNumber || 1;
    const effectiveReason = reason || (justificationReason ? justificationReason.trim() : null);

    // If policy requires reason and none was provided yet, open reason dialog
    if (revealPolicy?.requireReason && !effectiveReason) {
      setPendingVersionNumber(targetVer);
      setPendingStepUpProof(stepUpProof);
      setIsReasonModalOpen(true);
      return;
    }

    setIsRevealing(true);
    setRevealError(null);

    try {
      // Phase 1: Request Single-Use Reveal Intent Token
      const intentPayload = {
        versionNumber: targetVer,
        reason: effectiveReason,
      };

      const intentRes = await secretApi.createRevealIntent(
        workspaceId,
        projectId,
        environmentId,
        secret.id,
        intentPayload,
        stepUpProof || pendingStepUpProof
      );

      const intentData = intentRes?.data || intentRes;
      const intentToken = intentData?.intentToken;

      if (!intentToken) {
        throw new Error('Failed to obtain reveal intent token.');
      }

      // Phase 2: Consume Intent Token and Decrypt Payload In-Memory
      const revealPayload = {
        intentToken,
        versionNumber: targetVer,
        reason: effectiveReason,
        stepUpProof: stepUpProof || pendingStepUpProof,
      };

      const revealRes = await secretApi.reveal(
        workspaceId,
        projectId,
        environmentId,
        secret.id,
        revealPayload,
        stepUpProof || pendingStepUpProof
      );

      const data = revealRes?.data || revealRes;
      setRevealedValue(data.value);
      setRevealedVersion(data.versionNumber || targetVer);
      setPolicyLevel(data.policyLevel || intentData.policyLevel || 'DEFAULT');
      setCopyAllowed(data.copyAllowed !== false && intentData.copyAllowed !== false);
      setClipboardTimeoutSeconds(data.clipboardTimeoutSeconds || intentData.clipboardTimeoutSeconds || 15);
      setAutoMaskSeconds(data.maxDisplayDurationSeconds || intentData.maxDisplayDurationSeconds || 30);

      setIsReasonModalOpen(false);
      setJustificationReason('');
      setReasonError(null);
      setPendingStepUpProof(null);
      setPendingVersionNumber(null);
    } catch (err) {
      const errCode = err.payload?.code || err.code;
      const errMsg = err.payload?.message || err.message || '';

      if (
        errCode === 'STEP_UP_REQUIRED' ||
        errMsg.toLowerCase().includes('step-up') ||
        (err.status === 403 && errCode === 'STEP_UP_REQUIRED')
      ) {
        setPendingVersionNumber(targetVer);
        setIsStepUpOpen(true);
      } else if (errCode === 'JUSTIFICATION_REQUIRED' || errCode === 'INVALID_JUSTIFICATION_REASON') {
        setPendingVersionNumber(targetVer);
        setReasonError(errMsg || 'A valid justification reason (min 10 chars) is required.');
        setIsReasonModalOpen(true);
      } else {
        setRevealError(errMsg || 'Failed to reveal secret. Check your permissions or security policy.');
      }
    } finally {
      setIsRevealing(false);
    }
  };

  const submitJustification = (e) => {
    e.preventDefault();
    if (!justificationReason || justificationReason.trim().length < 10) {
      setReasonError('Justification reason must be at least 10 characters.');
      return;
    }
    setReasonError(null);
    setIsReasonModalOpen(false);
    handleReveal(pendingVersionNumber, pendingStepUpProof, justificationReason.trim());
  };

  const handleCopy = () => {
    if (!revealedValue || !copyAllowed) return;
    navigator.clipboard.writeText(revealedValue);
    setIsCopied(true);
    setTimeout(() => setIsCopied(false), 2000);

    // Auto-clear clipboard after configured timeout
    const timeoutMs = (clipboardTimeoutSeconds || 15) * 1000;
    setTimeout(() => {
      try {
        if (navigator.clipboard && navigator.clipboard.readText) {
          navigator.clipboard.readText().then((clipText) => {
            if (clipText === revealedValue) {
              navigator.clipboard.writeText('');
            }
          }).catch(() => {});
        }
      } catch (e) {
        // Ignored
      }
    }, timeoutMs);
  };

  const handleRotate = async (e) => {
    e.preventDefault();
    if (!newValue && !newDescription) {
      setRotateError('Please enter a new value or description to update.');
      return;
    }

    setIsRotating(true);
    setRotateError(null);

    try {
      const payload = {};
      if (newValue) payload.value = newValue;
      if (newDescription !== secret.description) payload.description = newDescription;

      const response = await secretApi.update(
        workspaceId,
        projectId,
        environmentId,
        secret.id,
        payload
      );
      const updated = response?.data || response;
      if (onSecretUpdated) {
        onSecretUpdated(updated);
      }
      setNewValue('');
      await fetchVersions();
      setActiveTab('overview');
    } catch (err) {
      setRotateError(err.response?.data?.message || err.message || 'Failed to rotate secret.');
    } finally {
      setIsRotating(false);
    }
  };

  const handleAddTag = async (versionNumber) => {
    if (!newTagName.trim()) return;
    setIsTagging(true);
    try {
      await versionsApi.addTag(workspaceId, projectId, environmentId, secret.id, versionNumber, newTagName.trim());
      setNewTagName('');
      setTaggingVersionNumber(null);
      await fetchVersions();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Failed to add tag');
    } finally {
      setIsTagging(false);
    }
  };

  const handleRemoveTag = async (versionNumber, tagName) => {
    try {
      await versionsApi.removeTag(workspaceId, projectId, environmentId, secret.id, versionNumber, tagName);
      await fetchVersions();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Failed to remove tag');
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`Are you sure you want to delete secret "${secret.name}"?`)) {
      return;
    }

    setIsDeleting(true);
    try {
      await secretApi.delete(workspaceId, projectId, environmentId, secret.id);
      if (onSecretDeleted) {
        onSecretDeleted(secret.id);
      }
      onClose();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Failed to delete secret');
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in font-body">
        <div className="relative w-full max-w-3xl bg-[#1E000A] border border-[#FFB4C8]/25 rounded-3xl shadow-2xl p-6 md:p-8 flex flex-col gap-6 text-white max-h-[90vh] overflow-y-auto">
          {/* Header */}
          <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
                <Key className="w-6 h-6" />
              </div>
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <h2 className="text-lg md:text-xl font-headline font-bold text-white tracking-tight">
                    {secret.name}
                  </h2>
                  <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#4ADE80] border border-[#4ADE80]/30 font-semibold">
                    {secret.status || 'ACTIVE'}
                  </span>
                  <span className="px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono text-[#FFB4C8] border border-[#FFB4C8]/20">
                    v{secret.currentVersionNumber || 1}
                  </span>
                  {policyLevel && policyLevel !== 'DEFAULT' && (
                    <span className="px-2 py-0.5 rounded-full bg-[#93000A]/50 text-[10px] font-mono text-[#FFB4AB] border border-[#FFB4AB]/40 font-bold flex items-center gap-1">
                      <ShieldAlert className="w-3 h-3 text-[#FFB4AB]" />
                      {policyLevel}
                    </span>
                  )}
                </div>
                <p className="text-[11px] font-mono text-[#A26377] flex items-center gap-2 mt-0.5">
                  <span>Environment: <strong className="text-[#FFB4C8]">{environmentName}</strong></span>
                  <span>•</span>
                  <span className={environmentType === 'DEVELOPMENT' ? 'text-[#4ADE80] font-semibold' : 'text-[#A26377]'}>
                    Branches: {environmentType === 'DEVELOPMENT' ? 'Enabled' : 'Disabled'}
                  </span>
                </p>
              </div>
            </div>

            <button
              onClick={() => {
                setRevealedValue(null);
                setRevealedVersion(null);
                setAutoMaskSeconds(0);
                onClose();
              }}
              className="p-2 rounded-xl text-[#A26377] hover:text-white hover:bg-[#30000F] transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Tab Navigation */}
          <div className="flex items-center gap-2 p-1 rounded-2xl bg-[#140007] border border-[#FFB4C8]/15 text-xs">
            <button
              onClick={() => setActiveTab('overview')}
              className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 ${
                activeTab === 'overview'
                  ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                  : 'text-[#A26377] hover:text-white'
              }`}
            >
              <ShieldCheck className="w-4 h-4 text-[#FF2D6D]" />
              <span>Overview &amp; Reveal</span>
            </button>

            <button
              onClick={() => setActiveTab('versions')}
              className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 ${
                activeTab === 'versions'
                  ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                  : 'text-[#A26377] hover:text-white'
              }`}
            >
              <History className="w-4 h-4 text-[#FF2D6D]" />
              <span>Versions &amp; Lineage ({versions.length || secret.currentVersionNumber || 1})</span>
            </button>

            <button
              onClick={() => setActiveTab('rotate')}
              className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 ${
                activeTab === 'rotate'
                  ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                  : 'text-[#A26377] hover:text-white'
              }`}
            >
              <RefreshCw className="w-4 h-4 text-[#FF2D6D]" />
              <span>Rotate / Edit</span>
            </button>
          </div>

          {/* Tab 1: OVERVIEW & REVEAL */}
          {activeTab === 'overview' && (
            <div className="flex flex-col gap-5">
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-2">
                <span className="text-[10px] font-mono uppercase tracking-wider text-[#A26377]">
                  Description
                </span>
                <p className="text-xs text-[#F4B5C8] leading-relaxed">
                  {secret.description || 'No description provided for this secret.'}
                </p>
              </div>

              {/* In-Memory Reveal Box */}
              <div className="p-5 rounded-2xl bg-[#140007] border border-[#FF2D6D]/30 flex flex-col gap-4 relative overflow-hidden">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <div className="flex items-center gap-2">
                    <Lock className="w-4 h-4 text-[#FF2D6D]" />
                    <span className="text-xs font-mono font-bold uppercase tracking-wider text-white">
                      Cryptographic Payload
                    </span>
                    {revealedVersion && (
                      <span className="px-2 py-0.5 rounded bg-[#30000F] text-[10px] font-mono text-[#FFB4C8] border border-[#FFB4C8]/20">
                        v{revealedVersion}
                      </span>
                    )}
                  </div>

                  {revealedValue && (
                    <div className="flex items-center gap-2 text-xs font-mono text-[#F87171] bg-[#3F0016] px-2.5 py-1 rounded-full border border-[#F87171]/40 animate-pulse">
                      <Clock className="w-3.5 h-3.5" />
                      <span>Auto-masking in {autoMaskSeconds}s</span>
                    </div>
                  )}
                </div>

                {revealError && (
                  <div className="p-3 rounded-xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6] flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{revealError}</span>
                  </div>
                )}

                <div className="p-3.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/20 flex items-center justify-between gap-4 font-mono text-xs">
                  {revealedValue ? (
                    <span className="text-[#4ADE80] select-all break-all flex-1 font-semibold" data-testid="revealed-secret-value">
                      {revealedValue}
                    </span>
                  ) : (
                    <span className="text-[#A26377] tracking-widest text-sm select-none">
                      ••••••••••••••••••••••••••••••••
                    </span>
                  )}

                  {revealedValue && (
                    copyAllowed ? (
                      <button
                        onClick={handleCopy}
                        data-testid="copy-secret-button"
                        className="p-2 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#FFB4C8] hover:text-white border border-[#FFB4C8]/15 transition-all shrink-0 flex items-center gap-1.5"
                        title={`Copy to clipboard (auto-clears in ${clipboardTimeoutSeconds}s)`}
                      >
                        {isCopied ? (
                          <>
                            <Check className="w-3.5 h-3.5 text-[#4ADE80]" />
                            <span className="text-[10px] text-[#4ADE80]">Copied</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3.5 h-3.5" />
                            <span className="text-[10px]">Copy</span>
                          </>
                        )}
                      </button>
                    ) : (
                      <div className="px-2.5 py-1.5 rounded-lg bg-[#30000F] border border-[#FFB4C8]/10 text-[10px] font-mono text-[#A26377] flex items-center gap-1">
                        <Ban className="w-3 h-3 text-[#F87171]" />
                        <span>Copy Restricted</span>
                      </div>
                    )
                  )}
                </div>

                <div className="flex items-center justify-between pt-1 flex-wrap gap-2">
                  <span className="text-[10px] font-mono text-[#A26377]">
                    Decrypted via single-use intent token bound to active session.
                  </span>

                  {revealedValue ? (
                    <button
                      onClick={() => {
                        setRevealedValue(null);
                        setRevealedVersion(null);
                        setAutoMaskSeconds(0);
                      }}
                      className="px-3 py-1.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all flex items-center gap-1.5"
                    >
                      <EyeOff className="w-3.5 h-3.5" />
                      <span>Hide Secret</span>
                    </button>
                  ) : (
                    <button
                      onClick={() => handleReveal(null)}
                      disabled={isRevealing}
                      data-testid="reveal-secret-button"
                      className="px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-mono font-bold tracking-wider uppercase shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-2 cursor-pointer active:scale-95"
                    >
                      {isRevealing ? (
                        <>
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          <span>Decrypting...</span>
                        </>
                      ) : (
                        <>
                          <Unlock className="w-3.5 h-3.5" />
                          <span>Reveal Secret</span>
                        </>
                      )}
                    </button>
                  )}
                </div>
              </div>

              {/* Metadata Details */}
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                <div className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-1">
                  <span className="text-[10px] font-mono text-[#A26377] uppercase">Encryption</span>
                  <span className="font-mono text-white font-semibold">AES-256-GCM (AAD)</span>
                </div>
                <div className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-1">
                  <span className="text-[10px] font-mono text-[#A26377] uppercase">Current Version</span>
                  <span className="font-mono text-white font-semibold">v{secret.currentVersionNumber || 1}</span>
                </div>
                <div className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-1">
                  <span className="text-[10px] font-mono text-[#A26377] uppercase">Last Updated</span>
                  <span className="font-mono text-white">
                    {secret.updatedAt ? new Date(secret.updatedAt).toLocaleString() : 'N/A'}
                  </span>
                </div>
              </div>

              {/* Footer Danger Zone */}
              <div className="flex items-center justify-between pt-4 border-t border-[#FFB4C8]/15">
                <button
                  onClick={handleDelete}
                  disabled={isDeleting}
                  className="px-3 py-2 rounded-xl bg-[#3F0016] hover:bg-[#93000A] text-[#F87171] hover:text-white text-xs font-mono font-semibold transition-all border border-[#F87171]/30 flex items-center gap-2"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Delete Secret</span>
                </button>

                <button
                  onClick={() => {
                    setRevealedValue(null);
                    setRevealedVersion(null);
                    setAutoMaskSeconds(0);
                    onClose();
                  }}
                  className="px-4 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all"
                >
                  Close
                </button>
              </div>
            </div>
          )}

          {/* Tab 2: IMMUTABLE VERSION HISTORY */}
          {activeTab === 'versions' && (
            <div className="flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono text-[#A26377]">
                  Chronological immutable versions ledger with lineage and tags.
                </span>
                <button
                  onClick={fetchVersions}
                  disabled={isLoadingVersions}
                  className="p-1.5 rounded-lg bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15"
                  title="Refresh versions"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isLoadingVersions ? 'animate-spin text-[#FF2D6D]' : ''}`} />
                </button>
              </div>

              {isLoadingVersions ? (
                <div className="py-12 flex flex-col items-center justify-center gap-2 text-xs font-mono text-[#A26377]">
                  <Loader2 className="w-5 h-5 animate-spin text-[#FF2D6D]" />
                  <span>Fetching version ledger...</span>
                </div>
              ) : versions.length === 0 ? (
                <div className="p-8 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 text-center text-xs font-mono text-[#A26377]">
                  No historical versions found.
                </div>
              ) : (
                <div className="flex flex-col gap-3">
                  {versions.map((ver) => {
                    const isCurrent = ver.versionNumber === secret.currentVersionNumber;
                    return (
                      <div
                        key={ver.id || ver.versionNumber}
                        className={`p-4 rounded-2xl border flex flex-col gap-3 transition-all ${
                          isCurrent
                            ? 'bg-[#30000F] border-[#FF2D6D]/40 shadow-sm'
                            : 'bg-[#1E000A] border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30'
                        }`}
                      >
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-xs font-mono font-bold text-[#FF2D6D]">
                              v{ver.versionNumber}
                            </div>
                            <div className="flex flex-col">
                              <div className="flex items-center gap-2">
                                <span className="text-xs font-mono font-bold text-white">
                                  Version {ver.versionNumber}
                                </span>
                                <span className="text-[9px] font-mono px-2 py-0.5 rounded bg-[#140007] text-[#FFB4C8] border border-[#FFB4C8]/20 font-bold uppercase">
                                  {ver.versionType || 'UPDATE'}
                                </span>
                                {isCurrent && (
                                  <span className="text-[9px] font-mono px-2 py-0.5 rounded-full bg-[#4ADE80]/20 text-[#4ADE80] border border-[#4ADE80]/40 font-semibold">
                                    CURRENT
                                  </span>
                                )}
                              </div>
                              <span className="text-[10px] font-mono text-[#A26377]">
                                Created {new Date(ver.createdAt).toLocaleString()} {ver.reason ? `• ${ver.reason}` : ''}
                              </span>
                            </div>
                          </div>

                          {/* Action Buttons for this Version */}
                          <div className="flex items-center gap-2 self-start sm:self-auto flex-wrap">
                            <button
                              onClick={() => handleReveal(ver.versionNumber)}
                              className="px-2.5 py-1 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 text-xs font-mono font-semibold transition-all flex items-center gap-1"
                            >
                              <Unlock className="w-3 h-3 text-[#FF2D6D]" />
                              <span>Reveal</span>
                            </button>

                            <button
                              onClick={() => setDiffParams({ from: ver.versionNumber, to: secret.currentVersionNumber })}
                              className="px-2.5 py-1 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 text-xs font-mono font-semibold transition-all flex items-center gap-1"
                              title="Compare with current"
                            >
                              <ArrowRightLeft className="w-3 h-3 text-[#FFB4C8]" />
                              <span>Diff</span>
                            </button>

                            {!isCurrent && (
                              <button
                                onClick={() => setRollbackTargetVersion(ver)}
                                className="px-2.5 py-1 rounded-lg bg-[#3F0016] hover:bg-[#FF2D6D] text-[#FFB4C8] hover:text-white border border-[#FF2D6D]/30 text-xs font-mono font-semibold transition-all flex items-center gap-1"
                                title="Rollback to this version"
                              >
                                <RotateCcw className="w-3 h-3" />
                                <span>Rollback</span>
                              </button>
                            )}
                          </div>
                        </div>

                        {/* Version Tags Row */}
                        <div className="flex items-center gap-1.5 flex-wrap pt-1 border-t border-[#FFB4C8]/10 text-xs font-mono">
                          <Tag className="w-3 h-3 text-[#A26377]" />
                          {(ver.tags || []).map((t) => (
                            <span
                              key={t}
                              className="px-2 py-0.5 rounded-full bg-[#140007] text-[10px] text-[#FFB4C8] border border-[#FFB4C8]/20 flex items-center gap-1"
                            >
                              <span>{t}</span>
                              <button
                                onClick={() => handleRemoveTag(ver.versionNumber, t)}
                                className="hover:text-red-400"
                              >
                                ×
                              </button>
                            </span>
                          ))}

                          {taggingVersionNumber === ver.versionNumber ? (
                            <div className="flex items-center gap-1">
                              <input
                                type="text"
                                value={newTagName}
                                onChange={(e) => setNewTagName(e.target.value)}
                                placeholder="tag name..."
                                className="px-2 py-0.5 rounded bg-[#140007] border border-[#FFB4C8]/40 text-[10px] text-white outline-none w-24"
                              />
                              <button
                                onClick={() => handleAddTag(ver.versionNumber)}
                                disabled={isTagging}
                                className="px-2 py-0.5 rounded bg-[#FF2D6D] text-white text-[10px] font-bold"
                              >
                                Add
                              </button>
                              <button
                                onClick={() => setTaggingVersionNumber(null)}
                                className="text-[10px] text-[#A26377]"
                              >
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => { setTaggingVersionNumber(ver.versionNumber); setNewTagName(''); }}
                              className="text-[10px] text-[#A26377] hover:text-[#FFB4C8] flex items-center gap-0.5"
                            >
                              <Plus className="w-2.5 h-2.5" />
                              <span>Tag</span>
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* Tab 3: ROTATE / UPDATE */}
          {activeTab === 'rotate' && (
            <form onSubmit={handleRotate} className="flex flex-col gap-5">
              <div className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex items-start gap-3">
                <Sparkles className="w-4 h-4 text-[#FF2D6D] shrink-0 mt-0.5" />
                <p className="text-xs text-[#F4B5C8] leading-relaxed">
                  Rotating this secret will generate a new ephemeral DEK, encrypt the new payload under AES-256-GCM, and create <span className="font-mono text-white font-bold">Version {(secret.currentVersionNumber || 1) + 1}</span>. Historical versions remain accessible for rollbacks.
                </p>
              </div>

              {rotateError && (
                <div className="p-3 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6] flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{rotateError}</span>
                </div>
              )}

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono font-bold tracking-wider text-[#A26377] uppercase">
                  New Secret Value *
                </label>
                <textarea
                  rows={3}
                  value={newValue}
                  onChange={(e) => setNewValue(e.target.value)}
                  placeholder="Enter new rotated secret value..."
                  className="w-full px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs font-mono text-white placeholder-[#A26377] outline-none transition-all resize-none"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-mono font-bold tracking-wider text-[#A26377] uppercase">
                  Updated Description / Reason
                </label>
                <input
                  type="text"
                  value={newDescription}
                  onChange={(e) => setNewDescription(e.target.value)}
                  placeholder="Updated context or reason for secret rotation..."
                  className="w-full px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs text-white placeholder-[#A26377] outline-none transition-all"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15 mt-2">
                <button
                  type="button"
                  onClick={() => setActiveTab('overview')}
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isRotating || (!newValue && !newDescription)}
                  className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-xs font-mono font-bold tracking-wider uppercase text-white shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-2 cursor-pointer active:scale-95"
                >
                  {isRotating ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Rotating &amp; Encrypting...</span>
                    </>
                  ) : (
                    <>
                      <RefreshCw className="w-4 h-4" />
                      <span>Rotate Secret</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>

      {/* Justification Reason Prompt Modal */}
      {isReasonModalOpen && (
        <div className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md animate-fade-in font-body">
          <div className="relative w-full max-w-lg bg-[#1E000A] border border-[#FF2D6D]/40 rounded-3xl shadow-2xl p-6 flex flex-col gap-4 text-white">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-3">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
                  <FileText className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-headline font-bold text-white">
                    Secret Reveal Justification Required
                  </h3>
                  <p className="text-[11px] font-mono text-[#A26377]">
                    Policy requires a documented business reason for decryption.
                  </p>
                </div>
              </div>
              <button
                onClick={() => setIsReasonModalOpen(false)}
                className="p-1.5 rounded-lg text-[#A26377] hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {reasonError && (
              <div className="p-3 rounded-xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6] flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{reasonError}</span>
              </div>
            )}

            <form onSubmit={submitJustification} className="flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between text-xs font-mono">
                  <label className="text-[#A26377] uppercase font-bold text-[10px]">
                    Reason / Ticket Reference *
                  </label>
                  <span className={`text-[10px] ${justificationReason.length < 10 ? 'text-[#F87171]' : 'text-[#4ADE80]'}`}>
                    {justificationReason.length}/500 chars (min 10)
                  </span>
                </div>
                <textarea
                  rows={3}
                  value={justificationReason}
                  onChange={(e) => setJustificationReason(e.target.value)}
                  placeholder="e.g. Incident response for ticket INC-9042: debugging database latency..."
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#140007] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs font-mono text-white placeholder-[#A26377] outline-none resize-none"
                  autoFocus
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#FFB4C8]/15">
                <button
                  type="button"
                  onClick={() => setIsReasonModalOpen(false)}
                  className="px-3.5 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={justificationReason.trim().length < 10}
                  className="px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-xs font-mono font-bold uppercase tracking-wider text-white shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-1.5"
                >
                  <Unlock className="w-3.5 h-3.5" />
                  <span>Verify &amp; Reveal</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Secret Diff Modal */}
      {diffParams && (
        <SecretDiffModal
          isOpen={!!diffParams}
          onClose={() => setDiffParams(null)}
          secret={secret}
          fromVersion={diffParams.from}
          toVersion={diffParams.to}
          workspaceId={workspaceId}
          projectId={projectId}
          environmentId={environmentId}
          onTriggerRollback={(targetVer) => {
            setDiffParams(null);
            setRollbackTargetVersion({ versionNumber: targetVer });
          }}
        />
      )}

      {/* Secret Rollback Modal */}
      {rollbackTargetVersion && (
        <SecretRollbackModal
          isOpen={!!rollbackTargetVersion}
          onClose={() => setRollbackTargetVersion(null)}
          secret={secret}
          targetVersion={rollbackTargetVersion}
          workspaceId={workspaceId}
          projectId={projectId}
          environmentId={environmentId}
          onRollbackSuccess={(newVer) => {
            fetchVersions();
            if (onSecretUpdated) {
              onSecretUpdated({ ...secret, currentVersionNumber: newVer.versionNumber });
            }
          }}
        />
      )}

      {/* Step-Up Authentication Modal */}
      {isStepUpOpen && (
        <StepUpAuthenticationModal
          isOpen={isStepUpOpen}
          onClose={() => setIsStepUpOpen(false)}
          action="SECRET_REVEAL"
          context={{
            workspaceId,
            projectId,
            environmentId,
            secretId: secret?.id,
          }}
          actionTitle="Reveal Protected Secret"
          actionDescription={`Step-up verification is required to reveal secret values in ${environmentName}.`}
          onSuccess={(proofToken) => {
            setIsStepUpOpen(false);
            setPendingStepUpProof(proofToken);
            handleReveal(pendingVersionNumber, proofToken, justificationReason);
          }}
        />
      )}
    </>
  );
};

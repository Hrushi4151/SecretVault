import React, { useState, useEffect } from 'react';
import { jitApi } from '../../api/jit';
import { environmentApi } from '../../api/environments';
import { projectApi } from '../../api/projects';
import { secretApi } from '../../api/secrets';
import {
  X,
  Clock,
  ShieldAlert,
  Key,
  Server,
  Sparkles,
  AlertTriangle,
  Loader2,
  CheckCircle2,
  Info
} from 'lucide-react';

export const RequestJitModal = ({ workspaceId, isOpen, onClose, onSuccess }) => {
  const [projects, setProjects] = useState([]);
  const [environments, setEnvironments] = useState([]);
  const [secrets, setSecrets] = useState([]);
  
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [selectedEnvId, setSelectedEnvId] = useState('');
  const [selectedSecretId, setSelectedSecretId] = useState('');
  const [requestedPermission, setRequestedPermission] = useState('secret.reveal');
  const [durationMinutes, setDurationMinutes] = useState(30);
  const [reason, setReason] = useState('');

  const [isLoadingMetadata, setIsLoadingMetadata] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (isOpen && workspaceId) {
      loadProjects();
    }
  }, [isOpen, workspaceId]);

  const loadProjects = async () => {
    try {
      setIsLoadingMetadata(true);
      const projs = await projectApi.list(workspaceId);
      const list = Array.isArray(projs) ? projs : projs?.data || [];
      setProjects(list);
      if (list.length > 0) {
        setSelectedProjectId(list[0].id);
        loadEnvironments(list[0].id);
      }
    } catch (err) {
      setErrorMessage(err.message || 'Failed to load projects');
    } finally {
      setIsLoadingMetadata(false);
    }
  };

  const loadEnvironments = async (projId) => {
    try {
      const envs = await environmentApi.list(workspaceId, projId);
      const list = Array.isArray(envs) ? envs : envs?.data || [];
      setEnvironments(list);
      if (list.length > 0) {
        setSelectedEnvId(list[0].id);
        loadSecrets(projId, list[0].id);
      } else {
        setSelectedEnvId('');
        setSecrets([]);
      }
    } catch (err) {
      console.error('Failed to load environments:', err);
    }
  };

  const loadSecrets = async (projId, envId) => {
    try {
      const secList = await secretApi.list(workspaceId, projId, envId);
      const list = Array.isArray(secList) ? secList : secList?.data || [];
      setSecrets(list);
    } catch (err) {
      console.error('Failed to load secrets:', err);
    }
  };

  const handleProjectChange = (e) => {
    const pId = e.target.value;
    setSelectedProjectId(pId);
    loadEnvironments(pId);
  };

  const handleEnvChange = (e) => {
    const eId = e.target.value;
    setSelectedEnvId(eId);
    loadSecrets(selectedProjectId, eId);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selectedEnvId) {
      setErrorMessage('Please select a target environment');
      return;
    }
    if (!reason || reason.trim().length < 10) {
      setErrorMessage('Please provide a detailed operational reason (at least 10 characters)');
      return;
    }

    try {
      setIsSubmitting(true);
      setErrorMessage(null);
      await jitApi.submitRequest(workspaceId, {
        projectId: selectedProjectId,
        environmentId: selectedEnvId,
        secretId: selectedSecretId || null,
        requestedPermission,
        durationMinutes: parseInt(durationMinutes, 10),
        reason: reason.trim()
      });
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setErrorMessage(err.message || 'Failed to submit JIT request');
    } finally {
      setIsSubmitting(false);
    }
  };

  const durationPresets = [15, 30, 60, 120, 240];

  if (!isOpen) return null;

  const currentEnv = environments.find(e => e.id === selectedEnvId);
  const isProtectedEnv = currentEnv && (currentEnv.isProtected || currentEnv.envType === 'PRODUCTION');

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in font-body">
      <div className="relative w-full max-w-xl rounded-2xl bg-[#1C000A] border border-[#FF2D6D]/30 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#FFB4C8]/15 bg-gradient-to-r from-[#2C0012] to-[#1C000A]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#3E0018] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
              <Clock className="w-5 h-5 animate-pulse" />
            </div>
            <div>
              <h3 className="font-headline font-bold text-base text-white flex items-center gap-2">
                Request Just-In-Time (JIT) Access
                <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-medium bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30">
                  Ephemeral Elevation
                </span>
              </h3>
              <p className="text-xs text-[#F4B5C8]/70">
                Temporary, time-bound privilege elevation subject to dual-custody approval
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
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto space-y-5">
          {errorMessage && (
            <div className="p-3.5 rounded-xl bg-[#4A0018]/80 border border-[#FF2D6D]/50 text-xs text-[#FFCCD6] flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-[#FF2D6D] shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          {isProtectedEnv && (
            <div className="p-3.5 rounded-xl bg-[#360014]/60 border border-[#FF9900]/40 text-xs text-[#FFE8C2] flex items-start gap-2.5">
              <ShieldAlert className="w-4 h-4 text-[#FF9900] shrink-0 mt-0.5" />
              <div>
                <span className="font-semibold text-white">Protected Production Enclave:</span>
                <p className="text-[11px] text-[#FFD699] mt-0.5">
                  Elevations in Production trigger mandatory anti-self-approval checks and emit high-priority audit logs.
                </p>
              </div>
            </div>
          )}

          {/* Project & Environment Selector */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5 flex items-center gap-1.5">
                <Server className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Project Scope
              </label>
              <select
                value={selectedProjectId}
                onChange={handleProjectChange}
                disabled={isLoadingMetadata}
                className="w-full h-10 px-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] transition-colors"
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5 flex items-center gap-1.5">
                <ShieldAlert className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Target Environment *
              </label>
              <select
                value={selectedEnvId}
                onChange={handleEnvChange}
                disabled={isLoadingMetadata || environments.length === 0}
                className="w-full h-10 px-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] transition-colors"
              >
                {environments.map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.name} ({e.envType})
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Specific Secret (Optional) */}
          <div>
            <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Key className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Specific Secret (Optional Scope Restrictor)
              </span>
              <span className="text-[10px] text-[#F4B5C8]/50 font-mono">
                Leave empty for environment-wide
              </span>
            </label>
            <select
              value={selectedSecretId}
              onChange={(e) => setSelectedSecretId(e.target.value)}
              disabled={isLoadingMetadata}
              className="w-full h-10 px-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] transition-colors"
            >
              <option value="">All Secrets in Environment</option>
              {secrets.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>

          {/* Requested Permission */}
          <div>
            <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5 flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-[#FF2D6D]" />
              Requested Permission Level
            </label>
            <div className="grid grid-cols-3 gap-2.5">
              {[
                { id: 'secret.reveal', label: 'Reveal Value', desc: 'Decrypt & view values' },
                { id: 'secret.update', label: 'Modify Secret', desc: 'Update/rotate secret' },
                { id: 'environment.promote', label: 'Promote', desc: 'Cross-env progression' },
              ].map((perm) => (
                <button
                  type="button"
                  key={perm.id}
                  onClick={() => setRequestedPermission(perm.id)}
                  className={`p-3 rounded-xl border text-left flex flex-col justify-between transition-all ${
                    requestedPermission === perm.id
                      ? 'bg-[#3E0018] border-[#FF2D6D] text-white shadow-lg shadow-[#FF2D6D]/15'
                      : 'bg-[#26000F] border-[#FFB4C8]/15 text-[#F4B5C8]/70 hover:border-[#FFB4C8]/30 hover:text-white'
                  }`}
                >
                  <span className="font-medium text-xs text-white">{perm.label}</span>
                  <span className="text-[10px] text-[#F4B5C8]/50 font-mono mt-1">{perm.desc}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Duration Slider & Presets */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-medium text-[#F4B5C8] flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Access Duration (TTL)
              </label>
              <span className="text-xs font-mono font-bold text-[#FF2D6D]">
                {durationMinutes} minutes ({Math.floor(durationMinutes / 60) > 0 ? `${Math.floor(durationMinutes / 60)}h ` : ''}{durationMinutes % 60 > 0 ? `${durationMinutes % 60}m` : ''})
              </span>
            </div>

            <input
              type="range"
              min="5"
              max="240"
              step="5"
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(parseInt(e.target.value, 10))}
              className="w-full accent-[#FF2D6D] bg-[#2C0012] rounded-lg h-2 cursor-pointer"
            />

            <div className="flex items-center gap-2 mt-2.5">
              {durationPresets.map((preset) => (
                <button
                  type="button"
                  key={preset}
                  onClick={() => setDurationMinutes(preset)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-mono font-medium transition-all ${
                    durationMinutes === preset
                      ? 'bg-[#FF2D6D] text-white'
                      : 'bg-[#2C0012] text-[#F4B5C8]/70 border border-[#FFB4C8]/15 hover:text-white'
                  }`}
                >
                  {preset >= 60 ? `${preset / 60}h` : `${preset}m`}
                </button>
              ))}
            </div>
          </div>

          {/* Justification Reason */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-xs font-medium text-[#F4B5C8]">
                Operational Justification / Incident Ticket *
              </label>
              <span className={`text-[10px] font-mono ${reason.length < 10 ? 'text-[#FF2D6D]' : 'text-[#F4B5C8]/50'}`}>
                {reason.length}/2000 (min 10)
              </span>
            </div>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Provide context e.g. 'Investigating INC-8492 connection timeout in production staging cluster...'"
              rows={3}
              className="w-full p-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white placeholder-[#F4B5C8]/30 focus:outline-none focus:border-[#FF2D6D] transition-colors resize-none"
            />
          </div>

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-medium text-[#F4B5C8]/80 hover:text-white hover:bg-[#2C0012] transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || reason.trim().length < 10}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#FF4D82] text-white text-xs font-medium shadow-lg shadow-[#FF2D6D]/20 hover:opacity-95 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 transition-all"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Submitting Request...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Submit JIT Request</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

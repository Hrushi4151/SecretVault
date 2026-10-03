import React, { useState, useEffect } from 'react';
import { rotationApi } from '../../api/rotation';
import { secretApi } from '../../api/secrets';
import {
  Flame,
  ShieldAlert,
  RotateCw,
  AlertTriangle,
  Key,
} from 'lucide-react';

export const CompromiseRemediationModal = ({
  workspaceId,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [secrets, setSecrets] = useState([]);
  const [selectedSecretId, setSelectedSecretId] = useState('');
  const [reason, setReason] = useState('');
  const [isExecuting, setIsExecuting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!isOpen || !workspaceId) return;
    secretApi
      .list(workspaceId, 'all', 'all', {})
      .then((res) => {
        const list = res?.data?.content || res?.content || res?.data || [];
        setSecrets(Array.isArray(list) ? list : []);
        if (list.length > 0) setSelectedSecretId(list[0].id);
      })
      .catch(() => {});
  }, [isOpen, workspaceId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!workspaceId || !selectedSecretId) return;
    setIsExecuting(true);
    setError(null);
    try {
      await rotationApi.markCompromised(workspaceId, selectedSecretId, {
        incidentDetails: reason || 'Secret marked compromised via UI incident remediation',
        rotateImmediately: true,
        revokeLeasesImmediately: true,
      });
      onSuccess && onSuccess();
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to remediate compromised secret');
    } finally {
      setIsExecuting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
      <div className="relative w-full max-w-lg rounded-2xl bg-[#1E000A] border border-red-500/50 shadow-2xl p-6 text-white space-y-4">
        <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-red-950/80 border border-red-500/40 text-red-400">
              <Flame className="w-5 h-5 animate-pulse" />
            </div>
            <div>
              <h3 className="text-base font-headline font-bold text-red-300">
                Compromised Secret Incident Remediation
              </h3>
              <p className="text-[11px] text-[#F4B5C8]/70">
                Immediate key rollover, version invalidation, and active lease revocation.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-[#F4B5C8]/50 hover:text-white text-xs font-mono cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div className="p-3.5 rounded-xl bg-red-950/40 border border-red-500/30 text-red-200 text-xs flex items-start gap-2 leading-relaxed">
          <AlertTriangle className="w-4 h-4 text-red-400 flex-shrink-0 mt-0.5" />
          <span>
            <strong>Warning:</strong> Executing this remediation invalidates current credentials immediately,
            revokes all runtime leases, generates candidate vN+1, and records a high-severity security finding.
          </span>
        </div>

        {error && (
          <div className="p-3 rounded-xl bg-red-950/60 border border-red-500/30 text-red-300 text-xs font-mono">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 text-xs">
          <div>
            <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
              Select Compromised Secret
            </label>
            <select
              value={selectedSecretId}
              onChange={(e) => setSelectedSecretId(e.target.value)}
              className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-red-500/30 text-white font-mono"
              required
            >
              {secrets.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  {sec.name || sec.key} (v{sec.versionNumber || sec.currentVersion || 1})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
              Incident Details / Audit Reason
            </label>
            <textarea
              rows={3}
              placeholder="e.g. Credential detected in public GitHub repository; CVE-2026-XXXX"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-red-500/30 text-white font-mono placeholder-[#F4B5C8]/30"
              required
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#FFB4C8]/10">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#F4B5C8] text-xs font-semibold cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isExecuting}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-red-600 to-rose-700 text-white font-bold text-xs shadow-lg shadow-red-600/30 flex items-center gap-1.5 cursor-pointer hover:opacity-90"
            >
              {isExecuting ? (
                <>
                  <RotateCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Remediating Incident...</span>
                </>
              ) : (
                <>
                  <Flame className="w-3.5 h-3.5" />
                  <span>Execute Emergency Remediation</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

import React, { useState, useEffect, useCallback } from 'react';
import { rotationApi } from '../../api/rotation';
import { secretApi } from '../../api/secrets';
import {
  Calendar,
  Clock,
  ShieldCheck,
  Plus,
  Edit2,
  Trash2,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Play,
  RotateCw,
  Search,
} from 'lucide-react';

export const RotationPoliciesView = ({
  workspaceId,
  onStartRotationForSecret,
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [secrets, setSecrets] = useState([]);
  const [policies, setPolicies] = useState({});
  const [searchQuery, setSearchQuery] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedSecret, setSelectedSecret] = useState(null);
  const [saving, setSaving] = useState(false);

  const [formData, setFormData] = useState({
    enabled: true,
    rotationIntervalDays: 30,
    minIntervalDays: 1,
    maxSecretAgeDays: 90,
    rotationWindowHours: 24,
    rotationWindowStartTime: '02:00',
    rotationWindowTimezone: 'UTC',
    strategy: 'SCHEDULED',
    rolloutStrategy: 'DUAL_CREDENTIAL_GRACE_PERIOD',
    validationType: 'CONNECTIVITY',
    gracePeriodMinutes: 60,
    maxRetries: 3,
    autoRevokeOldVersion: true,
    autoRollbackOnFailure: true,
    notifyOnSuccess: true,
  });

  const loadData = useCallback(async () => {
    if (!workspaceId) return;
    setLoading(true);
    setError(null);
    try {
      // In this environment, we load secrets and check policies
      // We can query secret metadata and fetch their rotation policies
      const secretsRes = await secretApi.list(workspaceId, 'all', 'all', {}).catch(() => ({ data: { content: [] } }));
      const list = secretsRes?.data?.content || secretsRes?.content || secretsRes?.data || [];
      setSecrets(Array.isArray(list) ? list : []);

      const policyMap = {};
      await Promise.all(
        list.slice(0, 30).map(async (sec) => {
          try {
            const pol = await rotationApi.getPolicy(workspaceId, sec.id);
            if (pol?.data) policyMap[sec.id] = pol.data;
          } catch (ignored) {}
        })
      );
      setPolicies(policyMap);
    } catch (err) {
      setError(err.message || 'Failed to load secrets and policies');
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleOpenModal = (secret, currentPolicy = null) => {
    setSelectedSecret(secret);
    if (currentPolicy) {
      setFormData({
        enabled: currentPolicy.enabled ?? true,
        rotationIntervalDays: currentPolicy.rotationIntervalDays || 30,
        minIntervalDays: currentPolicy.minIntervalDays || 1,
        maxSecretAgeDays: currentPolicy.maxSecretAgeDays || 90,
        rotationWindowHours: currentPolicy.rotationWindowHours || 24,
        rotationWindowStartTime: currentPolicy.rotationWindowStartTime || '02:00',
        rotationWindowTimezone: currentPolicy.rotationWindowTimezone || 'UTC',
        strategy: currentPolicy.strategy || 'SCHEDULED',
        rolloutStrategy: currentPolicy.rolloutStrategy || 'DUAL_CREDENTIAL_GRACE_PERIOD',
        validationType: currentPolicy.validationType || 'CONNECTIVITY',
        gracePeriodMinutes: currentPolicy.gracePeriodMinutes || 60,
        maxRetries: currentPolicy.maxRetries || 3,
        autoRevokeOldVersion: currentPolicy.autoRevokeOldVersion ?? true,
        autoRollbackOnFailure: currentPolicy.autoRollbackOnFailure ?? true,
        notifyOnSuccess: currentPolicy.notifyOnSuccess ?? true,
      });
    } else {
      setFormData({
        enabled: true,
        rotationIntervalDays: 30,
        minIntervalDays: 1,
        maxSecretAgeDays: 90,
        rotationWindowHours: 24,
        rotationWindowStartTime: '02:00',
        rotationWindowTimezone: 'UTC',
        strategy: 'SCHEDULED',
        rolloutStrategy: 'DUAL_CREDENTIAL_GRACE_PERIOD',
        validationType: 'CONNECTIVITY',
        gracePeriodMinutes: 60,
        maxRetries: 3,
        autoRevokeOldVersion: true,
        autoRollbackOnFailure: true,
        notifyOnSuccess: true,
      });
    }
    setIsModalOpen(true);
  };

  const handleSavePolicy = async (e) => {
    e.preventDefault();
    if (!selectedSecret) return;
    setSaving(true);
    setError(null);
    try {
      const existing = policies[selectedSecret.id];
      if (existing) {
        await rotationApi.updatePolicy(workspaceId, selectedSecret.id, formData);
      } else {
        await rotationApi.createPolicy(
          workspaceId,
          selectedSecret.projectId || selectedSecret.project_id || '00000000-0000-0000-0000-000000000000',
          selectedSecret.environmentId || selectedSecret.environment_id || '00000000-0000-0000-0000-000000000000',
          selectedSecret.id,
          formData
        );
      }
      setIsModalOpen(false);
      loadData();
    } catch (err) {
      setError(err.message || 'Failed to save rotation policy');
    } finally {
      setSaving(false);
    }
  };

  const handleDisablePolicy = async (secretId) => {
    if (!window.confirm('Are you sure you want to disable automatic rotation for this secret?')) return;
    try {
      await rotationApi.disablePolicy(workspaceId, secretId);
      loadData();
    } catch (err) {
      setError(err.message || 'Failed to disable policy');
    }
  };

  const filteredSecrets = secrets.filter((s) =>
    (s.name || s.key || '').toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* Header controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h3 className="text-base font-headline font-bold text-white">Automated Rotation Policies</h3>
          <p className="text-xs text-[#F4B5C8]/70">
            Define automated intervals, validation rules, zero-downtime grace periods, and rollback criteria.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#F4B5C8]/50" />
            <input
              type="text"
              placeholder="Search secrets..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 pr-4 py-2 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 text-xs text-white placeholder-[#F4B5C8]/40 focus:outline-none focus:border-[#FF2D6D]"
            />
          </div>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-950/60 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Policies Table */}
      <div className="rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-[#FFB4C8]/10 bg-[#26000B]/50 text-[#F4B5C8]/60 font-mono">
                <th className="py-3 px-4 font-medium">Secret Name</th>
                <th className="py-3 px-4 font-medium">Policy Status</th>
                <th className="py-3 px-4 font-medium">Interval</th>
                <th className="py-3 px-4 font-medium">Rollout Strategy</th>
                <th className="py-3 px-4 font-medium">Grace Period</th>
                <th className="py-3 px-4 font-medium">Next Rotation</th>
                <th className="py-3 px-4 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#FFB4C8]/5">
              {filteredSecrets.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-[#F4B5C8]/40 font-mono">
                    No secrets available in workspace.
                  </td>
                </tr>
              ) : (
                filteredSecrets.map((secret) => {
                  const policy = policies[secret.id];
                  return (
                    <tr key={secret.id} className="hover:bg-[#30000F]/40 transition-colors">
                      <td className="py-3.5 px-4 font-medium text-white flex items-center gap-2">
                        <div className="w-7 h-7 rounded-lg bg-[#FF2D6D]/10 text-[#FF2D6D] flex items-center justify-center font-mono font-bold text-xs">
                          {secret.name ? secret.name[0].toUpperCase() : 'S'}
                        </div>
                        <div>
                          <div className="text-white font-semibold">{secret.name || secret.key}</div>
                          <div className="text-[10px] font-mono text-[#F4B5C8]/50">
                            v{secret.versionNumber || secret.currentVersion || 1}
                          </div>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        {policy?.enabled ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-emerald-950/80 text-emerald-300 border border-emerald-500/30 text-[10px] font-mono">
                            <CheckCircle2 className="w-3 h-3" />
                            <span>Active</span>
                          </span>
                        ) : policy ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-neutral-900 text-neutral-400 border border-neutral-700 text-[10px] font-mono">
                            <XCircle className="w-3 h-3" />
                            <span>Disabled</span>
                          </span>
                        ) : (
                          <span className="text-[#F4B5C8]/40 text-[11px] font-mono italic">No Policy</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[#F4B5C8]">
                        {policy ? `${policy.rotationIntervalDays} days` : '—'}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-xs text-white">
                        {policy?.rolloutStrategy || '—'}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[#38BDF8]">
                        {policy ? `${policy.gracePeriodMinutes} mins` : '—'}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[#F4B5C8]/70">
                        {policy?.nextScheduledRotation
                          ? new Date(policy.nextScheduledRotation).toLocaleDateString()
                          : '—'}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {policy ? (
                            <>
                              <button
                                onClick={() => handleOpenModal(secret, policy)}
                                className="p-1.5 rounded-lg bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white transition-all cursor-pointer"
                                title="Edit Policy"
                              >
                                <Edit2 className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => handleDisablePolicy(secret.id)}
                                className="p-1.5 rounded-lg bg-red-950/40 hover:bg-red-900/60 text-red-400 transition-all cursor-pointer"
                                title="Disable Policy"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </>
                          ) : (
                            <button
                              onClick={() => handleOpenModal(secret, null)}
                              className="px-2.5 py-1 rounded-lg bg-[#FF2D6D]/20 hover:bg-[#FF2D6D]/30 border border-[#FF2D6D]/40 text-[#FF2D6D] text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
                            >
                              <Plus className="w-3 h-3" />
                              <span>Configure</span>
                            </button>
                          )}
                          <button
                            onClick={() => onStartRotationForSecret(secret.id)}
                            className="p-1.5 rounded-lg bg-sky-950/40 hover:bg-sky-900/60 text-sky-400 transition-all cursor-pointer"
                            title="Trigger Immediate Rotation"
                          >
                            <Play className="w-3.5 h-3.5" />
                          </button>
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

      {/* Policy Edit / Create Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="relative w-full max-w-lg rounded-2xl bg-[#1E000A] border border-[#FF2D6D]/30 shadow-2xl p-6 text-white space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
              <div className="flex items-center gap-2">
                <Calendar className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-sm font-headline font-bold">
                  Rotation Policy: {selectedSecret?.name || selectedSecret?.key}
                </h3>
              </div>
              <button
                onClick={() => setIsModalOpen(false)}
                className="text-[#F4B5C8]/50 hover:text-white text-xs font-mono"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSavePolicy} className="space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
                    Rotation Interval (Days)
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="365"
                    value={formData.rotationIntervalDays}
                    onChange={(e) =>
                      setFormData({ ...formData, rotationIntervalDays: parseInt(e.target.value) || 30 })
                    }
                    className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                    required
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
                    Dual Grace Period (Minutes)
                  </label>
                  <input
                    type="number"
                    min="0"
                    max="1440"
                    value={formData.gracePeriodMinutes}
                    onChange={(e) =>
                      setFormData({ ...formData, gracePeriodMinutes: parseInt(e.target.value) || 60 })
                    }
                    className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">Rollout Strategy</label>
                  <select
                    value={formData.rolloutStrategy}
                    onChange={(e) => setFormData({ ...formData, rolloutStrategy: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                  >
                    <option value="DUAL_CREDENTIAL_GRACE_PERIOD">Dual-Credential Grace Period</option>
                    <option value="IMMEDIATE">Immediate Cutover</option>
                    <option value="STAGED">Staged Rollout</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">Validation Engine</label>
                  <select
                    value={formData.validationType}
                    onChange={(e) => setFormData({ ...formData, validationType: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                  >
                    <option value="CONNECTIVITY">Connectivity Verification</option>
                    <option value="AUTHENTICATION">Auth Handshake</option>
                    <option value="PROVIDER_API">Target Provider API</option>
                    <option value="DATABASE_CONNECTION">Database Query Probe</option>
                    <option value="NONE">None</option>
                  </select>
                </div>
              </div>

              <div className="space-y-2 pt-2 border-t border-[#FFB4C8]/10">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.autoRevokeOldVersion}
                    onChange={(e) => setFormData({ ...formData, autoRevokeOldVersion: e.target.checked })}
                    className="rounded border-[#FFB4C8]/20 bg-[#26000B] text-[#FF2D6D]"
                  />
                  <span>Automatically decommission & revoke previous version after grace period</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.autoRollbackOnFailure}
                    onChange={(e) => setFormData({ ...formData, autoRollbackOnFailure: e.target.checked })}
                    className="rounded border-[#FFB4C8]/20 bg-[#26000B] text-[#FF2D6D]"
                  />
                  <span>Automatically rollback if staged validation fails</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.enabled}
                    onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                    className="rounded border-[#FFB4C8]/20 bg-[#26000B] text-[#FF2D6D]"
                  />
                  <span className="font-semibold text-emerald-400">Enable automatic schedule</span>
                </label>
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/10">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-[#26000B] text-[#F4B5C8]/80 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#E11D48] text-white font-semibold flex items-center gap-1.5 shadow-lg"
                >
                  {saving && <RotateCw className="w-3.5 h-3.5 animate-spin" />}
                  <span>Save Policy</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

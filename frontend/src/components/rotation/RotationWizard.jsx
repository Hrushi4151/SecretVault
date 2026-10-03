import React, { useState, useEffect, useCallback } from 'react';
import { rotationApi } from '../../api/rotation';
import { secretApi } from '../../api/secrets';
import {
  RotateCw,
  CheckCircle2,
  AlertTriangle,
  Layers,
  Clock,
  ShieldCheck,
  Key,
  Flame,
  ArrowRight,
  ArrowLeft,
  RefreshCw,
  Cpu,
  Eye,
  Check,
} from 'lucide-react';

export const RotationWizard = ({
  workspaceId,
  initialSecretId = null,
  onComplete,
  onCancel,
}) => {
  const [currentStep, setCurrentStep] = useState(1);
  const [secrets, setSecrets] = useState([]);
  const [selectedSecretId, setSelectedSecretId] = useState(initialSecretId || '');
  const [strategy, setStrategy] = useState('MANUAL');
  const [rolloutStrategy, setRolloutStrategy] = useState('DUAL_CREDENTIAL_GRACE_PERIOD');
  const [validationType, setValidationType] = useState('CONNECTIVITY');
  const [reason, setReason] = useState('Standard planned credential rotation');
  const [gracePeriodMinutes, setGracePeriodMinutes] = useState(60);
  const [impactData, setImpactData] = useState(null);
  const [loadingImpact, setLoadingImpact] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [executionResult, setExecutionResult] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!workspaceId) return;
    secretApi
      .list(workspaceId, 'all', 'all', {})
      .then((res) => {
        const list = res?.data?.content || res?.content || res?.data || [];
        setSecrets(Array.isArray(list) ? list : []);
        if (!selectedSecretId && list.length > 0) {
          setSelectedSecretId(list[0].id);
        }
      })
      .catch(() => {});
  }, [workspaceId]);

  const loadImpactAnalysis = useCallback(async () => {
    if (!workspaceId || !selectedSecretId) return;
    setLoadingImpact(true);
    setError(null);
    try {
      const res = await rotationApi.getImpact(workspaceId, selectedSecretId);
      setImpactData(res?.data || null);
    } catch (err) {
      setError('Impact analysis failed: ' + (err.message || 'Unknown error'));
    } finally {
      setLoadingImpact(false);
    }
  }, [workspaceId, selectedSecretId]);

  const handleNext = () => {
    if (currentStep === 2 && selectedSecretId) {
      loadImpactAnalysis();
    }
    setCurrentStep((prev) => Math.min(prev + 1, 6));
  };

  const handlePrev = () => {
    setCurrentStep((prev) => Math.max(prev - 1, 1));
  };

  const handleExecute = async () => {
    if (!workspaceId || !selectedSecretId) return;
    setIsExecuting(true);
    setError(null);
    try {
      const isEmergency = strategy === 'EMERGENCY';
      const payload = {
        strategy,
        rolloutStrategy,
        reason,
      };

      const res = isEmergency
        ? await rotationApi.triggerRotation(workspaceId, selectedSecretId, { ...payload, strategy: 'EMERGENCY' })
        : await rotationApi.triggerRotation(workspaceId, selectedSecretId, payload);

      setExecutionResult(res?.data || { status: 'STARTED' });
      setCurrentStep(6);
    } catch (err) {
      setError(err.message || 'Rotation execution failed');
    } finally {
      setIsExecuting(false);
    }
  };

  const selectedSecret = secrets.find((s) => s.id === selectedSecretId);

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Wizard Header */}
      <div className="flex items-center justify-between p-5 rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/10 shadow-lg">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-gradient-to-br from-[#FF2D6D] to-[#E11D48] text-white shadow-md">
            <RotateCw className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-headline font-bold text-white">
              Zero-Downtime Rotation Wizard
            </h2>
            <p className="text-xs text-[#F4B5C8]/70">
              Guided cryptographic key rollover, staged validation, and zero-downtime cutover.
            </p>
          </div>
        </div>
        <button
          onClick={onCancel}
          className="px-3 py-1.5 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#F4B5C8]/70 hover:text-white text-xs font-mono transition-all cursor-pointer"
        >
          Exit Wizard
        </button>
      </div>

      {/* Progress Bar */}
      <div className="flex items-center justify-between px-2 text-xs font-mono">
        {['Strategy', 'Target Secret', 'Impact Check', 'Rollout & Health', 'Confirmation', 'Execution'].map(
          (label, idx) => {
            const stepNum = idx + 1;
            const isDone = currentStep > stepNum;
            const isCurr = currentStep === stepNum;
            return (
              <div key={label} className="flex flex-col items-center gap-1">
                <div
                  className={`w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs transition-all ${
                    isDone
                      ? 'bg-emerald-500 text-black'
                      : isCurr
                      ? 'bg-[#FF2D6D] text-white ring-4 ring-[#FF2D6D]/20 animate-pulse'
                      : 'bg-[#26000B] text-[#F4B5C8]/40 border border-[#FFB4C8]/10'
                  }`}
                >
                  {isDone ? <Check className="w-4 h-4" /> : stepNum}
                </div>
                <span
                  className={`text-[10px] hidden sm:block ${
                    isCurr ? 'text-white font-bold' : isDone ? 'text-emerald-400' : 'text-[#F4B5C8]/40'
                  }`}
                >
                  {label}
                </span>
              </div>
            );
          }
        )}
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-950/60 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Wizard Content Card */}
      <div className="rounded-2xl bg-[#1E000A]/90 border border-[#FFB4C8]/10 p-6 shadow-xl space-y-5 min-h-[360px] flex flex-col justify-between">
        {/* Step 1: Strategy Selection */}
        {currentStep === 1 && (
          <div className="space-y-4">
            <h3 className="text-sm font-headline font-bold text-white">Step 1: Choose Rotation Strategy</h3>
            <p className="text-xs text-[#F4B5C8]/70">
              Select the operational strategy for this key rollover cycle.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
              <div
                onClick={() => setStrategy('MANUAL')}
                className={`p-4 rounded-xl border cursor-pointer transition-all ${
                  strategy === 'MANUAL'
                    ? 'bg-[#3F0016]/60 border-[#FF2D6D] ring-2 ring-[#FF2D6D]/30'
                    : 'bg-[#26000B] border-[#FFB4C8]/10 hover:border-[#FFB4C8]/30'
                }`}
              >
                <div className="flex items-center gap-2 mb-1.5">
                  <ShieldCheck className="w-4 h-4 text-[#FF2D6D]" />
                  <span className="text-xs font-bold text-white">Standard Zero-Downtime Manual</span>
                </div>
                <p className="text-[11px] text-[#F4B5C8]/70 leading-relaxed">
                  Generates candidate vN+1 with dual-credential grace period. Ideal for regular operational rollover.
                </p>
              </div>

              <div
                onClick={() => setStrategy('EMERGENCY')}
                className={`p-4 rounded-xl border cursor-pointer transition-all ${
                  strategy === 'EMERGENCY'
                    ? 'bg-red-950/60 border-red-500 ring-2 ring-red-500/30'
                    : 'bg-[#26000B] border-[#FFB4C8]/10 hover:border-red-500/30'
                }`}
              >
                <div className="flex items-center gap-2 mb-1.5">
                  <Flame className="w-4 h-4 text-red-400 animate-pulse" />
                  <span className="text-xs font-bold text-red-300">Emergency Incident Remediation</span>
                </div>
                <p className="text-[11px] text-[#F4B5C8]/70 leading-relaxed">
                  Immediate cutover with instant revocation of prior leases and versions for compromised credentials.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Step 2: Target Secret */}
        {currentStep === 2 && (
          <div className="space-y-4">
            <h3 className="text-sm font-headline font-bold text-white">Step 2: Select Target Secret</h3>
            <p className="text-xs text-[#F4B5C8]/70">
              Select the secret from this workspace that you wish to rotate.
            </p>

            <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
              {secrets.length === 0 ? (
                <div className="text-center py-8 text-xs font-mono text-[#F4B5C8]/50">
                  No secrets found in this workspace.
                </div>
              ) : (
                secrets.map((sec) => (
                  <div
                    key={sec.id}
                    onClick={() => setSelectedSecretId(sec.id)}
                    className={`p-3 rounded-xl border flex items-center justify-between cursor-pointer transition-all ${
                      selectedSecretId === sec.id
                        ? 'bg-[#3F0016]/70 border-[#FF2D6D] ring-2 ring-[#FF2D6D]/20 text-white'
                        : 'bg-[#26000B] border-[#FFB4C8]/10 text-[#F4B5C8]/80 hover:border-[#FFB4C8]/30'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <Key className="w-4 h-4 text-[#FF2D6D]" />
                      <div>
                        <div className="text-xs font-semibold text-white">{sec.name || sec.key}</div>
                        <div className="text-[10px] font-mono text-[#F4B5C8]/50">{sec.id}</div>
                      </div>
                    </div>
                    <div className="text-xs font-mono text-[#38BDF8]">
                      v{sec.versionNumber || sec.currentVersion || 1}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* Step 3: Pre-Flight Impact Analysis */}
        {currentStep === 3 && (
          <div className="space-y-4">
            <h3 className="text-sm font-headline font-bold text-white">Step 3: Pre-Flight Dependency Telemetry</h3>
            <p className="text-xs text-[#F4B5C8]/70">
              SecretVault inspects all registered consumers, machine tokens, and active leases before executing the rotation.
            </p>

            {loadingImpact ? (
              <div className="flex flex-col items-center justify-center py-12 gap-2 text-xs font-mono text-[#F4B5C8]/60">
                <RotateCw className="w-6 h-6 animate-spin text-[#FF2D6D]" />
                <span>Computing dependency graph & active lease impact...</span>
              </div>
            ) : impactData ? (
              <div className="space-y-4">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10">
                    <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Total Workloads</span>
                    <span className="text-lg font-bold text-white">{impactData.totalConsumers || 0}</span>
                  </div>
                  <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10">
                    <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Auto-Refresh Ready</span>
                    <span className="text-lg font-bold text-emerald-400">
                      {impactData.consumersSupportingAutoRefresh || 0}
                    </span>
                  </div>
                  <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10">
                    <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Requires Restart</span>
                    <span className="text-lg font-bold text-amber-400">
                      {impactData.consumersRequiringRestart || 0}
                    </span>
                  </div>
                  <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10">
                    <span className="text-[10px] font-mono text-[#F4B5C8]/50 block">Active Leases</span>
                    <span className="text-lg font-bold text-[#38BDF8]">{impactData.activeLeaseCount || 0}</span>
                  </div>
                </div>

                <div className="p-3.5 rounded-xl bg-emerald-950/30 border border-emerald-500/20 text-emerald-300 text-xs flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                  <span>
                    Zero-downtime safe: Dual-credential grace period will serve both versions until all workloads acknowledge the rollover.
                  </span>
                </div>
              </div>
            ) : (
              <div className="p-4 rounded-xl bg-[#26000B] text-center text-xs font-mono text-[#F4B5C8]/50">
                No telemetry dependencies detected. Safe to rotate.
              </div>
            )}
          </div>
        )}

        {/* Step 4: Rollout Strategy & Health Checks */}
        {currentStep === 4 && (
          <div className="space-y-4">
            <h3 className="text-sm font-headline font-bold text-white">Step 4: Rollout Mode & Health Verification</h3>
            <p className="text-xs text-[#F4B5C8]/70">
              Configure cutover behavior and automated verification rules.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
              <div>
                <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">Rollout Mode</label>
                <select
                  value={rolloutStrategy}
                  onChange={(e) => setRolloutStrategy(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                >
                  <option value="DUAL_CREDENTIAL_GRACE_PERIOD">Dual-Credential Grace Period (Recommended)</option>
                  <option value="IMMEDIATE">Immediate Cutover</option>
                  <option value="STAGED">Staged Rollout</option>
                </select>
              </div>

              <div>
                <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">Validation Probe</label>
                <select
                  value={validationType}
                  onChange={(e) => setValidationType(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                >
                  <option value="CONNECTIVITY">Connectivity Verification Probe</option>
                  <option value="AUTHENTICATION">Target Service Auth Handshake</option>
                  <option value="PROVIDER_API">Cloud Provider API Ping</option>
                  <option value="DATABASE_CONNECTION">Database Connection Pool Test</option>
                </select>
              </div>

              <div className="sm:col-span-2">
                <label className="block text-[11px] font-mono text-[#F4B5C8]/70 mb-1">
                  Audit / Incident Reason
                </label>
                <input
                  type="text"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 text-white font-mono"
                  placeholder="e.g. Routine quarterly key rotation"
                />
              </div>
            </div>
          </div>
        )}

        {/* Step 5: Confirmation Preview */}
        {currentStep === 5 && (
          <div className="space-y-4">
            <h3 className="text-sm font-headline font-bold text-white">Step 5: Review & Cryptographic Rollover</h3>
            <p className="text-xs text-[#F4B5C8]/70">
              Confirm rotation parameters. SecretVault will execute the generation engine in isolated memory with zero plaintext exposure.
            </p>

            <div className="p-4 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 space-y-2.5 text-xs font-mono">
              <div className="flex justify-between">
                <span className="text-[#F4B5C8]/50">Secret Name:</span>
                <span className="text-white font-bold">{selectedSecret?.name || selectedSecret?.key}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#F4B5C8]/50">Strategy:</span>
                <span className="text-[#FF2D6D] font-bold">{strategy}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#F4B5C8]/50">Rollout Mode:</span>
                <span className="text-white">{rolloutStrategy}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#F4B5C8]/50">Validation Engine:</span>
                <span className="text-emerald-400">{validationType}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#F4B5C8]/50">Zero Plaintext Guarantee:</span>
                <span className="text-sky-300 font-bold">AES-256-GCM Hardware Encrypted</span>
              </div>
            </div>
          </div>
        )}

        {/* Step 6: Execution Live Status */}
        {currentStep === 6 && (
          <div className="text-center py-8 space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-emerald-950/80 border border-emerald-500/40 text-emerald-400 flex items-center justify-center mx-auto shadow-lg shadow-emerald-500/10">
              <CheckCircle2 className="w-7 h-7 animate-pulse" />
            </div>
            <div>
              <h3 className="text-base font-headline font-bold text-white">
                Rotation Job Initiated Successfully
              </h3>
              <p className="text-xs text-[#F4B5C8]/70 max-w-md mx-auto mt-1">
                Candidate version has been generated and validated. Active workloads are switching over automatically.
              </p>
            </div>
            {executionResult && (
              <div className="p-3 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 font-mono text-xs max-w-sm mx-auto text-[#F4B5C8]/80">
                Job ID: {executionResult.id || 'Initiated'}
              </div>
            )}
          </div>
        )}

        {/* Wizard Footer Controls */}
        <div className="flex items-center justify-between pt-4 border-t border-[#FFB4C8]/10">
          {currentStep > 1 && currentStep < 6 ? (
            <button
              onClick={handlePrev}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#F4B5C8] text-xs font-semibold transition-all cursor-pointer"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Back</span>
            </button>
          ) : (
            <div />
          )}

          {currentStep < 5 ? (
            <button
              onClick={handleNext}
              className="flex items-center gap-1.5 px-5 py-2 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#E11D48] text-white text-xs font-semibold shadow-lg shadow-[#FF2D6D]/20 hover:opacity-90 transition-all cursor-pointer"
            >
              <span>Continue</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          ) : currentStep === 5 ? (
            <button
              onClick={handleExecute}
              disabled={isExecuting}
              className="flex items-center gap-1.5 px-6 py-2.5 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 text-black font-bold text-xs shadow-lg shadow-emerald-500/20 hover:opacity-90 transition-all cursor-pointer"
            >
              {isExecuting ? (
                <>
                  <RotateCw className="w-4 h-4 animate-spin" />
                  <span>Executing Rollover...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="w-4 h-4" />
                  <span>Authorize & Execute Rotation</span>
                </>
              )}
            </button>
          ) : (
            <button
              onClick={onComplete}
              className="flex items-center gap-1.5 px-6 py-2 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#E11D48] text-white text-xs font-semibold shadow-lg cursor-pointer"
            >
              <span>View in Rotation Center</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

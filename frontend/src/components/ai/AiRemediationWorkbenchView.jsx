import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { aiApi } from '../../api/ai';
import {
  Layers,
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Play,
  Check,
  X,
  ThumbsUp,
  ThumbsDown,
  RotateCw,
  Plus,
  Loader2,
  FileCode,
  Activity,
  ArrowRight,
  ExternalLink,
  ChevronRight
} from 'lucide-react';

export const AiRemediationWorkbenchView = ({ onNavigateToCopilot }) => {
  const { activeWorkspace } = useAuth();
  const [plans, setPlans] = useState([]);
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [isLoading, setIsLoading] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [isGenerateModalOpen, setIsGenerateModalOpen] = useState(false);
  const [isFeedbackModalOpen, setIsFeedbackModalOpen] = useState(false);
  const [feedbackPlanId, setFeedbackPlanId] = useState(null);
  const [feedbackHelpful, setFeedbackHelpful] = useState(true);
  const [feedbackText, setFeedbackText] = useState('');
  const [customGoal, setCustomGoal] = useState('');
  const [errorMsg, setErrorMsg] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const fetchPlans = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsLoading(true);
    try {
      const data = await aiApi.listPlans(activeWorkspace.id);
      setPlans(data || []);
      if (selectedPlan) {
        const refreshed = (data || []).find((p) => p.id === selectedPlan.id);
        if (refreshed) setSelectedPlan(refreshed);
      }
    } catch (err) {
      console.warn('Failed to load remediation plans', err);
    } finally {
      setIsLoading(false);
    }
  }, [activeWorkspace?.id, selectedPlan]);

  useEffect(() => {
    fetchPlans();
  }, [fetchPlans]);

  const handleGeneratePlan = async (e) => {
    e.preventDefault();
    if (!activeWorkspace?.id || !customGoal.trim()) return;

    setIsLoading(true);
    setErrorMsg(null);
    try {
      const newPlan = await aiApi.generatePlan(activeWorkspace.id, {
        customGoal: customGoal.trim(),
        findingId: null,
        failureLogId: null
      });
      setSuccessMsg(`Generated remediation plan: ${newPlan.title}`);
      setIsGenerateModalOpen(false);
      setCustomGoal('');
      fetchPlans();
      setSelectedPlan(newPlan);
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'Plan generation failed');
    } finally {
      setIsLoading(false);
    }
  };

  const handleApprove = async (planId) => {
    setActionLoadingId(planId);
    setErrorMsg(null);
    try {
      const updated = await aiApi.approvePlan(activeWorkspace.id, planId);
      setSuccessMsg('Plan approved by security officer. Ready for execution.');
      fetchPlans();
      if (selectedPlan?.id === planId) setSelectedPlan(updated);
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'Approval failed');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleExecute = async (planId) => {
    setActionLoadingId(planId);
    setErrorMsg(null);
    try {
      const updated = await aiApi.executePlan(activeWorkspace.id, planId);
      setSuccessMsg('Remediation plan executed through audited SecretVault core API.');
      fetchPlans();
      if (selectedPlan?.id === planId) setSelectedPlan(updated);
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'Execution failed');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleReject = async (planId) => {
    setActionLoadingId(planId);
    setErrorMsg(null);
    try {
      const updated = await aiApi.rejectPlan(activeWorkspace.id, planId);
      setSuccessMsg('Plan rejected.');
      fetchPlans();
      if (selectedPlan?.id === planId) setSelectedPlan(updated);
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'Rejection failed');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleSubmitFeedback = async (e) => {
    e.preventDefault();
    if (!feedbackPlanId) return;

    try {
      await aiApi.submitFeedback(activeWorkspace.id, feedbackPlanId, {
        helpful: feedbackHelpful,
        feedbackText: feedbackText.trim()
      });
      setSuccessMsg('Feedback recorded to refine future AI recommendations.');
      setIsFeedbackModalOpen(false);
      setFeedbackPlanId(null);
      setFeedbackText('');
      fetchPlans();
    } catch (err) {
      setErrorMsg('Failed to record feedback');
    }
  };

  const filteredPlans = plans.filter((p) => {
    if (statusFilter === 'ALL') return true;
    return p.status === statusFilter;
  });

  const getStatusBadge = (status) => {
    switch (status) {
      case 'APPROVED':
        return <span className="px-2 py-0.5 rounded-full bg-[#10B981]/20 text-[#34D399] border border-[#10B981]/30 font-mono text-[10px]">APPROVED</span>;
      case 'EXECUTED':
        return <span className="px-2 py-0.5 rounded-full bg-[#818CF8]/20 text-[#A5B4FC] border border-[#818CF8]/30 font-mono text-[10px]">EXECUTED</span>;
      case 'REJECTED':
        return <span className="px-2 py-0.5 rounded-full bg-[#EF4444]/20 text-[#FCA5A5] border border-[#EF4444]/30 font-mono text-[10px]">REJECTED</span>;
      case 'EXPIRED':
        return <span className="px-2 py-0.5 rounded-full bg-[#A26377]/20 text-[#A26377] border border-[#A26377]/30 font-mono text-[10px]">EXPIRED</span>;
      default:
        return <span className="px-2 py-0.5 rounded-full bg-[#F59E0B]/20 text-[#FCD34D] border border-[#F59E0B]/30 font-mono text-[10px]">PROPOSED</span>;
    }
  };

  const getRiskBadge = (risk) => {
    switch (risk) {
      case 'CRITICAL':
        return <span className="px-2 py-0.5 rounded bg-[#EF4444]/20 text-[#EF4444] border border-[#EF4444]/40 font-mono text-[10px] font-bold">CRITICAL RISK</span>;
      case 'HIGH':
        return <span className="px-2 py-0.5 rounded bg-[#F87171]/20 text-[#F87171] border border-[#F87171]/40 font-mono text-[10px] font-bold">HIGH RISK</span>;
      case 'MEDIUM':
        return <span className="px-2 py-0.5 rounded bg-[#F59E0B]/20 text-[#FCD34D] border border-[#F59E0B]/40 font-mono text-[10px]">MEDIUM RISK</span>;
      default:
        return <span className="px-2 py-0.5 rounded bg-[#10B981]/20 text-[#34D399] border border-[#10B981]/40 font-mono text-[10px]">LOW RISK</span>;
    }
  };

  return (
    <div className="flex flex-col gap-6 max-w-7xl mx-auto pb-12">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#FFB4C8]/15 pb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-[#818CF8]/20 border border-[#818CF8]/40 flex items-center justify-center text-[#818CF8] shadow-lg shadow-[#818CF8]/20 shrink-0">
            <Layers className="w-5 h-5" />
          </div>
          <div className="flex flex-col">
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-headline font-bold text-white tracking-tight">
                AI Remediation Workbench
              </h1>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30">
                Advisory Guardrails Enforced
              </span>
            </div>
            <p className="text-xs text-[#A26377]">
              Review, simulate blast radius, and execute AI-generated remediation plans through authoritative platform channels.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {onNavigateToCopilot && (
            <button
              type="button"
              onClick={onNavigateToCopilot}
              className="px-3.5 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] border border-[#FFB4C8]/15 text-xs font-medium transition-colors"
            >
              Back to Copilot
            </button>
          )}

          <button
            type="button"
            onClick={() => setIsGenerateModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-semibold text-xs shadow-md shadow-[#FF2D6D]/20 cursor-pointer transition-all"
          >
            <Plus className="w-4 h-4" />
            <span>Generate Plan</span>
          </button>
        </div>
      </div>

      {/* NOTICES */}
      {errorMsg && (
        <div className="p-4 rounded-2xl bg-[#EF4444]/15 border border-[#EF4444]/30 text-xs text-[#FCA5A5] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-[#EF4444]" />
            <span>{errorMsg}</span>
          </div>
          <button onClick={() => setErrorMsg(null)} className="text-[10px] uppercase font-mono font-bold hover:underline">
            Dismiss
          </button>
        </div>
      )}

      {successMsg && (
        <div className="p-4 rounded-2xl bg-[#10B981]/15 border border-[#10B981]/30 text-xs text-[#34D399] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-[#10B981]" />
            <span>{successMsg}</span>
          </div>
          <button onClick={() => setSuccessMsg(null)} className="text-[10px] uppercase font-mono font-bold hover:underline">
            Dismiss
          </button>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs">
        {['ALL', 'PROPOSED', 'APPROVED', 'EXECUTED', 'REJECTED', 'EXPIRED'].map((s) => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={`px-3 py-1.5 rounded-xl font-mono text-[11px] transition-all cursor-pointer ${
              statusFilter === s
                ? 'bg-[#30000F] text-white border border-[#FF2D6D]/50 font-bold'
                : 'text-[#A26377] hover:text-white'
            }`}
          >
            {s}
          </button>
        ))}
      </div>

      {/* Two Column Layout: Plan List (5 cols) & Plan Detail (7 cols) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Plans List Column */}
        <div className="lg:col-span-5 flex flex-col gap-3">
          {isLoading && plans.length === 0 ? (
            <div className="p-12 text-center text-[#A26377] flex flex-col items-center justify-center gap-2">
              <Loader2 className="w-6 h-6 animate-spin text-[#FF2D6D]" />
              <span className="text-xs font-mono">Loading remediation plans...</span>
            </div>
          ) : filteredPlans.length === 0 ? (
            <div className="p-12 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/10 text-center flex flex-col items-center justify-center gap-3">
              <ShieldCheck className="w-8 h-8 text-[#A26377]/40" />
              <span className="text-xs font-semibold text-white">No plans matching filter</span>
              <p className="text-[11px] text-[#A26377]">
                Click "Generate Plan" above to create an AI-suggested remediation plan.
              </p>
            </div>
          ) : (
            filteredPlans.map((plan) => {
              const isSelected = selectedPlan?.id === plan.id;
              return (
                <div
                  key={plan.id}
                  onClick={() => setSelectedPlan(plan)}
                  className={`p-4 rounded-3xl border transition-all cursor-pointer flex flex-col gap-3 ${
                    isSelected
                      ? 'bg-[#30000F] border-[#FF2D6D]/60 shadow-lg shadow-[#FF2D6D]/10'
                      : 'bg-[#1E000A] border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    {getStatusBadge(plan.status)}
                    {getRiskBadge(plan.riskLevel)}
                  </div>

                  <div className="flex flex-col gap-1">
                    <h3 className="text-sm font-headline font-bold text-white leading-snug">
                      {plan.title}
                    </h3>
                    <p className="text-xs text-[#F4B5C8]/70 line-clamp-2">
                      {plan.description}
                    </p>
                  </div>

                  <div className="flex items-center justify-between text-[10px] font-mono text-[#A26377] border-t border-[#FFB4C8]/10 pt-2">
                    <span>Confidence: {Math.round((plan.confidenceScore || 0.9) * 100)}%</span>
                    <span>{plan.steps?.length || 0} steps</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Plan Details & Inspection Panel */}
        <div className="lg:col-span-7">
          {selectedPlan ? (
            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/20 p-6 flex flex-col gap-6 shadow-2xl animate-scale-in">
              {/* Plan Header */}
              <div className="flex flex-col gap-3 border-b border-[#FFB4C8]/15 pb-5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    {getStatusBadge(selectedPlan.status)}
                    {getRiskBadge(selectedPlan.riskLevel)}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        setFeedbackPlanId(selectedPlan.id);
                        setIsFeedbackModalOpen(true);
                      }}
                      className="p-1.5 rounded-lg bg-[#30000F] text-[#A26377] hover:text-white transition-colors"
                      title="Rate Plan Feedback"
                    >
                      <ThumbsUp className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                <div className="flex flex-col gap-1">
                  <h2 className="text-base font-headline font-bold text-white">
                    {selectedPlan.title}
                  </h2>
                  <p className="text-xs text-[#F4B5C8]/80 leading-relaxed">
                    {selectedPlan.description}
                  </p>
                </div>
              </div>

              {/* Blast Radius Visualizer */}
              {selectedPlan.blastRadius && (
                <div className="rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 p-4 flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] font-mono font-bold text-[#FF85A2] uppercase flex items-center gap-1.5">
                      <Activity className="w-3.5 h-3.5" />
                      Blast Radius Assessment
                    </span>
                    <span className="text-[10px] font-mono text-[#38BDF8]">
                      Rollback: {selectedPlan.blastRadius.rollbackComplexity || 'AUTOMATED'}
                    </span>
                  </div>

                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
                    <div className="p-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 flex flex-col">
                      <span className="text-base font-headline font-bold text-white">
                        {selectedPlan.blastRadius.affectedSecretsCount || 0}
                      </span>
                      <span className="text-[9px] font-mono text-[#A26377]">Affected Secrets</span>
                    </div>

                    <div className="p-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 flex flex-col">
                      <span className="text-base font-headline font-bold text-white">
                        {selectedPlan.blastRadius.affectedServices?.length || 0}
                      </span>
                      <span className="text-[9px] font-mono text-[#A26377]">Services</span>
                    </div>

                    <div className="p-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 flex flex-col">
                      <span className="text-base font-headline font-bold text-[#34D399]">
                        {selectedPlan.blastRadius.downtimeEstimatedSeconds || 0}s
                      </span>
                      <span className="text-[9px] font-mono text-[#A26377]">Est. Downtime</span>
                    </div>

                    <div className="p-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10 flex flex-col">
                      <span className="text-base font-headline font-bold text-[#FCD34D]">
                        {selectedPlan.blastRadius.requiresStepUpMfa ? 'YES' : 'NO'}
                      </span>
                      <span className="text-[9px] font-mono text-[#A26377]">MFA Required</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Execution Steps */}
              <div className="flex flex-col gap-3">
                <span className="text-xs font-headline font-bold text-white uppercase tracking-wider">
                  Remediation Steps ({selectedPlan.steps?.length || 0})
                </span>

                <div className="flex flex-col gap-2">
                  {(selectedPlan.steps || []).map((step, idx) => (
                    <div
                      key={idx}
                      className="p-3.5 rounded-2xl bg-[#30000F]/60 border border-[#FFB4C8]/10 flex items-start gap-3"
                    >
                      <div className="w-6 h-6 rounded-full bg-[#FF2D6D]/20 text-[#FF2D6D] text-[11px] font-mono font-bold flex items-center justify-center shrink-0">
                        {step.stepNumber || idx + 1}
                      </div>

                      <div className="flex flex-col gap-1 flex-1">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-white">
                            {step.actionType}
                          </span>
                          <span className="text-[10px] font-mono text-[#38BDF8]">
                            Target: {step.targetEntity}
                          </span>
                        </div>
                        <p className="text-[11px] text-[#F4B5C8]/80">
                          {step.description}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Action Buttons & Authoritative Gateway Controls */}
              <div className="flex flex-wrap items-center justify-between gap-4 border-t border-[#FFB4C8]/15 pt-5">
                <div className="flex items-center gap-2 text-[10px] font-mono text-[#A26377]">
                  <ShieldCheck className="w-3.5 h-3.5 text-[#10B981]" />
                  <span>Execution routes via audited SecretVault core API</span>
                </div>

                <div className="flex items-center gap-2">
                  {selectedPlan.status === 'PROPOSED' && (
                    <>
                      <button
                        type="button"
                        onClick={() => handleReject(selectedPlan.id)}
                        disabled={actionLoadingId === selectedPlan.id}
                        className="px-3 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F87171] border border-[#EF4444]/30 text-xs font-medium cursor-pointer"
                      >
                        Reject
                      </button>
                      <button
                        type="button"
                        onClick={() => handleApprove(selectedPlan.id)}
                        disabled={actionLoadingId === selectedPlan.id}
                        className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#10B981] hover:bg-[#10B981]/90 text-white font-semibold text-xs shadow-md shadow-[#10B981]/20 cursor-pointer"
                      >
                        {actionLoadingId === selectedPlan.id ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <Check className="w-3.5 h-3.5" />
                        )}
                        <span>Approve Plan</span>
                      </button>
                    </>
                  )}

                  {selectedPlan.status === 'APPROVED' && (
                    <button
                      type="button"
                      onClick={() => handleExecute(selectedPlan.id)}
                      disabled={actionLoadingId === selectedPlan.id}
                      className="flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-semibold text-xs shadow-lg shadow-[#FF2D6D]/20 cursor-pointer"
                    >
                      {actionLoadingId === selectedPlan.id ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Play className="w-3.5 h-3.5" />
                      )}
                      <span>Execute Plan Authoritatively</span>
                    </button>
                  )}

                  {selectedPlan.status === 'EXECUTED' && (
                    <span className="text-xs font-mono text-[#34D399] flex items-center gap-1.5">
                      <CheckCircle2 className="w-4 h-4" />
                      Plan executed successfully
                    </span>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="rounded-3xl bg-[#1E000A]/40 border border-[#FFB4C8]/10 p-16 text-center flex flex-col items-center justify-center gap-3">
              <Layers className="w-10 h-10 text-[#A26377]/40" />
              <span className="text-sm font-semibold text-white">Select a Remediation Plan</span>
              <p className="text-xs text-[#A26377] max-w-sm">
                Choose any plan from the list to review the blast radius, step-by-step diffs, and execution controls.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* MODAL: GENERATE PLAN */}
      {isGenerateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg rounded-3xl bg-[#1E000A] border border-[#FF2D6D]/40 p-6 shadow-2xl flex flex-col gap-4 animate-scale-in">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-3">
              <h2 className="text-sm font-headline font-bold text-white">
                Generate Custom AI Remediation Plan
              </h2>
              <button
                type="button"
                onClick={() => setIsGenerateModalOpen(false)}
                className="text-[#A26377] hover:text-white text-xs font-mono"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleGeneratePlan} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-mono font-bold uppercase text-[#A26377]">
                  Target Goal / Remediation Scope
                </label>
                <textarea
                  value={customGoal}
                  onChange={(e) => setCustomGoal(e.target.value)}
                  placeholder="e.g. Rotate all expiring database credentials and reconcile Kubernetes sync jobs..."
                  rows={4}
                  required
                  className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsGenerateModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs text-[#F4B5C8]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isLoading || !customGoal.trim()}
                  className="px-5 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-40 text-white font-semibold text-xs shadow-md shadow-[#FF2D6D]/20 cursor-pointer flex items-center gap-2"
                >
                  {isLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Generate Plan</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: SUBMIT FEEDBACK */}
      {isFeedbackModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/30 p-6 shadow-2xl flex flex-col gap-4 animate-scale-in">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-3">
              <h2 className="text-sm font-headline font-bold text-white">
                Human Feedback on Recommendation
              </h2>
              <button
                type="button"
                onClick={() => setIsFeedbackModalOpen(false)}
                className="text-[#A26377] hover:text-white text-xs font-mono"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmitFeedback} className="flex flex-col gap-4">
              <div className="flex items-center gap-4">
                <button
                  type="button"
                  onClick={() => setFeedbackHelpful(true)}
                  className={`flex-1 flex items-center justify-center gap-2 p-3 rounded-xl text-xs font-semibold cursor-pointer border ${
                    feedbackHelpful
                      ? 'bg-[#10B981]/20 text-[#34D399] border-[#10B981]'
                      : 'bg-[#30000F] text-[#A26377] border-transparent'
                  }`}
                >
                  <ThumbsUp className="w-4 h-4" />
                  <span>Helpful Plan</span>
                </button>

                <button
                  type="button"
                  onClick={() => setFeedbackHelpful(false)}
                  className={`flex-1 flex items-center justify-center gap-2 p-3 rounded-xl text-xs font-semibold cursor-pointer border ${
                    !feedbackHelpful
                      ? 'bg-[#EF4444]/20 text-[#FCA5A5] border-[#EF4444]'
                      : 'bg-[#30000F] text-[#A26377] border-transparent'
                  }`}
                >
                  <ThumbsDown className="w-4 h-4" />
                  <span>Not Helpful</span>
                </button>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-mono font-bold uppercase text-[#A26377]">
                  Notes / Observations (Optional)
                </label>
                <textarea
                  value={feedbackText}
                  onChange={(e) => setFeedbackText(e.target.value)}
                  placeholder="Provide context on why this plan was accepted or adjusted..."
                  rows={3}
                  className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsFeedbackModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs text-[#F4B5C8]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-semibold text-xs shadow-md shadow-[#FF2D6D]/20 cursor-pointer"
                >
                  Submit Feedback
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default AiRemediationWorkbenchView;

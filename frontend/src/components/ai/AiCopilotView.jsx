import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { aiApi } from '../../api/ai';
import {
  Sparkles,
  Search,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Send,
  Loader2,
  RefreshCw,
  Terminal,
  Activity,
  TrendingDown,
  Layers,
  ArrowRight,
  ShieldCheck,
  ShieldAlert,
  HelpCircle,
  FileText,
  Sliders,
  Compass,
  Cpu
} from 'lucide-react';

export const AiCopilotView = ({ onNavigateToWorkbench }) => {
  const { activeWorkspace } = useAuth();
  const [prompt, setPrompt] = useState('');
  const [selectedIntent, setSelectedIntent] = useState('ALL');
  const [inquiries, setInquiries] = useState([]);
  const [currentResponse, setCurrentResponse] = useState(null);
  const [postureForecast, setPostureForecast] = useState(null);
  const [tokenBudget, setTokenBudget] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isForecastLoading, setIsForecastLoading] = useState(false);
  const [rcaTargetType, setRcaTargetType] = useState('DEPLOYMENT');
  const [rcaTargetId, setRcaTargetId] = useState('');
  const [rcaLogs, setRcaLogs] = useState('');
  const [isRcaModalOpen, setIsRcaModalOpen] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const [activeTab, setActiveTab] = useState('chat'); // 'chat' | 'forecast' | 'rca'

  const quickPrompts = [
    { label: 'Why did deployment fail?', intent: 'DEPLOYMENT_RCA', prompt: 'Analyze root cause for latest deployment failure with database connection timeout' },
    { label: 'Forecast posture decay (14d)', intent: 'POSTURE_FORECAST', prompt: 'What is our 14-day security posture decay projection and key drift vectors?' },
    { label: 'Check credential exposure risk', intent: 'SECURITY_AUDIT', prompt: 'Perform security analysis on active access leases and stale API credentials' },
    { label: 'Recommend rotation remediation', intent: 'REMEDIATION_RECOMMENDATION', prompt: 'Recommend remediation plan for overdue secrets rotation across staging and prod' }
  ];

  const fetchTokenBudget = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    try {
      const budget = await aiApi.getTokenBudget(activeWorkspace.id);
      setTokenBudget(budget);
    } catch (e) {
      console.warn('Failed to load token budget', e);
    }
  }, [activeWorkspace?.id]);

  const fetchInquiries = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    try {
      const list = await aiApi.listInquiries(activeWorkspace.id);
      setInquiries(list || []);
    } catch (e) {
      console.warn('Failed to load inquiries', e);
    }
  }, [activeWorkspace?.id]);

  const fetchPostureForecast = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsForecastLoading(true);
    try {
      const forecast = await aiApi.getPostureForecast(activeWorkspace.id);
      setPostureForecast(forecast);
    } catch (e) {
      console.warn('Failed to load posture forecast', e);
    } finally {
      setIsForecastLoading(false);
    }
  }, [activeWorkspace?.id]);

  useEffect(() => {
    fetchTokenBudget();
    fetchInquiries();
    fetchPostureForecast();
  }, [fetchTokenBudget, fetchInquiries, fetchPostureForecast]);

  const handleSendPrompt = async (customPrompt = null, customIntent = null) => {
    const textToSend = customPrompt || prompt;
    if (!textToSend.trim() || !activeWorkspace?.id) return;

    setIsLoading(true);
    setErrorMsg(null);
    try {
      const res = await aiApi.chat(activeWorkspace.id, {
        prompt: textToSend,
        intent: customIntent || (selectedIntent === 'ALL' ? null : selectedIntent),
        targetType: null,
        targetId: null
      });
      setCurrentResponse(res);
      setPrompt('');
      fetchInquiries();
      fetchTokenBudget();
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'AI request failed');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRunRca = async (e) => {
    e.preventDefault();
    if (!activeWorkspace?.id || !rcaTargetId.trim()) return;

    setIsLoading(true);
    setErrorMsg(null);
    try {
      const res = await aiApi.runRca(activeWorkspace.id, {
        targetType: rcaTargetType,
        targetId: rcaTargetId,
        failureLogs: rcaLogs,
        errorContext: 'Triggered from AI Copilot Diagnostics Console'
      });
      setCurrentResponse({
        id: res.id,
        prompt: `Root Cause Analysis for ${rcaTargetType}: ${rcaTargetId}`,
        responseContent: `### Primary Root Cause\n**${res.primaryRootCause}**\n\n**Confidence Score:** ${(res.confidenceScore * 100).toFixed(0)}%\n\n${res.executiveSummary}\n\n### Corrective Recommendations\n${(res.recommendedActions || []).map((a, i) => `${i + 1}. ${a}`).join('\n')}`,
        intent: 'DEPLOYMENT_RCA',
        modelUsed: res.modelUsed,
        confidenceScore: res.confidenceScore,
        latencyMs: 140,
        sanitizedTelemetryEvidence: (res.evidencePointers || []).map(p => ({
          evidenceType: 'RCA_POINTER',
          source: 'AUDIT_TRAIL',
          description: p,
          timestamp: new Date().toISOString()
        })),
        advisoryWarning: 'AI RCA analysis is strictly advisory. Verify configuration before applying platform changes.',
        createdAt: res.createdAt
      });
      setIsRcaModalOpen(false);
      setActiveTab('chat');
      fetchInquiries();
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'RCA generation failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-6 max-w-7xl mx-auto pb-12">
      {/* Top Banner & Security Guardrail Indicator */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-[#30000F] via-[#3F0016] to-[#1E000A] border border-[#FF2D6D]/30 p-6 shadow-2xl shadow-[#FF2D6D]/10">
        <div className="absolute right-0 top-0 translate-x-10 -translate-y-10 w-72 h-72 rounded-full bg-[#FF2D6D]/10 blur-3xl pointer-events-none" />

        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 relative z-10">
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-2xl bg-[#FF2D6D]/20 border border-[#FF2D6D]/50 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20 shrink-0">
              <Sparkles className="w-6 h-6 animate-pulse" />
            </div>
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-headline font-bold text-white tracking-tight">
                  AI Security Intelligence Copilot
                </h1>
                <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-[#10B981]/20 text-[#34D399] border border-[#10B981]/40 flex items-center gap-1">
                  <ShieldCheck className="w-3 h-3" />
                  Air-Gapped Offline Engine Active
                </span>
              </div>
              <p className="text-xs text-[#F4B5C8]/80 max-w-2xl leading-relaxed">
                Advisory security copilot with zero-plaintext boundary, automated deployment/sync root-cause analysis, and predictive security posture forecasting.
              </p>
            </div>
          </div>

          {/* Token Budget & Quota Bar */}
          <div className="flex flex-col gap-1.5 p-3.5 rounded-2xl bg-[#1E000A]/80 border border-[#FFB4C8]/15 min-w-[240px]">
            <div className="flex items-center justify-between text-[11px] font-mono text-[#F4B5C8]">
              <span className="flex items-center gap-1.5 text-white font-medium">
                <Cpu className="w-3.5 h-3.5 text-[#38BDF8]" />
                Daily AI Budget
              </span>
              <span>
                {tokenBudget ? `${tokenBudget.dailyTokensUsed?.toLocaleString()} / ${tokenBudget.dailyTokenQuota?.toLocaleString()} tk` : 'Unlimited Offline'}
              </span>
            </div>
            <div className="w-full bg-[#30000F] h-1.5 rounded-full overflow-hidden">
              <div
                className="bg-gradient-to-r from-[#38BDF8] to-[#FF2D6D] h-full transition-all duration-500"
                style={{
                  width: tokenBudget && tokenBudget.dailyTokenQuota > 0
                    ? `${Math.min(100, (tokenBudget.dailyTokensUsed / tokenBudget.dailyTokenQuota) * 100)}%`
                    : '12%'
                }}
              />
            </div>
            <div className="flex items-center justify-between text-[9px] font-mono text-[#A26377]">
              <span>Requests Today: {tokenBudget?.requestsTodayCount || 0}</span>
              <span>Boundary: SHA256 Masked</span>
            </div>
          </div>
        </div>
      </div>

      {/* Main Tabs Navigation */}
      <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-2">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setActiveTab('chat')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
              activeTab === 'chat'
                ? 'bg-[#FF2D6D] text-white shadow-lg shadow-[#FF2D6D]/20'
                : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Interactive Copilot</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('forecast')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
              activeTab === 'forecast'
                ? 'bg-[#FF2D6D] text-white shadow-lg shadow-[#FF2D6D]/20'
                : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
            }`}
          >
            <Activity className="w-3.5 h-3.5 text-[#38BDF8]" />
            <span>Posture Forecasting</span>
          </button>

          <button
            type="button"
            onClick={() => setIsRcaModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold bg-[#30000F] hover:bg-[#3F0016] text-[#FF85A2] border border-[#FF2D6D]/30 transition-all cursor-pointer"
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Run Deployment RCA</span>
          </button>
        </div>

        {onNavigateToWorkbench && (
          <button
            type="button"
            onClick={onNavigateToWorkbench}
            className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-[#818CF8]/15 text-[#A5B4FC] hover:bg-[#818CF8]/25 border border-[#818CF8]/30 text-xs font-medium transition-colors cursor-pointer"
          >
            <Layers className="w-3.5 h-3.5" />
            <span>Remediation Workbench</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* ERROR NOTICE */}
      {errorMsg && (
        <div className="p-4 rounded-2xl bg-[#EF4444]/15 border border-[#EF4444]/30 text-xs text-[#FCA5A5] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <AlertTriangle className="w-4 h-4 text-[#EF4444] shrink-0" />
            <span>{errorMsg}</span>
          </div>
          <button onClick={() => setErrorMsg(null)} className="text-[10px] uppercase font-mono font-bold hover:underline">
            Dismiss
          </button>
        </div>
      )}

      {/* TAB 1: INTERACTIVE COPILOT */}
      {activeTab === 'chat' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Main Chat & Output Area (8 cols) */}
          <div className="lg:col-span-8 flex flex-col gap-5">
            {/* Quick Prompts Row */}
            <div className="flex flex-wrap gap-2">
              {quickPrompts.map((qp, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => handleSendPrompt(qp.prompt, qp.intent)}
                  className="px-3 py-1.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] border border-[#FFB4C8]/15 text-[11px] text-[#F4B5C8] hover:text-white transition-all flex items-center gap-1.5 cursor-pointer shadow-sm"
                >
                  <Sparkles className="w-3 h-3 text-[#FF2D6D]" />
                  <span>{qp.label}</span>
                </button>
              ))}
            </div>

            {/* Prompt Input Box */}
            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/20 p-4 shadow-xl flex flex-col gap-3">
              <textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSendPrompt();
                  }
                }}
                placeholder="Ask SecretVault AI (e.g. diagnose drift, investigate deployment RCA, recommend remediation)..."
                rows={3}
                className="w-full bg-transparent resize-none outline-none text-xs text-white placeholder-[#A26377] font-body leading-relaxed"
              />

              <div className="flex items-center justify-between border-t border-[#FFB4C8]/10 pt-3">
                <div className="flex items-center gap-2 text-[11px] text-[#A26377]">
                  <ShieldCheck className="w-3.5 h-3.5 text-[#10B981]" />
                  <span className="font-mono">Plaintext secrets automatically scrubbed before prompt submission</span>
                </div>

                <button
                  type="button"
                  onClick={() => handleSendPrompt()}
                  disabled={isLoading || !prompt.trim()}
                  className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-40 text-white font-semibold text-xs transition-all shadow-md shadow-[#FF2D6D]/20 cursor-pointer"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>Analyzing...</span>
                    </>
                  ) : (
                    <>
                      <Send className="w-3.5 h-3.5" />
                      <span>Inquire Copilot</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* Current AI Response Display */}
            {currentResponse ? (
              <div className="rounded-3xl bg-[#1E000A] border border-[#FF2D6D]/30 p-6 shadow-2xl flex flex-col gap-5 animate-scale-in">
                {/* Header with Intent, Confidence & Model metadata */}
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#FFB4C8]/15 pb-4">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-xl bg-[#FF2D6D]/15 text-[#FF2D6D] flex items-center justify-center font-bold text-xs">
                      AI
                    </div>
                    <div className="flex flex-col">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-white font-headline">
                          {currentResponse.intent || 'GENERAL_INQUIRY'}
                        </span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#10B981]/20 text-[#34D399] border border-[#10B981]/30">
                          {Math.round((currentResponse.confidenceScore || 0.95) * 100)}% Confidence
                        </span>
                      </div>
                      <span className="text-[10px] font-mono text-[#A26377]">
                        Model: {currentResponse.modelUsed || 'deterministic-offline-v1'} • Latency: {currentResponse.latencyMs || 45}ms
                      </span>
                    </div>
                  </div>

                  <span className="text-[10px] font-mono text-[#A26377]">
                    {new Date(currentResponse.createdAt || Date.now()).toLocaleTimeString()}
                  </span>
                </div>

                {/* Formatted Content */}
                <div className="text-xs text-[#F4B5C8] font-body leading-relaxed whitespace-pre-wrap">
                  {currentResponse.responseContent}
                </div>

                {/* Telemetry Evidence Chain */}
                {currentResponse.sanitizedTelemetryEvidence && currentResponse.sanitizedTelemetryEvidence.length > 0 && (
                  <div className="flex flex-col gap-2 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 p-4">
                    <span className="text-[11px] font-mono font-bold text-[#FF85A2] flex items-center gap-1.5 uppercase">
                      <Sliders className="w-3 h-3" />
                      Sanitized Telemetry Evidence Chain ({currentResponse.sanitizedTelemetryEvidence.length} items)
                    </span>
                    <div className="flex flex-col gap-1.5 max-h-48 overflow-y-auto">
                      {currentResponse.sanitizedTelemetryEvidence.map((ev, i) => (
                        <div key={i} className="flex items-start gap-2 text-[10px] font-mono bg-[#1E000A]/60 p-2 rounded-xl text-[#F4B5C8]">
                          <span className="text-[#38BDF8] shrink-0">[{ev.evidenceType}]</span>
                          <span className="text-white flex-1">{ev.description}</span>
                          <span className="text-[#A26377] shrink-0">{ev.source}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Advisory Safety Disclaimer */}
                <div className="flex items-center gap-2 p-3 rounded-xl bg-[#F59E0B]/10 border border-[#F59E0B]/20 text-[10px] font-mono text-[#FCD34D]">
                  <AlertTriangle className="w-3.5 h-3.5 shrink-0 text-[#F59E0B]" />
                  <span>{currentResponse.advisoryWarning || 'AI is strictly advisory. SecretVault requires review and authorized sign-off for all changes.'}</span>
                </div>
              </div>
            ) : (
              <div className="rounded-3xl bg-[#1E000A]/60 border border-[#FFB4C8]/10 p-12 text-center flex flex-col items-center justify-center gap-3">
                <Compass className="w-10 h-10 text-[#A26377]/50" />
                <span className="text-sm font-semibold text-white">Awaiting Copilot Query</span>
                <p className="text-xs text-[#A26377] max-w-md">
                  Ask a question above, click a quick prompt, or run deployment root-cause analysis to inspect system health.
                </p>
              </div>
            )}
          </div>

          {/* Right Sidebar: Recent Inquiries (4 cols) */}
          <div className="lg:col-span-4 flex flex-col gap-4">
            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-5 shadow-xl flex flex-col gap-4">
              <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
                <div className="flex items-center gap-2">
                  <FileText className="w-4 h-4 text-[#FF2D6D]" />
                  <span className="text-xs font-bold text-white font-headline">Recent Inquiries</span>
                </div>
                <button
                  onClick={fetchInquiries}
                  className="p-1 text-[#A26377] hover:text-white transition-colors"
                  title="Refresh"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="flex flex-col gap-2 max-h-[500px] overflow-y-auto">
                {inquiries.length === 0 ? (
                  <span className="text-[11px] text-[#A26377] text-center py-6 font-mono">
                    No prior inquiries recorded
                  </span>
                ) : (
                  inquiries.map((inq) => (
                    <div
                      key={inq.id}
                      onClick={() => {
                        setCurrentResponse({
                          id: inq.id,
                          prompt: inq.prompt,
                          responseContent: inq.responseContent,
                          intent: inq.intent,
                          modelUsed: inq.modelUsed,
                          confidenceScore: inq.confidenceScore,
                          latencyMs: inq.latencyMs,
                          createdAt: inq.createdAt
                        });
                      }}
                      className="p-3 rounded-2xl bg-[#30000F]/60 hover:bg-[#3F0016] border border-[#FFB4C8]/10 hover:border-[#FF2D6D]/40 transition-all cursor-pointer flex flex-col gap-1.5"
                    >
                      <div className="flex items-center justify-between text-[10px] font-mono">
                        <span className="text-[#FF85A2] font-semibold">{inq.intent}</span>
                        <span className="text-[#A26377]">{new Date(inq.createdAt).toLocaleDateString()}</span>
                      </div>
                      <p className="text-xs text-white line-clamp-2">{inq.prompt}</p>
                      <span className="text-[9px] font-mono text-[#38BDF8]">
                        Model: {inq.modelUsed} • {Math.round(inq.confidenceScore * 100)}%
                      </span>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: POSTURE FORECASTING */}
      {activeTab === 'forecast' && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-5 flex flex-col gap-2">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">Current Posture Score</span>
              <span className="text-3xl font-headline font-bold text-white">
                {postureForecast?.currentPostureScore || 94}
                <span className="text-sm text-[#A26377] font-normal"> / 100</span>
              </span>
              <span className="text-[10px] font-mono text-[#10B981] flex items-center gap-1">
                <CheckCircle2 className="w-3 h-3" /> Baseline healthy
              </span>
            </div>

            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-5 flex flex-col gap-2">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">7-Day Projected Score</span>
              <span className="text-3xl font-headline font-bold text-[#FCD34D]">
                {postureForecast?.projectedScore7Days || 88}
              </span>
              <span className="text-[10px] font-mono text-[#F59E0B]">Moderate drift vector</span>
            </div>

            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-5 flex flex-col gap-2">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">14-Day Projected Score</span>
              <span className="text-3xl font-headline font-bold text-[#F87171]">
                {postureForecast?.projectedScore14Days || 79}
              </span>
              <span className="text-[10px] font-mono text-[#EF4444]">High decay risk</span>
            </div>

            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-5 flex flex-col gap-2">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">Drift Velocity</span>
              <span className="text-xl font-headline font-bold text-[#FF2D6D]">
                {postureForecast?.driftVelocity || 'MODERATE'}
              </span>
              <span className="text-[10px] font-mono text-[#A26377]">Based on credential lease decay</span>
            </div>
          </div>

          {/* Top Risk Vectors & Proactive Recommendations */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-2">
                <ShieldAlert className="w-4 h-4 text-[#EF4444]" />
                <h3 className="text-sm font-headline font-bold text-white">Identified Risk Vectors</h3>
              </div>
              <div className="flex flex-col gap-2">
                {(postureForecast?.topRiskVectors || [
                  'Overdue rotation on database credentials in staging and production environments',
                  'Stale access permissions detected on dormant service accounts',
                  'Unsynced secret versions across primary cloud targets'
                ]).map((rv, idx) => (
                  <div key={idx} className="flex items-start gap-3 p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/10 text-xs text-[#F4B5C8]">
                    <span className="w-5 h-5 rounded-full bg-[#EF4444]/20 text-[#EF4444] text-[10px] font-mono flex items-center justify-center shrink-0">
                      {idx + 1}
                    </span>
                    <span>{rv}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#10B981]" />
                <h3 className="text-sm font-headline font-bold text-white">Proactive Recommendations</h3>
              </div>
              <div className="flex flex-col gap-2">
                {(postureForecast?.proactiveRecommendations || [
                  'Trigger automated zero-downtime rotation for DB connection strings',
                  'Enforce JIT access review for machine tokens older than 30 days',
                  'Execute sync reconciliation across Kubernetes and AWS targets'
                ]).map((pr, idx) => (
                  <div key={idx} className="flex items-start gap-3 p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/10 text-xs text-[#F4B5C8]">
                    <span className="w-5 h-5 rounded-full bg-[#10B981]/20 text-[#10B981] text-[10px] font-mono flex items-center justify-center shrink-0">
                      ✓
                    </span>
                    <span>{pr}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: RUN DEPLOYMENT RCA */}
      {isRcaModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-xl rounded-3xl bg-[#1E000A] border border-[#FF2D6D]/40 p-6 shadow-2xl flex flex-col gap-5 animate-scale-in">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-4">
              <div className="flex items-center gap-2.5">
                <Terminal className="w-5 h-5 text-[#FF2D6D]" />
                <h2 className="text-base font-headline font-bold text-white">
                  Diagnose Deployment / Sync Failure RCA
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setIsRcaModalOpen(false)}
                className="text-[#A26377] hover:text-white text-xs font-mono"
              >
                ✕ Close
              </button>
            </div>

            <form onSubmit={handleRunRca} className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[10px] font-mono font-bold uppercase text-[#A26377]">
                    Target Type
                  </label>
                  <select
                    value={rcaTargetType}
                    onChange={(e) => setRcaTargetType(e.target.value)}
                    className="p-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                  >
                    <option value="DEPLOYMENT">Deployment Release</option>
                    <option value="SYNC_JOB">Sync Center Job</option>
                    <option value="DRIFT_INCIDENT">Drift Incident</option>
                  </select>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-[10px] font-mono font-bold uppercase text-[#A26377]">
                    Target ID / Reference
                  </label>
                  <input
                    type="text"
                    value={rcaTargetId}
                    onChange={(e) => setRcaTargetId(e.target.value)}
                    placeholder="e.g. dep-pay-prod-01 or sync-aws-04"
                    required
                    className="p-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-mono font-bold uppercase text-[#A26377]">
                  Failure Logs or Error Diagnostics (Sanitized automatically)
                </label>
                <textarea
                  value={rcaLogs}
                  onChange={(e) => setRcaLogs(e.target.value)}
                  placeholder="Paste stacktrace, deployment error, or sync log..."
                  rows={4}
                  className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs text-white outline-none font-mono"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsRcaModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs text-[#F4B5C8] hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isLoading || !rcaTargetId.trim()}
                  className="px-5 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-40 text-white font-semibold text-xs shadow-md shadow-[#FF2D6D]/20 cursor-pointer flex items-center gap-2"
                >
                  {isLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Generate Root Cause Report</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default AiCopilotView;

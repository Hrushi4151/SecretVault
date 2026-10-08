import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { aiApi } from '../../api/ai';
import { workspaceApi } from '../../api/workspaces';
import { PendingInvitationsDialog } from '../workspace/PendingInvitationsDialog';
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
  Cpu,
  PlusCircle,
  Trash2,
  MessageSquare,
  Server,
  Building2,
  FolderGit2,
  Key,
  RotateCw,
  Network,
  Lock,
  UserCheck,
  ExternalLink,
  Shield,
  Bot
} from 'lucide-react';

export const AiCopilotView = ({
  onNavigateToWorkbench,
  onNavigateToTab,
  onNavigateToSecrets,
  onNavigateToProjects
}) => {
  const { activeWorkspace, user, refreshWorkspaces, switchWorkspace } = useAuth();
  const [prompt, setPrompt] = useState('');
  const [inquiries, setInquiries] = useState([]);
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [currentResponse, setCurrentResponse] = useState(null);
  const [postureForecast, setPostureForecast] = useState(null);
  const [tokenBudget, setTokenBudget] = useState(null);
  const [providers, setProviders] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [progressStatus, setProgressStatus] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);
  const [activeTab, setActiveTab] = useState('chat'); // 'chat' | 'forecast' | 'rca'

  // Invitations modal state
  const [isInvitationsModalOpen, setIsInvitationsModalOpen] = useState(false);

  // RCA modal state
  const [rcaTargetType, setRcaTargetType] = useState('DEPLOYMENT');
  const [rcaTargetId, setRcaTargetId] = useState('');
  const [rcaLogs, setRcaLogs] = useState('');
  const [isRcaModalOpen, setIsRcaModalOpen] = useState(false);

  // Action execution state
  const [executingActionId, setExecutingActionId] = useState(null);
  const [actionSuccessMsg, setActionSuccessMsg] = useState(null);

  const quickPrompts = [
    { label: 'Do I have new invitations?', prompt: 'Do I have any new invitations?' },
    { label: 'What is wrong with my workspace?', prompt: 'What is wrong with my workspace?' },
    { label: 'What are my biggest risks?', prompt: 'What are my biggest security risks and overdue rotations?' },
    { label: 'What changed today?', prompt: 'What changed today and what happened since my last login?' },
    { label: 'Explain SecretVault encryption', prompt: 'Explain SecretVault architecture, zero-knowledge encryption, and JIT access.' }
  ];

  const fetchProviders = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    try {
      const list = await aiApi.listProviders(activeWorkspace.id);
      setProviders(Array.isArray(list) ? list : []);
    } catch (e) {
      console.warn('Failed to load AI providers', e);
    }
  }, [activeWorkspace?.id]);

  const fetchConversations = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    try {
      const list = await aiApi.listConversations(activeWorkspace.id);
      setConversations(Array.isArray(list) ? list : (list?.content || []));
    } catch (e) {
      console.warn('Failed to load conversations', e);
    }
  }, [activeWorkspace?.id]);

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
      const items = Array.isArray(list) ? list : (list?.content || []);
      setInquiries(items);
    } catch (e) {
      console.warn('Failed to load inquiries', e);
    }
  }, [activeWorkspace?.id]);

  const fetchPostureForecast = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    try {
      const forecast = await aiApi.getPostureForecast(activeWorkspace.id);
      setPostureForecast(forecast);
    } catch (e) {
      console.warn('Failed to load posture forecast', e);
    }
  }, [activeWorkspace?.id]);

  useEffect(() => {
    fetchProviders();
    fetchConversations();
    fetchTokenBudget();
    fetchInquiries();
    fetchPostureForecast();
  }, [fetchProviders, fetchConversations, fetchTokenBudget, fetchInquiries, fetchPostureForecast]);

  const handleStartNewChat = () => {
    setActiveConversationId(null);
    setCurrentResponse(null);
    setPrompt('');
    setProgressStatus(null);
  };

  const handleSelectConversation = async (convId) => {
    setActiveConversationId(convId);
    if (!activeWorkspace?.id) return;
    try {
      const conv = await aiApi.getConversation(activeWorkspace.id, convId);
      if (conv && conv.messages && conv.messages.length > 0) {
        const lastAssistant = [...conv.messages].reverse().find(m => m.role === 'ASSISTANT' || m.role === 'assistant');
        if (lastAssistant) {
          setCurrentResponse({
            id: lastAssistant.id,
            prompt: conv.title,
            responseContent: lastAssistant.content,
            intent: 'CONVERSATIONAL_COPILOT',
            modelProvider: lastAssistant.modelProvider || conv.modelProvider || 'GEMINI',
            modelUsed: lastAssistant.modelName || conv.modelName || 'gemini-3.5-flash-lite',
            confidenceScore: 0.95,
            latencyMs: 80,
            resources: [],
            actions: [],
            sanitizedTelemetryEvidence: [],
            createdAt: lastAssistant.createdAt
          });
        }
      }
    } catch (err) {
      console.warn('Failed to load conversation transcript', err);
    }
  };

  const handleDeleteConversation = async (e, convId) => {
    e.stopPropagation();
    if (!activeWorkspace?.id) return;
    try {
      await aiApi.deleteConversation(activeWorkspace.id, convId);
      if (activeConversationId === convId) {
        handleStartNewChat();
      }
      fetchConversations();
    } catch (err) {
      setErrorMsg('Failed to delete conversation');
    }
  };

  const handleSendPrompt = async (customPrompt = null) => {
    const textToSend = customPrompt || prompt;
    if (!textToSend.trim() || !activeWorkspace?.id) return;

    setIsLoading(true);
    setErrorMsg(null);
    setProgressStatus('Analyzing context and executing tools...');
    try {
      const res = await aiApi.chat(activeWorkspace.id, {
        prompt: textToSend,
        conversationId: activeConversationId,
        intent: null,
        targetType: null,
        targetId: null
      });
      setCurrentResponse(res);
      if (res.conversationId) {
        setActiveConversationId(res.conversationId);
      }
      setPrompt('');
      fetchInquiries();
      fetchConversations();
      fetchTokenBudget();
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'AI request failed');
    } finally {
      setIsLoading(false);
      setProgressStatus(null);
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
        resources: [],
        actions: [],
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

  const handleResourceClick = (resource) => {
    if (!resource) return;
    const type = (resource.resourceType || '').toUpperCase();

    if (type === 'INVITATION') {
      setIsInvitationsModalOpen(true);
      return;
    }

    if (type === 'PROJECT') {
      if (onNavigateToSecrets && resource.projectId) {
        onNavigateToSecrets(resource.projectId);
      } else if (onNavigateToProjects) {
        onNavigateToProjects();
      } else if (onNavigateToTab) {
        onNavigateToTab('projects');
      }
      return;
    }

    if (type === 'SECRET') {
      if (onNavigateToSecrets) {
        onNavigateToSecrets(resource.projectId, resource.environmentId);
      } else if (onNavigateToTab) {
        onNavigateToTab('secrets');
      }
      return;
    }

    if (type === 'FINDING' || type === 'SECURITY') {
      if (onNavigateToTab) onNavigateToTab('repo-security');
      return;
    }

    if (type === 'ROTATION') {
      if (onNavigateToTab) onNavigateToTab('rotation');
      return;
    }

    if (type === 'SYNC' || type === 'PROVIDER') {
      if (onNavigateToTab) onNavigateToTab('sync-center');
      return;
    }

    if (type === 'INCIDENT') {
      if (onNavigateToTab) onNavigateToTab('incidents');
      return;
    }

    if (type === 'ACCESS' || type === 'JIT') {
      if (onNavigateToTab) onNavigateToTab('access');
      return;
    }

    // Default fallback
    if (onNavigateToTab && resource.navigationTarget) {
      const cleanRoute = resource.navigationTarget.replace(/^\//, '').split('?')[0];
      onNavigateToTab(cleanRoute);
    }
  };

  const handleActionClick = async (action) => {
    if (!action) return;

    if (action.type === 'NAVIGATE') {
      if (action.resourceType === 'INVITATION') {
        setIsInvitationsModalOpen(true);
      } else if (action.route && onNavigateToTab) {
        const cleanRoute = action.route.replace(/^\//, '').split('?')[0];
        onNavigateToTab(cleanRoute);
      }
      return;
    }

    if (action.type === 'EXECUTE_ROTATION') {
      if (onNavigateToTab) {
        onNavigateToTab('rotation');
      }
      return;
    }

    if (action.type === 'RECONCILE_SYNC') {
      if (onNavigateToTab) {
        onNavigateToTab('sync-center');
      }
      return;
    }

    if (onNavigateToWorkbench) {
      onNavigateToWorkbench();
    }
  };

  const handleAcceptInvitationDirectly = async (invitationId) => {
    setExecutingActionId(invitationId);
    setErrorMsg(null);
    try {
      await workspaceApi.acceptInvitationById(invitationId);
      setActionSuccessMsg('Successfully accepted invitation!');
      await refreshWorkspaces();
      setTimeout(() => setActionSuccessMsg(null), 4000);
    } catch (err) {
      setErrorMsg(err.response?.data?.message || err.message || 'Failed to accept invitation');
    } finally {
      setExecutingActionId(null);
    }
  };

  const activeProvider = providers.find(p => p.isActive) || {
    name: 'GEMINI',
    model: 'gemini-3.5-flash-lite',
    type: 'CLOUD',
    available: true
  };

  return (
    <div className="flex flex-col gap-6 max-w-7xl mx-auto pb-12">
      {/* Top Banner & Active Provider Status */}
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
                  SecretVault AI Copilot
                </h1>
                <span className={`text-[10px] font-mono font-bold px-2.5 py-0.5 rounded-full border flex items-center gap-1.5 ${
                  activeProvider.name === 'GEMINI'
                    ? 'bg-[#38BDF8]/20 text-[#38BDF8] border-[#38BDF8]/40'
                    : activeProvider.name === 'OLLAMA'
                    ? 'bg-[#10B981]/20 text-[#34D399] border-[#10B981]/40'
                    : 'bg-[#F59E0B]/20 text-[#FCD34D] border-[#F59E0B]/40'
                }`}>
                  <span className="w-1.5 h-1.5 rounded-full bg-current animate-ping" />
                  {activeProvider.name === 'GEMINI' && `● Gemini · ${activeProvider.model || 'gemini-3.5-flash-lite'} (Cloud)`}
                  {activeProvider.name === 'OLLAMA' && `● Ollama · ${activeProvider.model || 'qwen3:8b'} (Local)`}
                  {activeProvider.name === 'OPENAI_COMPATIBLE' && `● OpenAI · ${activeProvider.model || 'gpt-4o-mini'}`}
                  {activeProvider.name === 'DETERMINISTIC_OFFLINE' && `● Deterministic Offline Fallback`}
                </span>
              </div>
              <p className="text-xs text-[#F4B5C8]/80 max-w-2xl leading-relaxed">
                Natural conversational assistant, live security analyst, and agentic remediation copilot with zero-plaintext firewall and server-authoritative resource navigation.
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
                {`${(tokenBudget?.dailyTokensUsed || 0).toLocaleString()} / ${(tokenBudget?.dailyTokenQuota || 100000).toLocaleString()} tk`}
              </span>
            </div>
            <div className="w-full bg-[#30000F] h-1.5 rounded-full overflow-hidden">
              <div
                className="bg-gradient-to-r from-[#38BDF8] to-[#FF2D6D] h-full transition-all duration-500"
                style={{
                  width: `${Math.min(100, ((tokenBudget?.dailyTokensUsed || 0) / Math.max(1, tokenBudget?.dailyTokenQuota || 100000)) * 100)}%`
                }}
              />
            </div>
            <div className="flex items-center justify-between text-[9px] font-mono text-[#A26377]">
              <span>Requests Today: {tokenBudget?.requestsTodayCount || 0}</span>
              <span>Firewall: Zero-Plaintext Active</span>
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

      {/* ERROR / SUCCESS NOTICES */}
      {errorMsg && (
        <div className="p-4 rounded-2xl bg-[#EF4444]/15 border border-[#EF4444]/30 text-xs text-[#FCA5A5] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <AlertTriangle className="w-4 h-4 text-[#EF4444] shrink-0" />
            <span>{errorMsg}</span>
          </div>
          <button onClick={() => setErrorMsg(null)} className="text-[10px] uppercase font-mono font-bold hover:underline cursor-pointer">
            Dismiss
          </button>
        </div>
      )}

      {actionSuccessMsg && (
        <div className="p-4 rounded-2xl bg-[#10B981]/15 border border-[#10B981]/30 text-xs text-[#34D399] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="w-4 h-4 text-[#10B981] shrink-0" />
            <span>{actionSuccessMsg}</span>
          </div>
          <button onClick={() => setActionSuccessMsg(null)} className="text-[10px] uppercase font-mono font-bold hover:underline cursor-pointer">
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
                  onClick={() => handleSendPrompt(qp.prompt)}
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
                placeholder="Ask anything (e.g., 'Do I have any new invitations?', 'What is wrong with Payments?', 'What should I fix first?')..."
                rows={3}
                className="w-full bg-transparent resize-none outline-none text-xs text-white placeholder-[#A26377] font-body leading-relaxed"
              />

              <div className="flex items-center justify-between border-t border-[#FFB4C8]/10 pt-3">
                <div className="flex items-center gap-2 text-[11px] text-[#A26377]">
                  <ShieldCheck className="w-3.5 h-3.5 text-[#10B981]" />
                  <span className="font-mono">Zero-Plaintext Isolation Enforced</span>
                </div>

                <div className="flex items-center gap-2">
                  {activeConversationId && (
                    <button
                      type="button"
                      onClick={handleStartNewChat}
                      className="px-3 py-1.5 rounded-xl bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/20 text-xs font-medium transition-all cursor-pointer"
                    >
                      New Thread
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => handleSendPrompt()}
                    disabled={isLoading || !prompt.trim()}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-40 text-white font-semibold text-xs transition-all shadow-md shadow-[#FF2D6D]/20 cursor-pointer"
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Reasoning...</span>
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
            </div>

            {/* Progress Status Bar */}
            {isLoading && progressStatus && (
              <div className="flex items-center gap-2 p-3 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/30 text-xs text-[#FF85A2] animate-pulse">
                <Loader2 className="w-4 h-4 animate-spin text-[#FF2D6D]" />
                <span className="font-mono">{progressStatus}</span>
              </div>
            )}

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
                          {currentResponse.intent || 'COPILOT_INTELLIGENCE'}
                        </span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#10B981]/20 text-[#34D399] border border-[#10B981]/30">
                          {Math.round((currentResponse.confidenceScore || 0.95) * 100)}% Confidence
                        </span>
                      </div>
                      <span className="text-[10px] font-mono text-[#A26377]">
                        Provider: {currentResponse.modelProvider || activeProvider.name} • Model: {currentResponse.modelUsed || activeProvider.model || 'gemini-3.5-flash-lite'} • Latency: {currentResponse.latencyMs || 45}ms
                      </span>
                    </div>
                  </div>

                  <span className="text-[10px] font-mono text-[#A26377]">
                    {new Date(currentResponse.createdAt || Date.now()).toLocaleTimeString()}
                  </span>
                </div>

                {/* Formatted Content */}
                <div className="text-xs text-[#F4B5C8] font-body leading-relaxed whitespace-pre-wrap">
                  {currentResponse.responseContent || currentResponse.responseText}
                </div>

                {/* INTERACTIVE RESOURCE CARDS */}
                {currentResponse.resources && currentResponse.resources.length > 0 && (
                  <div className="flex flex-col gap-3 rounded-2xl bg-[#30000F]/90 border border-[#FF2D6D]/30 p-4">
                    <span className="text-[11px] font-mono font-bold text-[#FF85A2] flex items-center gap-1.5 uppercase">
                      <Building2 className="w-3.5 h-3.5 text-[#FF2D6D]" />
                      Interactive Resources ({currentResponse.resources.length})
                    </span>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                      {currentResponse.resources.map((res, i) => {
                        const rType = (res.resourceType || '').toUpperCase();
                        const isInvitation = rType === 'INVITATION';
                        const isProject = rType === 'PROJECT';
                        const isSecret = rType === 'SECRET';
                        const isFinding = rType === 'FINDING';
                        const isRotation = rType === 'ROTATION';
                        const isSync = rType === 'SYNC';

                        return (
                          <div
                            key={i}
                            className="p-3 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 hover:border-[#FF2D6D]/40 transition-all flex flex-col justify-between gap-3 shadow-md"
                          >
                            <div className="flex items-start gap-3 min-w-0">
                              <div className="w-8 h-8 rounded-xl bg-[#4A001C] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shrink-0 mt-0.5">
                                {isInvitation && <Building2 className="w-4 h-4" />}
                                {isProject && <FolderGit2 className="w-4 h-4" />}
                                {isSecret && <Key className="w-4 h-4" />}
                                {isFinding && <ShieldAlert className="w-4 h-4 text-[#EF4444]" />}
                                {isRotation && <RotateCw className="w-4 h-4 text-[#38BDF8]" />}
                                {isSync && <RefreshCw className="w-4 h-4 text-[#818CF8]" />}
                                {!isInvitation && !isProject && !isSecret && !isFinding && !isRotation && !isSync && <Server className="w-4 h-4" />}
                              </div>

                              <div className="flex flex-col min-w-0 flex-1">
                                <div className="flex items-center justify-between gap-2">
                                  <span className="text-xs font-semibold text-white truncate">
                                    {res.displayName || res.title || res.resourceType}
                                  </span>
                                  {res.status && (
                                    <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#30000F] text-[#34D399] border border-[#10B981]/30">
                                      {res.status}
                                    </span>
                                  )}
                                </div>

                                <div className="flex flex-wrap gap-x-3 gap-y-1 text-[10px] font-mono text-[#A26377] mt-1">
                                  {res.safeMetadata && Object.entries(res.safeMetadata).map(([k, v]) => (
                                    <span key={k}>
                                      {k}: <strong className="text-[#F4B5C8]">{String(v)}</strong>
                                    </span>
                                  ))}
                                </div>
                              </div>
                            </div>

                            <div className="flex items-center justify-end gap-2 border-t border-[#FFB4C8]/10 pt-2">
                              {isInvitation && (
                                <button
                                  type="button"
                                  onClick={() => handleAcceptInvitationDirectly(res.resourceId)}
                                  disabled={executingActionId === res.resourceId}
                                  className="px-2.5 py-1 text-[11px] font-semibold rounded-lg bg-[#10B981]/20 text-[#34D399] hover:bg-[#10B981]/30 border border-[#10B981]/40 transition-all flex items-center gap-1 cursor-pointer"
                                >
                                  {executingActionId === res.resourceId ? (
                                    <Loader2 className="w-3 h-3 animate-spin" />
                                  ) : (
                                    <UserCheck className="w-3 h-3" />
                                  )}
                                  <span>Accept & Join</span>
                                </button>
                              )}

                              <button
                                type="button"
                                onClick={() => handleResourceClick(res)}
                                className="px-3 py-1 text-[11px] font-semibold rounded-lg bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white transition-all flex items-center gap-1 cursor-pointer shadow-sm"
                              >
                                <span>{isInvitation ? 'Open Invitation' : `Open ${rType}`}</span>
                                <ExternalLink className="w-3 h-3" />
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* AGENT ACTION PROPOSALS */}
                {currentResponse.actions && currentResponse.actions.length > 0 && (
                  <div className="flex flex-col gap-3 rounded-2xl bg-[#30000F] border border-[#FF85A2]/30 p-4">
                    <span className="text-[11px] font-mono font-bold text-[#FF85A2] flex items-center gap-1.5 uppercase">
                      <Bot className="w-3.5 h-3.5 text-[#FF85A2]" />
                      Agent Action Proposals ({currentResponse.actions.length})
                    </span>

                    <div className="flex flex-col gap-2">
                      {currentResponse.actions.map((act, i) => (
                        <div
                          key={i}
                          className="flex flex-col sm:flex-row sm:items-center justify-between p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 gap-3"
                        >
                          <div className="flex flex-col gap-1">
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-semibold text-white">{act.label}</span>
                              <span className={`text-[9px] font-mono px-1.5 py-0.5 rounded font-bold ${
                                act.risk === 'HIGH' || act.risk === 'CRITICAL'
                                  ? 'bg-[#EF4444]/20 text-[#EF4444] border border-[#EF4444]/30'
                                  : act.risk === 'MEDIUM'
                                  ? 'bg-[#F59E0B]/20 text-[#FCD34D] border border-[#F59E0B]/30'
                                  : 'bg-[#10B981]/20 text-[#34D399] border border-[#10B981]/30'
                              }`}>
                                {act.risk} RISK
                              </span>
                              {act.requiresStepUp && (
                                <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#818CF8]/20 text-[#A5B4FC] border border-[#818CF8]/30">
                                  Step-Up Auth Required
                                </span>
                              )}
                            </div>
                            <span className="text-[10px] text-[#F4B5C8]/80">{act.description}</span>
                          </div>

                          <button
                            type="button"
                            onClick={() => handleActionClick(act)}
                            className="px-3 py-1.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-semibold transition-all flex items-center justify-center gap-1.5 shrink-0 cursor-pointer shadow-sm"
                          >
                            <span>Execute Proposal</span>
                            <ArrowRight className="w-3 h-3" />
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

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
                <span className="text-sm font-semibold text-white">Awaiting Copilot Inquiry</span>
                <p className="text-xs text-[#A26377] max-w-md">
                  Ask any question above, click a suggested quick query, or inspect conversation history.
                </p>
              </div>
            )}
          </div>

          {/* Right Sidebar: Conversations & Recent Inquiries (4 cols) */}
          <div className="lg:col-span-4 flex flex-col gap-4">
            {/* Conversation Threads */}
            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-5 shadow-xl flex flex-col gap-3">
              <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
                <div className="flex items-center gap-2">
                  <MessageSquare className="w-4 h-4 text-[#FF2D6D]" />
                  <span className="text-xs font-bold text-white font-headline">Conversations</span>
                </div>
                <button
                  type="button"
                  onClick={handleStartNewChat}
                  className="flex items-center gap-1 text-[11px] text-[#38BDF8] hover:text-white transition-colors cursor-pointer"
                >
                  <PlusCircle className="w-3.5 h-3.5" />
                  <span>New Chat</span>
                </button>
              </div>

              <div className="flex flex-col gap-2 max-h-48 overflow-y-auto">
                {conversations.length === 0 ? (
                  <span className="text-[11px] text-[#A26377] text-center py-4 font-mono">
                    No active conversations
                  </span>
                ) : (
                  conversations.map((conv) => (
                    <div
                      key={conv.id}
                      onClick={() => handleSelectConversation(conv.id)}
                      className={`p-2.5 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-2 ${
                        activeConversationId === conv.id
                          ? 'bg-[#3F0016] border-[#FF2D6D]/60 text-white'
                          : 'bg-[#30000F]/60 hover:bg-[#3F0016] border-[#FFB4C8]/10 text-[#F4B5C8]'
                      }`}
                    >
                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-medium truncate">{conv.title || 'Conversation'}</span>
                        <span className="text-[9px] font-mono text-[#A26377]">
                          {new Date(conv.updatedAt || conv.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={(e) => handleDeleteConversation(e, conv.id)}
                        className="p-1 text-[#A26377] hover:text-[#EF4444] transition-colors"
                        title="Delete conversation"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Recent Inquiries Card */}
            <div className="rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 p-5 shadow-xl flex flex-col gap-4">
              <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-3">
                <div className="flex items-center gap-2">
                  <FileText className="w-4 h-4 text-[#FF2D6D]" />
                  <span className="text-xs font-bold text-white font-headline">Recent Inquiries</span>
                </div>
                <button
                  onClick={fetchInquiries}
                  className="p-1 text-[#A26377] hover:text-white transition-colors cursor-pointer"
                  title="Refresh"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="flex flex-col gap-2 max-h-64 overflow-y-auto">
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
                          responseContent: inq.responseContent || inq.responseText,
                          intent: inq.intent || inq.intentType,
                          modelProvider: inq.modelProvider,
                          modelUsed: inq.modelUsed || inq.modelName,
                          confidenceScore: inq.confidenceScore,
                          latencyMs: inq.latencyMs,
                          resources: [],
                          actions: [],
                          sanitizedTelemetryEvidence: [],
                          createdAt: inq.createdAt
                        });
                      }}
                      className="p-3 rounded-2xl bg-[#30000F]/60 hover:bg-[#3F0016] border border-[#FFB4C8]/10 hover:border-[#FF2D6D]/40 transition-all cursor-pointer flex flex-col gap-1.5"
                    >
                      <div className="flex items-center justify-between text-[10px] font-mono">
                        <span className="text-[#FF85A2] font-semibold">{inq.intent || inq.intentType}</span>
                        <span className="text-[#A26377]">{new Date(inq.createdAt).toLocaleDateString()}</span>
                      </div>
                      <p className="text-xs text-white line-clamp-2">{inq.prompt}</p>
                      <span className="text-[9px] font-mono text-[#38BDF8]">
                        Model: {inq.modelUsed || inq.modelName} • {Math.round((inq.confidenceScore || 0.9) * 100)}%
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
                <ShieldCheck className="w-4 h-4 text-[#10B981]" />
                <h3 className="text-sm font-headline font-bold text-white">Proactive Mitigations</h3>
              </div>
              <div className="flex flex-col gap-2">
                {(postureForecast?.proactiveMitigations || [
                  'Trigger automatic secret rotation with zero-downtime shadow verification',
                  'Run JIT access review to revoke unused elevated grants',
                  'Execute external provider synchronization to reconcile drift'
                ]).map((pm, idx) => (
                  <div key={idx} className="flex items-start gap-3 p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/10 text-xs text-[#F4B5C8]">
                    <span className="w-5 h-5 rounded-full bg-[#10B981]/20 text-[#10B981] text-[10px] font-mono flex items-center justify-center shrink-0">
                      {idx + 1}
                    </span>
                    <span>{pm}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: RUN DEPLOYMENT RCA */}
      {isRcaModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in">
          <div className="w-full max-w-lg rounded-3xl bg-[#1E000A] border border-[#FF2D6D]/40 p-6 shadow-2xl flex flex-col gap-5">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-4">
              <div className="flex items-center gap-2.5">
                <Terminal className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-headline font-bold text-white">Run AI Root-Cause Analysis</h3>
              </div>
              <button
                onClick={() => setIsRcaModalOpen(false)}
                className="text-[#A26377] hover:text-white text-xs font-mono cursor-pointer"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleRunRca} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-[11px] font-mono text-[#F4B5C8]">Target Resource Type</label>
                <select
                  value={rcaTargetType}
                  onChange={(e) => setRcaTargetType(e.target.value)}
                  className="w-full bg-[#30000F] border border-[#FFB4C8]/20 rounded-xl px-3 py-2 text-xs text-white outline-none"
                >
                  <option value="DEPLOYMENT">DEPLOYMENT (e.g. Kubernetes Pod, Service Worker)</option>
                  <option value="SYNC_JOB">SYNC_JOB (e.g. Cloud Provider Synchronization)</option>
                  <option value="SECRET_ROTATION">SECRET_ROTATION (e.g. Database Credential Rollover)</option>
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[11px] font-mono text-[#F4B5C8]">Target Identifier</label>
                <input
                  type="text"
                  value={rcaTargetId}
                  onChange={(e) => setRcaTargetId(e.target.value)}
                  placeholder="e.g. dep-payments-worker-01 or sync-job-aws-prod"
                  required
                  className="w-full bg-[#30000F] border border-[#FFB4C8]/20 rounded-xl px-3 py-2 text-xs text-white outline-none font-mono"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[11px] font-mono text-[#F4B5C8]">Failure Logs & Diagnostic Traces</label>
                <textarea
                  value={rcaLogs}
                  onChange={(e) => setRcaLogs(e.target.value)}
                  placeholder="Paste error logs, stacktraces, or container exit codes (secrets are automatically scrubbed)..."
                  rows={4}
                  className="w-full bg-[#30000F] border border-[#FFB4C8]/20 rounded-xl p-3 text-xs text-white outline-none font-mono resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsRcaModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs text-[#F4B5C8] hover:bg-[#30000F] transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isLoading || !rcaTargetId.trim()}
                  className="flex items-center gap-2 px-5 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-40 text-white font-semibold text-xs transition-all shadow-lg shadow-[#FF2D6D]/20 cursor-pointer"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>Synthesizing RCA...</span>
                    </>
                  ) : (
                    <>
                      <Terminal className="w-3.5 h-3.5" />
                      <span>Generate RCA Report</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* PENDING INVITATIONS MODAL */}
      <PendingInvitationsDialog
        isOpen={isInvitationsModalOpen}
        onClose={() => setIsInvitationsModalOpen(false)}
        onInvitationAccepted={async (inv) => {
          await refreshWorkspaces();
          if (inv?.workspaceId) {
            switchWorkspace(inv.workspaceId);
          }
        }}
      />
    </div>
  );
};

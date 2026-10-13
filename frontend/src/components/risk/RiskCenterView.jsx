import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  ShieldAlert,
  ShieldCheck,
  Shield,
  AlertTriangle,
  RefreshCw,
  Search,
  Filter,
  Layers,
  FolderGit2,
  FileCode,
  Key,
  ExternalLink,
  CheckCircle2,
  XCircle,
  Clock,
  UserCheck,
  ChevronRight,
  Zap,
  Eye,
  Check,
  Copy,
  CheckCheck,
  X,
  FileText,
  Terminal,
  Activity,
  ArrowRight,
  TrendingDown,
  TrendingUp,
  Cpu
} from 'lucide-react';
import { securityApi } from '../../api/security';
import { projectsApi } from '../../api/projects';
import { repositoriesApi } from '../../api/repositories';
import { useAuth } from '../../context/AuthContext';
import { apiClient } from '../../api/client';

export const RiskCenterView = ({ onNavigateToAudit, onNavigateToSecurityOverview }) => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id || apiClient.getActiveWorkspaceId();

  // Core State
  const [loading, setLoading] = useState(true);
  const [isScanning, setIsScanning] = useState(false);
  const [error, setError] = useState(null);

  // Risk & Findings State
  const [riskData, setRiskData] = useState({ score: 0, level: 'LOW', factors: [] });
  const [findingsPage, setFindingsPage] = useState({ content: [], totalPages: 1, totalElements: 0, number: 0 });
  const [allFindingsSummary, setAllFindingsSummary] = useState([]);
  const [projects, setProjects] = useState([]);
  const [repositories, setRepositories] = useState([]);

  // Filters & Pagination
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [currentPage, setCurrentPage] = useState(0);

  // Modal & Action State
  const [inspectFinding, setInspectFinding] = useState(null);
  const [resolveModalFinding, setResolveModalFinding] = useState(null);
  const [resolutionReason, setResolutionReason] = useState('');
  const [assignModalFinding, setAssignModalFinding] = useState(null);
  const [assigneeId, setAssigneeId] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState(null);
  const [copiedKey, setCopiedKey] = useState(null);

  const fetchRiskAndFindings = useCallback(async () => {
    if (!workspaceId) {
      setLoading(false);
      return;
    }

    try {
      setError(null);

      // Fetch risk assessment breakdown
      const riskRes = await securityApi.getRiskBreakdown(workspaceId);
      if (riskRes) {
        setRiskData({
          score: riskRes.score ?? 0,
          level: riskRes.level || 'LOW',
          factors: Array.isArray(riskRes.factors) ? riskRes.factors : []
        });
      }

      // Fetch paginated findings with current filters
      const findingsParams = {
        page: currentPage,
        size: 10,
        sort: 'lastObservedAt,desc'
      };
      if (selectedSeverity !== 'ALL') findingsParams.severity = selectedSeverity;
      if (selectedStatus !== 'ALL') findingsParams.status = selectedStatus;
      if (selectedCategory !== 'ALL') findingsParams.category = selectedCategory;
      if (searchQuery.trim()) findingsParams.search = searchQuery.trim();

      const findingsRes = await securityApi.getFindings(workspaceId, findingsParams);
      if (findingsRes) {
        setFindingsPage({
          content: Array.isArray(findingsRes.content) ? findingsRes.content : (Array.isArray(findingsRes) ? findingsRes : []),
          totalPages: findingsRes.totalPages || 1,
          totalElements: findingsRes.totalElements !== undefined ? findingsRes.totalElements : (findingsRes.content ? findingsRes.content.length : 0),
          number: findingsRes.page !== undefined ? findingsRes.page : (findingsRes.number || 0)
        });
      }

      // Fetch all findings once for comprehensive distribution metrics
      const allFindingsRes = await securityApi.getFindings(workspaceId, { size: 100 });
      if (allFindingsRes && allFindingsRes.content) {
        setAllFindingsSummary(allFindingsRes.content);
      }

      // Metadata context (projects & repos)
      try {
        const [projList, repoList] = await Promise.all([
          (projectsApi?.getProjects ? projectsApi.getProjects(workspaceId) : Promise.resolve([])).catch(() => []),
          (repositoriesApi?.getRepositories ? repositoriesApi.getRepositories(workspaceId) : Promise.resolve([])).catch(() => [])
        ]);
        setProjects(Array.isArray(projList) ? projList : (projList?.data || []));
        setRepositories(Array.isArray(repoList) ? repoList : (repoList?.data || []));
      } catch (err) {
        console.warn('Metadata hydration warning:', err);
      }

    } catch (err) {
      console.error('Failed to load risk center data:', err);
      setError(err?.message || 'Unable to retrieve workspace risk assessment from server.');
    } finally {
      setLoading(false);
    }
  }, [workspaceId, currentPage, selectedSeverity, selectedStatus, selectedCategory, searchQuery]);

  useEffect(() => {
    fetchRiskAndFindings();
  }, [fetchRiskAndFindings]);

  // Trigger On-Demand Scan
  const handleTriggerScan = async () => {
    if (!workspaceId || isScanning) return;
    setIsScanning(true);
    setFeedbackMessage(null);
    try {
      await securityApi.triggerAnalysis(workspaceId);
      setFeedbackMessage({ type: 'success', text: 'On-demand security & risk audit completed successfully.' });
      await fetchRiskAndFindings();
    } catch (err) {
      setFeedbackMessage({ type: 'error', text: err?.message || 'Audit execution failed.' });
    } finally {
      setIsScanning(false);
    }
  };

  // State Transitions
  const handleUpdateStatus = async (findingId, newStatus, reason = '') => {
    if (!workspaceId) return;
    setActionLoading(true);
    try {
      await securityApi.updateFindingStatus(workspaceId, findingId, newStatus, reason);
      setFeedbackMessage({ type: 'success', text: `Finding transitioned to ${newStatus}` });
      setResolveModalFinding(null);
      setResolutionReason('');
      if (inspectFinding && inspectFinding.id === findingId) {
        setInspectFinding(prev => ({ ...prev, status: newStatus }));
      }
      await fetchRiskAndFindings();
    } catch (err) {
      setFeedbackMessage({ type: 'error', text: err?.message || `Failed to transition finding to ${newStatus}` });
    } finally {
      setActionLoading(false);
    }
  };

  // Assign Finding
  const handleAssignFinding = async () => {
    if (!workspaceId || !assignModalFinding) return;
    setActionLoading(true);
    try {
      await securityApi.assignFinding(workspaceId, assignModalFinding.id, assigneeId || null);
      setFeedbackMessage({ type: 'success', text: assigneeId ? 'Finding assigned successfully' : 'Assignment cleared' });
      setAssignModalFinding(null);
      setAssigneeId('');
      await fetchRiskAndFindings();
    } catch (err) {
      setFeedbackMessage({ type: 'error', text: err?.message || 'Failed to assign finding' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleCopy = (text, key) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  // Parse location details
  const parseLocation = (finding) => {
    let repoName = 'Repository Codebase';
    let filePath = 'source/codebase/config.yaml';
    let lineNumber = '1';
    let secretType = 'CREDENTIAL';
    let maskedValue = '••••••••••••';

    if (finding?.projectId) {
      const p = projects.find(proj => proj.id === finding.projectId);
      if (p) repoName = p.name;
    }

    if (finding?.evidence) {
      if (finding.evidence.repositoryName) repoName = finding.evidence.repositoryName;
      if (finding.evidence.filePath) filePath = finding.evidence.filePath;
      if (finding.evidence.lineNumber) lineNumber = String(finding.evidence.lineNumber);
      if (finding.evidence.secretType) secretType = finding.evidence.secretType;
      if (finding.evidence.maskedValue) maskedValue = finding.evidence.maskedValue;
    }

    if (finding?.title && finding.title.includes(' in ')) {
      const parts = finding.title.split(' in ');
      if (parts[1]) filePath = parts[1].trim();
    }

    return { repoName, filePath, lineNumber, secretType, maskedValue };
  };

  // Severity & Status Metrics
  const severityCounts = useMemo(() => {
    const list = allFindingsSummary.length > 0 ? allFindingsSummary : findingsPage.content;
    return {
      CRITICAL: list.filter(f => f.severity === 'CRITICAL' && (f.status === 'OPEN' || f.status === 'ACKNOWLEDGED' || f.status === 'IN_PROGRESS')).length,
      HIGH: list.filter(f => f.severity === 'HIGH' && (f.status === 'OPEN' || f.status === 'ACKNOWLEDGED' || f.status === 'IN_PROGRESS')).length,
      MEDIUM: list.filter(f => f.severity === 'MEDIUM' && (f.status === 'OPEN' || f.status === 'ACKNOWLEDGED' || f.status === 'IN_PROGRESS')).length,
      LOW: list.filter(f => f.severity === 'LOW' && (f.status === 'OPEN' || f.status === 'ACKNOWLEDGED' || f.status === 'IN_PROGRESS')).length
    };
  }, [allFindingsSummary, findingsPage.content]);

  const statusCounts = useMemo(() => {
    const list = allFindingsSummary.length > 0 ? allFindingsSummary : findingsPage.content;
    return {
      OPEN: list.filter(f => f.status === 'OPEN').length,
      ACKNOWLEDGED: list.filter(f => f.status === 'ACKNOWLEDGED').length,
      IN_PROGRESS: list.filter(f => f.status === 'IN_PROGRESS').length,
      RESOLVED: list.filter(f => f.status === 'RESOLVED').length,
      FALSE_POSITIVE: list.filter(f => f.status === 'FALSE_POSITIVE').length
    };
  }, [allFindingsSummary, findingsPage.content]);

  // Category Distribution
  const categoryCounts = useMemo(() => {
    const list = allFindingsSummary.length > 0 ? allFindingsSummary : findingsPage.content;
    const map = {};
    list.forEach(f => {
      const cat = f.category || 'GENERAL_HYGIENE';
      map[cat] = (map[cat] || 0) + 1;
    });
    return Object.entries(map).sort((a, b) => b[1] - a[1]);
  }, [allFindingsSummary, findingsPage.content]);

  if (loading && !findingsPage.content.length) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-12 text-[#FFD9E2]">
        <RefreshCw className="w-8 h-8 animate-spin text-[#FF2D6D] mb-4" />
        <h3 className="text-lg font-headline font-bold">Computing Deterministic Risk Matrix...</h3>
        <p className="text-xs font-mono text-[#A26377] mt-1">Aggregating weighted risk factors and active security findings</p>
      </div>
    );
  }

  const score = riskData.score ?? 0;
  const level = riskData.level || 'LOW';

  const riskBadgeColor =
    level === 'CRITICAL' ? 'bg-[#EF4444]/20 text-[#EF4444] border-[#EF4444]/30' :
    level === 'HIGH' ? 'bg-[#FF85A2]/20 text-[#FF85A2] border-[#FF85A2]/30' :
    level === 'ELEVATED' || level === 'MODERATE' ? 'bg-[#F59E0B]/20 text-[#F59E0B] border-[#F59E0B]/30' :
    'bg-[#34D399]/20 text-[#34D399] border-[#34D399]/30';

  return (
    <div className="flex-1 bg-[#140005] text-[#FFD9E2] min-h-screen p-4 sm:p-8 flex flex-col gap-8 max-w-7xl mx-auto w-full">
      {/* Header Banner */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 border-b border-[#FFB4C8]/15 pb-6">
        <div>
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold uppercase tracking-wider bg-[#47001A] text-[#FFB4C8] border border-[#FF2D6D]/20">
              Risk Engine v2.4
            </span>
            <span className="text-[#A26377] font-mono text-xs">•</span>
            <span className="flex items-center gap-1.5 text-xs font-mono text-[#34D399]">
              <span className="w-2 h-2 rounded-full bg-[#34D399] animate-pulse" />
              AUTHORITATIVE RISK COMPUTATION
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-headline font-bold text-white tracking-tight">
            Risk Center &amp; Vulnerability Governance
          </h1>
          <p className="text-xs sm:text-sm text-[#A26377] font-mono mt-0.5">
            Real-time weighted risk scoring, lifecycle posture control, and cross-tier remediation tracking.
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-wrap">
          {onNavigateToSecurityOverview && (
            <button
              onClick={onNavigateToSecurityOverview}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#FFB4C8] text-xs font-mono border border-[#FFB4C8]/20 transition-all cursor-pointer"
            >
              <Shield className="w-4 h-4 text-[#38BDF8]" />
              <span>Security Overview</span>
            </button>
          )}

          {onNavigateToAudit && (
            <button
              onClick={onNavigateToAudit}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[#26000B] hover:bg-[#30000F] text-[#FFB4C8] text-xs font-mono border border-[#FFB4C8]/20 transition-all cursor-pointer"
            >
              <FileText className="w-4 h-4 text-[#FF85A2]" />
              <span>Audit Trail</span>
            </button>
          )}

          <button
            onClick={fetchRiskAndFindings}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-white text-xs font-mono border border-[#FFB4C8]/15 transition-all cursor-pointer"
            title="Refresh live risk data"
          >
            <RefreshCw className="w-4 h-4 text-[#FF85A2]" />
            <span>Sync</span>
          </button>

          <button
            onClick={handleTriggerScan}
            disabled={isScanning}
            className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-white text-xs font-mono font-bold transition-all shadow-lg shadow-[#FF2D6D]/20 cursor-pointer"
          >
            {isScanning ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Zap className="w-4 h-4" />}
            <span>Run Deep Audit</span>
          </button>
        </div>
      </div>

      {/* User Feedback Alert */}
      {feedbackMessage && (
        <div className={`p-4 rounded-2xl flex items-center justify-between border ${
          feedbackMessage.type === 'error'
            ? 'bg-[#EF4444]/15 border-[#EF4444]/30 text-[#EF4444]'
            : 'bg-[#34D399]/15 border-[#34D399]/30 text-[#34D399]'
        }`}>
          <div className="flex items-center gap-2 text-xs font-mono font-bold">
            {feedbackMessage.type === 'error' ? <AlertTriangle className="w-4 h-4" /> : <CheckCircle2 className="w-4 h-4" />}
            <span>{feedbackMessage.text}</span>
          </div>
          <button onClick={() => setFeedbackMessage(null)} className="p-1 hover:opacity-80">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Top Matrix: Risk Scorecard & Factor Explanation */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Left: Authoritative Score Card (lg:col-span-5) */}
        <div className="lg:col-span-5 rounded-3xl bg-[#26000B] border border-[#FFB4C8]/15 p-6 flex flex-col justify-between shadow-xl">
          <div className="flex items-start justify-between">
            <div>
              <span className="text-xs font-mono uppercase tracking-wider text-[#A26377]">
                Authoritative Risk Score
              </span>
              <div className="flex items-baseline gap-2 mt-1">
                <span className="text-5xl font-extrabold tracking-tighter text-white font-mono">
                  {score}
                </span>
                <span className="text-xl font-mono text-[#A26377]">/ 100</span>
              </div>
            </div>

            <span className={`px-3 py-1 rounded-xl text-xs font-mono font-bold uppercase tracking-wider border ${riskBadgeColor}`}>
              {level} RISK
            </span>
          </div>

          {/* Severity Breakdown Strips */}
          <div className="grid grid-cols-4 gap-2 my-6">
            <div className="p-3 rounded-2xl bg-[#30000F] border border-[#EF4444]/20 flex flex-col items-center">
              <span className="text-xs font-mono text-[#EF4444] font-bold">CRIT</span>
              <span className="text-xl font-bold font-mono text-white mt-0.5">{severityCounts.CRITICAL}</span>
            </div>
            <div className="p-3 rounded-2xl bg-[#30000F] border border-[#FF85A2]/20 flex flex-col items-center">
              <span className="text-xs font-mono text-[#FF85A2] font-bold">HIGH</span>
              <span className="text-xl font-bold font-mono text-white mt-0.5">{severityCounts.HIGH}</span>
            </div>
            <div className="p-3 rounded-2xl bg-[#30000F] border border-[#F59E0B]/20 flex flex-col items-center">
              <span className="text-xs font-mono text-[#F59E0B] font-bold">MED</span>
              <span className="text-xl font-bold font-mono text-white mt-0.5">{severityCounts.MEDIUM}</span>
            </div>
            <div className="p-3 rounded-2xl bg-[#30000F] border border-[#34D399]/20 flex flex-col items-center">
              <span className="text-xs font-mono text-[#34D399] font-bold">LOW</span>
              <span className="text-xl font-bold font-mono text-white mt-0.5">{severityCounts.LOW}</span>
            </div>
          </div>

          {/* Lifecycle Status Row */}
          <div className="flex items-center justify-between text-[11px] font-mono text-[#FFB4C8] pt-4 border-t border-[#FFB4C8]/10 flex-wrap gap-2">
            <span>Open: <strong className="text-white">{statusCounts.OPEN}</strong></span>
            <span>Ack: <strong className="text-[#FF85A2]">{statusCounts.ACKNOWLEDGED}</strong></span>
            <span>In Prog: <strong className="text-[#38BDF8]">{statusCounts.IN_PROGRESS}</strong></span>
            <span>Resolved: <strong className="text-[#34D399]">{statusCounts.RESOLVED}</strong></span>
          </div>
        </div>

        {/* Right: Weighted Contributing Factors Panel (lg:col-span-7) */}
        <div className="lg:col-span-7 rounded-3xl bg-[#26000B] border border-[#FFB4C8]/15 p-6 flex flex-col justify-between shadow-xl">
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-[#FFB4C8]/10">
              <div className="flex items-center gap-2">
                <Cpu className="w-4 h-4 text-[#FF2D6D]" />
                <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
                  Contributing Risk Factors &amp; Weight Analysis
                </h3>
              </div>
              <span className="text-[11px] font-mono text-[#A26377]">
                {riskData.factors.length} Active Engine Factor(s)
              </span>
            </div>

            {/* Factors List */}
            <div className="mt-4 space-y-3 max-h-[220px] overflow-y-auto pr-1 custom-scrollbar">
              {riskData.factors.length === 0 ? (
                <div className="p-6 rounded-2xl bg-[#30000F]/60 border border-[#34D399]/20 flex items-center gap-3 text-[#34D399]">
                  <ShieldCheck className="w-6 h-6 shrink-0" />
                  <div>
                    <h4 className="text-xs font-bold font-mono">Baseline Risk Hygiene Maintained</h4>
                    <p className="text-[11px] font-mono text-[#A26377] mt-0.5">
                      No active critical/high severity findings, overdue access reviews, or abnormal authorization anomalies detected.
                    </p>
                  </div>
                </div>
              ) : (
                riskData.factors.map(factor => (
                  <div
                    key={factor.factorId || factor.title}
                    className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/10 flex items-start justify-between gap-3"
                  >
                    <div className="flex flex-col gap-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-white font-mono">{factor.title}</span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#47001A] text-[#FF85A2] font-bold">
                          +{factor.weight} pts
                        </span>
                      </div>
                      <p className="text-[11px] font-mono text-[#A26377] leading-relaxed">
                        {factor.explanation}
                      </p>
                    </div>

                    <span className="px-2 py-1 rounded-xl bg-[#26000B] text-[10px] font-mono text-[#38BDF8] shrink-0 border border-[#38BDF8]/20">
                      Weighted
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>

          <div className="pt-3 border-t border-[#FFB4C8]/10 flex items-center justify-between text-[11px] font-mono text-[#A26377]">
            <span>Deterministic Score Engine</span>
            <span>Formula: min(100, Σ Critical(25) + High(15) + Med(8) + Reviews(15) + Denials(10))</span>
          </div>
        </div>
      </div>

      {/* Main Finding Inventory Section */}
      <div className="rounded-3xl bg-[#26000B] border border-[#FFB4C8]/15 p-6 shadow-xl flex flex-col gap-6">
        {/* Section Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#FFB4C8]/10 pb-4">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-[#FF2D6D]" />
            <div>
              <h2 className="text-base font-bold text-white uppercase tracking-wider font-mono">
                Risk Findings Inventory &amp; Lifecycle Workbench
              </h2>
              <span className="text-xs font-mono text-[#A26377]">
                {findingsPage.totalElements} Total Findings Matching Criteria
              </span>
            </div>
          </div>

          {/* Quick Filters */}
          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative">
              <Search className="w-3.5 h-3.5 text-[#A26377] absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search findings, files, repos..."
                value={searchQuery}
                onChange={e => {
                  setSearchQuery(e.target.value);
                  setCurrentPage(0);
                }}
                className="pl-8 pr-3 py-1.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-xs font-mono text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] w-52 sm:w-64"
              />
            </div>
          </div>
        </div>

        {/* Severity & Status Filter Bars */}
        <div className="flex flex-wrap items-center justify-between gap-3 text-xs font-mono">
          {/* Severity Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
            <span className="text-[11px] text-[#A26377] mr-1 uppercase font-bold">Severity:</span>
            {['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(sev => (
              <button
                key={sev}
                onClick={() => {
                  setSelectedSeverity(sev);
                  setCurrentPage(0);
                }}
                className={`px-3 py-1 rounded-xl transition-all cursor-pointer font-bold ${
                  selectedSeverity === sev
                    ? 'bg-[#FF2D6D] text-white'
                    : 'bg-[#30000F] text-[#FFB4C8] hover:bg-[#47001A]'
                }`}
              >
                {sev}
              </button>
            ))}
          </div>

          {/* Status Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
            <span className="text-[11px] text-[#A26377] mr-1 uppercase font-bold">Status:</span>
            {['ALL', 'OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'FALSE_POSITIVE'].map(st => (
              <button
                key={st}
                onClick={() => {
                  setSelectedStatus(st);
                  setCurrentPage(0);
                }}
                className={`px-2.5 py-1 rounded-xl transition-all cursor-pointer text-[11px] ${
                  selectedStatus === st
                    ? 'bg-[#38BDF8] text-black font-bold'
                    : 'bg-[#30000F] text-[#FFB4C8] hover:bg-[#47001A]'
                }`}
              >
                {st.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>

        {/* Finding Cards List with Scrollbar */}
        <div className="space-y-3.5 max-h-[680px] overflow-y-auto pr-2 [scrollbar-width:thin] [scrollbar-color:#47001A_transparent]">
          {findingsPage.content.length === 0 ? (
            <div className="p-12 rounded-2xl bg-[#30000F]/40 border border-[#FFB4C8]/10 text-center flex flex-col items-center">
              <ShieldCheck className="w-10 h-10 text-[#34D399] mb-3" />
              <h3 className="text-sm font-bold text-white font-mono">No Findings Matching Filter</h3>
              <p className="text-xs font-mono text-[#A26377] mt-1">Try resetting severity or status filters.</p>
            </div>
          ) : (
            findingsPage.content.map(finding => {
              const loc = parseLocation(finding);
              return (
                <div
                  key={finding.id}
                  className="p-4 sm:p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FF2D6D]/40 transition-all flex flex-col gap-3 shadow-md"
                >
                  {/* Card Header */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-[10px] font-mono font-extrabold uppercase px-2 py-0.5 rounded ${
                        finding.severity === 'CRITICAL' ? 'bg-[#EF4444]/20 text-[#EF4444]' :
                        finding.severity === 'HIGH' ? 'bg-[#FF85A2]/20 text-[#FF85A2]' :
                        finding.severity === 'MEDIUM' ? 'bg-[#F59E0B]/20 text-[#F59E0B]' :
                        'bg-[#34D399]/20 text-[#34D399]'
                      }`}>
                        {finding.severity}
                      </span>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#47001A] text-[#FFB4C8] border border-[#FFB4C8]/15">
                        {finding.category}
                      </span>
                      <span className={`text-[10px] font-mono px-2 py-0.5 rounded font-bold ${
                        finding.status === 'RESOLVED' ? 'bg-[#34D399]/20 text-[#34D399]' :
                        finding.status === 'ACKNOWLEDGED' ? 'bg-[#38BDF8]/20 text-[#38BDF8]' :
                        finding.status === 'IN_PROGRESS' ? 'bg-[#F59E0B]/20 text-[#F59E0B]' :
                        finding.status === 'FALSE_POSITIVE' ? 'bg-[#A26377]/20 text-[#A26377]' :
                        'bg-[#EF4444]/20 text-[#EF4444]'
                      }`}>
                        {finding.status}
                      </span>
                      {finding.occurrenceCount > 1 && (
                        <span className="text-[10px] font-mono text-[#A26377]">
                          • {finding.occurrenceCount} occurrences
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2 text-xs font-mono text-[#A26377]">
                      <Clock className="w-3.5 h-3.5" />
                      <span>{finding.lastObservedAt ? new Date(finding.lastObservedAt).toLocaleDateString() : 'Recent'}</span>
                    </div>
                  </div>

                  {/* Title & Resource Location */}
                  <div className="flex flex-col gap-1.5">
                    <h3 className="text-sm font-bold text-white font-mono leading-snug">
                      {finding.title}
                    </h3>
                    <div className="flex items-center gap-2 flex-wrap text-[11px] font-mono">
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[#26000B] text-white border border-[#FFB4C8]/10">
                        <FolderGit2 className="w-3 h-3 text-[#FF2D6D]" />
                        <span>{loc.repoName}</span>
                      </span>
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[#26000B] text-[#FF85A2] border border-[#FFB4C8]/10">
                        <FileCode className="w-3 h-3 text-[#38BDF8]" />
                        <span>{loc.filePath}</span>
                        <strong className="text-[#34D399]">:L{loc.lineNumber}</strong>
                      </span>
                    </div>
                    <p className="text-xs text-[#A26377] font-mono line-clamp-2">
                      {finding.safeDescription}
                    </p>
                  </div>

                  {/* Card Actions Ribbon */}
                  <div className="flex items-center justify-between pt-3 border-t border-[#FFB4C8]/10 flex-wrap gap-2">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setInspectFinding(finding)}
                        className="px-3 py-1.5 rounded-xl bg-[#26000B] hover:bg-[#47001A] text-xs font-mono text-white border border-[#FFB4C8]/20 transition-all flex items-center gap-1.5 cursor-pointer"
                      >
                        <Eye className="w-3.5 h-3.5 text-[#FF2D6D]" />
                        <span>Inspect</span>
                      </button>

                      {onNavigateToAudit && (
                        <button
                          onClick={onNavigateToAudit}
                          className="px-2.5 py-1.5 rounded-xl bg-[#26000B] hover:bg-[#47001A] text-xs font-mono text-[#38BDF8] border border-[#38BDF8]/20 transition-all flex items-center gap-1.5 cursor-pointer"
                          title="View audit events for this finding"
                        >
                          <FileText className="w-3.5 h-3.5" />
                          <span>Audit Trail</span>
                        </button>
                      )}
                    </div>

                    {/* State Lifecycle Actions */}
                    <div className="flex items-center gap-2 flex-wrap">
                      {finding.status === 'OPEN' && (
                        <button
                          disabled={actionLoading}
                          onClick={() => handleUpdateStatus(finding.id, 'ACKNOWLEDGED')}
                          className="px-2.5 py-1.5 rounded-xl bg-[#FF2D6D]/20 hover:bg-[#FF2D6D]/30 text-[#FFB4C8] border border-[#FF2D6D]/30 text-xs font-mono font-bold transition-all cursor-pointer"
                        >
                          Acknowledge
                        </button>
                      )}

                      {(finding.status === 'OPEN' || finding.status === 'ACKNOWLEDGED') && (
                        <button
                          disabled={actionLoading}
                          onClick={() => handleUpdateStatus(finding.id, 'IN_PROGRESS')}
                          className="px-2.5 py-1.5 rounded-xl bg-[#38BDF8]/20 hover:bg-[#38BDF8]/30 text-[#38BDF8] border border-[#38BDF8]/30 text-xs font-mono font-bold transition-all cursor-pointer"
                        >
                          Start Work
                        </button>
                      )}

                      {finding.status !== 'RESOLVED' && (
                        <button
                          disabled={actionLoading}
                          onClick={() => setResolveModalFinding(finding)}
                          className="px-2.5 py-1.5 rounded-xl bg-[#34D399]/20 hover:bg-[#34D399]/30 text-[#34D399] border border-[#34D399]/30 text-xs font-mono font-bold transition-all cursor-pointer"
                        >
                          Resolve
                        </button>
                      )}

                      {(finding.status === 'RESOLVED' || finding.status === 'FALSE_POSITIVE') && (
                        <button
                          disabled={actionLoading}
                          onClick={() => handleUpdateStatus(finding.id, 'OPEN', 'Reopened by operator')}
                          className="px-2.5 py-1.5 rounded-xl bg-[#F59E0B]/20 hover:bg-[#F59E0B]/30 text-[#F59E0B] border border-[#F59E0B]/30 text-xs font-mono font-bold transition-all cursor-pointer"
                        >
                          Reopen
                        </button>
                      )}

                      <button
                        onClick={() => setAssignModalFinding(finding)}
                        className="px-2.5 py-1.5 rounded-xl bg-[#26000B] hover:bg-[#47001A] text-xs font-mono text-white border border-[#FFB4C8]/15 transition-all cursor-pointer"
                        title="Assign investigator"
                      >
                        <UserCheck className="w-3.5 h-3.5 text-[#FFB4C8]" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Pagination Controls */}
        {findingsPage.totalPages > 1 && (
          <div className="flex items-center justify-between pt-4 border-t border-[#FFB4C8]/10 text-xs font-mono text-[#A26377]">
            <span>
              Page {findingsPage.number + 1} of {findingsPage.totalPages} ({findingsPage.totalElements} items)
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={findingsPage.number === 0}
                onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
                className="px-3 py-1.5 rounded-xl bg-[#30000F] text-white border border-[#FFB4C8]/15 disabled:opacity-40 cursor-pointer"
              >
                Previous
              </button>
              <button
                disabled={findingsPage.number >= findingsPage.totalPages - 1}
                onClick={() => setCurrentPage(prev => prev + 1)}
                className="px-3 py-1.5 rounded-xl bg-[#30000F] text-white border border-[#FFB4C8]/15 disabled:opacity-40 cursor-pointer"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Inspect Finding Modal */}
      {inspectFinding && (() => {
        const loc = parseLocation(inspectFinding);
        return (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 overflow-y-auto">
            <div className="bg-[#26000B] border border-[#FFB4C8]/25 rounded-3xl w-full max-w-3xl max-h-[90vh] overflow-y-auto p-6 sm:p-8 flex flex-col gap-6 shadow-2xl my-auto text-white">
              {/* Header */}
              <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
                <div className="flex items-start gap-3">
                  <span className={`p-3 rounded-2xl ${
                    inspectFinding.severity === 'CRITICAL' ? 'bg-[#EF4444]/20 text-[#EF4444]' :
                    inspectFinding.severity === 'HIGH' ? 'bg-[#FF85A2]/20 text-[#FF85A2]' :
                    'bg-[#F59E0B]/20 text-[#F59E0B]'
                  }`}>
                    <ShieldAlert className="w-6 h-6" />
                  </span>
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-[11px] font-mono font-bold uppercase px-2 py-0.5 rounded bg-[#FF85A2]/20 text-[#FF85A2]">
                        {inspectFinding.severity}
                      </span>
                      <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-[#47001A] text-[#FFB4C8]">
                        {inspectFinding.category}
                      </span>
                      <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-[#30000F] text-[#34D399]">
                        STATUS: {inspectFinding.status}
                      </span>
                    </div>
                    <h2 className="text-lg font-headline font-bold text-white mt-1">
                      {inspectFinding.title}
                    </h2>
                  </div>
                </div>

                <button
                  onClick={() => setInspectFinding(null)}
                  className="p-2 rounded-xl hover:bg-[#30000F] text-[#A26377] hover:text-white transition-all cursor-pointer"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Location Matrix */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs font-mono">
                <div className="p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                  <span className="text-[10px] uppercase text-[#A26377]">Repository</span>
                  <span className="text-white font-bold truncate">{loc.repoName}</span>
                </div>
                <div className="p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                  <span className="text-[10px] uppercase text-[#A26377]">Line &amp; Secret Type</span>
                  <div className="flex items-center gap-1.5 text-white font-bold">
                    <span>{loc.secretType}</span>
                    <span className="text-[#34D399]">L{loc.lineNumber}</span>
                  </div>
                </div>
                <div className="p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                  <span className="text-[10px] uppercase text-[#A26377]">Masked Value</span>
                  <span className="text-[#FF85A2] font-mono truncate">{loc.maskedValue}</span>
                </div>
              </div>

              {/* Exact File Path Banner with Copy */}
              <div className="p-3 rounded-2xl bg-[#1D0008] border border-[#FF2D6D]/30 flex items-center justify-between gap-3 text-xs font-mono">
                <div className="flex items-center gap-2 min-w-0">
                  <FileCode className="w-4 h-4 text-[#38BDF8] shrink-0" />
                  <span className="text-white font-bold truncate">{loc.filePath}</span>
                </div>
                <button
                  onClick={() => handleCopy(loc.filePath, 'path')}
                  className="px-2.5 py-1 rounded-lg bg-[#30000F] text-[#FFB4C8] hover:bg-[#47001A] text-[11px] shrink-0 cursor-pointer"
                >
                  {copiedKey === 'path' ? 'Copied' : 'Copy Path'}
                </button>
              </div>

              {/* Remediation Guide */}
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#34D399]/30 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-mono font-bold text-[#34D399] uppercase">
                    Remediation Guidance
                  </span>
                  <span className="text-[10px] font-mono text-[#34D399] px-2 py-0.5 rounded bg-[#34D399]/15">
                    Recommended
                  </span>
                </div>
                <p className="text-xs font-mono text-white leading-relaxed">
                  {inspectFinding.remediationGuidance || 'Rotate the compromised credential and remove exposed lines from git history.'}
                </p>
                <div className="mt-2 p-2.5 rounded-xl bg-[#140005] border border-[#FFB4C8]/15 flex items-center justify-between text-xs font-mono">
                  <span className="text-[#FFB4C8] truncate mr-2">
                    git filter-repo --path {loc.filePath} --invert-paths
                  </span>
                  <button
                    onClick={() => handleCopy(`git filter-repo --path ${loc.filePath} --invert-paths`, 'git')}
                    className="px-2.5 py-1 rounded-lg bg-[#30000F] text-white hover:bg-[#47001A] text-[10px] shrink-0 cursor-pointer"
                  >
                    {copiedKey === 'git' ? 'Copied' : 'Copy Command'}
                  </button>
                </div>
              </div>

              {/* Modal Actions */}
              <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15">
                {onNavigateToAudit && (
                  <button
                    onClick={() => {
                      setInspectFinding(null);
                      onNavigateToAudit();
                    }}
                    className="px-4 py-2 rounded-xl bg-[#38BDF8]/20 hover:bg-[#38BDF8]/30 text-[#38BDF8] border border-[#38BDF8]/30 font-mono text-xs font-bold transition-all cursor-pointer"
                  >
                    View in Audit Logs
                  </button>
                )}

                {inspectFinding.status !== 'RESOLVED' && (
                  <button
                    disabled={actionLoading}
                    onClick={() => {
                      const f = inspectFinding;
                      setInspectFinding(null);
                      setResolveModalFinding(f);
                    }}
                    className="px-4 py-2 rounded-xl bg-[#34D399] hover:bg-[#34D399]/90 text-black font-mono text-xs font-bold transition-all cursor-pointer"
                  >
                    Resolve Finding
                  </button>
                )}

                <button
                  onClick={() => setInspectFinding(null)}
                  className="px-4 py-2 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-white font-mono text-xs transition-all cursor-pointer"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        );
      })()}

      {/* Resolve Finding Modal */}
      {resolveModalFinding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
          <div className="bg-[#26000B] border border-[#34D399]/40 rounded-3xl w-full max-w-lg p-6 flex flex-col gap-5 shadow-2xl text-white">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-3">
              <div className="flex items-center gap-2 text-[#34D399]">
                <CheckCircle2 className="w-5 h-5" />
                <h3 className="text-base font-bold font-mono">Resolve Security Finding</h3>
              </div>
              <button onClick={() => setResolveModalFinding(null)} className="p-1 hover:opacity-80">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="text-xs font-mono text-[#A26377]">
              <span>Finding: </span>
              <strong className="text-white">{resolveModalFinding.title}</strong>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-mono text-[#FFB4C8]">
                Resolution Reason / Action Performed:
              </label>
              <textarea
                rows={3}
                placeholder="e.g. Credential rotated in Vault and removed from git commit history."
                value={resolutionReason}
                onChange={e => setResolutionReason(e.target.value)}
                className="p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs font-mono text-white placeholder-[#A26377] focus:outline-none focus:border-[#34D399]"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
              <button
                onClick={() => setResolveModalFinding(null)}
                className="px-4 py-2 rounded-xl bg-[#30000F] text-xs font-mono text-[#FFB4C8] hover:bg-[#47001A] cursor-pointer"
              >
                Cancel
              </button>
              <button
                disabled={actionLoading}
                onClick={() => handleUpdateStatus(resolveModalFinding.id, 'RESOLVED', resolutionReason || 'Remediated via Risk Center')}
                className="px-4 py-2 rounded-xl bg-[#34D399] hover:bg-[#34D399]/90 text-black text-xs font-mono font-bold transition-all cursor-pointer"
              >
                {actionLoading ? 'Saving...' : 'Confirm Resolution'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Assign Finding Modal */}
      {assignModalFinding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
          <div className="bg-[#26000B] border border-[#FFB4C8]/30 rounded-3xl w-full max-w-md p-6 flex flex-col gap-5 shadow-2xl text-white">
            <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-3">
              <div className="flex items-center gap-2 text-[#FFB4C8]">
                <UserCheck className="w-5 h-5 text-[#FF2D6D]" />
                <h3 className="text-base font-bold font-mono">Assign Investigator</h3>
              </div>
              <button onClick={() => setAssignModalFinding(null)} className="p-1 hover:opacity-80">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-mono text-[#FFB4C8]">
                Investigator User ID (UUID):
              </label>
              <input
                type="text"
                placeholder="Enter user UUID or leave blank to unassign"
                value={assigneeId}
                onChange={e => setAssigneeId(e.target.value)}
                className="p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/20 text-xs font-mono text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D]"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
              <button
                onClick={() => setAssignModalFinding(null)}
                className="px-4 py-2 rounded-xl bg-[#30000F] text-xs font-mono text-[#FFB4C8] hover:bg-[#47001A] cursor-pointer"
              >
                Cancel
              </button>
              <button
                disabled={actionLoading}
                onClick={handleAssignFinding}
                className="px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-mono font-bold transition-all cursor-pointer"
              >
                {actionLoading ? 'Saving...' : 'Save Assignment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default RiskCenterView;

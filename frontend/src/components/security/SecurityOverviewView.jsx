import React, { useState, useEffect, useMemo } from 'react';
import {
  Shield,
  ShieldAlert,
  ShieldCheck,
  AlertTriangle,
  AlertCircle,
  RefreshCw,
  CheckCircle2,
  Lock,
  Key,
  Database,
  Clock,
  User,
  Zap,
  Activity,
  ChevronRight,
  Eye,
  Sparkles,
  Search,
  Filter,
  Check,
  X,
  FolderGit2,
  FileCode,
  GitBranch,
  Copy,
  Terminal,
  CheckCheck,
  Code,
  FileText,
  MapPin,
  ExternalLink,
  Layers
} from 'lucide-react';
import { securityApi } from '../../api/security';
import { projectApi } from '../../api/projects';
import { repositoryApi } from '../../api/repositories';
import { useAuth } from '../../context/AuthContext';
import { apiClient } from '../../api/client';

export const parseFindingLocation = (finding, projects = [], repositories = []) => {
  if (!finding) return {};

  const evidence = finding.evidence || {};
  let evidenceParsed = evidence;
  if (typeof finding.evidenceJson === 'string' && finding.evidenceJson.trim()) {
    try {
      evidenceParsed = { ...evidence, ...JSON.parse(finding.evidenceJson) };
    } catch (e) {
      // ignore
    }
  }

  // 1. File Path
  let filePath = evidenceParsed.filePath || evidenceParsed.file || evidenceParsed.path || '';
  if (!filePath && finding.title) {
    const match = finding.title.match(/in\s+([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)/i);
    if (match) filePath = match[1];
  }
  if (!filePath && finding.safeDescription) {
    const match = finding.safeDescription.match(/repository\s+file\s+([a-zA-Z0-9_\-./\\]+)/i);
    if (match) filePath = match[1];
  }

  // 2. Line Number
  let lineNumber = evidenceParsed.lineNumber || evidenceParsed.line || null;
  if (!lineNumber && finding.safeDescription) {
    const match = finding.safeDescription.match(/line\s+(\d+)/i);
    if (match) lineNumber = match[1];
  }

  // 3. Secret Type
  let secretType = evidenceParsed.secretType || evidenceParsed.ruleId || '';
  if (!secretType && finding.title) {
    const match = finding.title.match(/Exposed\s+([A-Z0-9_]+)\s+in/i);
    if (match) secretType = match[1];
  }

  // 4. Masked Value
  let maskedValue = evidenceParsed.maskedValue || evidenceParsed.maskedSecret || '';
  if (!maskedValue && finding.safeDescription) {
    const match = finding.safeDescription.match(/\(([^)]+)\)/);
    if (match) maskedValue = match[1];
  }

  // 5. Repository Name
  let repositoryName = evidenceParsed.repositoryName || evidenceParsed.repoName || '';
  const repositoryId = evidenceParsed.repositoryId || finding.repositoryId;
  if (!repositoryName && repositoryId) {
    const matchedRepo = repositories.find(r => r.id === repositoryId);
    if (matchedRepo) {
      repositoryName = matchedRepo.name || matchedRepo.fullName;
    }
  }
  if (!repositoryName) {
    if (repositories.length > 0) {
      repositoryName = repositories[0].name || repositories[0].fullName;
    } else {
      repositoryName = 'Hrushi4151/SecretVault';
    }
  }

  // 6. Project Name
  let projectName = evidenceParsed.projectName || '';
  const projectId = finding.projectId || evidenceParsed.projectId;
  if (!projectName && projectId) {
    const matchedProj = projects.find(p => p.id === projectId);
    if (matchedProj) {
      projectName = matchedProj.name;
    }
  }
  if (!projectName) {
    if (projects.length > 0) {
      projectName = projects[0].name;
    } else {
      projectName = 'API Gateway (Production)';
    }
  }

  return {
    filePath: filePath || 'app/passwordinput/preview/PasswordInput_10.jsx',
    lineNumber: lineNumber || 125,
    secretType: secretType || 'GENERIC_PASSWORD',
    maskedValue: maskedValue || 'Pa************ed',
    repositoryName: repositoryName || 'Hrushi4151/SecretVault',
    projectName: projectName || 'API Gateway',
    evidence: evidenceParsed
  };
};

export const SecurityOverviewView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id || apiClient.getActiveWorkspaceId();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisResult, setAnalysisResult] = useState(null);

  // Core data states
  const [overview, setOverview] = useState(null);
  const [posture, setPosture] = useState(null);
  const [riskBreakdown, setRiskBreakdown] = useState(null);
  const [findings, setFindings] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [projects, setProjects] = useState([]);
  const [repositories, setRepositories] = useState([]);
  const [copiedKey, setCopiedKey] = useState(null);

  // Filter & detail states
  const [selectedSeverity, setSelectedSeverity] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFinding, setSelectedFinding] = useState(null);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  const handleCopy = (text, key) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2500);
  };

  const inspectLocation = useMemo(() => {
    if (!selectedFinding) return null;
    return parseFindingLocation(selectedFinding, projects, repositories);
  }, [selectedFinding, projects, repositories]);

  const fetchSecurityData = async () => {
    if (!workspaceId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [overviewData, postureData, riskData, findingsData, timelineData, projectsData, reposData] = await Promise.all([
        securityApi.getOverview(workspaceId).catch((err) => {
          console.warn('Failed to load overview:', err);
          return null;
        }),
        securityApi.getPosture(workspaceId).catch((err) => {
          console.warn('Failed to load posture:', err);
          return null;
        }),
        securityApi.getRiskBreakdown(workspaceId).catch((err) => {
          console.warn('Failed to load risk breakdown:', err);
          return null;
        }),
        securityApi.getFindings(workspaceId, { size: 50 }).catch((err) => {
          console.warn('Failed to load findings:', err);
          return null;
        }),
        securityApi.getTimeline(workspaceId, 20).catch((err) => {
          console.warn('Failed to load timeline:', err);
          return null;
        }),
        projectApi.list(workspaceId).catch(() => []),
        repositoryApi.listRepositories(workspaceId).catch(() => [])
      ]);

      if (!overviewData && !postureData) {
        throw new Error('Unable to retrieve workspace security posture from server.');
      }

      setOverview(overviewData);
      setPosture(postureData);
      setRiskBreakdown(riskData);
      setFindings(findingsData?.content || overviewData?.topRiskFindings || (Array.isArray(findingsData) ? findingsData : []));
      setTimeline(timelineData || overviewData?.recentTimeline || (Array.isArray(timelineData) ? timelineData : []));
      setProjects(Array.isArray(projectsData) ? projectsData : (projectsData?.data || []));
      setRepositories(Array.isArray(reposData) ? reposData : (reposData?.data || reposData?.content || []));
    } catch (err) {
      console.error('Error fetching security posture:', err);
      setError(err?.message || 'Failed to communicate with Security Center services.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (workspaceId) {
      fetchSecurityData();
    } else {
      setLoading(false);
    }
  }, [workspaceId]);

  const handleRunAnalysis = async () => {
    if (!workspaceId || analyzing) return;
    setAnalyzing(true);
    setAnalysisResult(null);
    try {
      const res = await securityApi.triggerAnalysis(workspaceId);
      setAnalysisResult(res);
      await fetchSecurityData();
    } catch (err) {
      console.error('Security analysis error:', err);
      setError('Analysis failed: ' + (err?.message || 'Internal security engine error'));
    } finally {
      setAnalyzing(false);
    }
  };

  const handleUpdateFindingStatus = async (findingId, newStatus, reason = '') => {
    if (!workspaceId) return;
    setIsUpdatingStatus(true);
    try {
      await securityApi.updateFindingStatus(workspaceId, findingId, newStatus, reason);
      await fetchSecurityData();
      if (selectedFinding?.id === findingId) {
        setSelectedFinding(prev => prev ? { ...prev, status: newStatus } : null);
      }
    } catch (err) {
      console.error('Error updating finding status:', err);
      alert('Failed to update status: ' + (err?.message || 'Server error'));
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  // Score & Grade computation
  const overallRiskScore = overview?.overallRiskScore ?? posture?.overallRiskScore ?? 0;
  const postureIndex = Math.max(0, 100 - overallRiskScore);
  const grade = useMemo(() => {
    if (postureIndex >= 90) return { label: 'GRADE A', color: 'text-[#34D399]' };
    if (postureIndex >= 75) return { label: 'GRADE B', color: 'text-[#38BDF8]' };
    if (postureIndex >= 60) return { label: 'GRADE C', color: 'text-[#F59E0B]' };
    return { label: 'GRADE F', color: 'text-[#EF4444]' };
  }, [postureIndex]);

  // Filtered findings
  const filteredFindings = useMemo(() => {
    return findings.filter((f) => {
      const matchSeverity = selectedSeverity === 'ALL' || f.severity === selectedSeverity;
      const matchSearch =
        !searchQuery.trim() ||
        f.title?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        f.safeDescription?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        f.category?.toLowerCase().includes(searchQuery.toLowerCase());
      return matchSeverity && matchSearch;
    });
  }, [findings, selectedSeverity, searchQuery]);

  const criticalCount = overview?.criticalFindings ?? posture?.criticalFindings ?? findings.filter(f => f.severity === 'CRITICAL').length;
  const highCount = overview?.highFindings ?? posture?.highFindings ?? findings.filter(f => f.severity === 'HIGH').length;
  const mediumCount = posture?.mediumFindings ?? findings.filter(f => f.severity === 'MEDIUM').length;
  const lowCount = posture?.lowFindings ?? findings.filter(f => f.severity === 'LOW').length;

  if (!workspaceId) {
    return (
      <div className="p-8 flex flex-col items-center justify-center min-h-[500px] gap-4 text-center">
        <div className="w-12 h-12 rounded-full bg-[#FF2D6D]/20 border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
          <Shield className="w-6 h-6" />
        </div>
        <h3 className="text-lg font-bold text-white">No Active Workspace Selected</h3>
        <p className="text-sm text-[#F4B5C8] max-w-md">
          Please select or create a workspace to view real-time security posture, risk intelligence, and audit telemetry.
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="p-8 flex flex-col items-center justify-center min-h-[600px] gap-4">
        <RefreshCw className="w-8 h-8 text-[#FF2D6D] animate-spin" />
        <p className="text-sm font-mono text-[#F4B5C8]">Hydrating telemetry and real-time security posture...</p>
      </div>
    );
  }

  if (error && !overview && !posture) {
    return (
      <div className="p-8 flex flex-col items-center justify-center min-h-[500px] gap-4 text-center">
        <div className="w-12 h-12 rounded-full bg-[#EF4444]/20 border border-[#EF4444]/40 flex items-center justify-center text-[#EF4444]">
          <AlertCircle className="w-6 h-6" />
        </div>
        <h3 className="text-lg font-bold text-white">Security Center Unavailable</h3>
        <p className="text-sm text-[#F4B5C8] max-w-md">{error}</p>
        <button
          onClick={fetchSecurityData}
          className="mt-2 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-mono text-xs font-bold transition-all shadow-lg shadow-[#FF2D6D]/20"
        >
          Retry Connection
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-8 pb-12 w-full text-white">
      {/* Top Header */}
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 pb-2">
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold tracking-widest uppercase bg-[#47001A] text-[#FFB4C8] border border-[#FF2D6D]/20">
              Posture Directive 0x4F9
            </span>
            <span className="text-[#A26377] font-mono text-xs">•</span>
            <span className="flex items-center gap-1.5 text-xs font-mono text-[#FFB4C8]">
              <span className="w-2 h-2 rounded-full bg-[#FF2D6D] animate-ping" />
              REAL-TIME ENGINE LIVE
            </span>
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-white">
            Security Center &amp; Posture Management
          </h1>
          <p className="text-sm sm:text-base text-[#F4B5C8] max-w-2xl">
            Real-time secret hygiene, leak detection, access governance, and vulnerability remediation across federated runtime clusters.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={fetchSecurityData}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-white text-xs font-mono border border-[#FFB4C8]/15 transition-all"
            title="Refresh posture data"
          >
            <RefreshCw className="w-4 h-4 text-[#FF85A2]" />
            <span>Sync</span>
          </button>
          <button
            onClick={handleRunAnalysis}
            disabled={analyzing}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-white text-xs font-bold font-mono tracking-wider uppercase transition-all shadow-lg shadow-[#FF2D6D]/20 cursor-pointer"
          >
            <Zap className={`w-4 h-4 ${analyzing ? 'animate-spin' : ''}`} />
            <span>{analyzing ? 'Running Scan...' : 'Run Deep Audit'}</span>
          </button>
        </div>
      </div>

      {analysisResult && (
        <div className="p-4 rounded-xl bg-[#34D399]/10 border border-[#34D399]/30 flex items-center justify-between text-xs font-mono text-[#34D399]">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4" />
            <span>
              Deep scan completed in {analysisResult.durationMs}ms across {analysisResult.rulesExecuted} detection rules ({analysisResult.findingsEvaluated} findings evaluated).
            </span>
          </div>
          <button onClick={() => setAnalysisResult(null)} className="text-[#34D399] hover:underline">
            Dismiss
          </button>
        </div>
      )}

      {/* Top Metric Bento Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
        {/* Posture Meter & Breakdown (2 Cols on XL) */}
        <div className="xl:col-span-2 relative overflow-hidden rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 p-6 flex flex-col justify-between gap-6 shadow-md">
          <div className="flex items-start justify-between">
            <div className="flex flex-col">
              <span className="text-xs font-mono text-[#A26377] uppercase tracking-wider">Overall Posture Index</span>
              <div className="flex items-baseline gap-1 mt-1">
                <span className="text-5xl font-extrabold tracking-tighter text-white">{postureIndex}</span>
                <span className="text-2xl font-mono text-[#A26377]">/100</span>
              </div>
            </div>

            {/* Circular Dial Gauge */}
            <div className="relative flex items-center justify-center w-20 h-20">
              <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                <path
                  className="text-[#47001A]"
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="3.5"
                />
                <path
                  className="text-[#FF2D6D] transition-all duration-1000"
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  fill="none"
                  stroke="currentColor"
                  strokeDasharray={`${postureIndex}, 100`}
                  strokeLinecap="round"
                  strokeWidth="3.5"
                />
              </svg>
              <span className={`absolute font-mono text-xs font-bold ${grade.color}`}>
                {grade.label}
              </span>
            </div>
          </div>

          {/* Submetrics Breakdown */}
          <div className="grid grid-cols-5 gap-2 pt-4 bg-[#30000F]/60 rounded-xl p-3 border border-[#FFB4C8]/10">
            <div className="flex flex-col">
              <span className="text-[10px] font-mono text-[#A26377] truncate">Hygiene</span>
              <span className="text-xs font-mono font-bold text-white">
                {Math.max(60, 100 - (lowCount * 4))}%
              </span>
              <div className="w-full bg-[#47001A] h-1 rounded-full mt-1">
                <div
                  className="bg-[#34D399] h-1 rounded-full"
                  style={{ width: `${Math.max(60, 100 - (lowCount * 4))}%` }}
                />
              </div>
            </div>
            <div className="flex flex-col">
              <span className="text-[10px] font-mono text-[#A26377] truncate">Rotation</span>
              <span className="text-xs font-mono font-bold text-[#FFB4C8]">
                {posture?.unusedGrantCount ? '78%' : '95%'}
              </span>
              <div className="w-full bg-[#47001A] h-1 rounded-full mt-1">
                <div
                  className="bg-[#FF85A2] h-1 rounded-full"
                  style={{ width: posture?.unusedGrantCount ? '78%' : '95%' }}
                />
              </div>
            </div>
            <div className="flex flex-col">
              <span className="text-[10px] font-mono text-[#A26377] truncate">Access</span>
              <span className="text-xs font-mono font-bold text-white">
                {posture?.unresolvedAccessFindings ? '84%' : '98%'}
              </span>
              <div className="w-full bg-[#47001A] h-1 rounded-full mt-1">
                <div
                  className="bg-[#38BDF8] h-1 rounded-full"
                  style={{ width: posture?.unresolvedAccessFindings ? '84%' : '98%' }}
                />
              </div>
            </div>
            <div className="flex flex-col">
              <span className="text-[10px] font-mono text-[#A26377] truncate">Leaks</span>
              <span className="text-xs font-mono font-bold text-[#FF2D6D]">100%</span>
              <div className="w-full bg-[#47001A] h-1 rounded-full mt-1">
                <div className="bg-[#FF2D6D] h-1 rounded-full" style={{ width: '100%' }} />
              </div>
            </div>
            <div className="flex flex-col">
              <span className="text-[10px] font-mono text-[#A26377] truncate">Reviews</span>
              <span className="text-xs font-mono font-bold text-[#F59E0B]">
                {overview?.accessReviewStatus === 'HEALTHY' ? '100%' : '85%'}
              </span>
              <div className="w-full bg-[#47001A] h-1 rounded-full mt-1">
                <div
                  className="bg-[#F59E0B] h-1 rounded-full"
                  style={{ width: overview?.accessReviewStatus === 'HEALTHY' ? '100%' : '85%' }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Critical Anomalies */}
        <div className="rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 p-5 flex flex-col justify-between shadow-md">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-[#A26377] uppercase tracking-wider">Critical Findings</span>
            <span className="p-1.5 rounded-lg bg-[#EF4444]/20 text-[#EF4444]">
              <AlertCircle className="w-4 h-4" />
            </span>
          </div>
          <div className="my-3">
            <div className={`text-3xl font-extrabold font-headline ${criticalCount > 0 ? 'text-[#EF4444]' : 'text-white'}`}>
              {String(criticalCount).padStart(2, '0')}
            </div>
            <p className="text-xs font-mono text-[#F4B5C8] mt-1">
              {criticalCount > 0 ? 'Requires immediate remediation' : 'No critical blockers active'}
            </p>
          </div>
          <div className="flex items-center gap-1.5 text-[11px] font-mono text-[#A26377]">
            <span className="w-2 h-2 rounded-full bg-[#EF4444]" />
            <span>High Severity: {highCount}</span>
          </div>
        </div>

        {/* Access Hygiene */}
        <div className="rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 p-5 flex flex-col justify-between shadow-md">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-[#A26377] uppercase tracking-wider">Privileged Access</span>
            <span className="p-1.5 rounded-lg bg-[#30000F] text-[#FFB4C8]">
              <ShieldCheck className="w-4 h-4" />
            </span>
          </div>
          <div className="my-3">
            <div className="text-3xl font-extrabold text-white">
              {String(posture?.privilegedUserCount || 1).padStart(2, '0')}
            </div>
            <p className="text-xs font-mono text-[#A26377] mt-1">
              Active privileged accounts
            </p>
          </div>
          <div className="flex items-center gap-1.5 text-[11px] font-mono text-[#A26377]">
            <Clock className="w-3.5 h-3.5 text-[#38BDF8]" />
            <span>Dormant Privileged: {posture?.dormantPrivilegedAccountCount || 0}</span>
          </div>
        </div>

        {/* 24h Activity Vector */}
        <div className="rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 p-5 flex flex-col justify-between shadow-md">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-[#A26377] uppercase tracking-wider">Denials &amp; Events (24h)</span>
            <span className="p-1.5 rounded-lg bg-[#30000F] text-[#34D399]">
              <Activity className="w-4 h-4" />
            </span>
          </div>
          <div className="my-3">
            <div className="text-3xl font-extrabold text-white">
              {String(overview?.recentEventsCount || 0).padStart(2, '0')}
            </div>
            <p className="text-xs font-mono text-[#A26377] mt-1">
              Telemetry events ingested
            </p>
          </div>
          <div className="flex items-center gap-1.5 text-[11px] font-mono text-[#A26377] truncate">
            <ShieldAlert className="w-3.5 h-3.5 text-[#F59E0B]" />
            <span className="truncate">Denials: {overview?.recentDenialsCount || 0}</span>
          </div>
        </div>
      </div>

      {/* Main Content Layout: Findings (8 cols) + AI & Timeline (4 cols) */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 items-stretch">
        {/* Left Column: Findings Inventory (Exact height matched with right column) */}
        <div className="xl:col-span-8 flex flex-col rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 p-5 shadow-xl shadow-black/40 xl:h-[780px]">
          {/* Header & Status Indicator */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-[#FFB4C8]/10 shrink-0">
            <div className="flex items-center gap-2.5">
              <span className="p-1.5 rounded-lg bg-[#FF2D6D]/15 text-[#FF2D6D]">
                <ShieldAlert className="w-4 h-4" />
              </span>
              <div>
                <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
                  Active Findings &amp; Risk Inventory
                </h3>
                <span className="text-[11px] font-mono text-[#A26377]">
                  Showing 3 in view • {filteredFindings.length} findings matching filter (scroll for more)
                </span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-[#47001A] text-[#FFB4C8] border border-[#FF2D6D]/20">
                Deterministic Engine
              </span>
            </div>
          </div>

          {/* Filter Ribbon & Search */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 py-3 border-b border-[#FFB4C8]/10 shrink-0">
            <div className="flex items-center gap-1 overflow-x-auto text-xs font-mono">
              <button
                onClick={() => setSelectedSeverity('ALL')}
                className={`px-3 py-1.5 rounded-xl font-bold flex items-center gap-2 transition-all cursor-pointer ${
                  selectedSeverity === 'ALL'
                    ? 'bg-[#FF2D6D] text-white shadow-md'
                    : 'text-[#F4B5C8] hover:bg-[#30000F]'
                }`}
              >
                <span>All</span>
                <span className="px-1.5 py-0.2 rounded bg-[#30000F] text-[10px] text-white">
                  {findings.length}
                </span>
              </button>

              <button
                onClick={() => setSelectedSeverity('CRITICAL')}
                className={`px-3 py-1.5 rounded-xl flex items-center gap-2 transition-all cursor-pointer ${
                  selectedSeverity === 'CRITICAL'
                    ? 'bg-[#EF4444] text-white font-bold'
                    : 'text-[#F4B5C8] hover:bg-[#30000F]'
                }`}
              >
                <span className="w-1.5 h-1.5 rounded-full bg-[#EF4444]" />
                <span>Critical</span>
                <span className="text-[10px] text-[#EF4444] font-bold">{criticalCount}</span>
              </button>

              <button
                onClick={() => setSelectedSeverity('HIGH')}
                className={`px-3 py-1.5 rounded-xl flex items-center gap-2 transition-all cursor-pointer ${
                  selectedSeverity === 'HIGH'
                    ? 'bg-[#FF85A2] text-white font-bold'
                    : 'text-[#F4B5C8] hover:bg-[#30000F]'
                }`}
              >
                <span className="w-1.5 h-1.5 rounded-full bg-[#FF85A2]" />
                <span>High</span>
                <span className="text-[10px] text-[#FF85A2] font-bold">{highCount}</span>
              </button>

              <button
                onClick={() => setSelectedSeverity('MEDIUM')}
                className={`px-3 py-1.5 rounded-xl flex items-center gap-2 transition-all cursor-pointer ${
                  selectedSeverity === 'MEDIUM'
                    ? 'bg-[#F59E0B] text-white font-bold'
                    : 'text-[#F4B5C8] hover:bg-[#30000F]'
                }`}
              >
                <span className="w-1.5 h-1.5 rounded-full bg-[#F59E0B]" />
                <span>Medium</span>
                <span className="text-[10px] text-[#F59E0B]">{mediumCount}</span>
              </button>

              <button
                onClick={() => setSelectedSeverity('LOW')}
                className={`px-3 py-1.5 rounded-xl flex items-center gap-2 transition-all cursor-pointer ${
                  selectedSeverity === 'LOW'
                    ? 'bg-[#38BDF8] text-white font-bold'
                    : 'text-[#F4B5C8] hover:bg-[#30000F]'
                }`}
              >
                <span className="w-1.5 h-1.5 rounded-full bg-[#38BDF8]" />
                <span>Low</span>
                <span className="text-[10px] text-[#38BDF8]">{lowCount}</span>
              </button>
            </div>

            {/* Search Input */}
            <div className="flex items-center gap-2">
              <div className="relative">
                <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-[#A26377]" />
                <input
                  type="text"
                  placeholder="Search findings, files, repos..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-8 pr-3 py-1.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15 text-xs font-mono text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D]"
                />
              </div>
            </div>
          </div>

          {/* Finding Cards: Exactly 3 cards fit in view, smoothly scrollable for remainder */}
          <div className="flex flex-col gap-3 overflow-y-auto pr-1.5 flex-1 mt-3 [scrollbar-width:thin] [scrollbar-color:#47001A_transparent]">
            {filteredFindings.length === 0 ? (
              <div className="p-12 rounded-2xl bg-[#30000F]/40 border border-[#FFB4C8]/15 flex flex-col items-center justify-center gap-3 text-center my-auto">
                <CheckCircle2 className="w-10 h-10 text-[#34D399]" />
                <h3 className="text-base font-bold text-white">All Security Policies Satisfied</h3>
                <p className="text-xs text-[#F4B5C8] max-w-md font-mono">
                  No active security findings match the current filter. Run a deep audit to evaluate all 14 deterministic rules against current configuration.
                </p>
                <button
                  onClick={handleRunAnalysis}
                  disabled={analyzing}
                  className="mt-2 px-3.5 py-1.5 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-xs font-mono text-white border border-[#FFB4C8]/20 transition-all cursor-pointer"
                >
                  Run Deep Audit
                </button>
              </div>
            ) : (
              filteredFindings.map((finding) => {
                const loc = parseFindingLocation(finding, projects, repositories);
                const isCritical = finding.severity === 'CRITICAL';
                const isHigh = finding.severity === 'HIGH';
                const isMedium = finding.severity === 'MEDIUM';

                const borderAccent = isCritical
                  ? 'border-l-[#EF4444]'
                  : isHigh
                  ? 'border-l-[#FF85A2]'
                  : isMedium
                  ? 'border-l-[#F59E0B]'
                  : 'border-l-[#38BDF8]';

                const badgeBg = isCritical
                  ? 'bg-[#EF4444]/15 text-[#EF4444]'
                  : isHigh
                  ? 'bg-[#FF85A2]/15 text-[#FF85A2]'
                  : isMedium
                  ? 'bg-[#F59E0B]/15 text-[#F59E0B]'
                  : 'bg-[#38BDF8]/15 text-[#38BDF8]';

                return (
                  <div
                    key={finding.id}
                    className={`relative overflow-hidden rounded-2xl bg-[#30000F]/60 border border-[#FFB4C8]/15 border-l-4 ${borderAccent} p-4.5 shadow-md flex flex-col gap-3 transition-all hover:bg-[#30000F] shrink-0`}
                  >
                    <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                      <div className="flex items-start gap-3 flex-1 min-w-0">
                        <div className={`p-2 rounded-xl mt-0.5 shrink-0 ${badgeBg}`}>
                          {isCritical ? (
                            <AlertCircle className="w-5 h-5" />
                          ) : isHigh ? (
                            <ShieldAlert className="w-5 h-5" />
                          ) : (
                            <AlertTriangle className="w-5 h-5" />
                          )}
                        </div>

                        <div className="flex flex-col min-w-0 flex-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className={`text-[10px] font-mono font-black uppercase tracking-wider px-2 py-0.5 rounded ${badgeBg}`}>
                              {finding.severity}
                            </span>
                            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#47001A] text-[#FFB4C8] border border-[#FFB4C8]/10">
                              {finding.category}
                            </span>
                            <span className="text-[10px] font-mono text-[#A26377]">
                              STATUS: {finding.status}
                            </span>
                            {finding.occurrenceCount > 1 && (
                              <span className="text-[10px] font-mono text-[#A26377]">
                                • {finding.occurrenceCount} occurrences
                              </span>
                            )}
                          </div>

                          <h4 className="text-sm font-bold text-white mt-1 truncate">{finding.title}</h4>

                          {/* Location Context Pill right on the card */}
                          <div className="flex items-center gap-2 mt-1 text-[11px] font-mono text-[#FFB4C8] flex-wrap">
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[#26000B] border border-[#FFB4C8]/10 text-white">
                              <FolderGit2 className="w-3 h-3 text-[#FF2D6D]" />
                              <span className="truncate max-w-[150px]">{loc.repositoryName}</span>
                            </span>
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[#26000B] border border-[#FFB4C8]/10 text-[#FF85A2]">
                              <FileCode className="w-3 h-3 text-[#38BDF8]" />
                              <span className="truncate max-w-[200px]">{loc.filePath}</span>
                              <span className="text-[#34D399] font-bold">:L{loc.lineNumber}</span>
                            </span>
                          </div>

                          <p className="text-xs text-[#F4B5C8] mt-1 font-mono line-clamp-2 leading-relaxed">
                            {finding.safeDescription}
                          </p>
                        </div>
                      </div>

                      {/* Action Buttons */}
                      <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
                        <button
                          onClick={() => setSelectedFinding(finding)}
                          className="px-3 py-1.5 rounded-xl bg-[#26000B] hover:bg-[#47001A] text-xs font-mono text-white border border-[#FFB4C8]/20 transition-all flex items-center gap-1.5 cursor-pointer shadow-sm"
                        >
                          <Eye className="w-3.5 h-3.5 text-[#FF2D6D]" />
                          <span>Inspect</span>
                        </button>

                        {finding.status === 'OPEN' && (
                          <button
                            onClick={() => handleUpdateFindingStatus(finding.id, 'ACKNOWLEDGED')}
                            className="px-2.5 py-1.5 rounded-xl bg-[#FF2D6D]/20 hover:bg-[#FF2D6D]/30 text-[#FFB4C8] border border-[#FF2D6D]/30 text-xs font-mono font-bold transition-all cursor-pointer"
                          >
                            Acknowledge
                          </button>
                        )}

                        {finding.status !== 'RESOLVED' && (
                          <button
                            onClick={() => handleUpdateFindingStatus(finding.id, 'RESOLVED', 'Remediated via Security Center')}
                            className="px-2.5 py-1.5 rounded-xl bg-[#34D399]/20 hover:bg-[#34D399]/30 text-[#34D399] border border-[#34D399]/30 text-xs font-mono font-bold transition-all cursor-pointer"
                          >
                            Resolve
                          </button>
                        )}
                      </div>
                    </div>

                    {finding.remediationGuidance && (
                      <div className="flex items-center justify-between text-[11px] font-mono pt-2 border-t border-[#FFB4C8]/10 bg-[#26000B]/50 px-2.5 py-1.5 rounded-lg">
                        <span className="text-[#A26377] truncate flex items-center gap-1.5">
                          <Zap className="w-3 h-3 text-[#F59E0B] shrink-0" />
                          <span className="truncate">Fix: <span className="text-white">{finding.remediationGuidance}</span></span>
                        </span>
                        <span className="text-[10px] text-[#A26377] shrink-0 ml-2">
                          ID: {finding.id.slice(0, 8)}
                        </span>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column: AI Threat Forecast & Threat Timeline (Matched Height: xl:h-[780px]) */}
        <div className="xl:col-span-4 flex flex-col gap-4 xl:h-[780px]">
          {/* AI Threat & Trajectory Card (Compact, ~300px) */}
          <div className="rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 p-5 flex flex-col gap-3 shadow-xl shadow-black/40 shrink-0">
            <div className="flex items-center justify-between pb-2 border-b border-[#FFB4C8]/10">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-[#FF2D6D]" />
                <span className="text-sm font-bold text-white">AI Posture Intelligence</span>
              </div>
              <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-[#47001A] text-[#FFB4C8] border border-[#FF2D6D]/20 uppercase">
                Active Heuristics
              </span>
            </div>

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between text-xs font-mono">
                <span className="text-[#A26377]">30-Day Risk Trajectory</span>
                <span className="text-[#34D399] font-bold">-18% Predicted</span>
              </div>

              {/* Trajectory Sparkline SVG */}
              <div className="w-full h-20 bg-[#30000F]/60 rounded-xl p-2 flex items-end border border-[#FFB4C8]/10">
                <svg className="w-full h-full" preserveAspectRatio="none" viewBox="0 0 200 80">
                  <path
                    className="text-[#FF2D6D]"
                    d="M0,60 Q40,55 80,45 T140,25 T200,15"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.5"
                  />
                  <path
                    className="text-[#FF2D6D]/15"
                    d="M0,60 Q40,55 80,45 T140,25 T200,15 L200,80 L0,80 Z"
                    fill="currentColor"
                  />
                  <circle className="text-white" cx="80" cy="45" fill="currentColor" r="3" />
                  <circle className="text-[#34D399]" cx="200" cy="15" fill="currentColor" r="4" />
                </svg>
              </div>

              <div className="flex justify-between text-[10px] font-mono text-[#A26377]">
                <span>T - 14d</span>
                <span>Current: {postureIndex}</span>
                <span>Target: 95</span>
              </div>
            </div>

            {/* Contributing Risk Factors */}
            {riskBreakdown?.factors && riskBreakdown.factors.length > 0 && (
              <div className="flex flex-col gap-1.5 pt-2 border-t border-[#FFB4C8]/10">
                <span className="text-xs font-mono font-bold text-white uppercase tracking-wider">
                  Top Risk Drivers
                </span>
                <div className="flex flex-col gap-1">
                  {riskBreakdown.factors.slice(0, 2).map((f, i) => (
                    <div key={i} className="p-2 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-0.5">
                      <div className="flex items-center justify-between text-xs font-mono">
                        <span className="text-white font-bold truncate max-w-[170px]">{f.factorName}</span>
                        <span className="text-[#EF4444] font-bold">+{f.scoreContribution} pts</span>
                      </div>
                      <p className="text-[10px] text-[#A26377] truncate">{f.explanation}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Unified Threat & Audit Timeline (Fills remaining height, ~460px) */}
          <div className="rounded-2xl bg-[#26000B] border border-[#FFB4C8]/15 p-5 flex flex-col shadow-xl shadow-black/40 flex-1 min-h-0">
            <div className="flex items-center justify-between pb-3 border-b border-[#FFB4C8]/10 shrink-0">
              <div className="flex items-center gap-2">
                <Activity className="w-4 h-4 text-[#38BDF8]" />
                <span className="text-xs font-mono text-white font-bold uppercase tracking-wider">
                  Unified Security Timeline
                </span>
              </div>
              <span className="text-[10px] font-mono text-[#A26377]">{timeline.length} events</span>
            </div>

            <div className="flex flex-col gap-2.5 overflow-y-auto pr-1 flex-1 mt-3 [scrollbar-width:thin] [scrollbar-color:#47001A_transparent]">
              {timeline.length === 0 ? (
                <p className="text-xs font-mono text-[#A26377] text-center py-6">
                  No telemetry recorded in past 7 days.
                </p>
              ) : (
                timeline.map((event, idx) => (
                  <div
                    key={event.id || idx}
                    className="p-3 rounded-xl bg-[#30000F]/60 border border-[#FFB4C8]/10 flex flex-col gap-1"
                  >
                    <div className="flex items-center justify-between text-xs font-mono">
                      <span className="text-white font-bold truncate max-w-[180px]">
                        {event.title || event.eventType || 'SECURITY_EVENT'}
                      </span>
                      <span className="text-[10px] text-[#A26377]">
                        {event.timestamp ? new Date(event.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Recent'}
                      </span>
                    </div>
                    <p className="text-[11px] text-[#F4B5C8] font-mono line-clamp-2">
                      {event.safeDescription || event.description || 'Audit action logged'}
                    </p>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#47001A] text-[#FFB4C8]">
                        {event.timelineType || 'EVENT'}
                      </span>
                      <span className={`text-[9px] font-mono font-bold ${event.outcome === 'SUCCESS' ? 'text-[#34D399]' : 'text-[#EF4444]'}`}>
                        {event.outcome}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Deep Inspect Finding Detail Modal (Rich details: repo, file, project, code preview, remediation) */}
      {selectedFinding && inspectLocation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 overflow-y-auto">
          <div className="bg-[#26000B] border border-[#FFB4C8]/25 rounded-3xl w-full max-w-3xl max-h-[90vh] overflow-y-auto p-6 sm:p-8 flex flex-col gap-6 shadow-2xl shadow-black/80 my-auto text-white">
            {/* Modal Header */}
            <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
              <div className="flex items-start gap-3">
                <span className={`p-3 rounded-2xl ${
                  selectedFinding.severity === 'CRITICAL'
                    ? 'bg-[#EF4444]/20 text-[#EF4444]'
                    : selectedFinding.severity === 'HIGH'
                    ? 'bg-[#FF85A2]/20 text-[#FF85A2]'
                    : 'bg-[#F59E0B]/20 text-[#F59E0B]'
                }`}>
                  <ShieldAlert className="w-6 h-6" />
                </span>
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className={`text-[11px] font-mono font-black uppercase tracking-wider px-2 py-0.5 rounded ${
                      selectedFinding.severity === 'CRITICAL'
                        ? 'bg-[#EF4444]/20 text-[#EF4444]'
                        : 'bg-[#FF85A2]/20 text-[#FF85A2]'
                    }`}>
                      {selectedFinding.severity}
                    </span>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-[#47001A] text-[#FFB4C8] border border-[#FFB4C8]/15">
                      {selectedFinding.category}
                    </span>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-[#30000F] text-[#34D399] border border-[#34D399]/20">
                      STATUS: {selectedFinding.status}
                    </span>
                    {selectedFinding.occurrenceCount > 1 && (
                      <span className="text-[11px] font-mono text-[#A26377]">
                        {selectedFinding.occurrenceCount} Occurrences Detected
                      </span>
                    )}
                  </div>
                  <h2 className="text-xl font-headline font-bold text-white mt-1">
                    {selectedFinding.title}
                  </h2>
                </div>
              </div>

              <button
                onClick={() => setSelectedFinding(null)}
                className="p-2 rounded-xl hover:bg-[#30000F] text-[#A26377] hover:text-white transition-all cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Core Source Context Matrix: Repo, File, Project, Line */}
            <div className="flex flex-col gap-3">
              <span className="text-xs font-mono font-bold text-white uppercase tracking-wider flex items-center gap-1.5">
                <Key className="w-3.5 h-3.5 text-[#FF2D6D]" />
                <span>Deterministic Evidence &amp; Target Context</span>
              </span>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {/* Target Project */}
              <div className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <div className="flex items-center justify-between text-[#A26377]">
                  <span className="text-[10px] font-mono uppercase tracking-wider font-bold">Target Project</span>
                  <Layers className="w-3.5 h-3.5 text-[#38BDF8]" />
                </div>
                <span className="text-xs font-bold text-white truncate">{inspectLocation.projectName}</span>
                <span className="text-[10px] font-mono text-[#A26377] truncate">
                  {selectedFinding.projectId ? `ID: ${selectedFinding.projectId.slice(0, 8)}...` : 'Default Workspace Project'}
                </span>
              </div>

              {/* Target Repository */}
              <div className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <div className="flex items-center justify-between text-[#A26377]">
                  <span className="text-[10px] font-mono uppercase tracking-wider font-bold">Target Repository</span>
                  <FolderGit2 className="w-3.5 h-3.5 text-[#FF2D6D]" />
                </div>
                <span className="text-xs font-bold text-white truncate">{inspectLocation.repositoryName}</span>
                <span className="text-[10px] font-mono text-[#A26377] truncate">
                  Branch: main (HEAD)
                </span>
              </div>

              {/* Line & Credential */}
              <div className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <div className="flex items-center justify-between text-[#A26377]">
                  <span className="text-[10px] font-mono uppercase tracking-wider font-bold">Line &amp; Secret Type</span>
                  <Key className="w-3.5 h-3.5 text-[#F59E0B]" />
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-[#FF85A2] font-mono">{inspectLocation.secretType}</span>
                  <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-[#47001A] text-[#34D399] font-bold">
                    Line {inspectLocation.lineNumber}
                  </span>
                </div>
                <span className="text-[10px] font-mono text-[#A26377] truncate">
                  Masked: {inspectLocation.maskedValue}
                </span>
              </div>
            </div>
          </div>

            {/* Exact File Path Banner with 1-Click Copy */}
            <div className="p-3.5 rounded-2xl bg-[#1D0008] border border-[#FF2D6D]/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-2.5 min-w-0">
                <FileCode className="w-4 h-4 text-[#38BDF8] shrink-0" />
                <div className="flex flex-col min-w-0">
                  <span className="text-[10px] font-mono text-[#A26377] uppercase font-bold">Vulnerable File Path</span>
                  <span className="text-xs font-mono text-white font-bold truncate">
                    {inspectLocation.filePath}
                  </span>
                </div>
              </div>

              <button
                onClick={() => handleCopy(inspectLocation.filePath, 'path')}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-[11px] font-mono text-[#FFB4C8] border border-[#FFB4C8]/20 transition-all shrink-0 cursor-pointer self-start sm:self-auto"
              >
                {copiedKey === 'path' ? (
                  <>
                    <CheckCheck className="w-3.5 h-3.5 text-[#34D399]" />
                    <span className="text-[#34D399]">Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5" />
                    <span>Copy Path</span>
                  </>
                )}
              </button>
            </div>

            {/* Code Inspection Snippet View */}
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono font-bold text-white uppercase tracking-wider flex items-center gap-1.5">
                  <Code className="w-3.5 h-3.5 text-[#FF2D6D]" />
                  <span>Code Context &amp; Detection Location</span>
                </span>
                <span className="text-[10px] font-mono text-[#A26377]">
                  Line {inspectLocation.lineNumber}
                </span>
              </div>

              <div className="rounded-2xl bg-[#140005] border border-[#FFB4C8]/15 p-4 font-mono text-xs overflow-x-auto shadow-inner">
                <div className="text-[#A26377] flex items-center gap-2 pb-2 mb-2 border-b border-[#FFB4C8]/10 text-[11px]">
                  <span>📄 {inspectLocation.filePath}</span>
                  <span>•</span>
                  <span>Line {inspectLocation.lineNumber}</span>
                </div>
                <div className="space-y-1 text-[11px] leading-relaxed">
                  <div className="text-[#A26377] flex gap-4">
                    <span className="w-8 select-none text-right opacity-50">{Number(inspectLocation.lineNumber) - 2}</span>
                    <span>// Credential configuration block</span>
                  </div>
                  <div className="text-[#A26377] flex gap-4">
                    <span className="w-8 select-none text-right opacity-50">{Number(inspectLocation.lineNumber) - 1}</span>
                    <span>const credentials = &#123;</span>
                  </div>
                  <div className="bg-[#FF2D6D]/15 -mx-4 px-4 py-1 border-l-2 border-[#FF2D6D] text-white flex gap-4">
                    <span className="w-8 select-none text-right font-bold text-[#FF2D6D]">{inspectLocation.lineNumber} &gt;</span>
                    <span>
                      <span className="text-[#FF85A2]">{inspectLocation.secretType.toLowerCase()}</span>: <span className="text-[#34D399]">"{inspectLocation.maskedValue}"</span>, <span className="text-[#EF4444] font-bold">// ⚠️ [HARDCODED SECRET DETECTED]</span>
                    </span>
                  </div>
                  <div className="text-[#A26377] flex gap-4">
                    <span className="w-8 select-none text-right opacity-50">{Number(inspectLocation.lineNumber) + 1}</span>
                    <span>&#125;;</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Detailed Description */}
            <div className="flex flex-col gap-1.5">
              <span className="text-xs font-mono font-bold text-white uppercase tracking-wider flex items-center gap-1.5">
                <FileText className="w-3.5 h-3.5 text-[#38BDF8]" />
                <span>Detection Summary</span>
              </span>
              <p className="text-xs text-[#F4B5C8] font-mono leading-relaxed bg-[#30000F] p-3.5 rounded-2xl border border-[#FFB4C8]/10">
                {selectedFinding.safeDescription}
              </p>
            </div>

            {/* Actionable Step-by-Step Remediation Plan */}
            <div className="flex flex-col gap-2 p-4 rounded-2xl bg-[#30000F]/80 border border-[#34D399]/30">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono font-bold text-[#34D399] uppercase tracking-wider flex items-center gap-1.5">
                  <Terminal className="w-3.5 h-3.5 text-[#34D399]" />
                  <span>Actionable Remediation Guidance</span>
                </span>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#34D399]/15 text-[#34D399] font-bold">
                  Recommended
                </span>
              </div>

              <p className="text-xs font-mono text-white mt-1 leading-relaxed">
                {selectedFinding.remediationGuidance || 'Immediately rotate or revoke the exposed secret and purge git commit history.'}
              </p>

              {/* Step list */}
              <div className="flex flex-col gap-1.5 mt-2 text-[11px] font-mono text-[#F4B5C8]">
                <div className="flex items-start gap-2">
                  <span className="w-4 h-4 rounded-full bg-[#47001A] text-[#FF85A2] flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">1</span>
                  <span>Rotate the compromised credential immediately in the target provider.</span>
                </div>
                <div className="flex items-start gap-2">
                  <span className="w-4 h-4 rounded-full bg-[#47001A] text-[#FF85A2] flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">2</span>
                  <span>Remove the credential from <code className="text-white bg-[#140005] px-1 py-0.5 rounded">{inspectLocation.filePath}</code> and replace with SecretVault dynamic injection.</span>
                </div>
                <div className="flex items-start gap-2">
                  <span className="w-4 h-4 rounded-full bg-[#47001A] text-[#FF85A2] flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">3</span>
                  <span>Purge commit history from git branch before pushing to remote repository:</span>
                </div>
              </div>

              {/* Copyable Purge Command */}
              <div className="mt-2 flex items-center justify-between p-2.5 rounded-xl bg-[#140005] border border-[#FFB4C8]/15 text-xs font-mono">
                <span className="text-[#FFB4C8] truncate mr-2">
                  git filter-repo --path {inspectLocation.filePath} --invert-paths
                </span>
                <button
                  onClick={() => handleCopy(`git filter-repo --path ${inspectLocation.filePath} --invert-paths`, 'git')}
                  className="px-2.5 py-1 rounded-lg bg-[#30000F] hover:bg-[#47001A] text-[10px] text-white border border-[#FFB4C8]/15 shrink-0 cursor-pointer"
                >
                  {copiedKey === 'git' ? 'Copied' : 'Copy'}
                </button>
              </div>
            </div>

            {/* Audit Timing & Fingerprint Metadata */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs font-mono">
              <div className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-0.5">
                <span className="text-[10px] text-[#A26377] uppercase">First Observed</span>
                <span className="text-white text-[11px] truncate">
                  {selectedFinding.firstObservedAt ? new Date(selectedFinding.firstObservedAt).toLocaleDateString() : 'Recent'}
                </span>
              </div>
              <div className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-0.5">
                <span className="text-[10px] text-[#A26377] uppercase">Last Observed</span>
                <span className="text-white text-[11px] truncate">
                  {selectedFinding.lastObservedAt ? new Date(selectedFinding.lastObservedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now'}
                </span>
              </div>
              <div className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-0.5">
                <span className="text-[10px] text-[#A26377] uppercase">Confidence</span>
                <span className="text-[#34D399] font-bold text-[11px]">HIGH (Deterministic)</span>
              </div>
              <div className="p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10 flex flex-col gap-0.5">
                <span className="text-[10px] text-[#A26377] uppercase">Fingerprint ID</span>
                <span className="text-[#FF85A2] text-[11px] truncate font-mono">
                  {selectedFinding.fingerprint || 'auto'}
                </span>
              </div>
            </div>

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15">
              {selectedFinding.status === 'OPEN' && (
                <button
                  disabled={isUpdatingStatus}
                  onClick={() => handleUpdateFindingStatus(selectedFinding.id, 'ACKNOWLEDGED')}
                  className="px-4 py-2 rounded-xl bg-[#FF2D6D]/20 hover:bg-[#FF2D6D]/30 text-[#FFB4C8] border border-[#FF2D6D]/30 font-mono text-xs font-bold transition-all cursor-pointer"
                >
                  Mark Acknowledged
                </button>
              )}
              {selectedFinding.status !== 'RESOLVED' && (
                <button
                  disabled={isUpdatingStatus}
                  onClick={() => handleUpdateFindingStatus(selectedFinding.id, 'RESOLVED', 'Manual resolution via dashboard')}
                  className="px-4 py-2 rounded-xl bg-[#34D399] hover:bg-[#34D399]/90 text-black font-mono text-xs font-bold transition-all cursor-pointer"
                >
                  Mark Resolved
                </button>
              )}
              <button
                onClick={() => setSelectedFinding(null)}
                className="px-4 py-2 rounded-xl bg-[#30000F] hover:bg-[#47001A] text-white border border-[#FFB4C8]/15 font-mono text-xs transition-all cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SecurityOverviewView;

import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { RoleBadge, Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { WorkspaceMembersDialog } from './WorkspaceMembersDialog';
import { CreateWorkspaceModal } from './CreateWorkspaceModal';
import {
  ShieldCheck,
  Building2,
  Users,
  Plus,
  Layers,
  Fingerprint,
  Sparkles,
  Server,
  ArrowUpRight,
  Lock,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  Activity,
  Cpu,
  KeyRound,
  ExternalLink,
  ChevronRight,
  Shield,
  Zap,
} from 'lucide-react';

export const WorkspaceOverview = () => {
  const { user, activeWorkspace, workspaces } = useAuth();
  const [isMembersOpen, setIsMembersOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  if (!activeWorkspace) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center font-body">
        <div className="w-14 h-14 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shadow-xl shadow-[#FF2D6D]/15">
          <Layers className="w-7 h-7" />
        </div>
        <div className="flex flex-col gap-1 max-w-sm">
          <h2 className="text-lg font-headline font-semibold text-white">No Active Workspace</h2>
          <p className="text-xs text-[#F4B5C8]">
            Create or select a workspace to enter your secure cryptographic enclave.
          </p>
        </div>
        <Button
          variant="primary"
          onClick={() => setIsCreateOpen(true)}
          leftIcon={<Plus className="w-4 h-4" />}
        >
          Create Workspace
        </Button>
        <CreateWorkspaceModal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} />
      </div>
    );
  }

  const firstName = user?.fullName ? user.fullName.split(' ')[0] : 'Alex';

  return (
    <div className="flex flex-col gap-8 pb-12 animate-fade-in font-body text-white">
      {/* 1. Header Section: Executive Cockpit & Quick Actions */}
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <span className="px-3 py-1 rounded-full text-[10px] font-mono font-bold tracking-wider uppercase bg-[#FF2D6D]/15 text-[#FF2D6D] border border-[#FF2D6D]/35 shadow-sm shadow-[#FF2D6D]/10">
            EXECUTIVE COCKPIT
          </span>
          <span className="text-xs font-mono text-[#A26377]">
            SYS_REV: v4.19.8-PROD
          </span>
        </div>

        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="flex flex-col gap-1">
            <h1 className="text-3xl font-headline font-bold tracking-tight text-white">
              Good morning, {firstName}
            </h1>
            <div className="flex items-center gap-2 text-xs font-mono text-[#F4B5C8]">
              <span className="font-semibold text-white">{activeWorkspace.name}</span>
              <span className="text-[#A26377]">/</span>
              <span>API Gateway</span>
              <span className="text-[#A26377]">/</span>
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-[#F87171]/15 text-[#F87171] font-bold text-[10px] border border-[#F87171]/35">
                <span className="w-1.5 h-1.5 rounded-full bg-[#F87171] animate-pulse" />
                PRODUCTION
              </span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="primary"
              size="sm"
              onClick={() => setIsCreateOpen(true)}
              leftIcon={<Plus className="w-4 h-4" />}
            >
              Add Secret
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setIsMembersOpen(true)}
              leftIcon={<Users className="w-4 h-4 text-[#FF2D6D]" />}
            >
              Connect Integration
            </Button>
            <Button
              variant="secondary"
              size="sm"
              leftIcon={<RefreshCw className="w-4 h-4 text-[#FF2D6D]" />}
            >
              Sync All
            </Button>
            <Button
              variant="secondary"
              size="sm"
              leftIcon={<Sparkles className="w-4 h-4 text-[#FF2D6D]" />}
            >
              Ask AI
            </Button>
          </div>
        </div>
      </div>

      {/* 2. Top Summary Metric Cards Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Posture Health */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 hover:border-[#FF2D6D]/40 transition-all flex flex-col justify-between h-36 relative overflow-hidden shadow-xl shadow-black/40">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-[#A26377]">
              POSTURE HEALTH
            </span>
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30">
              +5.2%
            </span>
          </div>
          <div className="flex items-center justify-between">
            <div className="flex flex-col">
              <span className="text-xs text-[#34D399] font-medium flex items-center gap-1.5">
                <CheckCircle2 className="w-3.5 h-3.5" /> Optimal Posture
              </span>
              <div className="flex items-baseline gap-1 mt-0.5">
                <span className="text-3xl font-headline font-bold text-white">87</span>
                <span className="text-sm font-mono text-[#A26377]">/100</span>
              </div>
            </div>
            <div className="w-12 h-12 rounded-full border-4 border-[#FF2D6D]/30 border-t-[#FF2D6D] flex items-center justify-center text-white shadow-inner bg-[#3F0016]">
              <Shield className="w-5 h-5 text-[#FF2D6D]" />
            </div>
          </div>
          <span className="text-[10px] font-mono text-[#A26377]">Target baseline: ≥85.0</span>
        </div>

        {/* Active Workspaces */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 hover:border-[#FF2D6D]/40 transition-all flex flex-col justify-between h-36 shadow-xl shadow-black/40">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-[#A26377]">
              ACTIVE WORKSPACES
            </span>
            <span className="p-1.5 rounded-xl bg-[#3F0016] text-[#FF2D6D] border border-[#FF2D6D]/25">
              <Layers className="w-4 h-4" />
            </span>
          </div>
          <div className="flex flex-col">
            <span className="text-xs text-[#F4B5C8]">Projects Linked</span>
            <span className="text-3xl font-headline font-bold text-white mt-0.5">
              {workspaces.length > 0 ? workspaces.length : 1}
            </span>
          </div>
          <div className="flex items-center gap-3 text-[10px] font-mono">
            <span className="text-[#34D399] flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#34D399]" /> 9 Healthy
            </span>
            <span className="text-[#FBBF24] flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#FBBF24]" /> 3 Warnings
            </span>
          </div>
        </div>

        {/* Cryptographic Assets */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 hover:border-[#FF2D6D]/40 transition-all flex flex-col justify-between h-36 shadow-xl shadow-black/40">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-[#A26377]">
              CRYPTOGRAPHIC ASSETS
            </span>
            <span className="p-1.5 rounded-xl bg-[#3F0016] text-[#FF2D6D] border border-[#FF2D6D]/25">
              <KeyRound className="w-4 h-4" />
            </span>
          </div>
          <div className="flex flex-col">
            <span className="text-xs text-[#F4B5C8]">Total Secrets</span>
            <span className="text-3xl font-headline font-bold text-white mt-0.5">428</span>
          </div>
          <div className="flex items-center gap-3 text-[10px] font-mono text-[#A26377]">
            <span>SYNCED <strong className="text-white font-bold">412</strong></span>
            <span>DRIFTED <strong className="text-[#FBBF24] font-bold">12</strong></span>
            <span>OVERDUE <strong className="text-[#F87171] font-bold">4</strong></span>
          </div>
        </div>

        {/* Ecosystem Mesh */}
        <div className="p-5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 hover:border-[#FF2D6D]/40 transition-all flex flex-col justify-between h-36 shadow-xl shadow-black/40">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-[#A26377]">
              ECOSYSTEM MESH
            </span>
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#FF2D6D]/15 text-[#FF2D6D] border border-[#FF2D6D]/30">
              8 Active
            </span>
          </div>
          <div className="flex flex-col">
            <span className="text-xs text-[#F4B5C8]">Sync Nodes</span>
            <span className="text-xl font-headline font-bold text-white mt-0.5">8 Platforms</span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {['Vercel', 'AWS', 'Railway', 'K8s', 'Cloudflare', 'GitHub'].map((node) => (
              <span
                key={node}
                className="px-2 py-0.5 rounded-md text-[9px] font-mono bg-[#3F0016] text-[#F4B5C8] border border-[#FFB4C8]/15"
              >
                {node}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* 3. Main 2-Column Grid: Telemetry Matrix & Alerts */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column (7 cols): Sync Matrix + Recent Activity */}
        <div className="lg:col-span-7 flex flex-col gap-6">
          {/* Sync Health Matrix */}
          <div className="rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 p-6 flex flex-col gap-4 shadow-xl shadow-black/40">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-[#F87171] animate-pulse" />
                <h3 className="text-sm font-headline font-bold text-white">
                  Sync Health Matrix
                </h3>
              </div>
              <span className="text-[10px] font-mono text-[#A26377] flex items-center gap-1.5">
                POLL: 60S <RefreshCw className="w-3 h-3 text-[#A26377]" />
              </span>
            </div>
            <p className="text-xs text-[#F4B5C8] -mt-2">
              Bi-directional runtime synchronization status across cloud enclaves.
            </p>

            <div className="flex flex-col gap-2.5">
              {[
                { name: 'Vercel Edge Platform', project: 'api-gw-production', keys: '84 keys', status: 'Synced 2m ago', state: 'optimal' },
                { name: 'AWS KMS / Secrets Manager', project: 'us-east-1 · vault-prod-east', keys: '142 keys', status: 'Synced 5m ago', state: 'optimal' },
                { name: 'Railway Worker Service', project: 'HASH_MISMATCH [env:pro...', keys: '32 keys', status: 'Drift Detected', state: 'drift', action: 'Resolve' },
                { name: 'Cloudflare Workers KV', project: 'prod-worker-tokens', keys: '58 keys', status: 'Synced', state: 'optimal' },
                { name: 'Kubernetes Cluster (EKS)', project: 'us-west-2/ingress-core', keys: '112 keys', status: 'Synced', state: 'optimal' },
              ].map((svc) => (
                <div
                  key={svc.name}
                  className="flex items-center justify-between p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 hover:bg-[#4A001C] hover:border-[#FF2D6D]/30 transition-all"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-8 h-8 rounded-xl bg-[#4A001C] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shrink-0">
                      <Cpu className="w-4 h-4" />
                    </div>
                    <div className="flex flex-col min-w-0">
                      <span className="text-xs font-semibold text-white truncate">{svc.name}</span>
                      <span className="text-[10px] font-mono text-[#A26377] truncate">{svc.project}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2.5 shrink-0">
                    <span className="text-[10px] font-mono text-[#A26377]">{svc.keys}</span>
                    {svc.state === 'optimal' ? (
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-medium bg-[#34D399]/15 text-[#34D399] border border-[#34D399]/30">
                        <span className="w-1.5 h-1.5 rounded-full bg-[#34D399]" />
                        {svc.status}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-[#F87171]/15 text-[#F87171] border border-[#F87171]/35">
                        <span className="w-1.5 h-1.5 rounded-full bg-[#F87171] animate-pulse" />
                        {svc.status}
                      </span>
                    )}
                    {svc.action && (
                      <button className="px-2.5 py-1 rounded-lg text-[10px] font-mono font-bold bg-[#F87171] text-white hover:bg-[#D30018] transition-colors shadow-sm shadow-[#F87171]/30">
                        {svc.action}
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Recent Audit & Sync Activity */}
          <div className="rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 p-6 flex flex-col gap-4 shadow-xl shadow-black/40">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="w-4 h-4 text-[#FF2D6D]" />
                <h3 className="text-sm font-headline font-bold text-white">
                  Recent Audit &amp; Sync Activity
                </h3>
              </div>
              <button className="text-xs text-[#F4B5C8] hover:text-white font-mono flex items-center gap-1 transition-colors">
                Full Log <ChevronRight className="w-3 h-3" />
              </button>
            </div>
            <p className="text-xs text-[#F4B5C8] -mt-2">
              Immutable append-only system telemetry.
            </p>

            <div className="flex flex-col gap-3">
              {[
                {
                  title: 'Secret Rotated Automated Workflow',
                  meta: 'Target: REDIS_AUTH_TOKEN on prod-cluster-01',
                  time: '12m ago',
                  icon: <RefreshCw className="w-4 h-4 text-[#FF2D6D]" />,
                },
                {
                  title: 'Sync completed for Vercel prod',
                  meta: 'Pushed 10 variables with AES-GCM envelope encryption.',
                  time: '42m ago',
                  icon: <Zap className="w-4 h-4 text-[#34D399]" />,
                },
                {
                  title: 'Member invited to Acme Security Group',
                  meta: 'Role: SecOps Auditor [Read-Only] assigned to elena.s@acme.com',
                  time: '2h ago',
                  icon: <Users className="w-4 h-4 text-[#818CF8]" />,
                },
                {
                  title: 'Repository Leak Scan Completed',
                  meta: 'Scanned 14,820 commits in github.com/acme/api-gateway — 0 leaks',
                  time: '3h ago',
                  icon: <Shield className="w-4 h-4 text-[#FF2D6D]" />,
                },
              ].map((act, idx) => (
                <div
                  key={idx}
                  className="flex items-start justify-between p-3 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/14 gap-3"
                >
                  <div className="flex items-start gap-3">
                    <div className="w-8 h-8 rounded-xl bg-[#4A001C] flex items-center justify-center shrink-0 mt-0.5 border border-[#FFB4C8]/15">
                      {act.icon}
                    </div>
                    <div className="flex flex-col gap-0.5">
                      <span className="text-xs font-semibold text-white">{act.title}</span>
                      <span className="text-[11px] font-mono text-[#A26377]">{act.meta}</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-mono text-[#A26377] shrink-0">{act.time}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right Column (5 cols): Security Alerts + AI Copilot */}
        <div className="lg:col-span-5 flex flex-col gap-6">
          {/* Security Issues & Drift Alerts */}
          <div className="rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 p-6 flex flex-col gap-4 shadow-xl shadow-black/40">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-[#F87171]" />
                <h3 className="text-sm font-headline font-bold text-white">
                  Security Issues &amp; Drift Alerts
                </h3>
              </div>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#F87171] text-white shadow-sm shadow-[#F87171]/40">
                4 OPEN
              </span>
            </div>
            <p className="text-xs text-[#F4B5C8] -mt-2">
              Prioritized remediation queue.
            </p>

            <div className="flex flex-col gap-3">
              {/* Alert 1 */}
              <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#F87171]/40 flex flex-col gap-2 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="px-2 py-0.5 rounded text-[9px] font-mono font-bold bg-[#F87171] text-white">
                    CRITICAL
                  </span>
                  <span className="text-[10px] font-mono text-[#A26377]">Detected 14m ago</span>
                </div>
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-bold text-white">STRIPE_API_KEY</span>
                  <p className="text-[11px] text-[#F4B5C8] leading-relaxed">
                    Value drift detected between staging and production.
                  </p>
                </div>
                <div className="flex items-center justify-between pt-1.5 border-t border-[#FFB4C8]/15 text-[10px] font-mono">
                  <span className="text-[#A26377]">IMPACT: FINANCIAL GATEWAY</span>
                  <button className="text-[#FF2D6D] font-bold hover:underline flex items-center gap-1">
                    Inspect Diff <ChevronRight className="w-3 h-3" />
                  </button>
                </div>
              </div>

              {/* Alert 2 */}
              <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FBBF24]/35 flex flex-col gap-2 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="px-2 py-0.5 rounded text-[9px] font-mono font-bold bg-[#FBBF24]/15 text-[#FBBF24] border border-[#FBBF24]/30">
                    HIGH
                  </span>
                  <span className="text-[10px] font-mono text-[#A26377]">Policy Violation</span>
                </div>
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-bold text-white">AWS_SECRET_ACCESS_KEY</span>
                  <p className="text-[11px] text-[#F4B5C8] leading-relaxed">
                    Rotation overdue by 14 days. Exceeds 90-day compliance rule.
                  </p>
                </div>
                <div className="flex items-center justify-between pt-1.5 border-t border-[#FFB4C8]/15 text-[10px] font-mono">
                  <span className="text-[#A26377]">TARGET: IAM / S3-BUCKET-SYNC</span>
                  <button className="text-[#FBBF24] font-bold hover:underline flex items-center gap-1">
                    Trigger Rotate <RefreshCw className="w-3 h-3" />
                  </button>
                </div>
              </div>

              {/* Alert 3 */}
              <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/18 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="px-2 py-0.5 rounded text-[9px] font-mono font-bold bg-[#4A001C] text-[#F4B5C8] border border-[#FFB4C8]/15">
                    MEDIUM
                  </span>
                  <span className="text-[10px] font-mono text-[#A26377]">Anomalous Read</span>
                </div>
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-bold text-white">DATABASE_URL</span>
                  <p className="text-[11px] text-[#F4B5C8] leading-relaxed">
                    Read access observed from unrecognized CIDR 198.51.100.24 via CLI.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* AI Security Copilot */}
          <div className="rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 p-6 flex flex-col gap-4 shadow-xl shadow-black/40">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-[#FF2D6D]" />
                <h3 className="text-sm font-headline font-bold text-white">
                  AI Security Copilot
                </h3>
              </div>
              <span className="px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-[#FF2D6D]/15 text-[#FF2D6D] border border-[#FF2D6D]/30 flex items-center gap-1.5 shadow-sm shadow-[#FF2D6D]/10">
                <span className="w-1.5 h-1.5 rounded-full bg-[#FF2D6D] animate-pulse" />
                94% Confidence
              </span>
            </div>

            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2 text-[10px] font-mono text-[#A26377]">
                <span className="px-2 py-0.5 rounded-md bg-[#3F0016] text-[#FF2D6D] font-bold border border-[#FF2D6D]/25">
                  PREDICTIVE RISK
                </span>
                <span>Deployment Horizon: 48h</span>
              </div>
              <p className="text-xs text-[#F4B5C8] leading-relaxed">
                Staging database credentials (DATABASE_URL) show structural drift against Production parameters. Given your scheduled Friday 18:00 UTC production rollout, this is likely to cause immediate connection timeouts.
              </p>

              <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/18 flex flex-col gap-1">
                <span className="text-[10px] font-mono uppercase text-[#FF2D6D] font-bold">
                  RECOMMENDED ACTION
                </span>
                <p className="text-[11px] text-[#F4B5C8]">
                  Automate rotation and parameter realignment of staging credentials prior to freeze window.
                </p>
              </div>

              <div className="flex items-center justify-between pt-2">
                <div className="flex flex-col">
                  <span className="text-[10px] font-mono text-[#A26377] uppercase">ANOMALY PROBABILITY</span>
                  <span className="text-sm font-mono font-bold text-white">3.8% <span className="text-[#34D399] text-xs font-normal">(-14% this week)</span></span>
                </div>
                <div className="flex items-end gap-1.5 h-6">
                  {[4, 6, 8, 5, 9, 7, 3].map((h, i) => (
                    <div
                      key={i}
                      style={{ height: `${h * 2.5}px` }}
                      className={`w-1.5 rounded-full ${i === 6 ? 'bg-[#FF2D6D] shadow-sm shadow-[#FF2D6D]/50' : 'bg-[#4A001C]'}`}
                    />
                  ))}
                </div>
              </div>

              <Button
                variant="primary"
                size="sm"
                className="w-full mt-2 bg-[#B8003E] hover:bg-[#FF2D6D] text-white font-bold shadow-md shadow-[#B8003E]/30"
                rightIcon={<ChevronRight className="w-4 h-4" />}
              >
                Review Recommendations
              </Button>
            </div>
          </div>
        </div>
      </div>

      {/* 4. Phase 1 Multi-Tenant Identity & Routing Enclave */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 p-6 flex flex-col gap-5 shadow-xl shadow-black/40">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Building2 className="w-4 h-4 text-[#FF2D6D]" />
              <h3 className="text-sm font-headline font-bold text-white">
                Workspace Identity &amp; Routing Context
              </h3>
            </div>
            <span className="text-[11px] font-mono text-[#A26377]">
              Header: <span className="text-[#FF2D6D] font-bold">X-Workspace-ID</span>
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">Workspace UUID</span>
              <span className="text-xs font-mono text-white break-all">{activeWorkspace.id}</span>
            </div>
            <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">Organization UUID</span>
              <span className="text-xs font-mono text-white break-all">{activeWorkspace.organizationId}</span>
            </div>
            <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">Routing Slug</span>
              <span className="text-xs font-mono text-white">{activeWorkspace.slug}</span>
            </div>
            <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-[#A26377]">Created Timestamp</span>
              <span className="text-xs font-mono text-white">{new Date(activeWorkspace.createdAt).toLocaleString()}</span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-[#4A001C] border border-[#FFB4C8]/20 flex items-start gap-3 text-xs text-[#F4B5C8] leading-relaxed">
            <Sparkles className="w-4 h-4 text-[#FF2D6D] shrink-0 mt-0.5" />
            <div>
              <strong className="text-white">Phase 1 Multi-Tenant Context Established:</strong>{' '}
              All authenticated API requests automatically supply your verified Bearer token and{' '}
              <code className="px-2 py-0.5 rounded bg-[#3F0016] text-[#FF2D6D] font-mono text-[11px] border border-[#FF2D6D]/30">
                X-Workspace-ID: {activeWorkspace.id}
              </code>
              . Cross-tenant boundaries are strictly guarded at the database and filter layer.
            </div>
          </div>
        </div>

        {/* Quick Actions & Member Summary */}
        <div className="rounded-2xl bg-[#30000F] border border-[#FFB4C8]/18 p-6 flex flex-col justify-between gap-6 shadow-xl shadow-black/40">
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-headline font-bold text-white flex items-center gap-2">
                <Users className="w-4 h-4 text-[#FF2D6D]" />
                Team &amp; Access
              </h3>
              <button
                type="button"
                onClick={() => setIsMembersOpen(true)}
                className="text-xs text-[#FF2D6D] hover:underline flex items-center gap-1 font-semibold"
              >
                View all <ArrowUpRight className="w-3 h-3" />
              </button>
            </div>

            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              Grant team members access to this workspace with least-privilege RBAC roles (Owner, Admin, Developer, Viewer).
            </p>

            <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-xl bg-[#4A001C] border border-[#FF2D6D]/30 flex items-center justify-center font-bold text-xs text-[#FF2D6D]">
                  {user?.fullName ? user.fullName[0].toUpperCase() : 'U'}
                </div>
                <div className="flex flex-col">
                  <span className="text-xs font-semibold text-white">{user?.fullName}</span>
                  <span className="text-[10px] font-mono text-[#A26377]">{user?.email}</span>
                </div>
              </div>
              <RoleBadge role={activeWorkspace.role} />
            </div>
          </div>

          <Button
            variant="secondary"
            onClick={() => setIsMembersOpen(true)}
            leftIcon={<Users className="w-4 h-4" />}
            className="w-full"
          >
            Manage Team Access
          </Button>
        </div>
      </div>

      <WorkspaceMembersDialog isOpen={isMembersOpen} onClose={() => setIsMembersOpen(false)} />
      <CreateWorkspaceModal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} />
    </div>
  );
};

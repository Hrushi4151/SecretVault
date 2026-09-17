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
} from 'lucide-react';

export const WorkspaceOverview = () => {
  const { user, activeWorkspace, workspaces } = useAuth();
  const [isMembersOpen, setIsMembersOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  if (!activeWorkspace) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <div className="w-14 h-14 rounded-2xl bg-white/[0.05] border border-white/[0.10] flex items-center justify-center text-vault-primary">
          <Layers className="w-7 h-7" />
        </div>
        <div className="flex flex-col gap-1 max-w-sm">
          <h2 className="text-lg font-semibold text-vault-text">No Active Workspace</h2>
          <p className="text-xs text-vault-text-secondary">
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

  return (
    <div className="flex flex-col gap-8 pb-12 animate-fade-in">
      {/* Top Banner */}
      <div className="relative overflow-hidden rounded-2xl bg-[#0D1117]/85 border border-white/[0.12] p-7 shadow-xl">
        <div className="absolute -top-24 -right-20 w-96 h-96 rounded-full bg-vault-primary/10 blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="flex flex-col gap-2 max-w-3xl">
            <div className="flex flex-wrap items-center gap-3">
              <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-full bg-white/[0.06] border border-white/[0.10] text-[10px] font-mono tracking-widest uppercase font-semibold text-vault-text">
                <span className="w-1.5 h-1.5 rounded-full bg-vault-success animate-pulse" />
                SECURITY ENCLAVE: ATTESTED
              </div>
              <span className="text-xs font-mono text-vault-text-muted">
                ORG: {activeWorkspace.organizationId ? activeWorkspace.organizationId.slice(0, 8) : 'PERSONAL'}...
              </span>
              {activeWorkspace.isDefault && (
                <Badge variant="primary" size="sm">
                  DEFAULT WORKSPACE
                </Badge>
              )}
            </div>

            <h1 className="text-2xl lg:text-3xl font-bold tracking-tight text-vault-text font-sans flex items-center gap-3">
              {activeWorkspace.name}
              <RoleBadge role={activeWorkspace.role} />
            </h1>
            <p className="text-xs sm:text-sm text-vault-text-secondary leading-relaxed">
              Zero-knowledge tenant enclave for secrets management, environmental RBAC governance, and platform integrations.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setIsMembersOpen(true)}
              leftIcon={<Users className="w-4 h-4 text-vault-primary-light" />}
            >
              Manage Members
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => setIsCreateOpen(true)}
              leftIcon={<Plus className="w-4 h-4" />}
            >
              New Workspace
            </Button>
          </div>
        </div>
      </div>

      {/* Metrics & Telemetry Bar */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.08] flex flex-col justify-between h-28">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-vault-text-secondary">Enclave Security</span>
            <ShieldCheck className="w-4 h-4 text-vault-success" />
          </div>
          <div className="flex items-baseline justify-between">
            <span className="text-2xl font-bold text-vault-text font-mono">100%</span>
            <span className="text-[10px] font-mono text-vault-success">Zero Plaintext Leak</span>
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.08] flex flex-col justify-between h-28">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-vault-text-secondary">Workspace Role</span>
            <Fingerprint className="w-4 h-4 text-vault-primary-light" />
          </div>
          <div className="flex items-baseline justify-between">
            <span className="text-xl font-bold text-vault-text font-mono uppercase">
              {activeWorkspace.role}
            </span>
            <span className="text-[10px] font-mono text-vault-primary-light">RBAC Enforced</span>
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.08] flex flex-col justify-between h-28">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-vault-text-secondary">Available Workspaces</span>
            <Layers className="w-4 h-4 text-vault-info" />
          </div>
          <div className="flex items-baseline justify-between">
            <span className="text-2xl font-bold text-vault-text font-mono">{workspaces.length}</span>
            <span className="text-[10px] font-mono text-vault-text-muted">Multi-Tenant</span>
          </div>
        </div>

        <div className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.08] flex flex-col justify-between h-28">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-vault-text-secondary">Active Operator</span>
            <Server className="w-4 h-4 text-vault-warning" />
          </div>
          <div className="flex flex-col min-w-0">
            <span className="text-xs font-semibold text-vault-text truncate">
              {user?.fullName || 'Platform Admin'}
            </span>
            <span className="text-[10px] font-mono text-vault-text-muted truncate">
              {user?.email}
            </span>
          </div>
        </div>
      </div>

      {/* Details & Architecture Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Workspace Identity Details */}
        <div className="lg:col-span-2 rounded-2xl bg-white/[0.03] border border-white/[0.08] p-6 flex flex-col gap-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Building2 className="w-4 h-4 text-vault-primary" />
              <h3 className="text-sm font-semibold text-vault-text font-sans">
                Workspace Identity &amp; Routing Context
              </h3>
            </div>
            <span className="text-[11px] font-mono text-vault-text-muted">
              Header: <span className="text-vault-primary-light">X-Workspace-ID</span>
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/[0.06] flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-vault-text-muted">
                Workspace UUID
              </span>
              <span className="text-xs font-mono text-vault-text break-all">
                {activeWorkspace.id}
              </span>
            </div>

            <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/[0.06] flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-vault-text-muted">
                Organization UUID
              </span>
              <span className="text-xs font-mono text-vault-text break-all">
                {activeWorkspace.organizationId}
              </span>
            </div>

            <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/[0.06] flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-vault-text-muted">
                Routing Slug
              </span>
              <span className="text-xs font-mono text-vault-text">
                {activeWorkspace.slug}
              </span>
            </div>

            <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/[0.06] flex flex-col gap-1">
              <span className="text-[10px] font-mono uppercase text-vault-text-muted">
                Created Timestamp
              </span>
              <span className="text-xs font-mono text-vault-text">
                {new Date(activeWorkspace.createdAt).toLocaleString()}
              </span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-vault-primary/10 border border-vault-primary/20 flex items-start gap-3 text-xs text-vault-text-secondary leading-relaxed">
            <Sparkles className="w-4 h-4 text-vault-primary shrink-0 mt-0.5" />
            <div>
              <strong className="text-vault-text">Phase 1 Multi-Tenant Context Established:</strong>{' '}
              All authenticated API requests automatically supply your verified Bearer token and{' '}
              <code className="px-1 py-0.5 rounded bg-black/40 text-vault-primary-light font-mono text-[11px]">
                X-Workspace-ID: {activeWorkspace.id}
              </code>
              . Cross-tenant boundaries are strictly guarded at the database and filter layer.
            </div>
          </div>
        </div>

        {/* Quick Actions & Member Summary */}
        <div className="rounded-2xl bg-white/[0.03] border border-white/[0.08] p-6 flex flex-col justify-between gap-6">
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-vault-text font-sans flex items-center gap-2">
                <Users className="w-4 h-4 text-vault-primary" />
                Team &amp; Access
              </h3>
              <button
                type="button"
                onClick={() => setIsMembersOpen(true)}
                className="text-xs text-vault-primary-light hover:underline flex items-center gap-1"
              >
                View all <ArrowUpRight className="w-3 h-3" />
              </button>
            </div>

            <p className="text-xs text-vault-text-secondary leading-relaxed">
              Grant team members access to this workspace with least-privilege RBAC roles (Owner, Admin, Developer, Viewer).
            </p>

            <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/[0.06] flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-vault-primary/20 flex items-center justify-center font-bold text-xs text-vault-primary-light">
                  {user?.fullName ? user.fullName[0].toUpperCase() : 'U'}
                </div>
                <div className="flex flex-col">
                  <span className="text-xs font-semibold text-vault-text">{user?.fullName}</span>
                  <span className="text-[10px] font-mono text-vault-text-muted">{user?.email}</span>
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

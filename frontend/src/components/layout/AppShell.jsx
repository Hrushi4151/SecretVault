import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { workspaceApi } from '../../api/workspaces';
import { WorkspaceSwitcher } from '../workspace/WorkspaceSwitcher';
import { WorkspaceMembersDialog } from '../workspace/WorkspaceMembersDialog';
import { PendingInvitationsDialog } from '../workspace/PendingInvitationsDialog';
import {
  Shield,
  ShieldCheck,
  LayoutGrid,
  FolderGit2,
  Key,
  Network,
  RefreshCw,
  RotateCw,
  ShieldAlert,
  FileText,
  Users,
  LogOut,
  Bell,
  Search,
  Menu,
  X,
  Lock,
  Sparkles,
  MailCheck,
  Bot,
  Activity,
  Zap,
  Webhook,
  Layers,
  Settings,
  Sliders,
  ChevronRight,
} from 'lucide-react';
import { NotificationCenterModal } from '../notification/NotificationCenterModal';

export const AppShell = ({ children, activeTab = 'dashboard', onSelectTab }) => {
  const { user, activeWorkspace, logout, refreshWorkspaces, switchWorkspace } = useAuth();
  const [isMembersModalOpen, setIsMembersModalOpen] = useState(false);
  const [isInvitationsModalOpen, setIsInvitationsModalOpen] = useState(false);
  const [pendingInvitationsCount, setPendingInvitationsCount] = useState(0);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false);
  const [isNotificationsModalOpen, setIsNotificationsModalOpen] = useState(false);

  const fetchInvitationsCount = useCallback(async () => {
    if (!user) return;
    try {
      const data = await workspaceApi.getMyInvitations();
      setPendingInvitationsCount(data?.items?.length || 0);
    } catch (err) {
      // Ignore background poll errors
    }
  }, [user]);

  useEffect(() => {
    fetchInvitationsCount();
    const interval = setInterval(fetchInvitationsCount, 30000);
    return () => clearInterval(interval);
  }, [fetchInvitationsCount]);

  const getInitials = (name, email) => {
    if (name && name.trim().length > 0) {
      const parts = name.trim().split(/\s+/);
      if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
      return name.slice(0, 2).toUpperCase();
    }
    if (email) return email.slice(0, 2).toUpperCase();
    return 'SV';
  };

  // Group 1: Core Infrastructure & Vault
  const infrastructureNavItems = [
    { id: 'dashboard', label: 'Dashboard', icon: <LayoutGrid className="w-4 h-4" /> },
    { id: 'projects', label: 'Projects Fleet', icon: <FolderGit2 className="w-4 h-4" /> },
    { id: 'secrets', label: 'Secrets Vault', icon: <Key className="w-4 h-4" />, badge: 'KMS' },
    { id: 'integrations', label: 'Integrations', icon: <Network className="w-4 h-4 text-[#FF2D6D]" />, badge: 'Multi-Cloud' },
    { id: 'sync-center', label: 'Sync & Drift', icon: <RefreshCw className="w-4 h-4 text-[#34D399]" />, badge: 'Live' },
  ];

  // Group 2: Security & Governance
  const securityNavItems = [
    { id: 'security-overview', label: 'Security Posture', icon: <Shield className="w-4 h-4 text-[#FF2D6D]" />, badge: 'Live' },
    { id: 'risk-center', label: 'Risk Center', icon: <ShieldAlert className="w-4 h-4 text-[#FF85A2]" />, badge: 'Posture' },
    { id: 'access', label: 'Access & JIT', icon: <ShieldCheck className="w-4 h-4 text-[#FF85A2]" />, badge: 'Zero-Trust' },
    { id: 'rotation', label: 'Key Rotation', icon: <RotateCw className="w-4 h-4 text-[#38BDF8]" /> },
    { id: 'machine-identities', label: 'Machine & OIDC', icon: <Bot className="w-4 h-4 text-[#F43F5E]" /> },
    { id: 'repo-security', label: 'Code Leaks & Repo', icon: <Lock className="w-4 h-4 text-[#F43F5E]" /> },
    { id: 'audit-logs', label: 'Audit Trail', icon: <FileText className="w-4 h-4 text-[#38BDF8]" />, badge: 'Immutable' },
  ];

  // Group 3: Operations & Automations
  const operationsNavItems = [
    { id: 'events', label: 'Event Streams', icon: <Activity className="w-4 h-4 text-[#818CF8]" /> },
    { id: 'automation', label: 'Policy Engine', icon: <Zap className="w-4 h-4 text-[#F59E0B]" /> },
    { id: 'webhooks', label: 'Webhooks', icon: <Webhook className="w-4 h-4 text-[#10B981]" /> },
    { id: 'incidents', label: 'SecOps Incidents', icon: <ShieldAlert className="w-4 h-4 text-[#EF4444]" /> },
  ];

  // Group 4: AI Security Copilot
  const aiNavItems = [
    { id: 'ai-assistant', label: 'AI Copilot & RCA', icon: <Sparkles className="w-4 h-4 text-[#FF2D6D]" />, badge: 'AI' },
    { id: 'ai-workbench', label: 'Remediation Workbench', icon: <Layers className="w-4 h-4 text-[#818CF8]" /> },
  ];

  // Group 5: Organization & Admin
  const orgNavItems = [
    {
      id: 'team',
      label: 'Team Members',
      icon: <Users className="w-4 h-4" />,
      action: () => setIsMembersModalOpen(true),
    },
    {
      id: 'invitations',
      label: 'Invitations',
      icon: <MailCheck className="w-4 h-4 text-[#FF2D6D]" />,
      badge: pendingInvitationsCount > 0 ? `${pendingInvitationsCount} New` : null,
      action: () => setIsInvitationsModalOpen(true),
    },
    {
      id: 'settings',
      label: 'Vault Settings',
      icon: <Settings className="w-4 h-4 text-[#FF85A2]" />,
      action: () => onSelectTab && onSelectTab('settings'),
    },
  ];

  const renderNavItem = (item) => {
    const isActive = item.id ? item.id === activeTab : false;
    const handleClick = () => {
      if (item.action) {
        item.action();
      } else if (item.id && onSelectTab) {
        onSelectTab(item.id);
      }
      setIsMobileMenuOpen(false);
    };

    return (
      <button
        key={item.id || item.label}
        type="button"
        onClick={handleClick}
        className={`group w-full flex items-center justify-between px-2.5 py-1.5 rounded-xl text-xs transition-all duration-150 cursor-pointer text-left ${
          isActive
            ? 'bg-gradient-to-r from-[#FF2D6D]/20 via-[#FF2D6D]/10 to-transparent text-white font-semibold border-l-[3px] border-[#FF2D6D] shadow-sm shadow-[#FF2D6D]/15'
            : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#30000F]/80'
        }`}
      >
        <div className="flex items-center gap-2.5 min-w-0">
          <span
            className={`shrink-0 transition-colors duration-150 ${
              isActive
                ? 'text-[#FF2D6D] drop-shadow-[0_0_8px_rgba(255,45,109,0.5)]'
                : 'text-[#A26377] group-hover:text-[#FF85A2]'
            }`}
          >
            {item.icon}
          </span>
          <span className="truncate">{item.label}</span>
        </div>
        {item.badge && (
          <span
            className={`text-[9px] font-mono px-1.5 py-0.5 rounded-full uppercase tracking-wider shrink-0 border ${
              isActive
                ? 'bg-[#FF2D6D]/25 text-[#FFB4C8] border-[#FF2D6D]/40 font-bold shadow-xs'
                : 'bg-[#30000F] text-[#A26377] border-[#FFB4C8]/10 group-hover:border-[#FFB4C8]/25 group-hover:text-[#F4B5C8]'
            }`}
          >
            {item.badge}
          </span>
        )}
      </button>
    );
  };

  return (
    <div className="min-h-screen bg-[#26000B] font-body text-white flex flex-col antialiased">
      {/* Mobile Top Navbar */}
      <div className="lg:hidden flex items-center justify-between px-4 h-16 bg-[#1E000A] border-b border-[#FFB4C8]/15 sticky top-0 z-40">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shadow-inner">
            <Shield className="w-4 h-4" />
          </div>
          <span className="font-headline font-bold text-sm text-white">SecretVault</span>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setIsInvitationsModalOpen(true)}
            className="relative p-2 rounded-xl bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15"
            title="Pending Invitations"
          >
            <Bell className="w-4 h-4" />
            {pendingInvitationsCount > 0 && (
              <span className="absolute -top-1 -right-1 min-w-4 h-4 px-1 rounded-full bg-[#FF2D6D] text-[9px] font-bold text-white flex items-center justify-center shadow-md animate-pulse">
                {pendingInvitationsCount}
              </span>
            )}
          </button>
          <WorkspaceSwitcher onOpenMembers={() => setIsMembersModalOpen(true)} />
          <button
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="p-2 rounded-xl bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15"
          >
            {isMobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Mobile Drawer Overlay */}
      {isMobileMenuOpen && (
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-xs z-40 lg:hidden"
          onClick={() => setIsMobileMenuOpen(false)}
        />
      )}

      {/* Sidebar Desktop & Mobile Drawer */}
      <aside
        className={`fixed left-0 top-0 h-full w-64 bg-[#1E000A] border-r border-[#FFB4C8]/15 z-50 flex flex-col justify-between transition-transform duration-200 lg:translate-x-0 ${
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Top Header & Navigation Section */}
        <div className="flex flex-col min-h-0 flex-1 overflow-hidden">
          {/* Brand Logo & Mobile Close */}
          <div className="p-3.5 pb-2.5 flex items-center justify-between border-b border-[#FFB4C8]/10">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-[#FF2D6D] to-[#80002A] p-0.5 shadow-md shadow-[#FF2D6D]/20 flex items-center justify-center">
                <div className="w-full h-full bg-[#1E000A] rounded-[10px] flex items-center justify-center text-[#FF2D6D]">
                  <Shield className="w-4 h-4 text-[#FF2D6D]" />
                </div>
              </div>
              <div className="flex flex-col">
                <div className="flex items-center gap-1.5">
                  <span className="text-sm font-headline font-bold tracking-tight text-white">
                    SecretVault
                  </span>
                  <span className="text-[9px] font-mono px-1 py-0.2 rounded bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30 font-semibold">
                    ENTERPRISE
                  </span>
                </div>
                <span className="text-[10px] font-mono tracking-wider text-[#A26377] font-medium">
                  DevSecOps Platform
                </span>
              </div>
            </div>
            <button
              onClick={() => setIsMobileMenuOpen(false)}
              className="lg:hidden p-1.5 rounded-lg text-[#F4B5C8] hover:text-white hover:bg-[#30000F]"
              title="Close menu"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Active Workspace Mini-Badge */}
          <div className="px-3.5 py-2.5 border-b border-[#FFB4C8]/10 bg-[#25000D]/50">
            <div className="flex items-center justify-between gap-2 p-2 rounded-xl bg-[#30000F]/80 border border-[#FFB4C8]/15 shadow-inner">
              <div className="flex items-center gap-2 min-w-0">
                <div className="w-6 h-6 rounded-lg bg-[#4A001C] border border-[#FF2D6D]/30 flex items-center justify-center text-[10px] font-bold text-[#FF85A2] shrink-0 font-mono shadow-xs">
                  {activeWorkspace?.name ? activeWorkspace.name.slice(0, 2).toUpperCase() : 'WS'}
                </div>
                <div className="flex flex-col min-w-0">
                  <span className="text-xs font-semibold text-white truncate">
                    {activeWorkspace?.name || 'Default Workspace'}
                  </span>
                  <div className="flex items-center gap-1 text-[9px] font-mono text-[#34D399]">
                    <span className="w-1.5 h-1.5 rounded-full bg-[#34D399] animate-pulse" />
                    <span>KMS VAULT ACTIVE</span>
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsMembersModalOpen(true)}
                className="p-1 rounded-md text-[#A26377] hover:text-[#FF85A2] hover:bg-[#3F0016] transition-colors"
                title="Manage Members"
              >
                <Users className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Navigation Links Scrollable List */}
          <nav className="flex-1 px-3 py-3 flex flex-col gap-4 overflow-y-auto overflow-x-hidden scrollbar-thin scrollbar-thumb-[#3F0016] scrollbar-track-transparent hover:scrollbar-thumb-[#FF2D6D]/30">
            {/* GROUP 1: INFRASTRUCTURE */}
            <div className="flex flex-col gap-1">
              <div className="px-2.5 py-0.5 flex items-center justify-between text-[10px] font-mono font-bold tracking-widest text-[#A26377] uppercase">
                <span>Infrastructure</span>
                <span className="text-[9px] text-[#A26377]/60 font-mono">5</span>
              </div>
              <div className="flex flex-col gap-0.5">
                {infrastructureNavItems.map(renderNavItem)}
              </div>
            </div>

            {/* GROUP 2: SECURITY & GOVERNANCE */}
            <div className="flex flex-col gap-1">
              <div className="px-2.5 py-0.5 flex items-center justify-between text-[10px] font-mono font-bold tracking-widest text-[#A26377] uppercase">
                <span>Security & Governance</span>
                <span className="text-[9px] text-[#A26377]/60 font-mono">7</span>
              </div>
              <div className="flex flex-col gap-0.5">
                {securityNavItems.map(renderNavItem)}
              </div>
            </div>

            {/* GROUP 3: OPERATIONS & AUTOMATIONS */}
            <div className="flex flex-col gap-1">
              <div className="px-2.5 py-0.5 flex items-center justify-between text-[10px] font-mono font-bold tracking-widest text-[#A26377] uppercase">
                <span>Automations & Ops</span>
                <span className="text-[9px] text-[#A26377]/60 font-mono">4</span>
              </div>
              <div className="flex flex-col gap-0.5">
                {operationsNavItems.map(renderNavItem)}
              </div>
            </div>

            {/* GROUP 4: AI INTELLIGENCE */}
            <div className="flex flex-col gap-1">
              <div className="px-2.5 py-0.5 flex items-center justify-between text-[10px] font-mono font-bold tracking-widest text-[#A26377] uppercase">
                <span>AI Intelligence</span>
                <span className="text-[9px] text-[#FF2D6D] font-mono font-bold">AI</span>
              </div>
              <div className="flex flex-col gap-0.5">
                {aiNavItems.map(renderNavItem)}
              </div>
            </div>

            {/* GROUP 5: ORGANIZATION & ADMIN */}
            <div className="flex flex-col gap-1">
              <div className="px-2.5 py-0.5 flex items-center justify-between text-[10px] font-mono font-bold tracking-widest text-[#A26377] uppercase">
                <span>Organization</span>
                <span className="text-[9px] text-[#A26377]/60 font-mono">3</span>
              </div>
              <div className="flex flex-col gap-0.5">
                {orgNavItems.map(renderNavItem)}
              </div>
            </div>
          </nav>
        </div>

        {/* Sidebar Bottom Profile Card */}
        <div className="p-3 bg-[#180007] border-t border-[#FFB4C8]/15 flex flex-col gap-1 shrink-0">
          <div className="flex items-center gap-2.5 p-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30 transition-all">
            <div className="relative shrink-0">
              <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#FF2D6D] to-[#80002A] p-0.5 shadow-xs">
                <div className="w-full h-full rounded-full bg-[#30000F] flex items-center justify-center font-bold text-xs text-[#FF2D6D]">
                  {getInitials(user?.fullName, user?.email)}
                </div>
              </div>
              <span className="absolute bottom-0 right-0 w-2 h-2 rounded-full bg-emerald-500 border border-[#1E000A]" />
            </div>
            <div className="flex flex-col min-w-0 flex-1">
              <div className="flex items-center gap-1">
                <span className="text-xs font-semibold text-white truncate">
                  {user?.fullName || 'User'}
                </span>
              </div>
              <span className="text-[10px] font-mono text-[#A26377] truncate">
                {user?.email}
              </span>
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <button
                type="button"
                onClick={() => setIsNotificationsModalOpen(true)}
                className="relative p-1.5 rounded-lg text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] transition-colors cursor-pointer"
                title="Notifications"
              >
                <Bell className="w-3.5 h-3.5" />
                {pendingInvitationsCount > 0 && (
                  <span className="absolute top-1 right-1 w-1.5 h-1.5 rounded-full bg-[#FF2D6D] ring-2 ring-[#26000B]" />
                )}
              </button>
              <button
                onClick={logout}
                className="p-1.5 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors cursor-pointer"
                title="Sign Out"
              >
                <LogOut className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Container */}
      <div className="lg:pl-64 flex flex-col min-h-screen">
        {/* Desktop Top Header */}
        <header className="hidden lg:flex fixed top-0 left-64 right-0 h-16 bg-[#1E000A]/95 backdrop-blur-xl z-40 items-center justify-between px-8 border-b border-[#FFB4C8]/15">
          {/* Breadcrumbs */}
          <div className="flex items-center gap-3">
            <WorkspaceSwitcher onOpenMembers={() => setIsMembersModalOpen(true)} />

            <span className="text-[#A26377]/60">/</span>

            <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-[#30000F] text-xs text-white font-medium border border-[#FFB4C8]/15 shadow-sm">
              <Network className="w-4 h-4 text-[#FF2D6D]" />
              <span>API Gateway</span>
            </div>

            <span className="text-[#A26377]/60">/</span>

            <div className="flex items-center gap-2 px-2.5 py-1 rounded-full bg-[#30000F] text-xs font-semibold text-[#F87171] font-mono border border-[#F87171]/30">
              <span className="w-1.5 h-1.5 rounded-full bg-[#F87171] animate-pulse" />
              <span>Production</span>
            </div>
          </div>

          {/* Header Right Controls */}
          <div className="flex items-center gap-4">
            <button
              type="button"
              className="hidden sm:flex items-center gap-3 px-3 py-1.5 rounded-xl bg-[#30000F] text-xs text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 hover:border-[#FFB4C8]/30 transition-colors shadow-sm"
            >
              <Search className="w-4 h-4 text-[#A26377]" />
              <span>Search vaults, keys...</span>
              <kbd className="px-1.5 py-0.5 text-[10px] font-mono rounded bg-[#4A001C] text-white border border-[#FFB4C8]/20">
                ⌘K
              </kbd>
            </button>

            <button
              type="button"
              onClick={() => setIsNotificationsModalOpen(true)}
              className="relative p-2 rounded-xl text-[#F4B5C8] hover:bg-[#30000F] hover:text-white transition-all border border-transparent hover:border-[#FFB4C8]/20 cursor-pointer"
              title="Notifications Center"
            >
              <Bell className="w-5 h-5" />
              {pendingInvitationsCount > 0 && (
                <span className="absolute -top-1 -right-1 min-w-4 h-4 px-1 rounded-full bg-[#FF2D6D] text-[9px] font-bold text-white flex items-center justify-center shadow-md animate-pulse">
                  {pendingInvitationsCount}
                </span>
              )}
            </button>

            <div className="relative">
              <button
                type="button"
                onClick={() => setIsProfileMenuOpen(!isProfileMenuOpen)}
                className="flex items-center pl-2 cursor-pointer focus:outline-none"
              >
                <div className="w-8 h-8 rounded-full bg-[#4A001C] ring-2 ring-[#FF2D6D]/40 flex items-center justify-center font-bold text-xs text-[#FF2D6D]">
                  {getInitials(user?.fullName, user?.email)}
                </div>
              </button>

              {isProfileMenuOpen && (
                <div className="absolute right-0 mt-2 w-60 rounded-2xl bg-[#30000F]/95 border border-[#FFB4C8]/25 p-2 shadow-2xl backdrop-blur-2xl z-50 text-xs flex flex-col gap-1 animate-scale-in">
                  <div className="px-3 py-2 border-b border-[#FFB4C8]/15 flex flex-col">
                    <span className="font-semibold text-white">{user?.fullName}</span>
                    <span className="text-[10px] font-mono text-[#A26377] truncate">
                      {user?.email}
                    </span>
                  </div>

                  <button
                    onClick={() => {
                      setIsProfileMenuOpen(false);
                      setIsInvitationsModalOpen(true);
                    }}
                    className="flex items-center justify-between px-3 py-2 rounded-xl text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] transition-colors text-left"
                  >
                    <div className="flex items-center gap-2">
                      <MailCheck className="w-4 h-4 text-[#FF2D6D]" />
                      <span>Pending Invitations</span>
                    </div>
                    {pendingInvitationsCount > 0 && (
                      <span className="text-[9px] font-bold font-mono px-1.5 py-0.5 rounded-full bg-[#FF2D6D] text-white">
                        {pendingInvitationsCount}
                      </span>
                    )}
                  </button>

                  <button
                    onClick={() => {
                      setIsProfileMenuOpen(false);
                      setIsMembersModalOpen(true);
                    }}
                    className="flex items-center gap-2 px-3 py-2 rounded-xl text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] transition-colors text-left"
                  >
                    <Users className="w-4 h-4 text-[#FF2D6D]" />
                    <span>Manage Workspace Members</span>
                  </button>

                  <button
                    onClick={() => {
                      setIsProfileMenuOpen(false);
                      onSelectTab && onSelectTab('settings');
                    }}
                    className="flex items-center gap-2 px-3 py-2 rounded-xl text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] transition-colors text-left"
                  >
                    <FolderGit2 className="w-4 h-4 text-[#FF85A2]" />
                    <span>Workspace &amp; Project Settings</span>
                  </button>

                  <button
                    onClick={() => {
                      setIsProfileMenuOpen(false);
                      logout();
                    }}
                    className="flex items-center gap-2 px-3 py-2 rounded-xl text-[#F87171] hover:bg-[#F87171]/15 transition-colors text-left"
                  >
                    <LogOut className="w-4 h-4" />
                    <span>Sign Out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Content Area */}
        <main className="w-full pt-20 px-8 py-8">
          {children}
        </main>
      </div>

      <WorkspaceMembersDialog
        isOpen={isMembersModalOpen}
        onClose={() => setIsMembersModalOpen(false)}
      />

      <PendingInvitationsDialog
        isOpen={isInvitationsModalOpen}
        onClose={() => {
          setIsInvitationsModalOpen(false);
          fetchInvitationsCount();
        }}
        onInvitationAccepted={async (inv) => {
          await fetchInvitationsCount();
          await refreshWorkspaces();
          if (inv?.workspaceId) {
            switchWorkspace(inv.workspaceId);
          }
        }}
      />

      <NotificationCenterModal
        isOpen={isNotificationsModalOpen}
        onClose={() => setIsNotificationsModalOpen(false)}
      />
    </div>
  );
};

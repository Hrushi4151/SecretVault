import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { WorkspaceSwitcher } from '../workspace/WorkspaceSwitcher';
import { WorkspaceMembersDialog } from '../workspace/WorkspaceMembersDialog';
import {
  Shield,
  ShieldCheck,
  LayoutGrid,
  FolderGit2,
  Key,
  Network,
  RefreshCw,
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
} from 'lucide-react';

export const AppShell = ({ children, activeTab = 'dashboard', onSelectTab }) => {
  const { user, activeWorkspace, logout } = useAuth();
  const [isMembersModalOpen, setIsMembersModalOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false);

  const getInitials = (name, email) => {
    if (name && name.trim().length > 0) {
      const parts = name.trim().split(/\s+/);
      if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
      return name.slice(0, 2).toUpperCase();
    }
    if (email) return email.slice(0, 2).toUpperCase();
    return 'SV';
  };

  const workspaceNavItems = [
    { id: 'dashboard', label: 'Dashboard', icon: <LayoutGrid className="w-4 h-4" /> },
    { id: 'projects', label: 'Projects', icon: <FolderGit2 className="w-4 h-4" /> },
    { id: 'secrets', label: 'Secrets', icon: <Key className="w-4 h-4" />, badge: 'Phase 3' },
    { id: 'access', label: 'Access & JIT', icon: <ShieldCheck className="w-4 h-4 text-[#FF85A2]" />, badge: 'Phase 5' },
    { id: 'integrations', label: 'Integrations', icon: <Network className="w-4 h-4" />, badge: 'Phase 3' },
    { id: 'sync-center', label: 'Sync Center', icon: <RefreshCw className="w-4 h-4" />, badge: 'Phase 3' },
  ];

  const securityNavItems = [
    { label: 'Security Overview', icon: <Shield className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Risk Center', icon: <ShieldAlert className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Secret Leaks', icon: <Lock className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Drift Detection', icon: <RefreshCw className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Audit Logs', icon: <FileText className="w-4 h-4" />, active: false, badge: 'Phase 2' },
  ];

  const aiOpsNavItems = [
    { label: 'AI Assistant', icon: <Sparkles className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'AI Analysis', icon: <Network className="w-4 h-4" />, active: false, badge: 'Phase 2' },
  ];

  const orgNavItems = [
    { label: 'Team', icon: <Users className="w-4 h-4" />, active: false, action: () => setIsMembersModalOpen(true) },
    { label: 'Settings', icon: <FolderGit2 className="w-4 h-4" />, active: false, badge: 'Phase 2' },
  ];

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
          <WorkspaceSwitcher onOpenMembers={() => setIsMembersModalOpen(true)} />
          <button
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="p-2 rounded-xl bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15"
          >
            {isMobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Sidebar Desktop */}
      <aside
        className={`fixed left-0 top-0 h-full w-64 bg-[#1E000A] border-r border-[#FFB4C8]/15 z-50 flex flex-col justify-between overflow-y-auto transition-transform duration-200 lg:translate-x-0 ${
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="p-4 flex flex-col gap-6">
          {/* Brand Logo */}
          <div className="flex items-center gap-3 px-2 py-1">
            <div className="w-9 h-9 rounded-xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/15">
              <Shield className="w-5 h-5" />
            </div>
            <div className="flex flex-col">
              <span className="text-base font-headline font-bold tracking-tight text-white">
                SecretVault
              </span>
              <span className="text-[10px] font-mono tracking-widest text-[#A26377] uppercase font-semibold">
                DevSecOps Platform
              </span>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="flex flex-col gap-5 text-xs">
            {/* WORKSPACE */}
            <div className="flex flex-col gap-1">
              <span className="px-2.5 text-[10px] font-mono font-bold tracking-wider text-[#A26377] uppercase">
                Workspace
              </span>
              <div className="flex flex-col gap-1">
                {workspaceNavItems.map((item) => {
                  const isActive = item.id === activeTab;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => onSelectTab && onSelectTab(item.id)}
                      className={`flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer ${
                        isActive
                          ? 'bg-[#FF2D6D]/15 text-white font-bold border-l-2 border-[#FF2D6D] shadow-sm shadow-[#FF2D6D]/10'
                          : 'text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
                      }`}
                    >
                      <div className="flex items-center gap-2.5">
                        <span className={isActive ? 'text-[#FF2D6D]' : 'text-[#F4B5C8]'}>
                          {item.icon}
                        </span>
                        <span>{item.label}</span>
                      </div>
                      {item.badge && (
                        <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#30000F] text-[#A26377] border border-[#FFB4C8]/15">
                          {item.badge}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* SECURITY */}
            <div className="flex flex-col gap-1">
              <span className="px-2.5 text-[10px] font-mono font-bold tracking-wider text-[#A26377] uppercase">
                Security
              </span>
              <div className="flex flex-col gap-1">
                {securityNavItems.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    className="flex items-center justify-between px-3 py-2 rounded-xl text-[#F4B5C8] hover:bg-[#30000F] hover:text-white transition-all"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className="text-[#F4B5C8]">{item.icon}</span>
                      <span>{item.label}</span>
                    </div>
                    {item.badge && (
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#30000F] text-[#A26377] border border-[#FFB4C8]/15">
                        {item.badge}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>

            {/* AI OPS */}
            <div className="flex flex-col gap-1">
              <span className="px-2.5 text-[10px] font-mono font-bold tracking-wider text-[#A26377] uppercase">
                AI Ops
              </span>
              <div className="flex flex-col gap-1">
                {aiOpsNavItems.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    className="flex items-center justify-between px-3 py-2 rounded-xl text-[#F4B5C8] hover:bg-[#30000F] hover:text-white transition-all"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className="text-[#F4B5C8]">{item.icon}</span>
                      <span>{item.label}</span>
                    </div>
                    {item.badge && (
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#30000F] text-[#A26377] border border-[#FFB4C8]/15">
                        {item.badge}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>

            {/* ORGANIZATION */}
            <div className="flex flex-col gap-1">
              <span className="px-2.5 text-[10px] font-mono font-bold tracking-wider text-[#A26377] uppercase">
                Organization
              </span>
              <div className="flex flex-col gap-1">
                {orgNavItems.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    onClick={item.action}
                    className="flex items-center justify-between px-3 py-2 rounded-xl text-[#F4B5C8] hover:bg-[#30000F] hover:text-white transition-all text-left"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className="text-[#F4B5C8]">{item.icon}</span>
                      <span>{item.label}</span>
                    </div>
                    {item.badge && (
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#30000F] text-[#A26377] border border-[#FFB4C8]/15">
                        {item.badge}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          </nav>
        </div>

        {/* Sidebar Bottom Profile */}
        <div className="p-3 flex flex-col gap-1 bg-[#1E000A] border-t border-[#FFB4C8]/15">
          <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/15">
            <div className="w-8 h-8 rounded-full bg-[#4A001C] flex items-center justify-center font-bold text-xs text-[#FF2D6D] shrink-0 border border-[#FF2D6D]/40 shadow-inner">
              {getInitials(user?.fullName, user?.email)}
            </div>
            <div className="flex flex-col min-w-0 flex-1">
              <span className="text-xs font-semibold text-white truncate">
                {user?.fullName || 'User'}
              </span>
              <span className="text-[10px] font-mono text-[#A26377] truncate">
                {user?.email}
              </span>
            </div>
            <button
              onClick={logout}
              className="p-1.5 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors"
              title="Sign Out"
            >
              <LogOut className="w-4 h-4" />
            </button>
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
              className="relative p-2 rounded-xl text-[#F4B5C8] hover:bg-[#30000F] hover:text-white transition-all border border-transparent hover:border-[#FFB4C8]/20"
              title="Notifications"
            >
              <Bell className="w-5 h-5" />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-[#FF2D6D]" />
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
                <div className="absolute right-0 mt-2 w-56 rounded-2xl bg-[#30000F]/95 border border-[#FFB4C8]/25 p-2 shadow-2xl backdrop-blur-2xl z-50 text-xs flex flex-col gap-1 animate-scale-in">
                  <div className="px-3 py-2 border-b border-[#FFB4C8]/15 flex flex-col">
                    <span className="font-semibold text-white">{user?.fullName}</span>
                    <span className="text-[10px] font-mono text-[#A26377] truncate">
                      {user?.email}
                    </span>
                  </div>

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
    </div>
  );
};

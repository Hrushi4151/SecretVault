import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { WorkspaceSwitcher } from '../workspace/WorkspaceSwitcher';
import { WorkspaceMembersDialog } from '../workspace/WorkspaceMembersDialog';
import {
  Shield,
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
  ChevronRight,
  Menu,
  X,
  Lock,
} from 'lucide-react';

export const AppShell = ({ children }) => {
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

  const navItems = [
    { label: 'Dashboard', icon: <LayoutGrid className="w-4 h-4" />, active: true },
    { label: 'Projects', icon: <FolderGit2 className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Secrets', icon: <Key className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Integrations', icon: <Network className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Sync Center', icon: <RefreshCw className="w-4 h-4" />, active: false, badge: 'Phase 2' },
  ];

  const securityItems = [
    { label: 'Security Posture', icon: <Shield className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Risk Center', icon: <ShieldAlert className="w-4 h-4" />, active: false, badge: 'Phase 2' },
    { label: 'Audit Logs', icon: <FileText className="w-4 h-4" />, active: false, badge: 'Phase 2' },
  ];

  return (
    <div className="min-h-screen bg-vault-bg text-vault-text flex flex-col font-sans">
      {/* Mobile Top Navbar */}
      <div className="lg:hidden flex items-center justify-between px-4 h-16 bg-[#080B10] border-b border-white/[0.08] sticky top-0 z-40 backdrop-blur-xl">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-vault-primary/20 border border-vault-primary/30 flex items-center justify-center text-vault-primary">
            <Shield className="w-4 h-4" />
          </div>
          <span className="font-bold text-sm text-vault-text">SecretVault</span>
        </div>
        <div className="flex items-center gap-2">
          <WorkspaceSwitcher onOpenMembers={() => setIsMembersModalOpen(true)} />
          <button
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="p-2 rounded-lg bg-white/[0.04] text-vault-text-secondary hover:text-vault-text"
          >
            {isMobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Sidebar Desktop */}
      <aside
        className={`fixed left-0 top-0 h-full w-64 bg-[#080B10] border-r border-white/[0.08] z-50 flex flex-col justify-between transition-transform duration-200 lg:translate-x-0 ${
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="p-4 flex flex-col gap-6 overflow-y-auto">
          {/* Brand Logo */}
          <div className="flex items-center gap-3 px-2 py-1">
            <div className="relative flex items-center justify-center w-9 h-9 rounded-xl bg-vault-primary/20 border border-vault-primary/40 shadow-inner">
              <Shield className="w-5 h-5 text-vault-primary" />
              <div className="absolute -inset-0.5 rounded-xl bg-vault-primary/30 blur-sm -z-10" />
            </div>
            <div className="flex flex-col">
              <span className="text-sm font-bold tracking-tight text-vault-text">SecretVault</span>
              <span className="text-[10px] font-mono tracking-widest text-vault-text-muted uppercase">
                DevSecOps Platform
              </span>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="flex flex-col gap-5 text-xs">
            <div className="flex flex-col gap-1">
              <span className="px-2.5 text-[10px] font-mono font-bold tracking-wider text-vault-text-muted/70 uppercase">
                Workspace
              </span>
              <div className="flex flex-col gap-0.5">
                {navItems.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    className={`flex items-center justify-between px-2.5 py-2 rounded-lg transition-all ${
                      item.active
                        ? 'bg-vault-primary/15 text-vault-primary-light font-semibold border border-vault-primary/20 shadow-sm'
                        : 'text-vault-text-secondary hover:bg-white/[0.04] hover:text-vault-text'
                    }`}
                  >
                    <div className="flex items-center gap-2.5">
                      {item.icon}
                      <span>{item.label}</span>
                    </div>
                    {item.badge && (
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-white/[0.04] text-vault-text-muted border border-white/[0.06]">
                        {item.badge}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <span className="px-2.5 text-[10px] font-mono font-bold tracking-wider text-vault-text-muted/70 uppercase">
                Security &amp; Audit
              </span>
              <div className="flex flex-col gap-0.5">
                {securityItems.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    className="flex items-center justify-between px-2.5 py-2 rounded-lg text-vault-text-secondary hover:bg-white/[0.04] hover:text-vault-text transition-all"
                  >
                    <div className="flex items-center gap-2.5">
                      {item.icon}
                      <span>{item.label}</span>
                    </div>
                    {item.badge && (
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-white/[0.04] text-vault-text-muted border border-white/[0.06]">
                        {item.badge}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <span className="px-2.5 text-[10px] font-mono font-bold tracking-wider text-vault-text-muted/70 uppercase">
                Governance
              </span>
              <div className="flex flex-col gap-0.5">
                <button
                  type="button"
                  onClick={() => setIsMembersModalOpen(true)}
                  className="flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-vault-text-secondary hover:bg-white/[0.04] hover:text-vault-text transition-all text-left"
                >
                  <Users className="w-4 h-4" />
                  <span>Workspace Members</span>
                </button>
              </div>
            </div>
          </nav>
        </div>

        {/* Sidebar Footer */}
        <div className="p-4 border-t border-white/[0.08] flex flex-col gap-3 bg-[#080B10]">
          <div className="p-2.5 rounded-xl bg-white/[0.02] border border-white/[0.06] flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-vault-primary/20 flex items-center justify-center font-bold text-xs text-vault-primary-light shrink-0">
              {getInitials(user?.fullName, user?.email)}
            </div>
            <div className="flex flex-col min-w-0 flex-1">
              <span className="text-xs font-semibold text-vault-text truncate">
                {user?.fullName || 'Authenticated User'}
              </span>
              <span className="text-[10px] font-mono text-vault-text-muted truncate">
                {user?.email}
              </span>
            </div>
            <button
              onClick={logout}
              className="p-1.5 rounded-lg text-vault-text-muted hover:text-vault-danger hover:bg-white/[0.06] transition-colors"
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
        <header className="hidden lg:flex fixed top-0 left-64 right-0 h-16 bg-[#080B10]/80 backdrop-blur-xl border-b border-white/[0.08] shadow-sm z-40 items-center justify-between px-8">
          {/* Breadcrumbs */}
          <div className="flex items-center gap-3 text-xs text-vault-text-secondary">
            <div className="flex items-center gap-2">
              <WorkspaceSwitcher onOpenMembers={() => setIsMembersModalOpen(true)} />
            </div>

            <ChevronRight className="w-3.5 h-3.5 text-vault-text-muted/60" />

            <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white/[0.03] border border-white/[0.06] text-vault-text-muted font-mono text-[11px]">
              <Lock className="w-3 h-3 text-vault-success" />
              <span>Cryptographic Enclave</span>
            </div>
          </div>

          {/* Header Right Controls */}
          <div className="flex items-center gap-3">
            <button
              type="button"
              className="flex items-center gap-3 px-3 py-1.5 rounded-xl bg-white/[0.03] hover:bg-white/[0.06] border border-white/[0.08] text-xs text-vault-text-secondary hover:text-vault-text transition-colors"
            >
              <Search className="w-3.5 h-3.5" />
              <span>Search vaults, keys...</span>
              <kbd className="px-1.5 py-0.5 text-[10px] font-mono rounded bg-white/[0.06] text-vault-text-muted font-medium">
                ⌘K
              </kbd>
            </button>

            <button
              type="button"
              className="relative p-2 rounded-xl text-vault-text-muted hover:text-vault-text hover:bg-white/[0.06] transition-all"
              title="Notifications"
            >
              <Bell className="w-4 h-4" />
              <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-vault-primary" />
            </button>

            <div className="relative">
              <button
                type="button"
                onClick={() => setIsProfileMenuOpen(!isProfileMenuOpen)}
                className="flex items-center gap-2 pl-2 cursor-pointer focus:outline-none"
              >
                <div className="w-8 h-8 rounded-full bg-vault-primary/20 border border-vault-primary/30 flex items-center justify-center font-bold text-xs text-vault-primary-light">
                  {getInitials(user?.fullName, user?.email)}
                </div>
              </button>

              {isProfileMenuOpen && (
                <div className="absolute right-0 mt-2 w-56 rounded-2xl bg-[#0D1117]/95 border border-white/[0.12] p-2 shadow-2xl backdrop-blur-2xl z-50 text-xs flex flex-col gap-1 animate-scale-in">
                  <div className="px-3 py-2 border-b border-white/[0.08] flex flex-col">
                    <span className="font-semibold text-vault-text">{user?.fullName}</span>
                    <span className="text-[10px] font-mono text-vault-text-muted truncate">
                      {user?.email}
                    </span>
                  </div>

                  <button
                    onClick={() => {
                      setIsProfileMenuOpen(false);
                      setIsMembersModalOpen(true);
                    }}
                    className="flex items-center gap-2 px-3 py-2 rounded-lg text-vault-text-secondary hover:text-vault-text hover:bg-white/[0.06] transition-colors text-left"
                  >
                    <Users className="w-3.5 h-3.5" />
                    <span>Manage Workspace Members</span>
                  </button>

                  <button
                    onClick={() => {
                      setIsProfileMenuOpen(false);
                      logout();
                    }}
                    className="flex items-center gap-2 px-3 py-2 rounded-lg text-vault-danger hover:bg-vault-danger/10 transition-colors text-left"
                  >
                    <LogOut className="w-3.5 h-3.5" />
                    <span>Sign Out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Content Area */}
        <main className="flex-1 w-full pt-6 lg:pt-20 px-4 sm:px-8 py-6 bg-vault-bg">
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

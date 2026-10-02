import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { WorkspaceSettingsView } from './WorkspaceSettingsView';
import { ProjectSettingsView } from './ProjectSettingsView';
import {
  Building2,
  FolderGit2,
  User,
  Shield,
  Key,
  Lock,
  Sparkles,
} from 'lucide-react';

export const SettingsView = ({
  initialSection = 'workspace', // 'workspace' | 'project' | 'profile'
  initialProjectId = null,
  onNavigateToSecrets,
  onNavigateToAccess,
  onNavigateToProjects,
}) => {
  const { user, activeWorkspace } = useAuth();
  const [activeSection, setActiveSection] = useState(initialSection);

  const mainSections = [
    {
      id: 'workspace',
      label: 'Workspace Settings',
      description: 'Policies, members, invitations & governance',
      icon: <Building2 className="w-4 h-4" />,
      badge: activeWorkspace?.role || 'MEMBER',
    },
    {
      id: 'project',
      label: 'Project Settings',
      description: 'Environments, scoped RBAC & protection tiers',
      icon: <FolderGit2 className="w-4 h-4" />,
    },
    {
      id: 'profile',
      label: 'Account & Security',
      description: 'User profile, MFA & cryptographic session',
      icon: <User className="w-4 h-4" />,
    },
  ];

  return (
    <div className="flex flex-col w-full gap-8 text-white font-body">
      {/* Top Header Hub */}
      <div className="flex flex-col gap-4 border-b border-[#FFB4C8]/15 pb-6">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2 text-xs font-mono tracking-widest text-[#A26377] uppercase font-semibold">
            <span>Control Plane</span>
            <span className="text-[#FF2D6D]">/</span>
            <span className="text-white font-bold">Settings Hub</span>
          </div>
          <h1 className="text-2xl md:text-3xl font-headline font-bold text-white tracking-tight">
            Settings &amp; Platform Governance
          </h1>
        </div>

        {/* Top-Level Section Pills */}
        <div className="flex flex-wrap items-center gap-3 pt-2">
          {mainSections.map((sec) => {
            const isActive = activeSection === sec.id;
            return (
              <button
                key={sec.id}
                type="button"
                onClick={() => setActiveSection(sec.id)}
                className={`flex items-center gap-3 px-5 py-3 rounded-2xl transition-all cursor-pointer text-left border ${
                  isActive
                    ? 'bg-[#30000F] border-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/15'
                    : 'bg-[#1E000A] border-[#FFB4C8]/15 text-[#F4B5C8] hover:bg-[#30000F] hover:text-white'
                }`}
              >
                <div className={`w-8 h-8 rounded-xl flex items-center justify-center ${
                  isActive ? 'bg-[#FF2D6D] text-white shadow-md' : 'bg-[#30000F] text-[#FF2D6D]'
                }`}>
                  {sec.icon}
                </div>
                <div className="flex flex-col">
                  <div className="flex items-center gap-2">
                    <span className={`text-xs font-bold font-headline ${isActive ? 'text-white' : 'text-[#F4B5C8]'}`}>
                      {sec.label}
                    </span>
                    {sec.badge && (
                      <span className="text-[9px] font-mono px-1.5 py-0.2 rounded-full bg-[#3F0016] text-[#4ADE80] border border-[#4ADE80]/30 font-semibold">
                        {sec.badge}
                      </span>
                    )}
                  </div>
                  <span className="text-[10px] font-mono text-[#A26377]">
                    {sec.description}
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Main Section Content */}
      {activeSection === 'workspace' ? (
        <WorkspaceSettingsView
          onNavigateToAccess={onNavigateToAccess}
          onNavigateToProjects={onNavigateToProjects}
        />
      ) : activeSection === 'project' ? (
        <ProjectSettingsView
          initialProjectId={initialProjectId}
          onNavigateToSecrets={onNavigateToSecrets}
          onNavigateToAccess={onNavigateToAccess}
        />
      ) : (
        /* Account & Security Profile View */
        <div className="flex flex-col gap-6 max-w-3xl">
          <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6">
            <div>
              <h2 className="text-base font-headline font-bold text-white">User Profile &amp; Authentication Identity</h2>
              <p className="text-xs text-[#A26377]">
                Authenticated principal details, organizations, and security controls.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <span className="text-[10px] font-mono text-[#A26377] uppercase">Full Name</span>
                <span className="text-xs font-bold text-white">{user?.fullName || 'N/A'}</span>
              </div>

              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <span className="text-[10px] font-mono text-[#A26377] uppercase">Email Address</span>
                <span className="text-xs font-mono text-[#F4B5C8]">{user?.email || 'N/A'}</span>
              </div>

              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <span className="text-[10px] font-mono text-[#A26377] uppercase">Active Workspace Role</span>
                <span className="text-xs font-mono font-bold text-[#4ADE80]">{activeWorkspace?.role || 'MEMBER'}</span>
              </div>

              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-1">
                <span className="text-[10px] font-mono text-[#A26377] uppercase">Session Security</span>
                <span className="text-xs font-mono text-[#FFB4C8]">Stateless JJWT (24h TTL)</span>
              </div>
            </div>

            <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex items-center justify-between">
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-headline font-bold text-white">Multi-Factor Authentication (MFA)</span>
                <span className="text-[11px] text-[#A26377]">Step-up TOTP verification for protected reveals and production mutations.</span>
              </div>
              <span className="px-2.5 py-1 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#4ADE80] border border-[#4ADE80]/30 font-semibold">
                ENFORCED
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

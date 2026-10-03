import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { RotationDashboard } from './RotationDashboard';
import { RotationPoliciesView } from './RotationPoliciesView';
import { RotationJobsView } from './RotationJobsView';
import { RotationWizard } from './RotationWizard';
import { SecretLeasesView } from './SecretLeasesView';
import { SecretConsumersView } from './SecretConsumersView';
import { RotationImpactModal } from './RotationImpactModal';
import { CompromiseRemediationModal } from './CompromiseRemediationModal';
import {
  RotateCw,
  Calendar,
  History,
  Key,
  Cpu,
  Sparkles,
  Flame,
  ShieldCheck,
} from 'lucide-react';

export const RotationCenterView = () => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [activeTab, setActiveTab] = useState('overview'); // 'overview' | 'policies' | 'jobs' | 'leases' | 'consumers' | 'wizard'
  const [selectedSecretForRotation, setSelectedSecretForRotation] = useState(null);
  const [impactModalSecret, setImpactModalSecret] = useState(null);
  const [isEmergencyModalOpen, setIsEmergencyModalOpen] = useState(false);

  const handleStartRotation = (secretId) => {
    setSelectedSecretForRotation(secretId);
    setActiveTab('wizard');
  };

  const tabs = [
    { id: 'overview', label: 'Overview', icon: <RotateCw className="w-4 h-4" /> },
    { id: 'policies', label: 'Policies', icon: <Calendar className="w-4 h-4" /> },
    { id: 'jobs', label: 'Execution Jobs', icon: <History className="w-4 h-4" /> },
    { id: 'leases', label: 'Secret Leases', icon: <Key className="w-4 h-4" /> },
    { id: 'consumers', label: 'Workloads & SDKs', icon: <Cpu className="w-4 h-4" /> },
    { id: 'wizard', label: 'Rotation Wizard', icon: <Sparkles className="w-4 h-4 text-[#FF2D6D]" /> },
  ];

  return (
    <div className="flex-1 overflow-y-auto px-4 lg:px-8 py-6 max-w-7xl mx-auto w-full space-y-6">
      {/* Top Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[#FFB4C8]/10 pb-5">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-[#FF2D6D] to-[#818CF8] flex items-center justify-center text-white shadow-lg shadow-[#FF2D6D]/20">
            <RotateCw className="w-5 h-5 animate-spin-slow" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-headline font-bold text-white tracking-wide">
                Secret Rotation & Runtime Lifecycle
              </h1>
              <span className="px-2 py-0.5 rounded-md bg-[#FF2D6D]/20 border border-[#FF2D6D]/40 text-[#FF2D6D] text-[10px] font-mono font-bold">
                Phase 12
              </span>
            </div>
            <p className="text-xs text-[#F4B5C8]/70">
              Automated zero-downtime secret rollover, dynamic lease renewals, and live consumer dependency telemetry.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setIsEmergencyModalOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-red-950/60 hover:bg-red-900/80 border border-red-500/40 text-red-300 text-xs font-semibold transition-all cursor-pointer shadow-md"
          >
            <Flame className="w-3.5 h-3.5 text-red-400 animate-pulse" />
            <span>Remediate Compromise</span>
          </button>
        </div>
      </div>

      {/* Navigation Sub-Tabs */}
      <div className="flex items-center gap-1.5 overflow-x-auto border-b border-[#FFB4C8]/10 pb-1 scrollbar-none">
        {tabs.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-medium transition-all whitespace-nowrap cursor-pointer ${
                isActive
                  ? 'bg-[#3F0016] border border-[#FF2D6D]/40 text-white font-semibold shadow-inner'
                  : 'text-[#F4B5C8]/70 hover:text-white hover:bg-[#30000F]/40'
              }`}
            >
              {tab.icon}
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Tab Content */}
      {activeTab === 'overview' && (
        <RotationDashboard
          workspaceId={workspaceId}
          onNavigateToTab={setActiveTab}
          onStartRotation={handleStartRotation}
          onTriggerEmergency={() => setIsEmergencyModalOpen(true)}
        />
      )}

      {activeTab === 'policies' && (
        <RotationPoliciesView
          workspaceId={workspaceId}
          onStartRotationForSecret={handleStartRotation}
        />
      )}

      {activeTab === 'jobs' && (
        <RotationJobsView
          workspaceId={workspaceId}
          onViewImpact={(secretId) => setImpactModalSecret(secretId)}
        />
      )}

      {activeTab === 'leases' && <SecretLeasesView workspaceId={workspaceId} />}

      {activeTab === 'consumers' && <SecretConsumersView workspaceId={workspaceId} />}

      {activeTab === 'wizard' && (
        <RotationWizard
          workspaceId={workspaceId}
          initialSecretId={selectedSecretForRotation}
          onComplete={() => setActiveTab('jobs')}
          onCancel={() => setActiveTab('overview')}
        />
      )}

      {/* Modals */}
      {impactModalSecret && (
        <RotationImpactModal
          workspaceId={workspaceId}
          secretId={impactModalSecret}
          isOpen={Boolean(impactModalSecret)}
          onClose={() => setImpactModalSecret(null)}
        />
      )}

      <CompromiseRemediationModal
        workspaceId={workspaceId}
        isOpen={isEmergencyModalOpen}
        onClose={() => setIsEmergencyModalOpen(false)}
        onSuccess={() => setActiveTab('jobs')}
      />
    </div>
  );
};

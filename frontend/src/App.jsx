import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginScreen } from './components/auth/LoginScreen';
import { RegisterScreen } from './components/auth/RegisterScreen';
import { MfaChallengeScreen } from './components/auth/MfaChallengeScreen';
import { AppShell } from './components/layout/AppShell';
import { WorkspaceOverview } from './components/workspace/WorkspaceOverview';
import { MfaSuggestionModal } from './components/auth/MfaSuggestionModal';
import { MfaEnrollmentModal } from './components/settings/mfa/MfaEnrollmentModal';
import { Shield, Loader2 } from 'lucide-react';

// Route-level code-splitting for non-critical views
const ProjectsView = React.lazy(() => import('./components/project/ProjectsView').then(m => ({ default: m.ProjectsView })));
const SecretsView = React.lazy(() => import('./components/secrets/SecretsView').then(m => ({ default: m.SecretsView })));
const AccessControlCenterView = React.lazy(() => import('./components/access/AccessControlCenterView').then(m => ({ default: m.AccessControlCenterView })));
const MachineIdentitiesView = React.lazy(() => import('./components/machine/MachineIdentitiesView').then(m => ({ default: m.MachineIdentitiesView })));
const IntegrationsView = React.lazy(() => import('./components/integrations/IntegrationsView').then(m => ({ default: m.IntegrationsView })));
const SyncCenterView = React.lazy(() => import('./components/sync/SyncCenterView').then(m => ({ default: m.SyncCenterView })));
const RotationCenterView = React.lazy(() => import('./components/rotation/RotationCenterView').then(m => ({ default: m.RotationCenterView })));
const EventCenterView = React.lazy(() => import('./components/events/EventCenterView').then(m => ({ default: m.EventCenterView })));
const AutomationCenterView = React.lazy(() => import('./components/automation/AutomationCenterView').then(m => ({ default: m.AutomationCenterView })));
const WebhookCenterView = React.lazy(() => import('./components/webhook/WebhookCenterView').then(m => ({ default: m.WebhookCenterView })));
const SecurityOperationsView = React.lazy(() => import('./components/incident/SecurityOperationsView').then(m => ({ default: m.SecurityOperationsView })));
const RepositorySecurityView = React.lazy(() => import('./components/repository/RepositorySecurityView').then(m => ({ default: m.RepositorySecurityView })));
const SecurityOverviewView = React.lazy(() => import('./components/security/SecurityOverviewView').then(m => ({ default: m.SecurityOverviewView })));
const RiskCenterView = React.lazy(() => import('./components/risk/RiskCenterView').then(m => ({ default: m.RiskCenterView })));
const AuditLogsView = React.lazy(() => import('./components/audit/AuditLogsView').then(m => ({ default: m.AuditLogsView })));
const SettingsView = React.lazy(() => import('./components/settings/SettingsView').then(m => ({ default: m.SettingsView })));
const AiCopilotView = React.lazy(() => import('./components/ai/AiCopilotView').then(m => ({ default: m.AiCopilotView })));
const AiRemediationWorkbenchView = React.lazy(() => import('./components/ai/AiRemediationWorkbenchView').then(m => ({ default: m.AiRemediationWorkbenchView })));

const ViewLoader = () => (
  <div className="w-full h-96 flex flex-col items-center justify-center gap-3">
    <Loader2 className="w-6 h-6 animate-spin text-brand-primary" />
    <span className="text-xs font-mono text-text-secondary">Loading module...</span>
  </div>
);

const MainRouter = () => {
  const { isAuthenticated, isLoading, user, justRegistered, setJustRegistered } = useAuth();
  const [authView, setAuthView] = useState('login'); // 'login' | 'register' | 'mfa'
  const [mfaChallengeId, setMfaChallengeId] = useState(null);
  const [currentTab, setCurrentTab] = useState('dashboard'); // 'dashboard' | 'projects' | 'secrets' | 'rotation' | 'access' | 'integrations' | 'sync-center' | 'settings'
  const [targetProjectId, setTargetProjectId] = useState(null);
  const [targetEnvironmentId, setTargetEnvironmentId] = useState(null);
  const [settingsSection, setSettingsSection] = useState('workspace'); // 'workspace' | 'project' | 'profile'
  const [showMfaSuggestion, setShowMfaSuggestion] = useState(false);
  const [showMfaEnrollment, setShowMfaEnrollment] = useState(false);

  React.useEffect(() => {
    if (justRegistered) {
      setShowMfaSuggestion(true);
      setJustRegistered(false);
    }
  }, [justRegistered, setJustRegistered]);

  const handleNavigateToSecrets = (projectId, environmentId = null) => {
    setTargetProjectId(projectId);
    setTargetEnvironmentId(environmentId);
    setCurrentTab('secrets');
  };

  const handleNavigateToProjectSettings = (projectId) => {
    setTargetProjectId(projectId);
    setSettingsSection('project');
    setCurrentTab('settings');
  };

  const handleNavigateToIntegrations = (projectId = null) => {
    setTargetProjectId(projectId);
    setCurrentTab('integrations');
  };

  const handleNavigateToWorkspaceSettings = () => {
    setSettingsSection('workspace');
    setCurrentTab('settings');
  };

  if (isLoading) {
    return (
      <div className="min-h-screen w-full flex flex-col items-center justify-center bg-surface-container-lowest gap-4 font-body">
        <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-surface-container-high border border-outline-variant text-brand-primary">
          <Shield className="w-7 h-7 animate-pulse" />
        </div>
        <div className="flex items-center gap-2 text-xs font-mono text-text-secondary">
          <Loader2 className="w-3.5 h-3.5 animate-spin text-brand-primary" />
          <span>Verifying cryptographic session...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    if (authView === 'register') {
      return <RegisterScreen onNavigateToLogin={() => setAuthView('login')} />;
    }
    if (authView === 'mfa') {
      return (
        <MfaChallengeScreen
          challengeId={mfaChallengeId}
          onBackToLogin={() => {
            setMfaChallengeId(null);
            setAuthView('login');
          }}
          onSuccess={() => {
            setMfaChallengeId(null);
            setAuthView('login');
          }}
        />
      );
    }
    return (
      <LoginScreen
        onNavigateToRegister={() => setAuthView('register')}
        onNavigateToMfa={(challengeId) => {
          setMfaChallengeId(challengeId);
          setAuthView('mfa');
        }}
      />
    );
  }

  return (
    <>
      <AppShell activeTab={currentTab} onSelectTab={setCurrentTab}>
        <React.Suspense fallback={<ViewLoader />}>
          {currentTab === 'projects' ? (
            <ProjectsView
              onNavigateToSecrets={handleNavigateToSecrets}
              onNavigateToProjectSettings={handleNavigateToProjectSettings}
              onNavigateToIntegrations={handleNavigateToIntegrations}
            />
          ) : currentTab === 'secrets' ? (
            <SecretsView
              initialProjectId={targetProjectId}
              initialEnvironmentId={targetEnvironmentId}
            />
          ) : currentTab === 'rotation' ? (
            <RotationCenterView />
          ) : currentTab === 'access' ? (
            <AccessControlCenterView />
          ) : currentTab === 'machine-identities' ? (
            <MachineIdentitiesView />
          ) : currentTab === 'integrations' ? (
            <IntegrationsView initialProjectId={targetProjectId} />
          ) : currentTab === 'sync-center' || currentTab === 'drift' ? (
            <SyncCenterView />
          ) : currentTab === 'events' ? (
            <EventCenterView />
          ) : currentTab === 'automation' ? (
            <AutomationCenterView />
          ) : currentTab === 'webhooks' ? (
            <WebhookCenterView />
          ) : currentTab === 'incidents' ? (
            <SecurityOperationsView />
          ) : currentTab === 'repo-security' ? (
            <RepositorySecurityView />
          ) : currentTab === 'security-overview' ? (
            <SecurityOverviewView />
          ) : currentTab === 'risk-center' ? (
            <RiskCenterView
              onNavigateToAudit={() => setCurrentTab('audit-logs')}
              onNavigateToSecurityOverview={() => setCurrentTab('security-overview')}
            />
          ) : currentTab === 'audit-logs' ? (
            <AuditLogsView onNavigateToRiskCenter={() => setCurrentTab('risk-center')} />
          ) : currentTab === 'ai-assistant' ? (
            <AiCopilotView
              onNavigateToWorkbench={() => setCurrentTab('ai-workbench')}
              onNavigateToTab={(tab) => setCurrentTab(tab)}
              onNavigateToSecrets={handleNavigateToSecrets}
              onNavigateToProjects={() => setCurrentTab('projects')}
            />
          ) : currentTab === 'ai-workbench' ? (
            <AiRemediationWorkbenchView onNavigateToCopilot={() => setCurrentTab('ai-assistant')} />
          ) : currentTab === 'settings' ? (
            <SettingsView
              initialSection={settingsSection}
              initialProjectId={targetProjectId}
              onNavigateToSecrets={handleNavigateToSecrets}
              onNavigateToAccess={() => setCurrentTab('access')}
              onNavigateToProjects={() => setCurrentTab('projects')}
            />
          ) : (
            <WorkspaceOverview
              onNavigateToProjects={() => setCurrentTab('projects')}
              onNavigateToSecrets={handleNavigateToSecrets}
              onNavigateToAccess={() => setCurrentTab('access')}
              onNavigateToWorkspaceSettings={handleNavigateToWorkspaceSettings}
            />
          )}
        </React.Suspense>
      </AppShell>

      <MfaSuggestionModal
        isOpen={showMfaSuggestion}
        onEnableMfa={() => {
          setShowMfaSuggestion(false);
          setShowMfaEnrollment(true);
        }}
        onDismiss={() => setShowMfaSuggestion(false)}
      />

      <MfaEnrollmentModal
        isOpen={showMfaEnrollment}
        userEmail={user?.email}
        onClose={() => setShowMfaEnrollment(false)}
        onSuccess={() => setShowMfaEnrollment(false)}
      />
    </>
  );
};

export function App() {
  return (
    <AuthProvider>
      <MainRouter />
    </AuthProvider>
  );
}

export default App;

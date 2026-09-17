import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginScreen } from './components/auth/LoginScreen';
import { RegisterScreen } from './components/auth/RegisterScreen';
import { MfaChallengeScreen } from './components/auth/MfaChallengeScreen';
import { AppShell } from './components/layout/AppShell';
import { WorkspaceOverview } from './components/workspace/WorkspaceOverview';
import { ProjectsView } from './components/project/ProjectsView';
import { SecretsView } from './components/secrets/SecretsView';
import { Shield, Loader2 } from 'lucide-react';

const MainRouter = () => {
  const { isAuthenticated, isLoading } = useAuth();
  const [authView, setAuthView] = useState('login'); // 'login' | 'register' | 'mfa'
  const [currentTab, setCurrentTab] = useState('dashboard'); // 'dashboard' | 'projects' | 'secrets'

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
          onBackToLogin={() => setAuthView('login')}
          onSuccess={() => setAuthView('login')}
        />
      );
    }
    return (
      <LoginScreen
        onNavigateToRegister={() => setAuthView('register')}
        onNavigateToMfa={() => setAuthView('mfa')}
      />
    );
  }

  return (
    <AppShell activeTab={currentTab} onSelectTab={setCurrentTab}>
      {currentTab === 'projects' ? (
        <ProjectsView />
      ) : currentTab === 'secrets' ? (
        <SecretsView />
      ) : (
        <WorkspaceOverview />
      )}
    </AppShell>
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

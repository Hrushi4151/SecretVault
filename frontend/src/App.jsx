import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginScreen } from './components/auth/LoginScreen';
import { RegisterScreen } from './components/auth/RegisterScreen';
import { MfaChallengeScreen } from './components/auth/MfaChallengeScreen';
import { AppShell } from './components/layout/AppShell';
import { WorkspaceOverview } from './components/workspace/WorkspaceOverview';
import { Shield, Loader2 } from 'lucide-react';

const MainRouter = () => {
  const { isAuthenticated, isLoading } = useAuth();
  const [authView, setAuthView] = useState('login'); // 'login' | 'register' | 'mfa'

  if (isLoading) {
    return (
      <div className="min-h-screen w-full flex flex-col items-center justify-center bg-vault-bg gap-4">
        <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-white/[0.05] border border-white/[0.12] shadow-inner text-vault-primary">
          <Shield className="w-7 h-7 animate-pulse" />
          <div className="absolute -inset-1 rounded-2xl bg-vault-primary/20 blur-md -z-10" />
        </div>
        <div className="flex items-center gap-2 text-xs font-mono text-vault-text-muted">
          <Loader2 className="w-3.5 h-3.5 animate-spin text-vault-primary" />
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
    <AppShell>
      <WorkspaceOverview />
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

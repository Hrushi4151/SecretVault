import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { ForgotPasswordModal } from './ForgotPasswordModal';
import { Shield, Mail, Lock, Eye, EyeOff, ArrowRight, Sparkles } from 'lucide-react';

export const LoginScreen = ({ onNavigateToRegister, onNavigateToMfa }) => {
  const { login, isLoading, error, clearError } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isForgotModalOpen, setIsForgotModalOpen] = useState(false);
  const [formError, setFormError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    clearError();
    setFormError(null);

    if (!email.trim() || !password) {
      setFormError('Please provide both email and password.');
      return;
    }

    try {
      await login({ email: email.trim(), password });
    } catch (err) {
      // Handled by context
    }
  };

  return (
    <div className="relative min-h-screen w-full flex items-center justify-center p-4 bg-vault-bg overflow-hidden">
      {/* Subtle Background Glows */}
      <div className="absolute -top-40 -left-40 w-96 h-96 rounded-full bg-vault-primary/15 blur-[120px] pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 w-96 h-96 rounded-full bg-vault-primary/10 blur-[140px] pointer-events-none" />

      {/* Main Glass Card */}
      <div className="relative w-full max-w-md rounded-2xl bg-[#0D1117]/85 border border-white/[0.12] p-8 backdrop-blur-2xl shadow-2xl shadow-black/80 flex flex-col gap-6 z-10 animate-fade-in">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center gap-3">
          <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-white/[0.05] border border-white/[0.12] shadow-inner">
            <Shield className="w-7 h-7 text-vault-primary" />
            <div className="absolute -inset-1 rounded-2xl bg-vault-primary/20 blur-md -z-10" />
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold tracking-tight text-vault-text font-sans flex items-center justify-center gap-2">
              SecretVault
              <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded-full bg-vault-primary/20 text-vault-primary-light border border-vault-primary/30">
                v1.0
              </span>
            </h1>
            <p className="text-xs text-vault-text-secondary">
              Zero-Trust DevSecOps Secret Management Control Plane
            </p>
          </div>
        </div>

        {/* Security Attestation Banner */}
        <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-white/[0.03] border border-white/[0.06] text-[11px] text-vault-text-muted font-mono">
          <span className="flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-vault-success animate-pulse" />
            SHA-256 Verified Enclave
          </span>
          <span>BCrypt Strict</span>
        </div>

        {/* Error Alerts */}
        {(formError || error) && (
          <Alert
            variant="danger"
            message={formError || error || 'Authentication failed.'}
            onDismiss={() => {
              setFormError(null);
              clearError();
            }}
          />
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Input
            label="Work Email"
            type="email"
            id="login-email"
            placeholder="alice@company.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            leftIcon={<Mail className="w-4 h-4" />}
            required
            autoComplete="email"
            autoFocus
          />

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label
                htmlFor="login-password"
                className="text-xs font-medium text-vault-text-secondary"
              >
                Password
              </label>
              <button
                type="button"
                onClick={() => setIsForgotModalOpen(true)}
                className="text-[11px] text-vault-primary-light hover:text-vault-primary hover:underline transition-colors"
              >
                Forgot password?
              </button>
            </div>
            <Input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              leftIcon={<Lock className="w-4 h-4" />}
              rightIcon={
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="p-1 hover:text-vault-text transition-colors"
                  title={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              }
              required
              autoComplete="current-password"
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            size="md"
            isLoading={isLoading}
            rightIcon={<ArrowRight className="w-4 h-4" />}
            className="w-full mt-2"
          >
            Sign In to Vault
          </Button>
        </form>

        {/* Footer */}
        <div className="flex flex-col items-center gap-3 pt-2 border-t border-white/[0.08] text-xs text-vault-text-secondary">
          <div>
            Don't have an organization yet?{' '}
            <button
              type="button"
              onClick={onNavigateToRegister}
              className="font-semibold text-vault-primary-light hover:text-vault-primary hover:underline transition-colors"
            >
              Sign up now
            </button>
          </div>

          {onNavigateToMfa && (
            <button
              type="button"
              onClick={onNavigateToMfa}
              className="text-[11px] text-vault-text-muted hover:text-vault-text flex items-center gap-1 transition-colors"
            >
              <Sparkles className="w-3 h-3 text-vault-primary" />
              Hardware MFA Step-Up Mode
            </button>
          )}
        </div>
      </div>

      <ForgotPasswordModal
        isOpen={isForgotModalOpen}
        onClose={() => setIsForgotModalOpen(false)}
      />
    </div>
  );
};

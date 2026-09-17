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
    <div className="relative min-h-screen w-full flex items-center justify-center p-4 bg-[#0D0106] text-white font-body overflow-hidden">
      {/* Background Accent Gradients */}
      <div className="absolute -top-40 -left-40 w-96 h-96 rounded-full bg-[#760031]/30 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 w-96 h-96 rounded-full bg-[#580023]/40 blur-3xl pointer-events-none" />

      {/* Main Glass Card */}
      <div className="relative w-full max-w-md rounded-2xl bg-[#30000F]/95 backdrop-blur-2xl border border-[#FFB4C8]/25 p-8 shadow-2xl shadow-black/90 flex flex-col gap-6 z-10 animate-fade-in">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center gap-3">
          <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/40 shadow-inner shadow-black">
            <Shield className="w-7 h-7 text-[#FF2D6D]" />
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-headline font-bold tracking-tight text-white flex items-center justify-center gap-2">
              SecretVault
              <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded-full bg-[#4A001C] text-[#FF2D6D] border border-[#FF2D6D]/30">
                v1.0
              </span>
            </h1>
            <span className="text-[11px] font-mono tracking-widest text-[#A26377] uppercase font-semibold">
              DevSecOps Platform
            </span>
          </div>
        </div>

        {/* Security Attestation Banner */}
        <div className="flex items-center justify-between px-3.5 py-2 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15 text-[11px] text-[#F4B5C8] font-mono">
          <span className="flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-[#34D399] animate-pulse" />
            SECURITY LEVEL: MAXIMUM
          </span>
          <span className="text-[#A26377]">SHA256:VERIFIED</span>
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
            placeholder="developer@company.com"
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
                className="text-xs font-semibold text-[#F4B5C8]"
              >
                Password
              </label>
              <button
                type="button"
                onClick={() => setIsForgotModalOpen(true)}
                className="text-[11px] text-[#FF2D6D] hover:text-[#FF4D85] hover:underline transition-colors"
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
                  className="p-1 hover:text-white transition-colors text-[#A26377]"
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
        <div className="flex flex-col items-center gap-3 pt-3 border-t border-[#FFB4C8]/15 text-xs text-[#F4B5C8]">
          <div>
            Don't have an organization yet?{' '}
            <button
              type="button"
              onClick={onNavigateToRegister}
              className="font-semibold text-[#FF2D6D] hover:text-[#FF4D85] hover:underline transition-colors"
            >
              Sign up now
            </button>
          </div>

          {onNavigateToMfa && (
            <button
              type="button"
              onClick={onNavigateToMfa}
              className="text-[11px] text-[#A26377] hover:text-white flex items-center gap-1 transition-colors"
            >
              <Sparkles className="w-3 h-3 text-[#FF2D6D]" />
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

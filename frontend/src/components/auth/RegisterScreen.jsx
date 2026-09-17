import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { Shield, Mail, Lock, User, Building2, Eye, EyeOff, ArrowRight, Check } from 'lucide-react';

export const RegisterScreen = ({ onNavigateToLogin }) => {
  const { register, isLoading, error, clearError } = useAuth();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState(null);

  const isPasswordLengthValid = password.length >= 8;
  const hasUpperCase = /[A-Z]/.test(password);
  const hasNumber = /[0-9]/.test(password);

  const handleSubmit = async (e) => {
    e.preventDefault();
    clearError();
    setFormError(null);

    if (!fullName.trim()) {
      setFormError('Full name is required.');
      return;
    }
    if (!email.trim() || !email.includes('@')) {
      setFormError('Please enter a valid work email.');
      return;
    }
    if (password.length < 8) {
      setFormError('Password must be at least 8 characters long.');
      return;
    }

    try {
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        organizationName: organizationName.trim() || undefined,
      });
    } catch (err) {
      // Handled by context
    }
  };

  return (
    <div className="relative min-h-screen w-full flex items-center justify-center p-4 bg-vault-bg overflow-hidden">
      {/* Subtle Background Glows */}
      <div className="absolute -top-40 -right-40 w-96 h-96 rounded-full bg-vault-primary/15 blur-[120px] pointer-events-none" />
      <div className="absolute -bottom-40 -left-40 w-96 h-96 rounded-full bg-vault-primary/10 blur-[140px] pointer-events-none" />

      {/* Main Glass Card */}
      <div className="relative w-full max-w-lg rounded-2xl bg-[#0D1117]/85 border border-white/[0.12] p-8 backdrop-blur-2xl shadow-2xl shadow-black/80 flex flex-col gap-6 z-10 animate-fade-in">
        {/* Header */}
        <div className="flex flex-col items-center text-center gap-3">
          <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-white/[0.05] border border-white/[0.12] shadow-inner">
            <Shield className="w-7 h-7 text-vault-primary" />
            <div className="absolute -inset-1 rounded-2xl bg-vault-primary/20 blur-md -z-10" />
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold tracking-tight text-vault-text font-sans">
              Create your SecretVault Account
            </h1>
            <p className="text-xs text-vault-text-secondary">
              Provision a zero-knowledge enclave and your multi-tenant workspace.
            </p>
          </div>
        </div>

        {/* Error Alerts */}
        {(formError || error) && (
          <Alert
            variant="danger"
            message={formError || error || 'Registration failed.'}
            onDismiss={() => {
              setFormError(null);
              clearError();
            }}
          />
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Input
              label="Full Name"
              type="text"
              id="register-fullname"
              placeholder="Alice Vance"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              leftIcon={<User className="w-4 h-4" />}
              required
              autoFocus
            />

            <Input
              label="Work Email"
              type="email"
              id="register-email"
              placeholder="alice@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="w-4 h-4" />}
              required
            />
          </div>

          <Input
            label="Initial Organization (Optional)"
            type="text"
            id="register-org"
            placeholder="Acme Corp (defaults to Personal Org)"
            value={organizationName}
            onChange={(e) => setOrganizationName(e.target.value)}
            leftIcon={<Building2 className="w-4 h-4" />}
            helperText="We will automatically provision your root workspace and cryptographic enclave."
          />

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="register-password"
              className="text-xs font-medium text-vault-text-secondary"
            >
              Master Password
            </label>
            <Input
              id="register-password"
              type={showPassword ? 'text' : 'password'}
              placeholder="Minimum 8 characters"
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
            />

            {/* Password Validation Hints */}
            <div className="grid grid-cols-3 gap-2 pt-1 text-[11px] font-mono">
              <span
                className={`flex items-center gap-1 ${
                  isPasswordLengthValid ? 'text-vault-success' : 'text-vault-text-muted'
                }`}
              >
                <Check className="w-3 h-3" /> 8+ chars
              </span>
              <span
                className={`flex items-center gap-1 ${
                  hasUpperCase ? 'text-vault-success' : 'text-vault-text-muted'
                }`}
              >
                <Check className="w-3 h-3" /> Uppercase
              </span>
              <span
                className={`flex items-center gap-1 ${
                  hasNumber ? 'text-vault-success' : 'text-vault-text-muted'
                }`}
              >
                <Check className="w-3 h-3" /> Number
              </span>
            </div>
          </div>

          <Button
            type="submit"
            variant="primary"
            size="md"
            isLoading={isLoading}
            rightIcon={<ArrowRight className="w-4 h-4" />}
            className="w-full mt-2"
          >
            Create Account &amp; Workspace
          </Button>
        </form>

        {/* Footer */}
        <div className="text-center pt-2 border-t border-white/[0.08] text-xs text-vault-text-secondary">
          Already have an account?{' '}
          <button
            type="button"
            onClick={onNavigateToLogin}
            className="font-semibold text-vault-primary-light hover:text-vault-primary hover:underline transition-colors"
          >
            Sign in
          </button>
        </div>
      </div>
    </div>
  );
};

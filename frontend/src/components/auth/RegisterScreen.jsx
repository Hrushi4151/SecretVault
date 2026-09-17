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
    <div className="relative min-h-screen w-full flex items-center justify-center p-4 bg-[#0D0106] text-white font-body overflow-hidden">
      {/* Background Accent Gradients */}
      <div className="absolute -top-40 -right-40 w-96 h-96 rounded-full bg-[#760031]/30 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-40 -left-40 w-96 h-96 rounded-full bg-[#580023]/40 blur-3xl pointer-events-none" />

      {/* Main Glass Card */}
      <div className="relative w-full max-w-lg rounded-2xl bg-[#30000F]/95 backdrop-blur-2xl border border-[#FFB4C8]/25 p-8 shadow-2xl shadow-black/90 flex flex-col gap-6 z-10 animate-fade-in">
        {/* Header */}
        <div className="flex flex-col items-center text-center gap-3">
          <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/40 shadow-inner shadow-black">
            <Shield className="w-7 h-7 text-[#FF2D6D]" />
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-headline font-bold tracking-tight text-white">
              Create your SecretVault Account
            </h1>
            <p className="text-xs text-[#F4B5C8]">
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
              className="text-xs font-semibold text-[#F4B5C8]"
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
                  className="p-1 hover:text-white transition-colors text-[#A26377]"
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
                  isPasswordLengthValid ? 'text-[#34D399] font-bold' : 'text-[#A26377]/60'
                }`}
              >
                <Check className="w-3 h-3" /> 8+ chars
              </span>
              <span
                className={`flex items-center gap-1 ${
                  hasUpperCase ? 'text-[#34D399] font-bold' : 'text-[#A26377]/60'
                }`}
              >
                <Check className="w-3 h-3" /> Uppercase
              </span>
              <span
                className={`flex items-center gap-1 ${
                  hasNumber ? 'text-[#34D399] font-bold' : 'text-[#A26377]/60'
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
        <div className="text-center pt-3 border-t border-[#FFB4C8]/15 text-xs text-[#F4B5C8]">
          Already have an account?{' '}
          <button
            type="button"
            onClick={onNavigateToLogin}
            className="font-semibold text-[#FF2D6D] hover:text-[#FF4D85] hover:underline transition-colors"
          >
            Sign in
          </button>
        </div>
      </div>
    </div>
  );
};

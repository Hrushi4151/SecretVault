import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { authApi } from '../../api/auth';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import {
  Shield,
  Mail,
  Lock,
  User,
  Building2,
  Eye,
  EyeOff,
  ArrowRight,
  ArrowLeft,
  Check,
  KeyRound,
  RefreshCw,
  Sparkles,
} from 'lucide-react';

export const RegisterScreen = ({ onNavigateToLogin }) => {
  const { verifyRegistrationOtp, isLoading, error, clearError } = useAuth();
  const [step, setStep] = useState(1); // 1: Enter details -> 2: Verify OTP
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [otp, setOtp] = useState('');
  const [formError, setFormError] = useState(null);
  const [isSendingOtp, setIsSendingOtp] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [deliveryPreview, setDeliveryPreview] = useState(null);

  const isPasswordLengthValid = password.length >= 8;
  const hasUpperCase = /[A-Z]/.test(password);
  const hasNumber = /[0-9]/.test(password);

  useEffect(() => {
    let timer;
    if (resendCooldown > 0) {
      timer = setInterval(() => {
        setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [resendCooldown]);

  useEffect(() => {
    if (step === 2 && email && typeof authApi?.getOtpPreview === 'function') {
      authApi
        .getOtpPreview({ email: email.trim().toLowerCase(), type: 'reg' })
        .then((res) => {
          if (res) setDeliveryPreview(res);
        })
        .catch(() => {});
    }
  }, [step, email, resendCooldown]);

  const handleSendOtp = async (e) => {
    e.preventDefault();
    clearError();
    setFormError(null);

    if (!fullName.trim()) {
      setFormError('Full name is required.');
      return;
    }

    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail.endsWith('@gmail.com') && !normalizedEmail.endsWith('@example.com')) {
      setFormError('Registration is restricted to Google Mail (@gmail.com) accounts only.');
      return;
    }

    if (password.length < 8) {
      setFormError('Password must be at least 8 characters long.');
      return;
    }

    setIsSendingOtp(true);
    try {
      await authApi.sendRegistrationOtp({
        fullName: fullName.trim(),
        email: normalizedEmail,
        password,
        organizationName: organizationName.trim() || undefined,
      });
      setStep(2);
      setResendCooldown(60);
      try {
        if (typeof authApi?.getOtpPreview === 'function') {
          const preview = await authApi.getOtpPreview({ email: normalizedEmail, type: 'reg' });
          setDeliveryPreview(preview);
        }
      } catch (e) {}
    } catch (err) {
      const msg = err.payload?.message || err.message || 'Failed to dispatch verification code.';
      setFormError(msg);
    } finally {
      setIsSendingOtp(false);
    }
  };

  const handleResendOtp = async () => {
    if (resendCooldown > 0 || isSendingOtp) return;
    clearError();
    setFormError(null);
    setIsSendingOtp(true);
    try {
      const normalizedEmail = email.trim().toLowerCase();
      await authApi.sendRegistrationOtp({
        fullName: fullName.trim(),
        email: normalizedEmail,
        password,
        organizationName: organizationName.trim() || undefined,
      });
      setResendCooldown(60);
      try {
        if (typeof authApi?.getOtpPreview === 'function') {
          const preview = await authApi.getOtpPreview({ email: normalizedEmail, type: 'reg' });
          setDeliveryPreview(preview);
        }
      } catch (e) {}
    } catch (err) {
      const msg = err.payload?.message || err.message || 'Failed to resend verification code.';
      setFormError(msg);
    } finally {
      setIsSendingOtp(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    clearError();
    setFormError(null);

    const cleanOtp = otp.trim();
    if (!cleanOtp || cleanOtp.length !== 6 || !/^\d+$/.test(cleanOtp)) {
      setFormError('Please enter a valid 6-digit numeric verification code.');
      return;
    }

    try {
      await verifyRegistrationOtp({
        email: email.trim().toLowerCase(),
        otp: cleanOtp,
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
            {step === 1 ? (
              <Shield className="w-7 h-7 text-[#FF2D6D]" />
            ) : (
              <KeyRound className="w-7 h-7 text-[#FF2D6D] animate-pulse" />
            )}
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-headline font-bold tracking-tight text-white">
              {step === 1 ? 'Create your SecretVault Enclave' : 'Verify Your Gmail Account'}
            </h1>
            <p className="text-xs text-[#F4B5C8]">
              {step === 1
                ? 'Only verified Google Mail accounts are permitted to provision workspaces.'
                : `We've transmitted a 6-digit cryptographic OTP to ${email.trim().toLowerCase()}`}
            </p>
          </div>
        </div>

        {/* Step Indicator */}
        <div className="flex items-center justify-center gap-2">
          <div
            className={`h-1.5 rounded-full transition-all duration-300 ${
              step === 1 ? 'w-10 bg-[#FF2D6D]' : 'w-4 bg-[#34D399]'
            }`}
          />
          <div
            className={`h-1.5 rounded-full transition-all duration-300 ${
              step === 2 ? 'w-10 bg-[#FF2D6D]' : 'w-4 bg-[#4A001C]'
            }`}
          />
        </div>

        {/* Error Alerts */}
        {(formError || error) && (
          <Alert
            variant="danger"
            message={formError || error || 'Action failed.'}
            onDismiss={() => {
              setFormError(null);
              clearError();
            }}
          />
        )}

        {step === 1 ? (
          /* Step 1: Input Account Details */
          <form onSubmit={handleSendOtp} className="flex flex-col gap-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Input
                label="Full Name"
                type="text"
                id="register-fullname"
                placeholder="Ada Lovelace"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                leftIcon={<User className="w-4 h-4" />}
                required
                autoFocus
              />

              <Input
                label="Google Mail (Gmail)"
                type="email"
                id="register-email"
                placeholder="engineer@gmail.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                leftIcon={<Mail className="w-4 h-4" />}
                required
                helperText="Must be a valid @gmail.com address"
              />
            </div>

            <Input
              label="Organization Name (Optional)"
              type="text"
              id="register-org"
              placeholder="Acme Corp (defaults to Personal Enclave)"
              value={organizationName}
              onChange={(e) => setOrganizationName(e.target.value)}
              leftIcon={<Building2 className="w-4 h-4" />}
              helperText="We will automatically provision your root workspace and zero-knowledge enclave."
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
              isLoading={isSendingOtp}
              rightIcon={<ArrowRight className="w-4 h-4" />}
              className="w-full mt-2"
            >
              Verify Gmail &amp; Continue
            </Button>
          </form>
        ) : (
          /* Step 2: OTP Verification */
          <form onSubmit={handleVerifyOtp} className="flex flex-col gap-4">
            <div className="p-4 rounded-xl bg-[#20000A] border border-[#FFB4C8]/15 flex items-center gap-3">
              <Mail className="w-6 h-6 text-[#FF2D6D] shrink-0" />
              <div className="flex flex-col text-xs">
                <span className="text-[#F4B5C8]">Code dispatched to:</span>
                <span className="font-mono text-white font-bold">{email.trim().toLowerCase()}</span>
              </div>
            </div>

            {deliveryPreview && deliveryPreview.smtpDelivered && (
              <div className="flex items-center gap-2 p-2.5 rounded-xl bg-[#064E3B]/40 border border-[#34D399]/40 text-xs text-[#34D399]">
                <Check className="w-4 h-4 shrink-0" />
                <span>Verification email successfully transmitted to your Gmail inbox (Live Delivery via Pingram).</span>
              </div>
            )}

            {deliveryPreview && !deliveryPreview.smtpDelivered && (
              <div className="flex flex-col gap-2 p-3 rounded-xl bg-[#2A1700]/80 border border-[#F59E0B]/50 text-xs text-[#FDE68A]">
                <div className="flex items-start gap-2">
                  <Sparkles className="w-4 h-4 text-[#F59E0B] shrink-0 mt-0.5" />
                  <div className="flex flex-col gap-0.5">
                    <span className="font-semibold text-white">Sandbox Delivery: SMTP Not Yet Configured in .env</span>
                    <span className="text-[#FDE68A]/80 text-[11px]">
                      To send live emails to Google Mail inboxes, add your Gmail App Password to <code className="bg-black/50 px-1 py-0.5 rounded text-white">SPRING_MAIL_PASSWORD</code> in <code className="bg-black/50 px-1 py-0.5 rounded text-white">.env</code>.
                    </span>
                  </div>
                </div>
                {deliveryPreview.otp && (
                  <div className="flex items-center justify-between pt-1 border-t border-[#F59E0B]/20">
                    <span className="text-[11px]">
                      Your OTP code: <strong className="font-mono text-base tracking-widest text-white ml-1">{deliveryPreview.otp}</strong>
                    </span>
                    <button
                      type="button"
                      onClick={() => setOtp(deliveryPreview.otp)}
                      className="px-2.5 py-1 rounded bg-[#F59E0B] text-black font-semibold text-[11px] hover:bg-[#FBBF24] transition-colors"
                    >
                      Auto-fill Code
                    </button>
                  </div>
                )}
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="register-otp"
                className="text-xs font-semibold text-[#F4B5C8]"
              >
                6-Digit Verification Code
              </label>
              <Input
                id="register-otp"
                type="text"
                maxLength={6}
                placeholder="123456"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                leftIcon={<KeyRound className="w-4 h-4" />}
                className="tracking-widest font-mono text-lg text-center"
                required
                autoFocus
                helperText="Enter the 6-digit code received in your Google Mail inbox (expires in 10 minutes)"
              />
            </div>

            <div className="flex items-center justify-between text-xs text-[#F4B5C8] pt-1">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="flex items-center gap-1 hover:text-white transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" /> Back to details
              </button>

              <button
                type="button"
                onClick={handleResendOtp}
                disabled={resendCooldown > 0 || isSendingOtp}
                className={`flex items-center gap-1 transition-colors ${
                  resendCooldown > 0
                    ? 'text-[#A26377] cursor-not-allowed'
                    : 'text-[#FF2D6D] hover:text-[#FF4D85]'
                }`}
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isSendingOtp ? 'animate-spin' : ''}`} />
                {resendCooldown > 0 ? `Resend code in ${resendCooldown}s` : 'Resend code'}
              </button>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={isLoading}
              rightIcon={<ArrowRight className="w-4 h-4" />}
              className="w-full mt-2"
            >
              Confirm OTP &amp; Create Account
            </Button>
          </form>
        )}

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

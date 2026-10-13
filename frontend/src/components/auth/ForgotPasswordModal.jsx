import React, { useState, useEffect } from 'react';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { authApi } from '../../api/auth';
import {
  Mail,
  CheckCircle2,
  KeyRound,
  ShieldAlert,
  ShieldCheck,
  Lock,
  Eye,
  EyeOff,
  ArrowRight,
  ArrowLeft,
  RefreshCw,
  Check,
  Sparkles,
} from 'lucide-react';

export const ForgotPasswordModal = ({ isOpen, onClose }) => {
  // Steps:
  // 1: Enter Gmail
  // 2: Enter 6-digit Email OTP
  // 3: Step-Up MFA (if user has MFA enabled)
  // 4: Set New Password
  // 5: Complete / Success
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [resetToken, setResetToken] = useState(null);
  const [mfaMode, setMfaMode] = useState('totp'); // 'totp' | 'recovery'
  const [mfaCode, setMfaCode] = useState('');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [deliveryPreview, setDeliveryPreview] = useState(null);

  const isPasswordLengthValid = newPassword.length >= 8;
  const hasUpperCase = /[A-Z]/.test(newPassword);
  const hasNumber = /[0-9]/.test(newPassword);
  const passwordsMatch = newPassword.length > 0 && newPassword === confirmPassword;

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
        .getOtpPreview({ email: email.trim().toLowerCase(), type: 'pwd' })
        .then((res) => {
          if (res) setDeliveryPreview(res);
        })
        .catch(() => {});
    }
  }, [step, email, resendCooldown]);

  const handleResetState = () => {
    setStep(1);
    setEmail('');
    setOtp('');
    setResetToken(null);
    setMfaMode('totp');
    setMfaCode('');
    setRecoveryCode('');
    setNewPassword('');
    setConfirmPassword('');
    setShowPassword(false);
    setIsLoading(false);
    setError(null);
    setResendCooldown(0);
    setDeliveryPreview(null);
    onClose();
  };

  // Step 1: Send OTP to Gmail
  const handleRequestOtp = async (e) => {
    e.preventDefault();
    setError(null);

    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail.endsWith('@gmail.com') && !normalizedEmail.endsWith('@example.com')) {
      setError('Password reset is restricted to Google Mail (@gmail.com) accounts only.');
      return;
    }

    setIsLoading(true);
    try {
      await authApi.requestPasswordReset({ email: normalizedEmail });
      setStep(2);
      setResendCooldown(60);
      try {
        if (typeof authApi?.getOtpPreview === 'function') {
          const preview = await authApi.getOtpPreview({ email: normalizedEmail, type: 'pwd' });
          setDeliveryPreview(preview);
        }
      } catch (e) {}
    } catch (err) {
      const msg = err.payload?.message || err.message || 'Failed to dispatch password reset code.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  // Resend OTP
  const handleResendOtp = async () => {
    if (resendCooldown > 0 || isLoading) return;
    setError(null);
    setIsLoading(true);
    try {
      const normalizedEmail = email.trim().toLowerCase();
      await authApi.requestPasswordReset({ email: normalizedEmail });
      setResendCooldown(60);
      try {
        if (typeof authApi?.getOtpPreview === 'function') {
          const preview = await authApi.getOtpPreview({ email: normalizedEmail, type: 'pwd' });
          setDeliveryPreview(preview);
        }
      } catch (e) {}
    } catch (err) {
      const msg = err.payload?.message || err.message || 'Failed to resend code.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  // Step 2: Verify OTP
  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setError(null);

    const cleanOtp = otp.trim();
    if (!cleanOtp || cleanOtp.length !== 6 || !/^\d+$/.test(cleanOtp)) {
      setError('Please enter a valid 6-digit numeric verification code.');
      return;
    }

    setIsLoading(true);
    try {
      const res = await authApi.verifyPasswordResetOtp({
        email: email.trim().toLowerCase(),
        otp: cleanOtp,
      });

      setResetToken(res.resetToken);
      if (res.mfaRequired) {
        setStep(3); // Enforce Step-Up MFA
      } else {
        setStep(4); // Direct to new password
      }
    } catch (err) {
      const msg = err.payload?.message || err.message || 'Invalid or expired verification code.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  // Step 3: Verify MFA (TOTP or Recovery Code)
  const handleVerifyMfa = async (e) => {
    e.preventDefault();
    setError(null);

    if (mfaMode === 'totp' && (!mfaCode.trim() || mfaCode.trim().length !== 6)) {
      setError('Please enter your 6-digit authenticator code.');
      return;
    }
    if (mfaMode === 'recovery' && !recoveryCode.trim()) {
      setError('Please enter an unused recovery code.');
      return;
    }

    setIsLoading(true);
    try {
      await authApi.verifyPasswordResetMfa({
        resetToken,
        code: mfaMode === 'totp' ? mfaCode.trim() : undefined,
        recoveryCode: mfaMode === 'recovery' ? recoveryCode.trim() : undefined,
      });
      setStep(4);
    } catch (err) {
      const msg = err.payload?.message || err.message || 'MFA verification failed. Please try again.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  // Step 4: Set New Password
  const handleSetNewPassword = async (e) => {
    e.preventDefault();
    setError(null);

    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setIsLoading(true);
    try {
      await authApi.resetPassword({
        resetToken,
        newPassword,
      });
      setStep(5);
    } catch (err) {
      const msg = err.payload?.message || err.message || 'Failed to reset password.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleResetState}
      title={
        step === 5
          ? 'Password Reset Successful'
          : step === 3
          ? 'Step-Up MFA Verification'
          : 'Reset Account Password'
      }
      description={
        step === 1
          ? 'Enter your registered Google Mail address to receive a secure verification code.'
          : step === 2
          ? `Enter the 6-digit OTP code transmitted to ${email}`
          : step === 3
          ? 'Your account is fortified with MFA. Provide your authenticator code to proceed.'
          : step === 4
          ? 'Create a strong master password to secure your enclave.'
          : 'Your credentials have been securely updated.'
      }
      maxWidth="md"
    >
      <div className="flex flex-col gap-4 text-white font-body">
        {/* Step Indicator (Steps 1-4) */}
        {step < 5 && (
          <div className="flex items-center justify-between px-2 pb-1 border-b border-[#FFB4C8]/15 text-[11px] font-mono text-[#F4B5C8]">
            <span className="flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-[#FF2D6D]" />
              STAGE {step} OF 4
            </span>
            <span className="text-[#A26377]">
              {step === 1 && 'GMAIL DISPATCH'}
              {step === 2 && 'EMAIL OTP'}
              {step === 3 && 'MFA STEP-UP'}
              {step === 4 && 'NEW CREDENTIALS'}
            </span>
          </div>
        )}

        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}

        {/* STEP 1: Enter Gmail */}
        {step === 1 && (
          <form onSubmit={handleRequestOtp} className="flex flex-col gap-4">
            <Input
              label="Google Mail (Gmail)"
              type="email"
              placeholder="user@gmail.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="w-4 h-4" />}
              required
              autoFocus
              helperText="Only verified @gmail.com accounts are eligible for password recovery."
            />

            <div className="flex items-center justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={handleResetState}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="primary"
                isLoading={isLoading}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Send Reset Code
              </Button>
            </div>
          </form>
        )}

        {/* STEP 2: Enter Email OTP */}
        {step === 2 && (
          <form onSubmit={handleVerifyOtp} className="flex flex-col gap-4">
            <div className="p-3.5 rounded-xl bg-[#20000A] border border-[#FFB4C8]/15 flex items-center gap-3">
              <Mail className="w-5 h-5 text-[#FF2D6D] shrink-0" />
              <div className="flex flex-col text-xs">
                <span className="text-[#F4B5C8]">Verification code sent to:</span>
                <span className="font-mono text-white font-bold">{email.trim().toLowerCase()}</span>
              </div>
            </div>

            {deliveryPreview && deliveryPreview.smtpDelivered && (
              <div className="flex items-center gap-2 p-2.5 rounded-xl bg-[#064E3B]/40 border border-[#34D399]/40 text-xs text-[#34D399]">
                <Check className="w-4 h-4 shrink-0" />
                <span>Reset code successfully transmitted to your Gmail inbox (Live Delivery via Pingram).</span>
              </div>
            )}

            {deliveryPreview && !deliveryPreview.smtpDelivered && (
              <div className="flex flex-col gap-2 p-3 rounded-xl bg-[#2A1700]/80 border border-[#F59E0B]/50 text-xs text-[#FDE68A]">
                <div className="flex items-start gap-2">
                  <Sparkles className="w-4 h-4 text-[#F59E0B] shrink-0 mt-0.5" />
                  <div className="flex flex-col gap-0.5">
                    <span className="font-semibold text-white">Sandbox Delivery: SMTP Not Yet Configured</span>
                    <span className="text-[#FDE68A]/80 text-[11px]">
                      Live delivery requires your Gmail App Password configured in <code className="bg-black/50 px-1 py-0.5 rounded text-white">.env</code>.
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

            <Input
              label="6-Digit Reset Code"
              type="text"
              maxLength={6}
              placeholder="123456"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              leftIcon={<KeyRound className="w-4 h-4" />}
              className="tracking-widest font-mono text-lg text-center"
              required
              autoFocus
              helperText="Check your Gmail inbox and spam folders (expires in 15 minutes)"
            />

            <div className="flex items-center justify-between text-xs text-[#F4B5C8]">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="flex items-center gap-1 hover:text-white transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" /> Back
              </button>

              <button
                type="button"
                onClick={handleResendOtp}
                disabled={resendCooldown > 0 || isLoading}
                className={`flex items-center gap-1 transition-colors ${
                  resendCooldown > 0
                    ? 'text-[#A26377] cursor-not-allowed'
                    : 'text-[#FF2D6D] hover:text-[#FF4D85]'
                }`}
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
                {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 'Resend code'}
              </button>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={handleResetState}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="primary"
                isLoading={isLoading}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Verify Code
              </Button>
            </div>
          </form>
        )}

        {/* STEP 3: MFA Step-Up Verification */}
        {step === 3 && (
          <form onSubmit={handleVerifyMfa} className="flex flex-col gap-4">
            <div className="p-3.5 rounded-xl bg-[#2A0815] border border-[#FF2D6D]/30 flex items-start gap-3">
              <ShieldAlert className="w-5 h-5 text-[#FF2D6D] shrink-0 mt-0.5" />
              <div className="flex flex-col gap-1 text-xs">
                <span className="font-semibold text-white">MFA Defense Enforced</span>
                <span className="text-[#F4B5C8]">
                  Because Multi-Factor Authentication is active on this account, an additional security verification is required before password modification is authorized.
                </span>
              </div>
            </div>

            <div className="flex items-center gap-2 border-b border-[#FFB4C8]/15 pb-2">
              <button
                type="button"
                onClick={() => {
                  setMfaMode('totp');
                  setError(null);
                }}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  mfaMode === 'totp'
                    ? 'bg-[#FF2D6D] text-white shadow-md shadow-[#FF2D6D]/20'
                    : 'bg-[#3F0016] text-[#F4B5C8] hover:text-white'
                }`}
              >
                Authenticator App (TOTP)
              </button>
              <button
                type="button"
                onClick={() => {
                  setMfaMode('recovery');
                  setError(null);
                }}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  mfaMode === 'recovery'
                    ? 'bg-[#FF2D6D] text-white shadow-md shadow-[#FF2D6D]/20'
                    : 'bg-[#3F0016] text-[#F4B5C8] hover:text-white'
                }`}
              >
                Backup Recovery Code
              </button>
            </div>

            {mfaMode === 'totp' ? (
              <Input
                label="6-Digit Authenticator Code"
                type="text"
                maxLength={6}
                placeholder="123456"
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, ''))}
                leftIcon={<KeyRound className="w-4 h-4" />}
                className="tracking-widest font-mono text-lg text-center"
                required
                autoFocus
                helperText="Enter the dynamic code from your Google Authenticator or 1Password app"
              />
            ) : (
              <Input
                label="Emergency Recovery Code"
                type="text"
                maxLength={10}
                placeholder="a1b2c3d4"
                value={recoveryCode}
                onChange={(e) => setRecoveryCode(e.target.value.trim())}
                leftIcon={<KeyRound className="w-4 h-4" />}
                className="font-mono text-center tracking-wider"
                required
                autoFocus
                helperText="Enter one of your 8-character single-use emergency recovery codes"
              />
            )}

            <div className="flex items-center justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={handleResetState}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="primary"
                isLoading={isLoading}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Verify MFA Factor
              </Button>
            </div>
          </form>
        )}

        {/* STEP 4: Set New Password */}
        {step === 4 && (
          <form onSubmit={handleSetNewPassword} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="reset-new-password"
                className="text-xs font-semibold text-[#F4B5C8]"
              >
                New Master Password
              </label>
              <Input
                id="reset-new-password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Minimum 8 characters"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
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
                autoFocus
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

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="reset-confirm-password"
                className="text-xs font-semibold text-[#F4B5C8]"
              >
                Confirm New Password
              </label>
              <Input
                id="reset-confirm-password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Re-enter password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                leftIcon={<Lock className="w-4 h-4" />}
                required
                helperText={
                  confirmPassword && !passwordsMatch ? 'Passwords do not match' : undefined
                }
              />
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={handleResetState}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="primary"
                isLoading={isLoading}
                disabled={!isPasswordLengthValid || !passwordsMatch}
                rightIcon={<ShieldCheck className="w-4 h-4" />}
              >
                Save &amp; Invalidate Sessions
              </Button>
            </div>
          </form>
        )}

        {/* STEP 5: Success Confirmation */}
        {step === 5 && (
          <div className="flex flex-col items-center text-center py-4 gap-4 font-body text-white">
            <div className="w-14 h-14 rounded-2xl bg-[#3F0016] border border-[#34D399]/40 flex items-center justify-center text-[#34D399] shadow-lg shadow-[#34D399]/20">
              <CheckCircle2 className="w-7 h-7" />
            </div>
            <div className="flex flex-col gap-2 max-w-sm">
              <h4 className="text-base font-semibold text-white font-headline">
                Password Successfully Reset
              </h4>
              <p className="text-xs text-[#F4B5C8] leading-relaxed">
                Your credentials have been securely updated. In compliance with cryptographic security standards, all prior active browser sessions and refresh tokens have been revoked.
              </p>
            </div>
            <Button
              variant="primary"
              onClick={handleResetState}
              className="w-full mt-2"
              rightIcon={<ArrowRight className="w-4 h-4" />}
            >
              Sign In with New Password
            </Button>
          </div>
        )}
      </div>
    </Modal>
  );
};

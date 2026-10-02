import React, { useState, useRef, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Alert } from '../common/Alert';
import {
  Shield,
  KeyRound,
  ArrowRight,
  ArrowLeft,
  Smartphone,
  AlertTriangle,
  RotateCcw,
} from 'lucide-react';

export const MfaChallengeScreen = ({ challengeId, onBackToLogin, onSuccess }) => {
  const { completeMfaTotpLogin, completeMfaRecoveryLogin } = useAuth();

  const [authMode, setAuthMode] = useState('totp'); // 'totp' | 'recovery'
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [recoveryCode, setRecoveryCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [isSessionExpired, setIsSessionExpired] = useState(false);
  const [isChallengeLocked, setIsChallengeLocked] = useState(false);

  const inputRefs = useRef([]);

  useEffect(() => {
    if (!challengeId) {
      setIsSessionExpired(true);
      setError('No active authentication challenge found. Please sign in again.');
      return;
    }
    if (authMode === 'totp') {
      inputRefs.current[0]?.focus();
    }
  }, [challengeId, authMode]);

  const handleDigitChange = (index, value) => {
    if (value.length > 1) {
      // Handle paste
      const pasted = value.replace(/\D/g, '').slice(0, 6);
      if (pasted.length > 0) {
        const newDigits = [...digits];
        for (let i = 0; i < 6; i++) {
          newDigits[i] = pasted[i] || '';
        }
        setDigits(newDigits);
        const nextIndex = Math.min(pasted.length, 5);
        inputRefs.current[nextIndex]?.focus();
        return;
      }
    }

    const clean = value.replace(/\D/g, '');
    const newDigits = [...digits];
    newDigits[index] = clean;
    setDigits(newDigits);

    if (clean && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handleVerifyTotp = async (e) => {
    if (e) e.preventDefault();
    const code = digits.join('');
    if (code.length !== 6) {
      setError('Please enter the complete 6-digit verification code.');
      return;
    }

    if (!challengeId) {
      setIsSessionExpired(true);
      setError('Your verification session expired. Please sign in again.');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await completeMfaTotpLogin({ challengeId, code });
      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      const status = err.status || err.payload?.status;
      const codeType = err.payload?.code;

      if (codeType === 'MFA_CHALLENGE_EXPIRED' || err.message?.includes('expired')) {
        setIsSessionExpired(true);
        setError('Your verification session expired. Please sign in again.');
      } else if (codeType === 'MFA_CHALLENGE_LOCKED' || err.message?.includes('locked')) {
        setIsChallengeLocked(true);
        setError('Too many incorrect verification attempts. Challenge locked. Please sign in again.');
      } else if (status === 429 || codeType === 'RATE_LIMIT_EXCEEDED') {
        setError('Too many verification attempts. Please wait before trying again.');
      } else {
        setError('The verification code is incorrect. Check your authenticator app and try again.');
      }
      setDigits(['', '', '', '', '', '']);
      inputRefs.current[0]?.focus();
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyRecovery = async (e) => {
    if (e) e.preventDefault();
    const cleanCode = recoveryCode.trim();
    if (!cleanCode) {
      setError('Please enter a valid backup recovery code.');
      return;
    }

    if (!challengeId) {
      setIsSessionExpired(true);
      setError('Your verification session expired. Please sign in again.');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await completeMfaRecoveryLogin({ challengeId, recoveryCode: cleanCode });
      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      const status = err.status || err.payload?.status;
      const codeType = err.payload?.code;

      if (codeType === 'MFA_CHALLENGE_EXPIRED' || err.message?.includes('expired')) {
        setIsSessionExpired(true);
        setError('Your verification session expired. Please sign in again.');
      } else if (codeType === 'MFA_CHALLENGE_LOCKED' || err.message?.includes('locked')) {
        setIsChallengeLocked(true);
        setError('Too many incorrect verification attempts. Challenge locked. Please sign in again.');
      } else if (status === 429 || codeType === 'RATE_LIMIT_EXCEEDED') {
        setError('Too many attempts. Please wait before trying again.');
      } else {
        setError('The recovery code is invalid, expired, or already used.');
      }
      setRecoveryCode('');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen w-full flex items-center justify-center p-4 bg-[#0D0106] text-white font-body overflow-hidden">
      {/* Background Accent Gradients */}
      <div className="absolute -top-40 -left-40 w-96 h-96 rounded-full bg-[#760031]/30 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 w-96 h-96 rounded-full bg-[#580023]/40 blur-3xl pointer-events-none" />

      {/* Main Card */}
      <div className="relative w-full max-w-md rounded-2xl bg-[#30000F]/95 backdrop-blur-2xl border border-[#FFB4C8]/25 p-8 shadow-2xl shadow-black/90 flex flex-col gap-6 z-10 animate-fade-in">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center gap-3">
          <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/40 shadow-inner text-[#FF2D6D]">
            <KeyRound className="w-7 h-7" />
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-headline font-bold tracking-tight text-white">
              Verify Your Identity
            </h1>
            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              {authMode === 'totp'
                ? 'Enter the 6-digit code from your authenticator app.'
                : 'Enter one of your 12-character backup recovery codes.'}
            </p>
          </div>
        </div>

        {/* Security Challenge Badge */}
        <div className="flex items-center justify-between px-3.5 py-2 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 text-[11px] text-[#FF2D6D] font-mono">
          <span className="flex items-center gap-2">
            <Shield className="w-3.5 h-3.5 text-[#FF2D6D]" />
            MFA CHALLENGE
          </span>
          <span className="text-[#A26377]">
            {authMode === 'totp' ? 'STEP-UP TOTP' : 'RECOVERY CODE'}
          </span>
        </div>

        {/* Error Alert */}
        {error && (
          <Alert
            variant="danger"
            message={error}
            onDismiss={() => setError(null)}
          />
        )}

        {/* Session Expired / Locked Terminal State */}
        {isSessionExpired || isChallengeLocked ? (
          <div className="flex flex-col items-center gap-4 py-2 text-center animate-fade-in">
            <div className="w-12 h-12 rounded-2xl bg-[#3F0016] border border-[#EF4444]/40 flex items-center justify-center text-[#EF4444]">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              {isChallengeLocked
                ? 'Your verification session was locked due to excessive invalid attempts. Please log in again to generate a new challenge.'
                : 'Your authentication challenge has expired. Please sign in with your email and password again.'}
            </p>
            <Button
              variant="primary"
              size="md"
              onClick={onBackToLogin}
              leftIcon={<RotateCcw className="w-4 h-4" />}
              className="w-full mt-2"
            >
              Return to Sign In
            </Button>
          </div>
        ) : authMode === 'totp' ? (
          /* TOTP Verification Form */
          <form onSubmit={handleVerifyTotp} className="flex flex-col gap-5">
            <div className="flex flex-col items-center gap-2.5">
              <label className="text-xs font-semibold text-[#F4B5C8]">
                Authenticator One-Time Code
              </label>
              <div className="flex items-center justify-center gap-2">
                {digits.map((digit, idx) => (
                  <input
                    key={idx}
                    ref={(el) => (inputRefs.current[idx] = el)}
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={digit}
                    onChange={(e) => handleDigitChange(idx, e.target.value)}
                    onKeyDown={(e) => handleKeyDown(idx, e)}
                    className="w-11 h-12 text-center text-xl font-mono font-bold rounded-xl bg-[#3F0016] text-white border border-[#FFB4C8]/25 focus:border-[#FF2D6D] focus:ring-2 focus:ring-[#FF2D6D]/25 focus:bg-[#4A001C] focus:outline-none transition-all"
                  />
                ))}
              </div>
              <span className="text-[10px] font-mono text-[#A26377]">
                Check Google Authenticator, 1Password, Authy, etc.
              </span>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={isLoading}
              disabled={digits.join('').length !== 6}
              rightIcon={<ArrowRight className="w-4 h-4" />}
              className="w-full"
            >
              Verify &amp; Continue
            </Button>

            {/* Switch to Recovery Code Mode */}
            <div className="flex items-center justify-center pt-2">
              <button
                type="button"
                onClick={() => {
                  setAuthMode('recovery');
                  setError(null);
                }}
                className="text-xs text-[#FF2D6D] hover:text-[#FF4D85] hover:underline transition-colors cursor-pointer"
              >
                Use a backup recovery code instead
              </button>
            </div>
          </form>
        ) : (
          /* Recovery Code Verification Form */
          <form onSubmit={handleVerifyRecovery} className="flex flex-col gap-5">
            <div className="flex flex-col gap-2">
              <label htmlFor="mfa-login-recovery-code" className="text-xs font-semibold text-[#F4B5C8]">
                Backup Recovery Code
              </label>
              <Input
                id="mfa-login-recovery-code"
                type="text"
                placeholder="XXXX-XXXX-XXXX"
                value={recoveryCode}
                onChange={(e) => setRecoveryCode(e.target.value.toUpperCase())}
                leftIcon={<KeyRound className="w-4 h-4" />}
                autoFocus
                autoComplete="off"
                required
              />
              <span className="text-[11px] text-[#A26377]">
                Enter one of your 10 saved single-use recovery codes.
              </span>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={isLoading}
              disabled={!recoveryCode.trim()}
              rightIcon={<ArrowRight className="w-4 h-4" />}
              className="w-full"
            >
              Verify Recovery Code
            </Button>

            {/* Switch Back to TOTP */}
            <div className="flex items-center justify-center pt-2">
              <button
                type="button"
                onClick={() => {
                  setAuthMode('totp');
                  setError(null);
                }}
                className="text-xs text-[#FF2D6D] hover:text-[#FF4D85] hover:underline transition-colors flex items-center gap-1 cursor-pointer"
              >
                <Smartphone className="w-3.5 h-3.5" />
                Use authenticator code instead
              </button>
            </div>
          </form>
        )}

        {/* Back to Login Footer */}
        <div className="flex items-center justify-center pt-3 border-t border-[#FFB4C8]/15">
          <button
            type="button"
            onClick={onBackToLogin}
            className="flex items-center gap-1.5 text-xs text-[#A26377] hover:text-white transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Sign In
          </button>
        </div>
      </div>
    </div>
  );
};

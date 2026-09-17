import React, { useState, useRef, useEffect } from 'react';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { Shield, KeyRound, ArrowRight, ArrowLeft, Smartphone, Fingerprint } from 'lucide-react';

export const MfaChallengeScreen = ({ onBackToLogin, onSuccess }) => {
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [authMethod, setAuthMethod] = useState('totp');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const inputRefs = useRef([]);

  useEffect(() => {
    inputRefs.current[0]?.focus();
  }, [authMethod]);

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

  const handleVerify = async (e) => {
    if (e) e.preventDefault();
    const code = digits.join('');
    if (authMethod === 'totp' && code.length !== 6) {
      setError('Please enter the complete 6-digit verification code.');
      return;
    }

    setIsLoading(true);
    setError(null);

    setTimeout(() => {
      setIsLoading(false);
      if (onSuccess) {
        onSuccess();
      } else {
        onBackToLogin();
      }
    }, 600);
  };

  return (
    <div className="relative min-h-screen w-full flex items-center justify-center p-4 bg-vault-bg overflow-hidden">
      {/* Background Glow */}
      <div className="absolute -top-40 -left-40 w-96 h-96 rounded-full bg-vault-primary/15 blur-[120px] pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 w-96 h-96 rounded-full bg-vault-primary/10 blur-[140px] pointer-events-none" />

      {/* Main Glass Card */}
      <div className="relative w-full max-w-md rounded-2xl bg-[#0D1117]/85 border border-white/[0.12] p-8 backdrop-blur-2xl shadow-2xl shadow-black/80 flex flex-col gap-6 z-10 animate-fade-in">
        {/* Header */}
        <div className="flex flex-col items-center text-center gap-3">
          <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-white/[0.05] border border-white/[0.12] shadow-inner text-vault-primary">
            <KeyRound className="w-7 h-7" />
            <div className="absolute -inset-1 rounded-2xl bg-vault-primary/20 blur-md -z-10" />
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold tracking-tight text-vault-text font-sans">
              Multi-Factor Authentication
            </h1>
            <p className="text-xs text-vault-text-secondary">
              Zero-Trust Enclave step-up verification required.
            </p>
          </div>
        </div>

        {/* Security Badge */}
        <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-vault-primary-subtle border border-vault-primary/30 text-[11px] text-vault-primary-light font-mono">
          <span className="flex items-center gap-1.5">
            <Shield className="w-3.5 h-3.5" />
            STEP-UP CHALLENGE
          </span>
          <span className="text-vault-text-muted">EXP: 300s</span>
        </div>

        {/* Auth Method Selector */}
        <div className="grid grid-cols-2 gap-2 p-1 rounded-xl bg-white/[0.04] border border-white/[0.08]">
          <button
            type="button"
            onClick={() => setAuthMethod('totp')}
            className={`flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-medium transition-all ${
              authMethod === 'totp'
                ? 'bg-vault-primary text-white shadow-md'
                : 'text-vault-text-secondary hover:text-vault-text'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            Authenticator
          </button>
          <button
            type="button"
            onClick={() => setAuthMethod('hardware')}
            className={`flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-medium transition-all ${
              authMethod === 'hardware'
                ? 'bg-vault-primary text-white shadow-md'
                : 'text-vault-text-secondary hover:text-vault-text'
            }`}
          >
            <Fingerprint className="w-3.5 h-3.5" />
            Security Key
          </button>
        </div>

        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}

        {authMethod === 'totp' ? (
          <form onSubmit={handleVerify} className="flex flex-col gap-5">
            <div className="flex flex-col items-center gap-2">
              <label className="text-xs font-medium text-vault-text-secondary">
                Enter 6-digit TOTP code
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
                    className="w-11 h-12 text-center text-lg font-mono font-bold rounded-xl bg-white/[0.04] text-vault-text border border-white/[0.12] focus:border-vault-primary focus:ring-2 focus:ring-vault-primary/20 focus:bg-white/[0.08] focus:outline-none transition-all"
                  />
                ))}
              </div>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={isLoading}
              rightIcon={<ArrowRight className="w-4 h-4" />}
              className="w-full"
            >
              Verify &amp; Enter Enclave
            </Button>
          </form>
        ) : (
          <div className="flex flex-col items-center text-center py-4 gap-4">
            <div className="w-16 h-16 rounded-2xl bg-vault-primary-subtle border border-vault-primary/30 flex items-center justify-center text-vault-primary animate-pulse">
              <Fingerprint className="w-8 h-8" />
            </div>
            <div className="flex flex-col gap-1">
              <h4 className="text-sm font-semibold text-vault-text">Touch Security Key</h4>
              <p className="text-xs text-vault-text-secondary">
                Insert your YubiKey or touch your device biometric sensor.
              </p>
            </div>
            <Button
              variant="primary"
              onClick={handleVerify}
              isLoading={isLoading}
              className="w-full mt-2"
            >
              Authenticate via WebAuthn
            </Button>
          </div>
        )}

        {/* Back Link */}
        <div className="flex items-center justify-center pt-2 border-t border-white/[0.08]">
          <button
            type="button"
            onClick={onBackToLogin}
            className="flex items-center gap-1.5 text-xs text-vault-text-muted hover:text-vault-text transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Sign In
          </button>
        </div>
      </div>
    </div>
  );
};

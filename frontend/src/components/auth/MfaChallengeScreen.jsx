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
    <div className="relative min-h-screen w-full flex items-center justify-center p-4 bg-[#0D0106] text-white font-body overflow-hidden">
      {/* Background Glow */}
      <div className="absolute -top-40 -left-40 w-96 h-96 rounded-full bg-[#760031]/30 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 w-96 h-96 rounded-full bg-[#580023]/40 blur-3xl pointer-events-none" />

      {/* Main Glass Card */}
      <div className="relative w-full max-w-md rounded-2xl bg-[#30000F]/95 backdrop-blur-2xl border border-[#FFB4C8]/25 p-8 shadow-2xl shadow-black/90 flex flex-col gap-6 z-10 animate-fade-in">
        {/* Header */}
        <div className="flex flex-col items-center text-center gap-3">
          <div className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/40 shadow-inner text-[#FF2D6D]">
            <KeyRound className="w-7 h-7" />
          </div>
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-headline font-bold tracking-tight text-white">
              Multi-Factor Authentication
            </h1>
            <p className="text-xs text-[#F4B5C8]">
              Zero-Trust Enclave step-up verification required.
            </p>
          </div>
        </div>

        {/* Security Badge */}
        <div className="flex items-center justify-between px-3.5 py-2 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 text-[11px] text-[#FF2D6D] font-mono">
          <span className="flex items-center gap-2">
            <Shield className="w-3.5 h-3.5 text-[#FF2D6D]" />
            STEP-UP CHALLENGE
          </span>
          <span className="text-[#A26377]">EXP: 300s</span>
        </div>

        {/* Auth Method Selector */}
        <div className="grid grid-cols-2 gap-2 p-1 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15">
          <button
            type="button"
            onClick={() => setAuthMethod('totp')}
            className={`flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-medium transition-all ${
              authMethod === 'totp'
                ? 'bg-[#FF2D6D] text-white font-bold shadow-md shadow-[#FF2D6D]/25'
                : 'text-[#F4B5C8] hover:text-white hover:bg-[#4A001C]'
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
                ? 'bg-[#FF2D6D] text-white font-bold shadow-md shadow-[#FF2D6D]/25'
                : 'text-[#F4B5C8] hover:text-white hover:bg-[#4A001C]'
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
              <label className="text-xs font-semibold text-[#F4B5C8]">
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
                    className="w-11 h-12 text-center text-lg font-mono font-bold rounded-xl bg-[#3F0016] text-white border border-[#FFB4C8]/25 focus:border-[#FF2D6D] focus:ring-2 focus:ring-[#FF2D6D]/25 focus:bg-[#4A001C] focus:outline-none transition-all"
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
            <div className="w-16 h-16 rounded-2xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] animate-pulse">
              <Fingerprint className="w-8 h-8" />
            </div>
            <div className="flex flex-col gap-1">
              <h4 className="text-sm font-semibold text-white">Touch Security Key</h4>
              <p className="text-xs text-[#F4B5C8]">
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
        <div className="flex items-center justify-center pt-3 border-t border-[#FFB4C8]/15">
          <button
            type="button"
            onClick={onBackToLogin}
            className="flex items-center gap-1.5 text-xs text-[#A26377] hover:text-white transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Sign In
          </button>
        </div>
      </div>
    </div>
  );
};

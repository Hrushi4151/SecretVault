import React, { useState, useEffect, useRef } from 'react';
import { Modal } from '../common/Modal';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Alert } from '../common/Alert';
import { authApi } from '../../api/auth';
import {
  ShieldCheck,
  KeyRound,
  Smartphone,
  Shield,
  Loader2,
  AlertCircle,
  Key,
} from 'lucide-react';

export const StepUpAuthenticationModal = ({
  isOpen,
  onClose,
  action = 'SECRET_REVEAL',
  context = {},
  actionTitle = 'Additional Verification Required',
  actionDescription = 'This operation requires recent authentication because the target is in a protected environment.',
  onSuccess,
}) => {
  const [challenge, setChallenge] = useState(null);
  const [activeFactor, setActiveFactor] = useState('PASSWORD'); // 'PASSWORD' | 'TOTP' | 'RECOVERY_CODE'
  const [password, setPassword] = useState('');
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [recoveryCode, setRecoveryCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isInitializing, setIsInitializing] = useState(false);
  const [error, setError] = useState(null);

  const totpInputRefs = useRef([]);

  // Initialize step-up challenge when modal opens
  useEffect(() => {
    let isMounted = true;

    if (isOpen) {
      setError(null);
      setPassword('');
      setDigits(['', '', '', '', '', '']);
      setRecoveryCode('');
      setIsInitializing(true);

      authApi
        .createStepUpChallenge({ action, context })
        .then((res) => {
          if (isMounted) {
            setChallenge(res);
            if (res.supportedFactors && res.supportedFactors.includes('TOTP')) {
              setActiveFactor('TOTP');
            } else {
              setActiveFactor('PASSWORD');
            }
            setIsInitializing(false);
          }
        })
        .catch((err) => {
          if (isMounted) {
            setError(err.message || 'Failed to initialize step-up verification.');
            setIsInitializing(false);
          }
        });
    } else {
      // Clear sensitive states on modal close
      setChallenge(null);
      setPassword('');
      setDigits(['', '', '', '', '', '']);
      setRecoveryCode('');
      setError(null);
    }

    return () => {
      isMounted = false;
    };
  }, [isOpen, action, JSON.stringify(context)]);

  // Handle factor change with zeroization of other factor inputs
  const handleFactorChange = (factor) => {
    setActiveFactor(factor);
    setError(null);
    setPassword('');
    setDigits(['', '', '', '', '', '']);
    setRecoveryCode('');
  };

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
        totpInputRefs.current[nextIndex]?.focus();
        return;
      }
    }

    const clean = value.replace(/\D/g, '');
    const newDigits = [...digits];
    newDigits[index] = clean;
    setDigits(newDigits);

    if (clean && index < 5) {
      totpInputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      totpInputRefs.current[index - 1]?.focus();
    }
  };

  const handleVerifyPassword = async (e) => {
    if (e) e.preventDefault();
    if (!password) {
      setError('Password is required.');
      return;
    }
    if (!challenge?.challengeId) {
      setError('Active challenge not found. Please try again.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const result = await authApi.verifyStepUpPassword({
        challengeId: challenge.challengeId,
        password,
      });
      setPassword('');
      if (onSuccess && result?.proofToken) {
        onSuccess(result.proofToken);
      }
      onClose();
    } catch (err) {
      setPassword('');
      setError(err.message || 'Invalid password for step-up verification.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyTotp = async (e) => {
    if (e) e.preventDefault();
    const code = digits.join('');
    if (code.length !== 6) {
      setError('Please enter the 6-digit verification code.');
      return;
    }
    if (!challenge?.challengeId) {
      setError('Active challenge not found. Please try again.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const result = await authApi.verifyStepUpTotp({
        challengeId: challenge.challengeId,
        code,
      });
      setDigits(['', '', '', '', '', '']);
      if (onSuccess && result?.proofToken) {
        onSuccess(result.proofToken);
      }
      onClose();
    } catch (err) {
      setDigits(['', '', '', '', '', '']);
      setError(err.message || 'Invalid verification code.');
      totpInputRefs.current[0]?.focus();
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyRecovery = async (e) => {
    if (e) e.preventDefault();
    if (!recoveryCode.trim()) {
      setError('Recovery code is required.');
      return;
    }
    if (!challenge?.challengeId) {
      setError('Active challenge not found. Please try again.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const result = await authApi.verifyStepUpRecoveryCode({
        challengeId: challenge.challengeId,
        recoveryCode: recoveryCode.trim(),
      });
      setRecoveryCode('');
      if (onSuccess && result?.proofToken) {
        onSuccess(result.proofToken);
      }
      onClose();
    } catch (err) {
      setRecoveryCode('');
      setError(err.message || 'Invalid or already-used backup recovery code.');
    } finally {
      setIsLoading(false);
    }
  };

  const supportedFactors = challenge?.supportedFactors || ['PASSWORD'];
  const hasTotp = supportedFactors.includes('TOTP');
  const hasRecovery = supportedFactors.includes('RECOVERY_CODE');

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={actionTitle}
      description={actionDescription}
      maxWidth="md"
    >
      <div className="flex flex-col gap-5 pt-1">
        {/* Security Banner */}
        <div className="flex items-center gap-3 p-3 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/20 text-xs text-[#F4B5C8]">
          <ShieldCheck className="w-5 h-5 text-[#FFB4C8] shrink-0" />
          <span>
            Security Policy: Step-up re-authentication confirms your identity for this high-privilege action.
          </span>
        </div>

        {error && (
          <Alert variant="danger" icon={<AlertCircle className="w-4 h-4" />}>
            {error}
          </Alert>
        )}

        {isInitializing ? (
          <div className="flex flex-col items-center justify-center py-8 gap-3">
            <Loader2 className="w-8 h-8 text-[#FFB4C8] animate-spin" />
            <span className="text-sm text-[#F4B5C8]">Preparing security verification...</span>
          </div>
        ) : (
          <>
            {/* Factor Selector Tabs */}
            {supportedFactors.length > 1 && (
              <div className="flex rounded-xl bg-[#230009] p-1 border border-[#FFB4C8]/15">
                {hasTotp && (
                  <button
                    type="button"
                    onClick={() => handleFactorChange('TOTP')}
                    className={`flex-1 flex items-center justify-center gap-2 py-2 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                      activeFactor === 'TOTP'
                        ? 'bg-[#E50046] text-white shadow-md'
                        : 'text-[#F4B5C8] hover:text-white'
                    }`}
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    Authenticator App
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => handleFactorChange('PASSWORD')}
                  className={`flex-1 flex items-center justify-center gap-2 py-2 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                    activeFactor === 'PASSWORD'
                      ? 'bg-[#E50046] text-white shadow-md'
                      : 'text-[#F4B5C8] hover:text-white'
                  }`}
                >
                  <KeyRound className="w-3.5 h-3.5" />
                  Account Password
                </button>
                {hasRecovery && (
                  <button
                    type="button"
                    onClick={() => handleFactorChange('RECOVERY_CODE')}
                    className={`flex-1 flex items-center justify-center gap-2 py-2 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                      activeFactor === 'RECOVERY_CODE'
                        ? 'bg-[#E50046] text-white shadow-md'
                        : 'text-[#F4B5C8] hover:text-white'
                    }`}
                  >
                    <Key className="w-3.5 h-3.5" />
                    Recovery Code
                  </button>
                )}
              </div>
            )}

            {/* Password Verification View */}
            {activeFactor === 'PASSWORD' && (
              <form onSubmit={handleVerifyPassword} className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-medium text-[#F4B5C8]">
                    Current Account Password
                  </label>
                  <Input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your account password"
                    autoFocus
                    disabled={isLoading}
                    required
                  />
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={onClose}
                    disabled={isLoading}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    disabled={isLoading || !password}
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                        Verifying...
                      </>
                    ) : (
                      'Verify & Continue'
                    )}
                  </Button>
                </div>
              </form>
            )}

            {/* TOTP Verification View */}
            {activeFactor === 'TOTP' && (
              <form onSubmit={handleVerifyTotp} className="flex flex-col gap-4">
                <div className="flex flex-col items-center gap-3 py-2">
                  <span className="text-xs text-[#F4B5C8]">
                    Enter the 6-digit code from your authenticator app:
                  </span>
                  <div className="flex items-center gap-2">
                    {digits.map((digit, idx) => (
                      <input
                        key={idx}
                        ref={(el) => (totpInputRefs.current[idx] = el)}
                        type="text"
                        inputMode="numeric"
                        maxLength={6}
                        value={digit}
                        onChange={(e) => handleDigitChange(idx, e.target.value)}
                        onKeyDown={(e) => handleKeyDown(idx, e)}
                        autoFocus={idx === 0}
                        disabled={isLoading}
                        className="w-10 h-12 text-center text-lg font-mono font-bold rounded-xl bg-[#230009] border border-[#FFB4C8]/25 text-white focus:outline-none focus:border-[#FFB4C8] focus:ring-1 focus:ring-[#FFB4C8] transition-all"
                      />
                    ))}
                  </div>
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={onClose}
                    disabled={isLoading}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    disabled={isLoading || digits.join('').length !== 6}
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                        Verifying...
                      </>
                    ) : (
                      'Verify & Continue'
                    )}
                  </Button>
                </div>
              </form>
            )}

            {/* Recovery Code Verification View */}
            {activeFactor === 'RECOVERY_CODE' && (
              <form onSubmit={handleVerifyRecovery} className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-medium text-[#F4B5C8]">
                    Backup Recovery Code
                  </label>
                  <Input
                    type="text"
                    value={recoveryCode}
                    onChange={(e) => setRecoveryCode(e.target.value)}
                    placeholder="e.g. A1B2-C3D4-E5"
                    autoFocus
                    disabled={isLoading}
                    required
                  />
                  <p className="text-[11px] text-[#A26377]">
                    Entering an unused backup recovery code will single-use consume it.
                  </p>
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={onClose}
                    disabled={isLoading}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    disabled={isLoading || !recoveryCode.trim()}
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                        Verifying...
                      </>
                    ) : (
                      'Verify & Continue'
                    )}
                  </Button>
                </div>
              </form>
            )}
          </>
        )}
      </div>
    </Modal>
  );
};

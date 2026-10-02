import React, { useState } from 'react';
import { Modal } from '../../common/Modal';
import { Button } from '../../common/Button';
import { Input } from '../../common/Input';
import { Alert } from '../../common/Alert';
import { authApi } from '../../../api/auth';
import {
  ShieldAlert,
  Lock,
  Smartphone,
  KeyRound,
  Eye,
  EyeOff,
  AlertTriangle,
  ArrowRight,
} from 'lucide-react';

export const MfaDisableModal = ({ isOpen, onClose, onSuccess }) => {
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [verificationMethod, setVerificationMethod] = useState('totp'); // 'totp' | 'recovery'
  const [totpCode, setTotpCode] = useState('');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const resetState = () => {
    setPassword('');
    setShowPassword(false);
    setVerificationMethod('totp');
    setTotpCode('');
    setRecoveryCode('');
    setIsLoading(false);
    setError(null);
  };

  const handleClose = () => {
    resetState();
    onClose();
  };

  const handleDisable = async (e) => {
    if (e) e.preventDefault();
    if (!password) {
      setError('Please enter your account password.');
      return;
    }

    if (verificationMethod === 'totp' && totpCode.trim().length !== 6) {
      setError('Please enter the 6-digit code from your authenticator app.');
      return;
    }

    if (verificationMethod === 'recovery' && !recoveryCode.trim()) {
      setError('Please enter a valid backup recovery code.');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await authApi.disableMfa({
        password,
        code: verificationMethod === 'totp' ? totpCode.trim() : null,
        recoveryCode: verificationMethod === 'recovery' ? recoveryCode.trim() : null,
      });

      handleClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Failed to disable MFA. Please verify your password and verification code.';
      setError(errMsg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Disable Multi-Factor Authentication"
      description="Step-up authentication is required to remove MFA protection from your account."
      maxWidth="md"
    >
      <form onSubmit={handleDisable} className="flex flex-col gap-5 text-white font-body">
        {/* Security Warning */}
        <div className="p-4 rounded-2xl bg-[#3F0016] border border-[#EF4444]/40 flex items-start gap-3 text-white">
          <ShieldAlert className="w-5 h-5 text-[#EF4444] shrink-0 mt-0.5" />
          <div className="flex flex-col gap-0.5">
            <h4 className="text-xs font-headline font-bold text-[#FCA5A5] uppercase tracking-wider">
              Security Warning
            </h4>
            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              Disabling MFA reduces account protection. Future logins will require only your password.
            </p>
          </div>
        </div>

        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}

        {/* Current Password Field */}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="mfa-disable-password" className="text-xs font-semibold text-[#F4B5C8]">
            Current Password <span className="text-[#FF2D6D]">*</span>
          </label>
          <Input
            id="mfa-disable-password"
            type={showPassword ? 'text' : 'password'}
            placeholder="Enter your current password"
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

        {/* Step-Up Factor Choice */}
        <div className="flex flex-col gap-2">
          <label className="text-xs font-semibold text-[#F4B5C8]">
            Step-Up Verification Factor <span className="text-[#FF2D6D]">*</span>
          </label>

          <div className="grid grid-cols-2 gap-2 p-1 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15">
            <button
              type="button"
              onClick={() => setVerificationMethod('totp')}
              className={`flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-medium transition-all ${
                verificationMethod === 'totp'
                  ? 'bg-[#FF2D6D] text-white font-bold shadow-md shadow-[#FF2D6D]/25'
                  : 'text-[#F4B5C8] hover:text-white hover:bg-[#30000F]'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              Authenticator App
            </button>
            <button
              type="button"
              onClick={() => setVerificationMethod('recovery')}
              className={`flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-medium transition-all ${
                verificationMethod === 'recovery'
                  ? 'bg-[#FF2D6D] text-white font-bold shadow-md shadow-[#FF2D6D]/25'
                  : 'text-[#F4B5C8] hover:text-white hover:bg-[#30000F]'
              }`}
            >
              <KeyRound className="w-3.5 h-3.5" />
              Recovery Code
            </button>
          </div>
        </div>

        {/* Factor Input */}
        {verificationMethod === 'totp' ? (
          <div className="flex flex-col gap-1.5 animate-fade-in">
            <label htmlFor="mfa-disable-totp" className="text-xs font-semibold text-[#F4B5C8]">
              6-Digit Authenticator Code
            </label>
            <Input
              id="mfa-disable-totp"
              type="text"
              inputMode="numeric"
              maxLength={6}
              placeholder="000000"
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              leftIcon={<Smartphone className="w-4 h-4" />}
              autoComplete="one-time-code"
            />
          </div>
        ) : (
          <div className="flex flex-col gap-1.5 animate-fade-in">
            <label htmlFor="mfa-disable-recovery" className="text-xs font-semibold text-[#F4B5C8]">
              Backup Recovery Code
            </label>
            <Input
              id="mfa-disable-recovery"
              type="text"
              placeholder="XXXX-XXXX-XXXX"
              value={recoveryCode}
              onChange={(e) => setRecoveryCode(e.target.value.toUpperCase())}
              leftIcon={<KeyRound className="w-4 h-4" />}
              autoComplete="off"
            />
            <span className="text-[11px] text-[#A26377]">
              Format: 12-character alphanumeric recovery code.
            </span>
          </div>
        )}

        <div className="flex justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
          <Button type="button" variant="ghost" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="danger"
            isLoading={isLoading}
            disabled={
              !password ||
              (verificationMethod === 'totp' ? totpCode.length !== 6 : !recoveryCode.trim())
            }
          >
            Confirm &amp; Disable MFA
          </Button>
        </div>
      </form>
    </Modal>
  );
};

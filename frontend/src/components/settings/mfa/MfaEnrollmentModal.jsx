import React, { useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Modal } from '../../common/Modal';
import { Button } from '../../common/Button';
import { Alert } from '../../common/Alert';
import { authApi } from '../../../api/auth';
import {
  Shield,
  Smartphone,
  QrCode,
  KeyRound,
  CheckCircle2,
  Copy,
  Check,
  Eye,
  EyeOff,
  Download,
  AlertTriangle,
  ArrowRight,
  ArrowLeft,
  Lock,
} from 'lucide-react';

export const MfaEnrollmentModal = ({ isOpen, onClose, onSuccess, userEmail }) => {
  const [step, setStep] = useState(1); // 1: Info -> 2: QR/Secret -> 3: Verification -> 4: Recovery Codes
  const [enrollmentData, setEnrollmentData] = useState(null);
  const [showSecret, setShowSecret] = useState(false);
  const [copiedSecret, setCopiedSecret] = useState(false);
  const [verificationCode, setVerificationCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState([]);
  const [copiedAllCodes, setCopiedAllCodes] = useState(false);
  const [copiedCodeIndex, setCopiedCodeIndex] = useState(null);
  const [hasAcknowledged, setHasAcknowledged] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const resetState = () => {
    setStep(1);
    setEnrollmentData(null);
    setShowSecret(false);
    setCopiedSecret(false);
    setVerificationCode('');
    setRecoveryCodes([]);
    setCopiedAllCodes(false);
    setCopiedCodeIndex(null);
    setHasAcknowledged(false);
    setIsLoading(false);
    setError(null);
  };

  const handleClose = () => {
    resetState();
    onClose();
  };

  const handleStartEnrollment = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await authApi.enrollMfa();
      setEnrollmentData(data);
      setStep(2);
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Failed to initiate MFA enrollment. Please try again.';
      setError(errMsg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopySecret = () => {
    if (enrollmentData?.secret) {
      navigator.clipboard.writeText(enrollmentData.secret);
      setCopiedSecret(true);
      setTimeout(() => setCopiedSecret(false), 2000);
    }
  };

  const handleActivate = async (e) => {
    if (e) e.preventDefault();
    const cleanCode = verificationCode.trim().replace(/\D/g, '');
    if (cleanCode.length !== 6) {
      setError('Please enter a valid 6-digit numerical code from your authenticator.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const result = await authApi.activateMfa({ code: cleanCode });
      setRecoveryCodes(result.recoveryCodes || []);
      setStep(4);
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'The verification code is incorrect. Check your authenticator app and try again.';
      setError(errMsg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopySingleCode = (code, index) => {
    navigator.clipboard.writeText(code);
    setCopiedCodeIndex(index);
    setTimeout(() => setCopiedCodeIndex(null), 2000);
  };

  const handleCopyAllCodes = () => {
    const text = recoveryCodes.join('\n');
    navigator.clipboard.writeText(text);
    setCopiedAllCodes(true);
    setTimeout(() => setCopiedAllCodes(false), 2000);
  };

  const handleDownloadCodes = () => {
    const header = [
      '==================================================',
      'SECRETVAULT MULTI-FACTOR RECOVERY CODES',
      '==================================================',
      `Account: ${userEmail || 'SecretVault User'}`,
      `Generated: ${new Date().toUTCString()}`,
      '',
      'IMPORTANT SECURITY NOTICE:',
      '- Store these recovery codes in a secure password manager or offline safe.',
      '- Each recovery code is single-use and valid for exactly one login.',
      '- If you lose access to your authenticator, these codes are the only way to recover your account.',
      '==================================================',
      '',
      ...recoveryCodes.map((code, i) => `[${i + 1 < 10 ? '0' + (i + 1) : i + 1}] ${code}`),
      '',
      '==================================================',
    ].join('\n');

    const blob = new Blob([header], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'SecretVault-Recovery-Codes.txt';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleFinish = () => {
    if (!hasAcknowledged) return;
    handleClose();
    if (onSuccess) {
      onSuccess();
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Configure Multi-Factor Authentication"
      description="Add an extra layer of cryptographic protection using a time-based one-time password (TOTP) authenticator."
      maxWidth="lg"
    >
      <div className="flex flex-col gap-5 text-white font-body">
        {/* Step Progress Bar */}
        <div className="grid grid-cols-4 gap-2 pt-1 pb-2">
          {[
            { id: 1, label: 'Overview' },
            { id: 2, label: 'Scan QR' },
            { id: 3, label: 'Verify' },
            { id: 4, label: 'Backup Codes' },
          ].map((s) => {
            const isCompleted = step > s.id;
            const isCurrent = step === s.id;
            return (
              <div key={s.id} className="flex flex-col gap-1.5">
                <div
                  className={`h-1.5 rounded-full transition-all duration-300 ${
                    isCompleted
                      ? 'bg-[#34D399]'
                      : isCurrent
                      ? 'bg-[#FF2D6D]'
                      : 'bg-[#3F0016]'
                  }`}
                />
                <span
                  className={`text-[10px] font-mono font-semibold tracking-wider uppercase ${
                    isCurrent
                      ? 'text-[#FF2D6D]'
                      : isCompleted
                      ? 'text-[#34D399]'
                      : 'text-[#A26377]'
                  }`}
                >
                  {s.label}
                </span>
              </div>
            );
          })}
        </div>

        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}

        {/* STEP 1: Overview */}
        {step === 1 && (
          <div className="flex flex-col gap-5 animate-fade-in">
            <div className="p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 flex items-start gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shrink-0">
                <Smartphone className="w-5 h-5" />
              </div>
              <div className="flex flex-col gap-1">
                <h4 className="text-sm font-headline font-bold text-white">
                  Supported Authenticator Apps
                </h4>
                <p className="text-xs text-[#F4B5C8] leading-relaxed">
                  Use any RFC 6238 compliant authenticator application such as{' '}
                  <strong className="text-white">Google Authenticator</strong>,{' '}
                  <strong className="text-white">1Password</strong>,{' Bitwarden'},{' '}
                  <strong className="text-white">Authy</strong>, or{' '}
                  <strong className="text-white">Microsoft Authenticator</strong>.
                </p>
              </div>
            </div>

            <div className="flex flex-col gap-2.5 text-xs text-[#F4B5C8]">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#34D399]" />
                <span>Zero-Trust secondary factor on every login and privilege escalation</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#34D399]" />
                <span>AES-256-GCM envelope encrypted secret protection</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#34D399]" />
                <span>10 single-use offline recovery codes provided upon activation</span>
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
              <Button variant="ghost" onClick={handleClose}>
                Cancel
              </Button>
              <Button
                variant="primary"
                onClick={handleStartEnrollment}
                isLoading={isLoading}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Continue Setup
              </Button>
            </div>
          </div>
        )}

        {/* STEP 2: Scan QR Code & Manual Fallback */}
        {step === 2 && enrollmentData && (
          <div className="flex flex-col gap-5 animate-fade-in">
            <div className="flex flex-col sm:flex-row items-center gap-5 p-5 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15">
              {/* QR Code Canvas */}
              <div className="p-3 rounded-xl bg-white shadow-lg shadow-black/60 shrink-0">
                <QRCodeSVG
                  value={enrollmentData.provisioningUri}
                  size={160}
                  level="M"
                  includeMargin={false}
                />
              </div>

              <div className="flex flex-col gap-2 text-center sm:text-left">
                <h4 className="text-sm font-headline font-bold text-white flex items-center justify-center sm:justify-start gap-2">
                  <QrCode className="w-4 h-4 text-[#FF2D6D]" />
                  Scan with Authenticator
                </h4>
                <p className="text-xs text-[#F4B5C8] leading-relaxed">
                  Open your authenticator app, select <span className="text-white font-semibold">"Scan QR Code"</span>, and point your camera at this code.
                </p>
                <div className="text-[11px] font-mono text-[#A26377]">
                  URI: {enrollmentData.provisioningUri.slice(0, 32)}...
                </div>
              </div>
            </div>

            {/* Manual Secret Key Fallback */}
            <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-headline font-semibold text-white">
                  Can't scan the QR code?
                </span>
                <button
                  type="button"
                  onClick={() => setShowSecret(!showSecret)}
                  className="text-xs text-[#FF2D6D] hover:text-[#FF4D85] flex items-center gap-1 transition-colors"
                >
                  {showSecret ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  {showSecret ? 'Hide key' : 'Show setup key'}
                </button>
              </div>

              {showSecret && (
                <div className="flex flex-col gap-2 animate-fade-in">
                  <div className="flex items-center gap-2 p-2.5 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/25 font-mono text-xs text-[#F4B5C8] tracking-widest break-all">
                    <span className="flex-1 select-all">{enrollmentData.secret}</span>
                    <button
                      type="button"
                      onClick={handleCopySecret}
                      className="p-1.5 rounded-lg bg-[#3F0016] text-[#FF2D6D] hover:text-white hover:bg-[#4A001C] transition-colors shrink-0"
                      title="Copy setup key"
                    >
                      {copiedSecret ? <Check className="w-4 h-4 text-[#34D399]" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                  <span className="text-[11px] text-[#A26377] flex items-center gap-1.5">
                    <AlertTriangle className="w-3.5 h-3.5 text-[#F59E0B] shrink-0" />
                    Keep this key private. Anyone with it can generate your MFA codes.
                  </span>
                </div>
              )}
            </div>

            <div className="flex justify-between items-center pt-3 border-t border-[#FFB4C8]/15">
              <Button variant="ghost" onClick={() => setStep(1)} leftIcon={<ArrowLeft className="w-4 h-4" />}>
                Back
              </Button>
              <Button
                variant="primary"
                onClick={() => setStep(3)}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                I have scanned the code
              </Button>
            </div>
          </div>
        )}

        {/* STEP 3: Verification Code */}
        {step === 3 && (
          <form onSubmit={handleActivate} className="flex flex-col gap-5 animate-fade-in">
            <div className="p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-3">
              <div className="flex items-center gap-2 text-sm font-headline font-bold text-white">
                <KeyRound className="w-4 h-4 text-[#FF2D6D]" />
                Enter the 6-Digit Code
              </div>
              <p className="text-xs text-[#F4B5C8] leading-relaxed">
                Enter the numerical code displayed in your authenticator app to verify that setup was successful.
              </p>

              <div className="flex flex-col items-center gap-2 my-2">
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  autoFocus
                  placeholder="000000"
                  value={verificationCode}
                  onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  className="w-48 h-12 text-center text-2xl font-mono font-bold tracking-[0.3em] rounded-xl bg-[#3F0016] text-white border border-[#FFB4C8]/25 focus:border-[#FF2D6D] focus:ring-2 focus:ring-[#FF2D6D]/25 focus:outline-none transition-all placeholder-[#A26377]"
                />
                <span className="text-[10px] font-mono text-[#A26377]">
                  Code refreshes every 30 seconds
                </span>
              </div>
            </div>

            <div className="flex justify-between items-center pt-3 border-t border-[#FFB4C8]/15">
              <Button
                type="button"
                variant="ghost"
                onClick={() => setStep(2)}
                leftIcon={<ArrowLeft className="w-4 h-4" />}
              >
                Back to QR
              </Button>
              <Button
                type="submit"
                variant="primary"
                isLoading={isLoading}
                disabled={verificationCode.trim().length !== 6}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Activate &amp; Reveal Recovery Codes
              </Button>
            </div>
          </form>
        )}

        {/* STEP 4: Recovery Codes */}
        {step === 4 && (
          <div className="flex flex-col gap-5 animate-fade-in">
            <div className="p-4 rounded-2xl bg-[#3F0016]/80 border border-[#34D399]/30 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#34D399] shrink-0 mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <h4 className="text-sm font-headline font-bold text-white">
                  MFA Successfully Activated!
                </h4>
                <p className="text-xs text-[#F4B5C8]">
                  Save these 10 backup recovery codes. They are displayed <strong className="text-white">only once</strong>.
                </p>
              </div>
            </div>

            {/* Recovery Codes Monospace Grid */}
            <div className="p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono font-semibold text-[#A26377] uppercase tracking-wider">
                  Single-Use Recovery Codes (10)
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={handleCopyAllCodes}
                    leftIcon={copiedAllCodes ? <Check className="w-3.5 h-3.5 text-[#34D399]" /> : <Copy className="w-3.5 h-3.5" />}
                  >
                    {copiedAllCodes ? 'Copied' : 'Copy All'}
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={handleDownloadCodes}
                    leftIcon={<Download className="w-3.5 h-3.5" />}
                  >
                    Download .txt
                  </Button>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 p-3 rounded-xl bg-[#30000F] border border-[#FFB4C8]/10">
                {recoveryCodes.map((code, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => handleCopySingleCode(code, idx)}
                    className="flex items-center justify-between px-3 py-2 rounded-lg bg-[#3F0016]/60 hover:bg-[#4A001C] border border-[#FFB4C8]/10 hover:border-[#FF2D6D]/40 font-mono text-xs text-white transition-all group cursor-pointer text-left"
                    title="Click to copy single code"
                  >
                    <span className="text-[#A26377] mr-1 text-[10px]">#{idx + 1 < 10 ? '0' + (idx + 1) : idx + 1}</span>
                    <span className="font-bold tracking-wider">{code}</span>
                    <span className="text-[#A26377] group-hover:text-white transition-colors ml-2">
                      {copiedCodeIndex === idx ? <Check className="w-3.5 h-3.5 text-[#34D399]" /> : <Copy className="w-3 h-3 opacity-40 group-hover:opacity-100" />}
                    </span>
                  </button>
                ))}
              </div>

              <div className="flex items-start gap-2 p-3 rounded-xl bg-[#30000F] border border-[#F59E0B]/30 text-[11px] text-[#F59E0B]">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>
                  Store these recovery codes in a secure password manager. Each code can be used exactly once if you lose your phone.
                </span>
              </div>
            </div>

            {/* Mandatory Acknowledgement */}
            <label className="flex items-center gap-3 p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/15 cursor-pointer hover:bg-[#30000F] transition-colors">
              <input
                type="checkbox"
                id="mfa-acknowledge-saved"
                checked={hasAcknowledged}
                onChange={(e) => setHasAcknowledged(e.target.checked)}
                className="w-4 h-4 rounded bg-[#3F0016] border-[#FFB4C8]/30 text-[#FF2D6D] focus:ring-[#FF2D6D] focus:ring-offset-0 cursor-pointer"
              />
              <span className="text-xs text-[#F4B5C8] font-medium select-none">
                I have securely saved these backup recovery codes and understand they will not be shown again.
              </span>
            </label>

            <div className="flex justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
              <Button
                variant="primary"
                onClick={handleFinish}
                disabled={!hasAcknowledged}
                leftIcon={<Lock className="w-4 h-4" />}
              >
                Complete MFA Setup
              </Button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
};

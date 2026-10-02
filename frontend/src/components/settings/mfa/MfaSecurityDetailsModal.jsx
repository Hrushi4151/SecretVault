import React from 'react';
import { Modal } from '../../common/Modal';
import { Button } from '../../common/Button';
import { Shield, Smartphone, Key, Lock, CheckCircle2, Clock } from 'lucide-react';

export const MfaSecurityDetailsModal = ({ isOpen, onClose, mfaStatus }) => {
  const formatDate = (isoString) => {
    if (!isoString) return 'Not recorded';
    try {
      return new Date(isoString).toLocaleString();
    } catch {
      return isoString;
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="MFA Security & Cryptographic Details"
      description="Cryptographic configuration and authentication factors protecting this account."
      maxWidth="md"
    >
      <div className="flex flex-col gap-5 text-white font-body">
        {/* Status Header Banner */}
        <div className="p-4 rounded-2xl bg-[#3F0016] border border-[#34D399]/40 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#1E000A] border border-[#34D399]/30 flex items-center justify-center text-[#34D399]">
              <Shield className="w-5 h-5" />
            </div>
            <div className="flex flex-col">
              <span className="text-xs font-headline font-bold text-white">
                Multi-Factor Protection Active
              </span>
              <span className="text-[11px] font-mono text-[#34D399]">
                STATUS: {mfaStatus?.status || 'ENABLED'}
              </span>
            </div>
          </div>
          <span className="px-2.5 py-1 rounded-full bg-[#1E000A] text-[10px] font-mono font-semibold text-[#34D399] border border-[#34D399]/30">
            ENFORCED
          </span>
        </div>

        {/* Factors Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-1.5">
            <div className="flex items-center gap-2 text-xs font-headline font-semibold text-[#F4B5C8]">
              <Smartphone className="w-3.5 h-3.5 text-[#FF2D6D]" />
              Primary Factor
            </div>
            <span className="text-xs font-bold text-white">Authenticator App</span>
            <span className="text-[10px] font-mono text-[#A26377]">
              RFC 6238 TOTP (HMAC-SHA1, 30s)
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-1.5">
            <div className="flex items-center gap-2 text-xs font-headline font-semibold text-[#F4B5C8]">
              <Key className="w-3.5 h-3.5 text-[#FF2D6D]" />
              Backup Recovery Codes
            </div>
            <span className="text-xs font-bold text-white">
              {mfaStatus?.remainingRecoveryCodes ?? 10} Remaining
            </span>
            <span className="text-[10px] font-mono text-[#A26377]">
              BCrypt one-way hashed in database
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-1.5">
            <div className="flex items-center gap-2 text-xs font-headline font-semibold text-[#F4B5C8]">
              <Clock className="w-3.5 h-3.5 text-[#FF2D6D]" />
              Enrolled On
            </div>
            <span className="text-xs font-mono text-white">
              {formatDate(mfaStatus?.enrolledAt)}
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-1.5">
            <div className="flex items-center gap-2 text-xs font-headline font-semibold text-[#F4B5C8]">
              <Clock className="w-3.5 h-3.5 text-[#FF2D6D]" />
              Last Verified
            </div>
            <span className="text-xs font-mono text-white">
              {formatDate(mfaStatus?.lastUsedAt || mfaStatus?.verifiedAt)}
            </span>
          </div>
        </div>

        {/* Cryptographic Attestation */}
        <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col gap-2">
          <div className="flex items-center gap-2 text-xs font-headline font-bold text-white">
            <Lock className="w-4 h-4 text-[#FF2D6D]" />
            Zero-Knowledge Cryptographic Safeguards
          </div>
          <p className="text-xs text-[#F4B5C8] leading-relaxed">
            TOTP secret keys are encrypted at rest using envelope AES-256-GCM encryption with
            KMS-derived master keys and authenticated user metadata. Plaintext secrets are decrypted
            strictly in volatile memory during authentication and never persisted.
          </p>
        </div>

        <div className="flex justify-end pt-3 border-t border-[#FFB4C8]/15">
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
};

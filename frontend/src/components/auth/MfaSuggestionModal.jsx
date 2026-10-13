import React from 'react';
import { Modal } from '../common/Modal';
import { Button } from '../common/Button';
import { ShieldAlert, ShieldCheck, Smartphone, Key, ArrowRight, X } from 'lucide-react';

export const MfaSuggestionModal = ({ isOpen, onEnableMfa, onDismiss }) => {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onDismiss}
      title="Fortify Your Account with MFA"
      description="Zero-trust security recommendations for your newly provisioned enclave."
      maxWidth="md"
    >
      <div className="flex flex-col gap-5 text-white font-body">
        {/* Enclave Hero Badge */}
        <div className="p-4 rounded-2xl bg-gradient-to-br from-[#3F0016] to-[#20000A] border border-[#FF2D6D]/40 flex items-start gap-3.5 shadow-lg shadow-black/50">
          <div className="w-10 h-10 rounded-xl bg-[#4A001C] border border-[#FF2D6D]/50 flex items-center justify-center text-[#FF2D6D] shrink-0">
            <ShieldAlert className="w-5 h-5 animate-pulse" />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm font-semibold text-white font-headline flex items-center gap-2">
              Recommended: Multi-Factor Authentication
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#FF2D6D]/20 text-[#FF2D6D] border border-[#FF2D6D]/30">
                CRITICAL
              </span>
            </span>
            <p className="text-xs text-[#F4B5C8] leading-relaxed">
              Your organization houses high-value secrets, encryption keys, and environment variables. Adding a second factor ensures that even if credentials leak, attackers cannot breach your enclave.
            </p>
          </div>
        </div>

        {/* Benefits list */}
        <div className="grid grid-cols-1 gap-2.5 text-xs text-[#F4B5C8]">
          <div className="flex items-center gap-3 p-3 rounded-xl bg-[#2A0815]/60 border border-[#FFB4C8]/10">
            <Smartphone className="w-4 h-4 text-[#FF2D6D] shrink-0" />
            <span>Time-based OTP compatible with Google Authenticator, Authy, or 1Password</span>
          </div>
          <div className="flex items-center gap-3 p-3 rounded-xl bg-[#2A0815]/60 border border-[#FFB4C8]/10">
            <Key className="w-4 h-4 text-[#34D399] shrink-0" />
            <span>10 single-use emergency backup recovery codes generated for offline safety</span>
          </div>
          <div className="flex items-center gap-3 p-3 rounded-xl bg-[#2A0815]/60 border border-[#FFB4C8]/10">
            <ShieldCheck className="w-4 h-4 text-[#60A5FA] shrink-0" />
            <span>Enforces hardware/software-bound verification on all future sessions</span>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-3 pt-2 border-t border-[#FFB4C8]/15">
          <Button
            type="button"
            variant="ghost"
            onClick={onDismiss}
            className="text-xs text-[#A26377] hover:text-[#F4B5C8]"
          >
            I'll do this later
          </Button>
          <Button
            type="button"
            variant="primary"
            onClick={onEnableMfa}
            rightIcon={<ArrowRight className="w-4 h-4" />}
          >
            Enable MFA Now
          </Button>
        </div>
      </div>
    </Modal>
  );
};

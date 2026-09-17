import React, { useState } from 'react';
import { secretApi } from '../../api/secrets';
import {
  X,
  Key,
  Lock,
  ShieldCheck,
  Loader2,
  AlertCircle,
  FileText,
  Sparkles,
} from 'lucide-react';

export const CreateSecretModal = ({
  isOpen,
  onClose,
  workspaceId,
  projectId,
  environmentId,
  environmentName = 'Production',
  onSecretCreated,
}) => {
  const [keyName, setKeyName] = useState('');
  const [value, setValue] = useState('');
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  if (!isOpen) return null;

  const handleKeyNameChange = (e) => {
    // Standardize to uppercase and replace spaces/hyphens with underscores
    const formatted = e.target.value
      .toUpperCase()
      .replace(/[^A-Z0-9_]/g, '_');
    setKeyName(formatted);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!keyName.trim()) {
      setError('Secret key name is required.');
      return;
    }
    if (!value) {
      setError('Secret value is required.');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await secretApi.create(workspaceId, projectId, environmentId, {
        name: keyName.trim(),
        value: value,
        description: description.trim() || undefined,
      });

      // Response contains ApiResponse.data or direct payload
      const createdSecret = response?.data || response;
      if (onSecretCreated) {
        onSecretCreated(createdSecret);
      }
      onClose();
      // Reset form
      setKeyName('');
      setValue('');
      setDescription('');
    } catch (err) {
      setError(err.message || 'Failed to create secret. Please verify your permissions.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in font-body">
      <div className="relative w-full max-w-lg bg-[#1E000A] border border-[#FFB4C8]/25 rounded-3xl shadow-2xl p-6 md:p-8 flex flex-col gap-6 text-white max-h-[90vh] overflow-y-auto">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
              <Key className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-headline font-bold text-white tracking-tight">
                Add New Secret
              </h2>
              <p className="text-[11px] font-mono text-[#A26377]">
                Target: <span className="text-[#FFB4C8] font-semibold">{environmentName}</span> Environment
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-[#A26377] hover:text-white hover:bg-[#30000F] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Cryptographic Envelope Encryption Banner */}
        <div className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/30 flex items-start gap-3">
          <div className="p-1.5 rounded-xl bg-[#3F0016] text-[#FF2D6D] shrink-0 mt-0.5">
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div className="flex flex-col gap-0.5 text-xs">
            <span className="font-semibold text-white font-mono flex items-center gap-1.5">
              AES-256-GCM Envelope Encryption
              <span className="text-[9px] px-1.5 py-0.2 rounded-full bg-[#FF2D6D]/20 text-[#FF2D6D] border border-[#FF2D6D]/40 font-mono">
                Hardware KEK
              </span>
            </span>
            <p className="text-[#F4B5C8] text-[11px] leading-relaxed">
              Secrets are encrypted using dynamic ephemeral 256-bit DEKs and cryptographically bound to this environment via Authenticated Additional Data (AAD).
            </p>
          </div>
        </div>

        {/* Error Notification */}
        {error && (
          <div className="p-3 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 flex items-center gap-3 text-xs text-[#FFDAD6]">
            <AlertCircle className="w-4 h-4 shrink-0 text-[#FFB4AB]" />
            <span>{error}</span>
          </div>
        )}

        {/* Form Inputs */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {/* Key Name Input */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-mono font-bold tracking-wider text-[#A26377] uppercase flex items-center justify-between">
              <span>Secret Key Name *</span>
              <span className="text-[10px] text-[#FFB4C8] lowercase font-normal">e.g. STRIPE_API_KEY</span>
            </label>
            <div className="relative">
              <input
                type="text"
                required
                value={keyName}
                onChange={handleKeyNameChange}
                placeholder="DATABASE_PASSWORD"
                className="w-full px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs font-mono text-white placeholder-[#A26377] outline-none transition-all"
              />
            </div>
            <span className="text-[10px] font-mono text-[#A26377]">
              Only uppercase alphanumeric characters and underscores allowed.
            </span>
          </div>

          {/* Secret Value Input */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-mono font-bold tracking-wider text-[#A26377] uppercase">
              Secret Value (Plaintext) *
            </label>
            <div className="relative">
              <textarea
                required
                rows={3}
                value={value}
                onChange={(e) => setValue(e.target.value)}
                placeholder="Enter sensitive key, credential, token, or private certificate..."
                className="w-full px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs font-mono text-white placeholder-[#A26377] outline-none transition-all resize-none"
              />
            </div>
            <span className="text-[10px] font-mono text-[#A26377]">
              Encrypted in-memory immediately. Never stored or logged in plaintext.
            </span>
          </div>

          {/* Description Input */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-mono font-bold tracking-wider text-[#A26377] uppercase">
              Description (Optional)
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Production database access token for primary replication cluster"
              className="w-full px-4 py-2.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs text-white placeholder-[#A26377] outline-none transition-all"
            />
          </div>

          {/* Action Buttons */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15 mt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading || !keyName.trim() || !value}
              className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-xs font-mono font-bold tracking-wider uppercase text-white shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-2 cursor-pointer active:scale-95"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Encrypting...</span>
                </>
              ) : (
                <>
                  <Lock className="w-4 h-4" />
                  <span>Encrypt &amp; Store</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

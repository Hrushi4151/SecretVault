import React, { useState, useRef } from 'react';
import { secretApi } from '../../api/secrets';
import { parseEnvContent } from './ImportEnvModal';
import {
  X,
  Key,
  Lock,
  ShieldCheck,
  Loader2,
  AlertCircle,
  FileText,
  Sparkles,
  Upload,
  Check,
  Trash2,
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
  const [mode, setMode] = useState('single'); // 'single' | 'env'
  
  // Single secret state
  const [keyName, setKeyName] = useState('');
  const [value, setValue] = useState('');
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  // Bulk .env state
  const [envInputMode, setEnvInputMode] = useState('upload'); // 'upload' | 'paste'
  const [rawEnvText, setRawEnvText] = useState('');
  const [envFileName, setEnvFileName] = useState(null);
  const [parsedEnvItems, setParsedEnvItems] = useState([]);
  const [overwriteExisting, setOverwriteExisting] = useState(true);
  const [isImporting, setIsImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);

  const fileInputRef = useRef(null);

  if (!isOpen) return null;

  const handleKeyNameChange = (e) => {
    const formatted = e.target.value
      .toUpperCase()
      .replace(/[^A-Z0-9_.-]/g, '_');
    setKeyName(formatted);
  };

  const handleSingleSubmit = async (e) => {
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

      const createdSecret = response?.data || response;
      if (onSecretCreated) {
        onSecretCreated(createdSecret);
      }
      onClose();
      setKeyName('');
      setValue('');
      setDescription('');
    } catch (err) {
      setError(err.message || 'Failed to create secret. Please verify your permissions.');
    } finally {
      setIsLoading(false);
    }
  };

  // .env file upload handling
  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setEnvFileName(file.name);
    setError(null);
    setImportResult(null);

    const reader = new FileReader();
    reader.onload = (evt) => {
      const content = evt.target?.result;
      if (typeof content === 'string') {
        setRawEnvText(content);
        const parsed = parseEnvContent(content);
        setParsedEnvItems(parsed);
        if (parsed.length === 0) {
          setError('No valid key-value pairs found in the uploaded file.');
        }
      }
    };
    reader.onerror = () => {
      setError('Failed to read file.');
    };
    reader.readAsText(file);
  };

  const handleRawEnvTextChange = (e) => {
    const text = e.target.value;
    setRawEnvText(text);
    setError(null);
    setImportResult(null);
    const parsed = parseEnvContent(text);
    setParsedEnvItems(parsed);
  };

  const toggleSelectAll = (checked) => {
    setParsedEnvItems((prev) => prev.map((item) => ({ ...item, selected: checked })));
  };

  const toggleItemSelect = (id) => {
    setParsedEnvItems((prev) =>
      prev.map((item) => (item.id === id ? { ...item, selected: !item.selected } : item))
    );
  };

  const handleRemoveItem = (id) => {
    setParsedEnvItems((prev) => prev.filter((item) => item.id !== id));
  };

  const selectedCount = parsedEnvItems.filter((i) => i.selected).length;

  const handleBatchImport = async () => {
    const itemsToImport = parsedEnvItems.filter((i) => i.selected);
    if (itemsToImport.length === 0) {
      setError('Please select at least one secret to import.');
      return;
    }

    setIsImporting(true);
    setError(null);
    setImportResult(null);

    try {
      const payload = {
        secrets: itemsToImport.map((item) => ({
          name: item.name,
          value: item.value,
          description: item.description,
        })),
        overwriteExisting: overwriteExisting,
      };

      const response = await secretApi.batchImport(
        workspaceId,
        projectId,
        environmentId,
        payload
      );
      const data = response?.data || response;
      setImportResult(data);

      if (onSecretCreated) {
        // Trigger list refresh
        onSecretCreated(data);
      }

      setTimeout(() => {
        onClose();
        setRawEnvText('');
        setEnvFileName(null);
        setParsedEnvItems([]);
        setImportResult(null);
      }, 1500);
    } catch (err) {
      setError(err.message || 'Failed to import secrets. Please check your permissions.');
    } finally {
      setIsImporting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in font-body">
      <div className="relative w-full max-w-2xl bg-[#1E000A] border border-[#FFB4C8]/25 rounded-3xl shadow-2xl p-6 md:p-8 flex flex-col gap-6 text-white max-h-[90vh] overflow-y-auto">
        {/* Modal Header */}
        <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
              {mode === 'single' ? <Key className="w-6 h-6" /> : <Upload className="w-6 h-6" />}
            </div>
            <div>
              <h2 className="text-lg md:text-xl font-headline font-bold text-white tracking-tight">
                {mode === 'single' ? 'Add New Secret' : 'Import Secrets from .env'}
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

        {/* Mode Switcher Tabs */}
        <div className="flex items-center gap-2 p-1 rounded-2xl bg-[#140007] border border-[#FFB4C8]/15 text-xs">
          <button
            type="button"
            onClick={() => {
              setMode('single');
              setError(null);
            }}
            className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 cursor-pointer ${
              mode === 'single'
                ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                : 'text-[#A26377] hover:text-white'
            }`}
          >
            <Key className="w-4 h-4 text-[#FF2D6D]" />
            <span>Single Secret</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setMode('env');
              setError(null);
            }}
            className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 cursor-pointer ${
              mode === 'env'
                ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                : 'text-[#A26377] hover:text-white'
            }`}
          >
            <Upload className="w-4 h-4 text-[#FF2D6D]" />
            <span>Import from .env File</span>
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
              <span className="text-[9px] px-1.5 py-0.2 rounded-full bg-[#FF2D6D]/20 text-[#FF2D6D] border border-[#FF2D6D]/40 font-mono font-semibold">
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
          <div className="p-3.5 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 flex items-center gap-3 text-xs text-[#FFDAD6]">
            <AlertCircle className="w-4 h-4 shrink-0 text-[#FFB4AB]" />
            <span>{error}</span>
          </div>
        )}

        {/* Success Notification for Bulk Import */}
        {importResult && (
          <div className="p-3.5 rounded-2xl bg-[#14532D]/40 border border-[#4ADE80]/40 flex items-center gap-3 text-xs text-[#DCFCE7]">
            <Check className="w-4 h-4 shrink-0 text-[#4ADE80]" />
            <div>
              <span className="font-bold">Batch Import Successful!</span>
              <span className="ml-2 font-mono text-[11px]">
                Imported: {importResult.importedCount}, Updated: {importResult.updatedCount}, Skipped: {importResult.skippedCount}
              </span>
            </div>
          </div>
        )}

        {/* MODE 1: SINGLE SECRET FORM */}
        {mode === 'single' && (
          <form onSubmit={handleSingleSubmit} className="flex flex-col gap-5">
            {/* Key Name Input */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-mono font-bold tracking-wider text-[#A26377] uppercase flex items-center justify-between">
                <span>Secret Key Name *</span>
                <span className="text-[10px] text-[#FFB4C8] lowercase font-normal">e.g. stripe_api_key</span>
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
        )}

        {/* MODE 2: IMPORT FROM .ENV */}
        {mode === 'env' && (
          <div className="flex flex-col gap-5">
            {/* Input Submode Selector */}
            <div className="flex items-center gap-3 text-xs">
              <button
                type="button"
                onClick={() => setEnvInputMode('upload')}
                className={`px-3 py-1.5 rounded-xl font-mono text-xs transition-all flex items-center gap-1.5 cursor-pointer ${
                  envInputMode === 'upload'
                    ? 'bg-[#FF2D6D] text-white font-bold'
                    : 'bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15'
                }`}
              >
                <Upload className="w-3.5 h-3.5" />
                <span>Upload File</span>
              </button>

              <button
                type="button"
                onClick={() => setEnvInputMode('paste')}
                className={`px-3 py-1.5 rounded-xl font-mono text-xs transition-all flex items-center gap-1.5 cursor-pointer ${
                  envInputMode === 'paste'
                    ? 'bg-[#FF2D6D] text-white font-bold'
                    : 'bg-[#30000F] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15'
                }`}
              >
                <FileText className="w-3.5 h-3.5" />
                <span>Paste Text</span>
              </button>
            </div>

            {/* Dropzone */}
            {envInputMode === 'upload' && (
              <div
                onClick={() => fileInputRef.current?.click()}
                className="p-8 rounded-2xl border-2 border-dashed border-[#FFB4C8]/25 hover:border-[#FF2D6D]/60 bg-[#140007] flex flex-col items-center justify-center gap-3 cursor-pointer transition-all group text-center"
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".env,.env.local,.env.production,.env.staging,.env.development,.txt"
                  onChange={handleFileChange}
                  className="hidden"
                />
                <div className="w-12 h-12 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] group-hover:scale-110 transition-transform shadow-md">
                  <Upload className="w-6 h-6" />
                </div>
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-headline font-bold text-white">
                    {envFileName ? envFileName : 'Click or drag & drop your .env file here'}
                  </span>
                  <span className="text-[10px] font-mono text-[#A26377]">
                    Supports .env, .env.local, .env.production, or key-value text files
                  </span>
                </div>
              </div>
            )}

            {/* Raw Text Paste */}
            {envInputMode === 'paste' && (
              <div className="flex flex-col gap-2">
                <textarea
                  rows={5}
                  value={rawEnvText}
                  onChange={handleRawEnvTextChange}
                  placeholder={`# Paste your .env contents here\nDATABASE_URL=postgresql://user:pass@db.internal:5432/app\nJWT_SECRET=super_secret_signing_key_2026\nSTRIPE_API_KEY=sk_live_51AbcDef123`}
                  className="w-full p-4 rounded-2xl bg-[#140007] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs font-mono text-white placeholder-[#A26377] outline-none transition-all resize-none"
                />
              </div>
            )}

            {/* Parsed Items Preview Table */}
            {parsedEnvItems.length > 0 && (
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono font-bold text-white uppercase tracking-wider">
                      Detected Secrets ({parsedEnvItems.length})
                    </span>
                    <span className="px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono text-[#4ADE80] border border-[#4ADE80]/30 font-semibold">
                      {selectedCount} Selected
                    </span>
                  </div>

                  <label className="flex items-center gap-2 text-xs font-mono text-[#F4B5C8] cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={selectedCount === parsedEnvItems.length && parsedEnvItems.length > 0}
                      onChange={(e) => toggleSelectAll(e.target.checked)}
                      className="rounded border-[#FFB4C8]/30 text-[#FF2D6D] focus:ring-[#FF2D6D] bg-[#30000F] cursor-pointer"
                    />
                    <span>Select All</span>
                  </label>
                </div>

                <div className="rounded-2xl bg-[#140007] border border-[#FFB4C8]/20 max-h-48 overflow-y-auto divide-y divide-[#FFB4C8]/10 text-xs">
                  {parsedEnvItems.map((item) => (
                    <div
                      key={item.id}
                      className="p-3 flex items-center justify-between gap-4 hover:bg-[#30000F]/50 transition-colors"
                    >
                      <div className="flex items-center gap-3 min-w-0 flex-1">
                        <input
                          type="checkbox"
                          checked={item.selected}
                          onChange={() => toggleItemSelect(item.id)}
                          className="rounded border-[#FFB4C8]/30 text-[#FF2D6D] focus:ring-[#FF2D6D] bg-[#30000F] cursor-pointer shrink-0"
                        />
                        <div className="flex flex-col min-w-0 flex-1">
                          <span className="font-mono font-bold text-white truncate">
                            {item.name}
                          </span>
                          <span className="font-mono text-[10px] text-[#A26377] truncate">
                            •••••••••••••••• ({item.value.length} chars)
                          </span>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => handleRemoveItem(item.id)}
                        className="p-1 rounded-lg text-[#A26377] hover:text-[#F87171] hover:bg-[#3F0016] transition-colors"
                        title="Remove from import"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>

                {/* Overwrite Toggle */}
                <div className="p-3 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex items-center justify-between">
                  <div className="flex flex-col">
                    <span className="text-xs font-semibold text-white">Overwrite Existing Secrets</span>
                    <span className="text-[10px] font-mono text-[#A26377]">
                      Appends a new version if key already exists.
                    </span>
                  </div>

                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={overwriteExisting}
                      onChange={(e) => setOverwriteExisting(e.target.checked)}
                      className="sr-only peer"
                    />
                    <div className="w-9 h-5 bg-[#140007] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-[#FFB4C8] after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-[#FF2D6D]"></div>
                  </label>
                </div>
              </div>
            )}

            {/* Footer Actions for .env Mode */}
            <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#FFB4C8]/15">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all"
              >
                Cancel
              </button>

              <button
                type="button"
                onClick={handleBatchImport}
                disabled={isImporting || selectedCount === 0}
                className="px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-xs font-mono font-bold tracking-wider uppercase text-white shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-2 cursor-pointer active:scale-95"
              >
                {isImporting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Encrypting {selectedCount} Secrets...</span>
                  </>
                ) : (
                  <>
                    <Lock className="w-4 h-4" />
                    <span>Encrypt &amp; Import ({selectedCount})</span>
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

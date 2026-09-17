import React, { useState, useRef } from 'react';
import { secretApi } from '../../api/secrets';
import {
  X,
  Upload,
  FileText,
  ShieldCheck,
  Check,
  AlertCircle,
  Loader2,
  Lock,
  Layers,
  Sparkles,
  Info,
  Trash2,
} from 'lucide-react';

/**
 * Robustly parses .env file content into an array of { name, value, description, selected } objects.
 */
export function parseEnvContent(rawText) {
  if (!rawText) return [];
  const lines = rawText.split(/\r?\n/);
  const results = [];

  for (let i = 0; i < lines.length; i++) {
    let line = lines[i].trim();

    // Skip empty lines or pure comment lines
    if (!line || line.startsWith('#')) {
      continue;
    }

    // Strip leading "export " if present (e.g. export KEY=VAL)
    if (line.startsWith('export ')) {
      line = line.substring(7).trim();
    }

    // Find the first '=' separator
    const equalIdx = line.indexOf('=');
    if (equalIdx === -1) {
      continue;
    }

    let key = line.substring(0, equalIdx).trim();
    let val = line.substring(equalIdx + 1).trim();

    // Standardize key name
    key = key.toUpperCase().replace(/[^A-Z0-9_.-]/g, '_');

    // Handle quoted values: "value" or 'value'
    if (
      (val.startsWith('"') && val.endsWith('"') && val.length >= 2) ||
      (val.startsWith("'") && val.endsWith("'") && val.length >= 2)
    ) {
      val = val.substring(1, val.length - 1);
      // Unescape standard sequences
      val = val.replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t');
    } else {
      // Remove trailing inline comments if any (e.g. KEY=val # comment)
      const commentIdx = val.indexOf(' #');
      if (commentIdx !== -1) {
        val = val.substring(0, commentIdx).trim();
      }
    }

    if (key) {
      results.push({
        id: `${key}_${i}`,
        name: key,
        value: val,
        description: 'Imported from .env file',
        selected: true,
      });
    }
  }

  return results;
}

export const ImportEnvModal = ({
  isOpen,
  onClose,
  workspaceId,
  projectId,
  environmentId,
  environmentName = 'Production',
  onSecretsImported,
}) => {
  const [inputMode, setInputMode] = useState('upload'); // 'upload' | 'paste'
  const [rawText, setRawText] = useState('');
  const [fileName, setFileName] = useState(null);
  const [parsedItems, setParsedItems] = useState([]);
  const [overwriteExisting, setOverwriteExisting] = useState(true);
  const [isImporting, setIsImporting] = useState(false);
  const [error, setError] = useState(null);
  const [importResult, setImportResult] = useState(null);

  const fileInputRef = useRef(null);

  if (!isOpen) return null;

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setFileName(file.name);
    setError(null);
    setImportResult(null);

    const reader = new FileReader();
    reader.onload = (evt) => {
      const content = evt.target?.result;
      if (typeof content === 'string') {
        setRawText(content);
        const parsed = parseEnvContent(content);
        setParsedItems(parsed);
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

  const handleRawTextChange = (e) => {
    const text = e.target.value;
    setRawText(text);
    setError(null);
    setImportResult(null);
    const parsed = parseEnvContent(text);
    setParsedItems(parsed);
  };

  const toggleSelectAll = (checked) => {
    setParsedItems((prev) => prev.map((item) => ({ ...item, selected: checked })));
  };

  const toggleItemSelect = (id) => {
    setParsedItems((prev) =>
      prev.map((item) => (item.id === id ? { ...item, selected: !item.selected } : item))
    );
  };

  const handleRemoveItem = (id) => {
    setParsedItems((prev) => prev.filter((item) => item.id !== id));
  };

  const selectedCount = parsedItems.filter((i) => i.selected).length;

  const handleImport = async () => {
    const itemsToImport = parsedItems.filter((i) => i.selected);
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

      if (onSecretsImported) {
        onSecretsImported(data);
      }

      // Close after 1.5 seconds if successful
      setTimeout(() => {
        onClose();
        // Reset state
        setRawText('');
        setFileName(null);
        setParsedItems([]);
        setImportResult(null);
      }, 1800);
    } catch (err) {
      setError(err.message || 'Failed to import secrets. Please check your permissions.');
    } finally {
      setIsImporting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in font-body">
      <div className="relative w-full max-w-3xl bg-[#1E000A] border border-[#FFB4C8]/25 rounded-3xl shadow-2xl p-6 md:p-8 flex flex-col gap-6 text-white max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
              <Upload className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg md:text-xl font-headline font-bold text-white tracking-tight">
                  Import Secrets from .env File
                </h2>
                <span className="px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono text-[#FFB4C8] border border-[#FFB4C8]/20">
                  Bulk Encryption
                </span>
              </div>
              <p className="text-[11px] font-mono text-[#A26377]">
                Destination: <span className="text-[#FFB4C8] font-semibold">{environmentName}</span> Environment
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

        {/* Cryptographic Guarantee Banner */}
        <div className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/30 flex items-start gap-3">
          <div className="p-1.5 rounded-xl bg-[#3F0016] text-[#FF2D6D] shrink-0 mt-0.5">
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div className="flex flex-col gap-0.5 text-xs">
            <span className="font-semibold text-white font-mono flex items-center gap-1.5">
              Zero-Plaintext Envelope Encryption
              <span className="text-[9px] px-1.5 py-0.2 rounded-full bg-[#4ADE80]/20 text-[#4ADE80] border border-[#4ADE80]/40 font-mono font-semibold">
                AES-256-GCM
              </span>
            </span>
            <p className="text-[#F4B5C8] text-[11px] leading-relaxed">
              Every imported key is dynamically encrypted with an ephemeral 256-bit DEK, protected by hardware KMS KeyWrap, and bound to the <span className="text-white font-mono font-bold">{environmentName}</span> enclave.
            </p>
          </div>
        </div>

        {/* Input Mode Selector */}
        <div className="flex items-center gap-2 p-1 rounded-2xl bg-[#140007] border border-[#FFB4C8]/15 text-xs">
          <button
            type="button"
            onClick={() => setInputMode('upload')}
            className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 ${
              inputMode === 'upload'
                ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                : 'text-[#A26377] hover:text-white'
            }`}
          >
            <Upload className="w-4 h-4 text-[#FF2D6D]" />
            <span>Upload .env File</span>
          </button>

          <button
            type="button"
            onClick={() => setInputMode('paste')}
            className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 ${
              inputMode === 'paste'
                ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                : 'text-[#A26377] hover:text-white'
            }`}
          >
            <FileText className="w-4 h-4 text-[#FF2D6D]" />
            <span>Paste Raw .env Text</span>
          </button>
        </div>

        {/* Error / Success Notifications */}
        {error && (
          <div className="p-3.5 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 flex items-center gap-3 text-xs text-[#FFDAD6]">
            <AlertCircle className="w-4 h-4 shrink-0 text-[#FFB4AB]" />
            <span>{error}</span>
          </div>
        )}

        {importResult && (
          <div className="p-3.5 rounded-2xl bg-[#14532D]/40 border border-[#4ADE80]/40 flex items-center gap-3 text-xs text-[#DCFCE7]">
            <Check className="w-4 h-4 shrink-0 text-[#4ADE80]" />
            <div>
              <span className="font-bold">Batch Import Complete!</span>
              <span className="ml-2 font-mono text-[11px]">
                Imported: {importResult.importedCount}, Updated: {importResult.updatedCount}, Skipped: {importResult.skippedCount}
              </span>
            </div>
          </div>
        )}

        {/* Mode 1: File Dropzone */}
        {inputMode === 'upload' && (
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
                {fileName ? fileName : 'Click or drag & drop your .env file here'}
              </span>
              <span className="text-[10px] font-mono text-[#A26377]">
                Supports .env, .env.local, .env.production, or standard key-value text files
              </span>
            </div>
          </div>
        )}

        {/* Mode 2: Paste Raw Text */}
        {inputMode === 'paste' && (
          <div className="flex flex-col gap-2">
            <textarea
              rows={6}
              value={rawText}
              onChange={handleRawTextChange}
              placeholder={`# Paste your .env file contents here\nDATABASE_URL=postgresql://user:pass@db.local:5432/app\nJWT_SECRET=super_secret_signing_key_2026\nSTRIPE_API_KEY=sk_live_51AbcDef123`}
              className="w-full p-4 rounded-2xl bg-[#140007] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-xs font-mono text-white placeholder-[#A26377] outline-none transition-all resize-none"
            />
          </div>
        )}

        {/* Parsed Items Preview Table */}
        {parsedItems.length > 0 && (
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono font-bold text-white uppercase tracking-wider">
                  Detected Secrets ({parsedItems.length})
                </span>
                <span className="px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono text-[#4ADE80] border border-[#4ADE80]/30 font-semibold">
                  {selectedCount} Selected
                </span>
              </div>

              <label className="flex items-center gap-2 text-xs font-mono text-[#F4B5C8] cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={selectedCount === parsedItems.length && parsedItems.length > 0}
                  onChange={(e) => toggleSelectAll(e.target.checked)}
                  className="rounded border-[#FFB4C8]/30 text-[#FF2D6D] focus:ring-[#FF2D6D] bg-[#30000F] cursor-pointer"
                />
                <span>Select All</span>
              </label>
            </div>

            {/* Scrollable table */}
            <div className="rounded-2xl bg-[#140007] border border-[#FFB4C8]/20 max-h-56 overflow-y-auto divide-y divide-[#FFB4C8]/10 text-xs">
              {parsedItems.map((item) => (
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
          </div>
        )}

        {/* Options & Settings */}
        {parsedItems.length > 0 && (
          <div className="p-3.5 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex items-center justify-between">
            <div className="flex flex-col gap-0.5">
              <span className="text-xs font-semibold text-white">Overwrite Existing Secrets</span>
              <span className="text-[10px] font-mono text-[#A26377]">
                If a secret with the same key name already exists, creates a new version instead of skipping.
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
        )}

        {/* Footer Actions */}
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
            onClick={handleImport}
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
    </div>
  );
};

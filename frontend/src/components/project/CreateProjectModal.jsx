import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { projectApi } from '../../api/projects';
import {
  FolderGit2,
  X,
  Plus,
  Loader2,
  AlertCircle,
  Layers,
  ShieldCheck,
  CheckCircle2,
} from 'lucide-react';

export const CreateProjectModal = ({ isOpen, onClose, onProjectCreated }) => {
  const { activeWorkspace } = useAuth();
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [isSlugManual, setIsSlugManual] = useState(false);
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  if (!isOpen) return null;

  const handleNameChange = (e) => {
    const val = e.target.value;
    setName(val);
    if (!isSlugManual) {
      const generated = val
        .toLowerCase()
        .replace(/[^a-z0-9\s-]/g, '')
        .replace(/\s+/g, '-')
        .replace(/-+/g, '-')
        .replace(/^-|-$/g, '');
      setSlug(generated);
    }
  };

  const handleSlugChange = (e) => {
    setIsSlugManual(true);
    setSlug(
      e.target.value
        .toLowerCase()
        .replace(/[^a-z0-9-]/g, '')
    );
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Project name is required');
      return;
    }
    if (!activeWorkspace?.id) {
      setError('No active workspace selected');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const payload = {
        name: name.trim(),
        slug: slug.trim() || undefined,
        description: description.trim() || undefined,
      };

      const newProject = await projectApi.create(activeWorkspace.id, payload);
      setName('');
      setSlug('');
      setIsSlugManual(false);
      setDescription('');
      if (onProjectCreated) {
        onProjectCreated(newProject);
      }
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to create project.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-lg rounded-2xl bg-[#30000F] border border-[#FFB4C8]/25 p-6 shadow-2xl shadow-black/80 flex flex-col gap-6 text-white font-body">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#3F0016] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-inner">
              <FolderGit2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-headline font-bold text-white">Create New Project</h2>
              <p className="text-xs text-[#A26377] font-mono">
                Scoped to {activeWorkspace?.name || 'Active Workspace'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-[#A26377] hover:text-white hover:bg-[#3F0016] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Error Notification */}
        {error && (
          <div className="flex items-center gap-3 p-3 rounded-xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6]">
            <AlertCircle className="w-4 h-4 shrink-0 text-[#FFB4AB]" />
            <span>{error}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4 text-xs">
          {/* Project Name */}
          <div className="flex flex-col gap-1.5">
            <label className="font-semibold text-[#F4B5C8]">
              Project Name <span className="text-[#FF2D6D]">*</span>
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={handleNameChange}
              placeholder="e.g. Payment Gateway API"
              className="w-full px-3.5 py-2.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-white placeholder-[#A26377] outline-none transition-all"
            />
          </div>

          {/* Project Slug */}
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label className="font-semibold text-[#F4B5C8]">
                Unique Slug <span className="text-[#FF2D6D]">*</span>
              </label>
              <span className="text-[10px] font-mono text-[#A26377]">
                lowercase, numbers & hyphens
              </span>
            </div>
            <input
              type="text"
              required
              value={slug}
              onChange={handleSlugChange}
              placeholder="e.g. payment-gateway-api"
              className="w-full px-3.5 py-2.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] font-mono text-white placeholder-[#A26377] outline-none transition-all"
            />
          </div>

          {/* Description */}
          <div className="flex flex-col gap-1.5">
            <label className="font-semibold text-[#F4B5C8]">
              Description <span className="text-[#A26377] text-[10px]">(Optional)</span>
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Primary microservice handling PCI-compliant transaction processing..."
              className="w-full px-3.5 py-2.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:ring-1 focus:ring-[#FF2D6D] text-white placeholder-[#A26377] outline-none transition-all resize-none"
            />
          </div>

          {/* Automatic Provisioning Notice */}
          <div className="p-3.5 rounded-xl bg-[#26000B] border border-[#FFB4C8]/15 flex flex-col gap-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-[#FFB4C8]">
              <Layers className="w-4 h-4 text-[#FF2D6D]" />
              <span>Automated Environment Provisioning</span>
            </div>
            <p className="text-[11px] text-[#A26377] leading-relaxed">
              Upon creation, SecretVault automatically creates 3 isolated deployment environments:
            </p>
            <div className="flex items-center gap-2 pt-1">
              <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#60A5FA] border border-[#60A5FA]/30">
                development
              </span>
              <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#FBBF24] border border-[#FBBF24]/30">
                staging
              </span>
              <span className="px-2 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#F87171] border border-[#F87171]/30 flex items-center gap-1">
                <ShieldCheck className="w-3 h-3 text-[#F87171]" />
                production (protected)
              </span>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl bg-[#26000B] hover:bg-[#3F0016] text-xs font-semibold text-[#F4B5C8] border border-[#FFB4C8]/15 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading || !name.trim()}
              className="px-5 py-2 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 disabled:opacity-50 text-xs font-bold text-white shadow-lg shadow-[#FF2D6D]/20 transition-all flex items-center gap-2 active:scale-95 cursor-pointer"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Provisioning Project...</span>
                </>
              ) : (
                <>
                  <Plus className="w-4 h-4" />
                  <span>Create Project</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

import React, { useState, useEffect } from 'react';
import { reviewsApi } from '../../api/reviews';
import { projectApi } from '../../api/projects';
import { environmentApi } from '../../api/environments';
import {
  X,
  ShieldCheck,
  Calendar,
  Layers,
  FileText,
  AlertTriangle,
  Loader2,
  CheckCircle2,
  Clock
} from 'lucide-react';

export const CreateCampaignModal = ({ workspaceId, isOpen, onClose, onSuccess }) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [scopeType, setScopeType] = useState('WORKSPACE'); // 'WORKSPACE' | 'PROJECT' | 'ENVIRONMENT'
  const [projects, setProjects] = useState([]);
  const [environments, setEnvironments] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [selectedEnvId, setSelectedEnvId] = useState('');
  const [daysUntilDue, setDaysUntilDue] = useState(14);

  const [isLoadingMetadata, setIsLoadingMetadata] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (isOpen && workspaceId) {
      loadProjects();
    }
  }, [isOpen, workspaceId]);

  const loadProjects = async () => {
    try {
      setIsLoadingMetadata(true);
      const projs = await projectApi.list(workspaceId);
      const list = Array.isArray(projs) ? projs : projs?.data || [];
      setProjects(list);
      if (list.length > 0) {
        setSelectedProjectId(list[0].id);
        loadEnvironments(list[0].id);
      }
    } catch (err) {
      console.error('Failed to load projects:', err);
    } finally {
      setIsLoadingMetadata(false);
    }
  };

  const loadEnvironments = async (projId) => {
    try {
      const envs = await environmentApi.list(workspaceId, projId);
      const list = Array.isArray(envs) ? envs : envs?.data || [];
      setEnvironments(list);
      if (list.length > 0) {
        setSelectedEnvId(list[0].id);
      } else {
        setSelectedEnvId('');
      }
    } catch (err) {
      console.error('Failed to load environments:', err);
    }
  };

  const handleProjectChange = (e) => {
    const pId = e.target.value;
    setSelectedProjectId(pId);
    loadEnvironments(pId);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name || name.trim().length === 0) {
      setErrorMessage('Please enter a campaign name');
      return;
    }

    try {
      setIsSubmitting(true);
      setErrorMessage(null);

      const dueDate = new Date(Date.now() + daysUntilDue * 24 * 60 * 60 * 1000).toISOString();

      await reviewsApi.createCampaign(workspaceId, {
        name: name.trim(),
        description: description.trim() || undefined,
        scopeType,
        projectId: scopeType !== 'WORKSPACE' ? selectedProjectId : undefined,
        environmentId: scopeType === 'ENVIRONMENT' ? selectedEnvId : undefined,
        dueDate
      });

      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to create access review campaign');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in font-body">
      <div className="relative w-full max-w-xl rounded-2xl bg-[#1C000A] border border-[#FF2D6D]/30 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#FFB4C8]/15 bg-gradient-to-r from-[#2C0012] to-[#1C000A]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#3E0018] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-headline font-bold text-base text-white">
                Launch Access Review Campaign
              </h3>
              <p className="text-xs text-[#F4B5C8]/70">
                Periodic certification & zero-trust privilege recertification
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-[#F4B5C8]/60 hover:text-white hover:bg-[#3E0018]/50 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto space-y-4">
          {errorMessage && (
            <div className="p-3.5 rounded-xl bg-[#4A0018]/80 border border-[#FF2D6D]/50 text-xs text-[#FFCCD6] flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-[#FF2D6D] shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5">
              Campaign Name *
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. 'Q3 2026 SOC-2 Standing Privileges Recertification'"
              className="w-full h-10 px-3.5 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white placeholder-[#F4B5C8]/30 focus:outline-none focus:border-[#FF2D6D] transition-colors"
              required
            />
          </div>

          {/* Scope Type Selector */}
          <div>
            <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5 flex items-center gap-1.5">
              <Layers className="w-3.5 h-3.5 text-[#FF2D6D]" />
              Certification Boundary Scope
            </label>
            <div className="grid grid-cols-3 gap-2.5">
              {[
                { id: 'WORKSPACE', label: 'Entire Workspace', desc: 'All projects & envs' },
                { id: 'PROJECT', label: 'Project Scope', desc: 'Specific project' },
                { id: 'ENVIRONMENT', label: 'Environment', desc: 'Specific environment' },
              ].map((s) => (
                <button
                  type="button"
                  key={s.id}
                  onClick={() => setScopeType(s.id)}
                  className={`p-3 rounded-xl border text-left transition-all ${
                    scopeType === s.id
                      ? 'bg-[#3E0018] border-[#FF2D6D] text-white shadow-lg shadow-[#FF2D6D]/15'
                      : 'bg-[#26000F] border-[#FFB4C8]/15 text-[#F4B5C8]/70 hover:border-[#FFB4C8]/30 hover:text-white'
                  }`}
                >
                  <span className="font-semibold text-xs text-white block">{s.label}</span>
                  <span className="text-[10px] text-[#F4B5C8]/50 font-mono mt-0.5 block">{s.desc}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Conditional Scope Selectors */}
          {scopeType !== 'WORKSPACE' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5">
                  Select Project *
                </label>
                <select
                  value={selectedProjectId}
                  onChange={handleProjectChange}
                  disabled={isLoadingMetadata}
                  className="w-full h-10 px-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] transition-colors"
                >
                  {projects.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>

              {scopeType === 'ENVIRONMENT' && (
                <div>
                  <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5">
                    Select Environment *
                  </label>
                  <select
                    value={selectedEnvId}
                    onChange={(e) => setSelectedEnvId(e.target.value)}
                    disabled={isLoadingMetadata || environments.length === 0}
                    className="w-full h-10 px-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] transition-colors"
                  >
                    {environments.map((e) => (
                      <option key={e.id} value={e.id}>
                        {e.name} ({e.envType})
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>
          )}

          {/* Due Date Presets */}
          <div>
            <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5 flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-[#FF2D6D]" />
              Review Deadline (Due Date)
            </label>
            <div className="grid grid-cols-4 gap-2">
              {[7, 14, 30, 60].map((days) => (
                <button
                  type="button"
                  key={days}
                  onClick={() => setDaysUntilDue(days)}
                  className={`py-2 rounded-xl text-xs font-mono font-medium transition-all ${
                    daysUntilDue === days
                      ? 'bg-[#FF2D6D] text-white font-bold'
                      : 'bg-[#2C0012] text-[#F4B5C8]/70 border border-[#FFB4C8]/15 hover:text-white'
                  }`}
                >
                  {days} Days
                </button>
              ))}
            </div>
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-medium text-[#F4B5C8] mb-1.5">
              Campaign Description / Audit Guidance
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. 'Review all active developer permissions and revoke unneeded Production credentials...'"
              rows={2}
              className="w-full p-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white placeholder-[#F4B5C8]/30 focus:outline-none focus:border-[#FF2D6D] transition-colors resize-none"
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#FFB4C8]/15">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-medium text-[#F4B5C8]/80 hover:text-white hover:bg-[#2C0012] transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || !name.trim()}
              className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#FF4D82] text-white text-xs font-medium shadow-lg shadow-[#FF2D6D]/20 hover:opacity-95 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 transition-all"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Snapshotting Access...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Create Campaign</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

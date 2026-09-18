import React, { useState, useEffect } from 'react';
import { branchesApi } from '../../api/branches';
import {
  X,
  GitBranch,
  Plus,
  PlusCircle,
  Merge,
  AlertCircle,
  Loader2,
  Check,
  Star,
  RefreshCw,
  Clock,
  Layers,
  Sparkles,
  Archive,
  ArrowRight
} from 'lucide-react';

export default function SecretBranchesModal({
  isOpen,
  onClose,
  secret,
  workspaceId,
  projectId,
  environmentId,
  environmentName = 'Development',
  environmentType = 'DEVELOPMENT',
  onBranchMerged
}) {
  const [branches, setBranches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('list'); // 'list', 'create', 'commit', 'merge'
  const [selectedBranch, setSelectedBranch] = useState(null);

  // Form states
  const [newBranchName, setNewBranchName] = useState('');
  const [newBranchBaseVersion, setNewBranchBaseVersion] = useState('');
  const [newBranchDesc, setNewBranchDesc] = useState('');
  const [commitValue, setCommitValue] = useState('');
  const [commitReason, setCommitReason] = useState('');
  const [mergeReason, setMergeReason] = useState('');
  const [comparison, setComparison] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen || !secret) return;
    loadBranches();
  }, [isOpen, secret, workspaceId, projectId, environmentId]);

  const loadBranches = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await branchesApi.getBranches(workspaceId, projectId, environmentId, secret.id);
      const branchList = Array.isArray(res) ? res : res?.data || [];
      setBranches(branchList);
      setNewBranchBaseVersion(secret.currentVersionNumber || 1);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load secret branches');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateBranch = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await branchesApi.createBranch(workspaceId, projectId, environmentId, secret.id, {
        name: newBranchName.trim(),
        fromVersion: parseInt(newBranchBaseVersion, 10),
        description: newBranchDesc.trim() || null
      });
      setNewBranchName('');
      setNewBranchDesc('');
      setActiveTab('list');
      await loadBranches();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to create branch');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCommitBranch = async (e) => {
    e.preventDefault();
    if (!selectedBranch) return;
    setSubmitting(true);
    setError(null);
    try {
      await branchesApi.commitBranchVersion(
        workspaceId,
        projectId,
        environmentId,
        secret.id,
        selectedBranch.id,
        {
          value: commitValue,
          expectedHeadVersion: selectedBranch.headVersionNumber,
          reason: commitReason.trim() || `Commit on branch ${selectedBranch.name}`
        }
      );
      setCommitValue('');
      setCommitReason('');
      setActiveTab('list');
      await loadBranches();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to commit to branch');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCompare = async (branch) => {
    setSelectedBranch(branch);
    setError(null);
    try {
      const res = await branchesApi.compareBranch(workspaceId, projectId, environmentId, secret.id, branch.id);
      const data = res?.data !== undefined ? res.data : res;
      setComparison(data);
      setActiveTab('merge');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to compare branch');
    }
  };

  const handleMerge = async () => {
    if (!selectedBranch) return;
    setSubmitting(true);
    setError(null);
    try {
      const res = await branchesApi.mergeBranch(
        workspaceId,
        projectId,
        environmentId,
        secret.id,
        selectedBranch.id,
        {
          expectedMainVersion: secret.currentVersionNumber,
          expectedBranchHeadVersion: selectedBranch.headVersionNumber,
          reason: mergeReason.trim() || `Merged branch ${selectedBranch.name} into main`
        }
      );
      const data = res?.data !== undefined ? res.data : res;
      if (onBranchMerged) {
        onBranchMerged(data);
      }
      setActiveTab('list');
      await loadBranches();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Merge conflict or failure');
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen || !secret) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in font-body">
      <div className="relative w-full max-w-4xl bg-[#1E000A] border border-[#FFB4C8]/25 rounded-3xl shadow-2xl p-6 md:p-8 flex flex-col gap-6 text-white max-h-[90vh] overflow-y-auto">
        
        {/* Header */}
        <div className="flex items-start justify-between border-b border-[#FFB4C8]/15 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-[#30000F] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D] shadow-lg shadow-[#FF2D6D]/20">
              <GitBranch className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg md:text-xl font-headline font-bold text-white tracking-tight">
                  Secret Branches &amp; Git Workflow
                </h2>
                <span className="px-2 py-0.5 rounded-full bg-[#30000F] text-[10px] font-mono text-[#FFB4C8] border border-[#FFB4C8]/20">
                  {secret.name}
                </span>
              </div>
              <p className="text-xs font-mono text-[#A26377] flex items-center gap-2 mt-0.5">
                <span>Environment: <strong className="text-[#FFB4C8]">{environmentName}</strong></span>
                <span>•</span>
                <span className={environmentType === 'DEVELOPMENT' ? 'text-[#4ADE80] font-semibold' : 'text-[#A26377]'}>
                  Branches: {environmentType === 'DEVELOPMENT' ? 'Enabled' : 'Disabled'}
                </span>
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

        {/* Navigation Tabs */}
        <div className="flex items-center gap-2 p-1 rounded-2xl bg-[#140007] border border-[#FFB4C8]/15 text-xs">
          <button
            onClick={() => { setActiveTab('list'); setError(null); }}
            className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 ${
              activeTab === 'list'
                ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                : 'text-[#A26377] hover:text-white'
            }`}
          >
            <GitBranch className="w-4 h-4 text-[#FF2D6D]" />
            <span>Branches ({branches.length})</span>
          </button>

          {environmentType === 'DEVELOPMENT' && (
            <button
              onClick={() => { setActiveTab('create'); setError(null); }}
              className={`flex-1 py-2 px-3 rounded-xl font-mono font-semibold transition-all flex items-center justify-center gap-2 ${
                activeTab === 'create'
                  ? 'bg-[#30000F] text-white border border-[#FF2D6D]/40 shadow-sm'
                  : 'text-[#A26377] hover:text-white'
              }`}
            >
              <PlusCircle className="w-4 h-4 text-[#FF2D6D]" />
              <span>Create New Branch</span>
            </button>
          )}
        </div>

        {/* Body Area */}
        <div className="flex flex-col gap-6">
          {error && (
            <div className="p-4 rounded-2xl bg-[#93000A]/30 border border-[#FFB4AB]/40 text-xs text-[#FFDAD6] flex items-center gap-3 font-mono">
              <AlertCircle className="w-5 h-5 shrink-0 text-[#FFB4AB]" />
              <span>{error}</span>
            </div>
          )}

          {/* Tab 1: Branches List */}
          {activeTab === 'list' && (
            <div className="flex flex-col gap-4">
              {loading ? (
                <div className="flex flex-col items-center justify-center py-12 gap-2 text-xs font-mono text-[#A26377]">
                  <Loader2 className="w-6 h-6 animate-spin text-[#FF2D6D]" />
                  <span>Loading branches...</span>
                </div>
              ) : branches.length === 0 ? (
                <div className="p-8 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 text-center text-xs font-mono text-[#A26377]">
                  No active branches found.
                </div>
              ) : (
                <div className="flex flex-col gap-3">
                  {branches.map((b, idx) => (
                    <div
                      key={b.id || idx}
                      className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col md:flex-row md:items-center justify-between gap-4 font-mono text-xs transition-all hover:border-[#FFB4C8]/30"
                    >
                      <div className="flex items-center gap-3">
                        <div className={`w-8 h-8 rounded-xl flex items-center justify-center border ${
                          b.name === 'main'
                            ? 'bg-[#3F0016] border-[#FF2D6D]/40 text-[#FF2D6D]'
                            : 'bg-[#1E000A] border-[#FFB4C8]/20 text-[#FFB4C8]'
                        }`}>
                          {b.name === 'main' ? <Star className="w-4 h-4 text-[#FF2D6D]" /> : <GitBranch className="w-4 h-4" />}
                        </div>

                        <div className="flex flex-col gap-0.5">
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-white text-sm">{b.name}</span>
                            <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold uppercase border ${
                              b.status === 'ACTIVE'
                                ? 'bg-[#4ADE80]/20 text-[#4ADE80] border-[#4ADE80]/30'
                                : 'bg-[#140007] text-[#A26377] border-[#FFB4C8]/10'
                            }`}>
                              {b.status}
                            </span>
                          </div>
                          <span className="text-[11px] text-[#A26377]">
                            {b.description || 'No description'} • Base v{b.baseVersionNumber || 1} → Head v{b.headVersionNumber || b.baseVersionNumber || 1}
                          </span>
                        </div>
                      </div>

                      {b.name !== 'main' && b.status === 'ACTIVE' && (
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => {
                              setSelectedBranch(b);
                              setActiveTab('commit');
                            }}
                            className="px-3 py-1.5 rounded-xl bg-[#1E000A] hover:bg-[#3F0016] text-[#FFB4C8] hover:text-white border border-[#FFB4C8]/15 text-xs font-mono font-semibold transition-all cursor-pointer"
                          >
                            Commit
                          </button>
                          <button
                            onClick={() => handleCompare(b)}
                            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white text-xs font-mono font-semibold transition-all shadow-md shadow-[#FF2D6D]/20 cursor-pointer active:scale-95"
                          >
                            <Merge className="w-3.5 h-3.5" />
                            <span>Merge</span>
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Tab 2: Create Branch */}
          {activeTab === 'create' && (
            <form onSubmit={handleCreateBranch} className="flex flex-col gap-4 font-mono text-xs max-w-xl">
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">Branch Name *</label>
                <input
                  type="text"
                  required
                  value={newBranchName}
                  onChange={(e) => setNewBranchName(e.target.value)}
                  placeholder="e.g. feature/stripe-webhook-v2"
                  className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] text-xs"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">Base Version (Origin)</label>
                <input
                  type="number"
                  required
                  min="1"
                  value={newBranchBaseVersion}
                  onChange={(e) => setNewBranchBaseVersion(e.target.value)}
                  className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white focus:outline-none focus:border-[#FF2D6D] text-xs"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">Description (Optional)</label>
                <textarea
                  rows="3"
                  value={newBranchDesc}
                  onChange={(e) => setNewBranchDesc(e.target.value)}
                  placeholder="Reason for creating branch..."
                  className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] text-xs resize-none"
                />
              </div>

              <div className="flex items-center gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setActiveTab('list')}
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting || !newBranchName.trim()}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-bold transition-all shadow-lg shadow-[#FF2D6D]/20 disabled:opacity-50 cursor-pointer active:scale-95"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Creating...</span>
                    </>
                  ) : (
                    <>
                      <Plus className="w-4 h-4" />
                      <span>Create Branch</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}

          {/* Tab 3: Commit to Branch */}
          {activeTab === 'commit' && selectedBranch && (
            <form onSubmit={handleCommitBranch} className="flex flex-col gap-4 font-mono text-xs max-w-xl">
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/20 flex items-center justify-between">
                <span>Target Branch: <strong className="text-white">{selectedBranch.name}</strong></span>
                <span className="text-[#FF2D6D]">Head: v{selectedBranch.headVersionNumber}</span>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">New Secret Value *</label>
                <textarea
                  rows="4"
                  required
                  value={commitValue}
                  onChange={(e) => setCommitValue(e.target.value)}
                  placeholder="Enter secret payload for branch..."
                  className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] text-xs resize-none font-mono"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">Commit Message / Reason</label>
                <input
                  type="text"
                  value={commitReason}
                  onChange={(e) => setCommitReason(e.target.value)}
                  placeholder="e.g. Updated API endpoint configuration"
                  className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] text-xs"
                />
              </div>

              <div className="flex items-center gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setActiveTab('list')}
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting || !commitValue}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-bold transition-all shadow-lg shadow-[#FF2D6D]/20 disabled:opacity-50 cursor-pointer active:scale-95"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Committing...</span>
                    </>
                  ) : (
                    <>
                      <Check className="w-4 h-4" />
                      <span>Commit to Branch</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}

          {/* Tab 4: 3-Way Merge & Comparison */}
          {activeTab === 'merge' && selectedBranch && comparison && (
            <div className="flex flex-col gap-5 font-mono text-xs">
              <div className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/20 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-white text-sm">3-Way Merge Evaluation</span>
                  <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold uppercase border ${
                    comparison.canAutoMerge
                      ? 'bg-[#4ADE80]/20 text-[#4ADE80] border-[#4ADE80]/30'
                      : 'bg-[#F87171]/20 text-[#F87171] border-[#F87171]/30'
                  }`}>
                    {comparison.mergeStatus}
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-3 text-[11px]">
                  <div className="p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10">
                    <span className="block text-[10px] text-[#A26377] uppercase">Base Version</span>
                    <span className="font-bold text-white">v{comparison.baseVersionNumber}</span>
                  </div>
                  <div className="p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10">
                    <span className="block text-[10px] text-[#A26377] uppercase">Main Head</span>
                    <span className="font-bold text-white">v{comparison.mainVersionNumber}</span>
                  </div>
                  <div className="p-3 rounded-xl bg-[#1E000A] border border-[#FFB4C8]/10">
                    <span className="block text-[10px] text-[#FF2D6D] uppercase font-bold">Branch Head</span>
                    <span className="font-bold text-[#FF2D6D]">v{comparison.branchHeadVersionNumber}</span>
                  </div>
                </div>
                <p className="text-[#A26377] text-[11px] leading-relaxed">{comparison.details}</p>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-[#A26377] uppercase text-[11px] tracking-wider">Merge Reason (Audited)</label>
                <input
                  type="text"
                  value={mergeReason}
                  onChange={(e) => setMergeReason(e.target.value)}
                  placeholder="e.g. Merged feature branch into production trunk"
                  className="p-3.5 rounded-xl bg-[#30000F] border border-[#FFB4C8]/20 text-white placeholder-[#A26377] focus:outline-none focus:border-[#FF2D6D] text-xs"
                />
              </div>

              <div className="flex items-center gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setActiveTab('list')}
                  className="px-4 py-2.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  onClick={handleMerge}
                  disabled={submitting || !comparison.canAutoMerge}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-xl bg-[#FF2D6D] hover:bg-[#FF2D6D]/90 text-white font-bold transition-all shadow-lg shadow-[#FF2D6D]/20 disabled:opacity-50 cursor-pointer active:scale-95"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Merging...</span>
                    </>
                  ) : (
                    <>
                      <Merge className="w-4 h-4" />
                      <span>Execute 3-Way Merge</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end pt-4 border-t border-[#FFB4C8]/15 mt-2">
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs font-mono font-semibold text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/15 transition-all cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

import React, { useState, useEffect } from 'react';
import { reviewsApi } from '../../api/reviews';
import { useAuth } from '../../context/AuthContext';
import { CreateCampaignModal } from './CreateCampaignModal';
import {
  ShieldCheck,
  Plus,
  RotateCcw,
  Calendar,
  CheckCircle2,
  Clock,
  Layers,
  ChevronRight,
  Loader2,
  FileText,
  AlertTriangle,
  Award
} from 'lucide-react';

export const AccessReviewsView = ({ onSelectCampaign }) => {
  const { activeWorkspace } = useAuth();
  const [campaigns, setCampaigns] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (activeWorkspace?.id) {
      loadCampaigns();
    }
  }, [activeWorkspace?.id]);

  const loadCampaigns = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const res = await reviewsApi.listCampaigns(activeWorkspace.id);
      const list = res.data?.data || res.data || [];
      setCampaigns(list);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to load access review campaigns');
    } finally {
      setIsLoading(false);
    }
  };

  const activeCampaignsCount = campaigns.filter(c => c.status === 'OPEN' || c.status === 'IN_PROGRESS').length;
  const completedCount = campaigns.filter(c => c.status === 'COMPLETED').length;

  return (
    <div className="space-y-6 font-body">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-6 rounded-2xl bg-gradient-to-r from-[#2C0012] via-[#1E000A] to-[#1C000A] border border-[#FFB4C8]/15 shadow-xl">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h2 className="font-headline font-bold text-xl text-white">Access Review & Certification</h2>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-mono font-medium bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30">
              SOC-2 / ISO-27001
            </span>
          </div>
          <p className="text-xs text-[#F4B5C8]/70">
            Periodic access certification campaigns, lineage inspection ("Why Access?"), and automated targeted revocation.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => loadCampaigns()}
            className="p-2.5 rounded-xl bg-[#26000F] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white hover:border-[#FFB4C8]/30 transition-all"
            title="Refresh campaigns"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#FF4D82] text-white text-xs font-semibold shadow-lg shadow-[#FF2D6D]/20 hover:opacity-95 flex items-center gap-2 transition-all"
          >
            <Plus className="w-4 h-4" />
            <span>Launch Review Campaign</span>
          </button>
        </div>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FF2D6D]/30 flex items-center justify-between">
          <div>
            <span className="text-[11px] font-medium text-[#F4B5C8]/70 uppercase tracking-wider">Active Campaigns</span>
            <div className="text-2xl font-bold font-headline text-white mt-1">{activeCampaignsCount}</div>
          </div>
          <div className="w-10 h-10 rounded-xl bg-[#3E0018] border border-[#FF2D6D]/40 flex items-center justify-center text-[#FF2D6D]">
            <Clock className="w-5 h-5 animate-pulse" />
          </div>
        </div>

        <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 flex items-center justify-between">
          <div>
            <span className="text-[11px] font-medium text-[#F4B5C8]/70 uppercase tracking-wider">Certified Campaigns</span>
            <div className="text-2xl font-bold font-headline text-[#00E676] mt-1">{completedCount}</div>
          </div>
          <div className="w-10 h-10 rounded-xl bg-[#003B1C] border border-[#00C853]/30 flex items-center justify-center text-[#00E676]">
            <Award className="w-5 h-5" />
          </div>
        </div>

        <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 flex items-center justify-between">
          <div>
            <span className="text-[11px] font-medium text-[#F4B5C8]/70 uppercase tracking-wider">Total Campaigns</span>
            <div className="text-2xl font-bold font-headline text-white mt-1">{campaigns.length}</div>
          </div>
          <div className="w-10 h-10 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 flex items-center justify-center text-[#F4B5C8]">
            <ShieldCheck className="w-5 h-5" />
          </div>
        </div>
      </div>

      {/* Campaigns List */}
      {isLoading ? (
        <div className="p-12 flex flex-col items-center justify-center gap-3 text-[#F4B5C8]/60">
          <Loader2 className="w-6 h-6 animate-spin text-[#FF2D6D]" />
          <span className="text-xs font-mono">Loading governance certification campaigns...</span>
        </div>
      ) : campaigns.length === 0 ? (
        <div className="p-12 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center space-y-3">
          <div className="w-12 h-12 rounded-2xl bg-[#2C0012] border border-[#FFB4C8]/15 mx-auto flex items-center justify-center text-[#F4B5C8]/40">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <p className="text-xs text-[#F4B5C8]/70">No access review campaigns have been launched yet.</p>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            className="px-4 py-2 rounded-xl bg-[#2C0012] hover:bg-[#3E0018] text-xs font-semibold text-white border border-[#FFB4C8]/20 transition-all inline-flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Launch First Campaign</span>
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {campaigns.map((camp) => {
            const isCompleted = camp.status === 'COMPLETED';
            return (
              <div
                key={camp.id}
                onClick={() => onSelectCampaign && onSelectCampaign(camp.id)}
                className="p-5 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 hover:border-[#FF2D6D]/40 cursor-pointer shadow-lg hover:shadow-xl hover:shadow-[#FF2D6D]/5 transition-all flex flex-col justify-between group"
              >
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="space-y-1">
                      <h3 className="font-headline font-bold text-sm text-white group-hover:text-[#FF85A2] transition-colors">
                        {camp.name}
                      </h3>
                      {camp.description && (
                        <p className="text-xs text-[#F4B5C8]/60 line-clamp-2">{camp.description}</p>
                      )}
                    </div>
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-medium border shrink-0 ${
                        isCompleted
                          ? 'bg-[#00C853]/20 text-[#00E676] border-[#00C853]/40'
                          : camp.status === 'IN_PROGRESS'
                          ? 'bg-[#FF9900]/20 text-[#FFB84D] border-[#FF9900]/40'
                          : 'bg-[#FF2D6D]/15 text-[#FF85A2] border-[#FF2D6D]/30'
                      }`}
                    >
                      {camp.status}
                    </span>
                  </div>

                  {/* Metadata Tags */}
                  <div className="flex items-center gap-3 text-xs text-[#F4B5C8]/70 flex-wrap">
                    <span className="flex items-center gap-1">
                      <Layers className="w-3.5 h-3.5 text-[#FF2D6D]" />
                      Scope: <strong className="text-white">{camp.scopeType}</strong>
                    </span>
                    <span className="flex items-center gap-1 font-mono">
                      <Calendar className="w-3.5 h-3.5 text-[#FF2D6D]" />
                      Due: {new Date(camp.dueDate).toLocaleDateString()}
                    </span>
                  </div>

                  {/* Progress Bar */}
                  <div className="space-y-1.5 pt-2">
                    <div className="flex items-center justify-between text-[11px] font-mono">
                      <span className="text-[#F4B5C8]/70">Certification Progress</span>
                      <span className="font-bold text-white">
                        {camp.decidedItemsCount} / {camp.totalItemsCount} items ({camp.progressPercentage}%)
                      </span>
                    </div>
                    <div className="w-full bg-[#2C0012] rounded-full h-2 overflow-hidden border border-[#FFB4C8]/10">
                      <div
                        className={`h-full transition-all duration-300 ${
                          isCompleted ? 'bg-[#00E676]' : 'bg-gradient-to-r from-[#FF2D6D] to-[#FF85A2]'
                        }`}
                        style={{ width: `${camp.progressPercentage}%` }}
                      />
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-4 mt-4 border-t border-[#FFB4C8]/10 text-xs">
                  <span className="text-[11px] text-[#F4B5C8]/50">
                    Created by {camp.createdByEmail}
                  </span>
                  <div className="flex items-center gap-1 text-[#FF2D6D] font-semibold group-hover:translate-x-0.5 transition-transform">
                    <span>Inspect Ledger</span>
                    <ChevronRight className="w-4 h-4" />
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create Modal */}
      <CreateCampaignModal
        workspaceId={activeWorkspace?.id}
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={() => loadCampaigns()}
      />
    </div>
  );
};

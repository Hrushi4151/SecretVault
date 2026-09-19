import React, { useState, useEffect } from 'react';
import { reviewsApi } from '../../api/reviews';
import {
  ArrowLeft,
  ShieldCheck,
  CheckCircle2,
  XCircle,
  Clock,
  Layers,
  Calendar,
  AlertTriangle,
  RotateCcw,
  Loader2,
  Award,
  FileCheck,
  User,
  Info,
  ChevronDown
} from 'lucide-react';

export const AccessReviewDetailsView = ({ workspaceId, campaignId, onBack }) => {
  const [campaign, setCampaign] = useState(null);
  const [items, setItems] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [decidingItemId, setDecidingItemId] = useState(null);
  const [attestationReport, setAttestationReport] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (workspaceId && campaignId) {
      loadDetails();
    }
  }, [workspaceId, campaignId]);

  const loadDetails = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const [campRes, itemsRes] = await Promise.all([
        reviewsApi.getCampaign(workspaceId, campaignId),
        reviewsApi.listCampaignItems(workspaceId, campaignId)
      ]);
      setCampaign(campRes.data?.data || campRes.data);
      setItems(itemsRes.data?.data || itemsRes.data || []);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to load campaign details');
    } finally {
      setIsLoading(false);
    }
  };

  const handleDecide = async (itemId, decision) => {
    const reason = window.prompt(
      `Enter operational reason for ${decision} decision (optional):`,
      decision === 'KEEP' ? 'Legitimate business requirement' : 'Excess privilege revoked per zero-trust policy'
    );
    if (reason === null) return; // User cancelled

    try {
      setDecidingItemId(itemId);
      await reviewsApi.decideItem(workspaceId, campaignId, itemId, {
        decision,
        decisionReason: reason || undefined
      });
      await loadDetails();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to record decision');
    } finally {
      setDecidingItemId(null);
    }
  };

  const handleFinalize = async () => {
    if (!window.confirm('Finalize and certify this access review campaign? This will generate a formal cryptographic attestation ledger.')) {
      return;
    }

    try {
      const res = await reviewsApi.completeCampaign(workspaceId, campaignId);
      setAttestationReport(res.data?.data || res.data);
      await loadDetails();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to finalize campaign');
    }
  };

  const loadAttestation = async () => {
    try {
      const res = await reviewsApi.getAttestation(workspaceId, campaignId);
      setAttestationReport(res.data?.data || res.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to fetch attestation report');
    }
  };

  if (isLoading) {
    return (
      <div className="p-16 flex flex-col items-center justify-center gap-3 text-[#F4B5C8]/60 font-body">
        <Loader2 className="w-8 h-8 animate-spin text-[#FF2D6D]" />
        <span className="text-xs font-mono">Loading certification ledger & lineage...</span>
      </div>
    );
  }

  if (!campaign) {
    return (
      <div className="p-8 text-center text-xs text-[#F4B5C8]/70">
        Campaign not found.
      </div>
    );
  }

  const isCompleted = campaign.status === 'COMPLETED';

  return (
    <div className="space-y-6 font-body">
      {/* Back Button & Title Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 rounded-xl bg-[#26000F] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white hover:bg-[#3E0018] transition-all"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-headline font-bold text-lg text-white">{campaign.name}</h2>
              <span
                className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-medium border ${
                  isCompleted
                    ? 'bg-[#00C853]/20 text-[#00E676] border-[#00C853]/40'
                    : 'bg-[#FF9900]/20 text-[#FFB84D] border-[#FF9900]/40'
                }`}
              >
                {campaign.status}
              </span>
            </div>
            <p className="text-xs text-[#F4B5C8]/60 font-mono mt-0.5">
              Scope: {campaign.scopeType} • Due: {new Date(campaign.dueDate).toLocaleDateString()}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => loadDetails()}
            className="p-2.5 rounded-xl bg-[#26000F] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white transition-all"
            title="Refresh ledger"
          >
            <RotateCcw className="w-4 h-4" />
          </button>

          {isCompleted ? (
            <button
              onClick={loadAttestation}
              className="px-4 py-2 rounded-xl bg-[#003B1C] text-[#00E676] border border-[#00C853]/40 text-xs font-semibold hover:bg-[#005026] flex items-center gap-2 transition-all"
            >
              <FileCheck className="w-4 h-4" />
              <span>View Audit Attestation</span>
            </button>
          ) : (
            <button
              onClick={handleFinalize}
              disabled={campaign.decidedItemsCount < campaign.totalItemsCount}
              className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#00A86B] to-[#00C853] text-white text-xs font-semibold shadow-lg shadow-[#00C853]/20 hover:opacity-95 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2 transition-all"
            >
              <Award className="w-4 h-4" />
              <span>Finalize & Sign Off</span>
            </button>
          )}
        </div>
      </div>

      {/* Progress & Stats Bar */}
      <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/15 grid grid-cols-1 sm:grid-cols-4 gap-4 text-xs">
        <div>
          <span className="text-[#F4B5C8]/60">Total Items Snapshotted</span>
          <div className="text-base font-bold text-white font-headline mt-0.5">{campaign.totalItemsCount}</div>
        </div>
        <div>
          <span className="text-[#F4B5C8]/60">Decided / Certified</span>
          <div className="text-base font-bold text-[#FF85A2] font-headline mt-0.5">
            {campaign.decidedItemsCount} / {campaign.totalItemsCount} ({campaign.progressPercentage}%)
          </div>
        </div>
        <div>
          <span className="text-[#F4B5C8]/60">Pending Decision</span>
          <div className="text-base font-bold text-[#FF9900] font-headline mt-0.5">
            {campaign.totalItemsCount - campaign.decidedItemsCount}
          </div>
        </div>
        <div>
          <span className="text-[#F4B5C8]/60">Target Completion</span>
          <div className="text-base font-bold text-white font-mono mt-0.5">
            {new Date(campaign.dueDate).toLocaleDateString()}
          </div>
        </div>
      </div>

      {/* Interactive Lineage Table */}
      <div className="rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 overflow-hidden shadow-xl">
        <div className="p-4 border-b border-[#FFB4C8]/10 flex items-center justify-between">
          <h3 className="font-headline font-bold text-sm text-white flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-[#FF2D6D]" />
            Effective Privilege Lineage Ledger ("Why Access?")
          </h3>
          <span className="text-[11px] font-mono text-[#F4B5C8]/60">
            {items.length} standing privilege records
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="bg-[#26000F] border-b border-[#FFB4C8]/10 text-[#F4B5C8]/70 font-mono text-[11px]">
                <th className="p-3.5">User Identity</th>
                <th className="p-3.5">Resource Target</th>
                <th className="p-3.5">Effective Access</th>
                <th className="p-3.5">Why Access? (Source Lineage)</th>
                <th className="p-3.5">Status & Decision</th>
                <th className="p-3.5 text-right">Certification Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#FFB4C8]/10">
              {items.map((item) => {
                const isItemPending = item.decision === 'PENDING';
                const isDeciding = decidingItemId === item.id;

                return (
                  <tr key={item.id} className="hover:bg-[#2C0012]/40 transition-colors">
                    {/* User */}
                    <td className="p-3.5">
                      <div className="flex items-center gap-2">
                        <div className="w-7 h-7 rounded-lg bg-[#3E0018] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF85A2] text-xs font-bold">
                          {item.userFullName ? item.userFullName.slice(0, 2).toUpperCase() : 'U'}
                        </div>
                        <div>
                          <span className="font-semibold text-white block">{item.userFullName}</span>
                          <span className="text-[11px] text-[#F4B5C8]/50 font-mono">{item.userEmail}</span>
                        </div>
                      </div>
                    </td>

                    {/* Resource Target */}
                    <td className="p-3.5">
                      <div className="font-mono text-white">{item.resourceName}</div>
                      <span className="text-[10px] text-[#F4B5C8]/50 uppercase">{item.resourceType}</span>
                    </td>

                    {/* Effective Access */}
                    <td className="p-3.5">
                      <span className="font-mono text-[#FF85A2] font-medium">
                        {item.permissionSummary}
                      </span>
                    </td>

                    {/* Source Lineage */}
                    <td className="p-3.5">
                      <span
                        className={`px-2 py-0.5 rounded font-mono text-[10px] font-semibold border ${
                          item.sourceType === 'WORKSPACE_ROLE'
                            ? 'bg-[#2C0012] text-[#F4B5C8] border-[#FFB4C8]/20'
                            : item.sourceType === 'ENVIRONMENT_ACCESS'
                            ? 'bg-[#3E0018] text-[#FF85A2] border-[#FF2D6D]/30'
                            : item.sourceType === 'GRANULAR_GRANT'
                            ? 'bg-[#003B1C] text-[#00E676] border-[#00C853]/30'
                            : 'bg-[#FF9900]/15 text-[#FFB84D] border-[#FF9900]/30'
                        }`}
                      >
                        {item.sourceType}
                      </span>
                    </td>

                    {/* Decision State */}
                    <td className="p-3.5">
                      {item.decision === 'KEEP' ? (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#00C853]/20 text-[#00E676] border border-[#00C853]/30 flex items-center gap-1 w-fit">
                          <CheckCircle2 className="w-3 h-3" />
                          KEPT
                        </span>
                      ) : item.decision === 'REVOKE' ? (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#FF2D6D]/20 text-[#FF4D82] border border-[#FF2D6D]/30 flex items-center gap-1 w-fit">
                          <XCircle className="w-3 h-3" />
                          REVOKED
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-medium bg-[#FF9900]/15 text-[#FFB84D] border border-[#FF9900]/30 flex items-center gap-1 w-fit">
                          <Clock className="w-3 h-3" />
                          PENDING
                        </span>
                      )}
                      {item.decisionReason && (
                        <span className="block text-[10px] text-[#F4B5C8]/50 mt-1 italic line-clamp-1">
                          "{item.decisionReason}"
                        </span>
                      )}
                    </td>

                    {/* Actions */}
                    <td className="p-3.5 text-right">
                      {!isCompleted && (
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            onClick={() => handleDecide(item.id, 'KEEP')}
                            disabled={isDeciding}
                            className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all ${
                              item.decision === 'KEEP'
                                ? 'bg-[#00C853] text-white shadow-sm shadow-[#00C853]/30'
                                : 'bg-[#003B1C]/60 text-[#00E676] border border-[#00C853]/30 hover:bg-[#00C853] hover:text-white'
                            }`}
                          >
                            Keep
                          </button>
                          <button
                            onClick={() => handleDecide(item.id, 'REVOKE')}
                            disabled={isDeciding}
                            className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all ${
                              item.decision === 'REVOKE'
                                ? 'bg-[#FF2D6D] text-white shadow-sm shadow-[#FF2D6D]/30'
                                : 'bg-[#360014]/60 text-[#FF4D82] border border-[#FF2D6D]/30 hover:bg-[#FF2D6D] hover:text-white'
                            }`}
                          >
                            Revoke
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Attestation Report Modal */}
      {attestationReport && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in font-body">
          <div className="relative w-full max-w-2xl rounded-2xl bg-[#1C000A] border border-[#00C853]/40 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[#FFB4C8]/15 bg-gradient-to-r from-[#003B1C] to-[#1C000A]">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-[#004D25] border border-[#00C853]/40 flex items-center justify-center text-[#00E676]">
                  <Award className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-headline font-bold text-base text-white">
                    Access Certification Attestation Ledger
                  </h3>
                  <p className="text-xs text-[#00E676]/80 font-mono">
                    Compliance Ledger • Certified by {attestationReport.certifiedByEmail}
                  </p>
                </div>
              </div>
              <button
                onClick={() => setAttestationReport(null)}
                className="p-1.5 rounded-lg text-[#F4B5C8]/60 hover:text-white hover:bg-[#2C0012] transition-colors"
              >
                <XCircle className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 overflow-y-auto space-y-4 text-xs">
              <div className="p-4 rounded-xl bg-[#26000F] border border-[#FFB4C8]/15 grid grid-cols-3 gap-3 text-center">
                <div>
                  <span className="text-[#F4B5C8]/60 font-mono">Total Reviewed</span>
                  <div className="text-xl font-bold text-white mt-1">{attestationReport.totalItemsReviewed}</div>
                </div>
                <div>
                  <span className="text-[#00E676] font-mono">Retained (Kept)</span>
                  <div className="text-xl font-bold text-[#00E676] mt-1">{attestationReport.keptCount}</div>
                </div>
                <div>
                  <span className="text-[#FF4D82] font-mono">Revoked</span>
                  <div className="text-xl font-bold text-[#FF4D82] mt-1">{attestationReport.revokedCount}</div>
                </div>
              </div>

              <div className="p-3.5 rounded-xl bg-[#180008] border border-[#FFB4C8]/10 text-xs font-mono text-[#F4B5C8]/80 space-y-1">
                <div>Campaign: <strong className="text-white">{attestationReport.campaignName}</strong></div>
                <div>Timestamp: <strong className="text-white">{attestationReport.certifiedAt}</strong></div>
                <div>Integrity Hash: <span className="text-[#FF85A2]">SHA256-{attestationReport.campaignId}</span></div>
              </div>

              <div className="text-right pt-2">
                <button
                  onClick={() => setAttestationReport(null)}
                  className="px-5 py-2 rounded-xl bg-[#2C0012] hover:bg-[#3E0018] text-white text-xs font-semibold transition-colors"
                >
                  Close Attestation
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

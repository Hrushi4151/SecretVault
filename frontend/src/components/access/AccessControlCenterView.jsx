import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { accessApi } from '../../api/access';
import { JitAccessView } from './JitAccessView';
import { AccessReviewsView } from './AccessReviewsView';
import { AccessReviewDetailsView } from './AccessReviewDetailsView';
import { EffectivePermissionInspector } from './EffectivePermissionInspector';
import {
  ShieldCheck,
  Key,
  Clock,
  Award,
  Layers,
  Search,
  Plus,
  Trash2,
  AlertTriangle,
  RotateCcw,
  Loader2,
  User,
  Server
} from 'lucide-react';

export const AccessControlCenterView = () => {
  const { activeWorkspace, user } = useAuth();
  const [activeSubTab, setActiveSubTab] = useState('JIT'); // 'JIT' | 'GRANTS' | 'REVIEWS' | 'INSPECTOR'
  const [selectedCampaignId, setSelectedCampaignId] = useState(null);

  // Granular Grants state
  const [grants, setGrants] = useState([]);
  const [isLoadingGrants, setIsLoadingGrants] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (activeWorkspace?.id && activeSubTab === 'GRANTS') {
      loadGrants();
    }
  }, [activeWorkspace?.id, activeSubTab]);

  const loadGrants = async () => {
    try {
      setIsLoadingGrants(true);
      setErrorMessage(null);
      const res = await accessApi.listGrants(activeWorkspace.id);
      setGrants(res.data?.data || res.data || []);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to load granular access grants');
    } finally {
      setIsLoadingGrants(false);
    }
  };

  const handleRevokeGrant = async (grantId) => {
    if (!window.confirm('Are you sure you want to revoke this granular access grant?')) {
      return;
    }
    try {
      await accessApi.revokeGrant(activeWorkspace.id, grantId);
      loadGrants();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to revoke access grant');
    }
  };

  return (
    <div className="p-6 max-w-7xl mx-auto space-y-6 font-body animate-fade-in">
      {/* Sub-tab Navigation Bar */}
      <div className="flex items-center gap-2 p-1.5 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 w-fit">
        {[
          { id: 'JIT', label: 'Just-In-Time (JIT) Access', icon: <Clock className="w-4 h-4" /> },
          { id: 'GRANTS', label: 'Granular Access Grants', icon: <Key className="w-4 h-4" /> },
          { id: 'REVIEWS', label: 'Access Reviews & Certification', icon: <Award className="w-4 h-4" /> },
          { id: 'INSPECTOR', label: 'Effective Permissions Matrix', icon: <Search className="w-4 h-4" /> },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => {
              setActiveSubTab(tab.id);
              if (tab.id === 'REVIEWS') setSelectedCampaignId(null);
            }}
            className={`px-4 py-2 rounded-xl text-xs font-medium flex items-center gap-2 transition-all ${
              activeSubTab === tab.id
                ? 'bg-gradient-to-r from-[#FF2D6D] to-[#FF4D82] text-white shadow-lg shadow-[#FF2D6D]/20 font-semibold'
                : 'text-[#F4B5C8]/70 hover:text-white hover:bg-[#2C0012]'
            }`}
          >
            {tab.icon}
            <span>{tab.label}</span>
          </button>
        ))}
      </div>

      {/* Main Tab Content */}
      {activeSubTab === 'JIT' && <JitAccessView />}

      {activeSubTab === 'GRANTS' && (
        <div className="space-y-6">
          {/* Header */}
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-6 rounded-2xl bg-gradient-to-r from-[#2C0012] via-[#1E000A] to-[#1C000A] border border-[#FFB4C8]/15 shadow-xl">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <h2 className="font-headline font-bold text-xl text-white">Granular Access Grants</h2>
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-mono font-medium bg-[#00C853]/20 text-[#00E676] border border-[#00C853]/30">
                  Explicit Resource Grants
                </span>
              </div>
              <p className="text-xs text-[#F4B5C8]/70">
                Resource-scoped explicit grants overriding baseline standing roles on specific projects, environments, or secrets.
              </p>
            </div>

            <div className="flex items-center gap-3">
              <button
                onClick={() => loadGrants()}
                className="p-2.5 rounded-xl bg-[#26000F] border border-[#FFB4C8]/15 text-[#F4B5C8] hover:text-white transition-all"
                title="Refresh grants"
              >
                <RotateCcw className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Grants Table */}
          {isLoadingGrants ? (
            <div className="p-12 flex flex-col items-center justify-center gap-3 text-[#F4B5C8]/60">
              <Loader2 className="w-6 h-6 animate-spin text-[#FF2D6D]" />
              <span className="text-xs font-mono">Loading granular grants...</span>
            </div>
          ) : grants.length === 0 ? (
            <div className="p-12 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/10 text-center space-y-3">
              <div className="w-12 h-12 rounded-2xl bg-[#2C0012] border border-[#FFB4C8]/15 mx-auto flex items-center justify-center text-[#F4B5C8]/40">
                <Key className="w-6 h-6" />
              </div>
              <p className="text-xs text-[#F4B5C8]/70">No explicit granular grants assigned. Users operate under standard RBAC scoping.</p>
            </div>
          ) : (
            <div className="rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 overflow-hidden shadow-xl">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-[#26000F] border-b border-[#FFB4C8]/10 text-[#F4B5C8]/70 font-mono text-[11px]">
                    <th className="p-3.5">User</th>
                    <th className="p-3.5">Scope</th>
                    <th className="p-3.5">Resource Target</th>
                    <th className="p-3.5">Explicit Permission</th>
                    <th className="p-3.5 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#FFB4C8]/10">
                  {grants.map((g) => (
                    <tr key={g.id} className="hover:bg-[#2C0012]/40 transition-colors">
                      <td className="p-3.5">
                        <span className="font-semibold text-white block">{g.userFullName}</span>
                        <span className="text-[11px] text-[#F4B5C8]/50 font-mono">{g.userEmail}</span>
                      </td>
                      <td className="p-3.5">
                        <span className="px-2 py-0.5 rounded font-mono text-[10px] bg-[#2C0012] text-[#FF85A2] border border-[#FFB4C8]/20">
                          {g.scopeType}
                        </span>
                      </td>
                      <td className="p-3.5 font-mono text-white">
                        {g.secretKey ? `Secret: ${g.secretKey}` : g.environmentName ? `Env: ${g.environmentName}` : g.projectName ? `Project: ${g.projectName}` : 'Workspace'}
                      </td>
                      <td className="p-3.5">
                        <span className="font-mono text-[#00E676] font-semibold">{g.permissionCode}</span>
                      </td>
                      <td className="p-3.5 text-right">
                        <button
                          onClick={() => handleRevokeGrant(g.id)}
                          className="p-1.5 rounded-lg text-[#FF4D82] hover:bg-[#360014] transition-colors"
                          title="Revoke grant"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {activeSubTab === 'REVIEWS' && (
        <>
          {selectedCampaignId ? (
            <AccessReviewDetailsView
              workspaceId={activeWorkspace?.id}
              campaignId={selectedCampaignId}
              onBack={() => setSelectedCampaignId(null)}
            />
          ) : (
            <AccessReviewsView
              onSelectCampaign={(cId) => setSelectedCampaignId(cId)}
            />
          )}
        </>
      )}

      {activeSubTab === 'INSPECTOR' && (
        <EffectivePermissionInspector workspaceId={activeWorkspace?.id} />
      )}
    </div>
  );
};

import React, { useState, useEffect } from 'react';
import { accessApi } from '../../api/access';
import { projectApi } from '../../api/projects';
import { environmentApi } from '../../api/environments';
import {
  ShieldCheck,
  Search,
  CheckCircle2,
  XCircle,
  Server,
  Key,
  ShieldAlert,
  RotateCcw,
  Loader2,
  Info,
  Layers
} from 'lucide-react';

export const EffectivePermissionInspector = ({ workspaceId }) => {
  const [projects, setProjects] = useState([]);
  const [environments, setEnvironments] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [selectedEnvId, setSelectedEnvId] = useState('');
  
  const [explanations, setExplanations] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (workspaceId) {
      loadProjects();
    }
  }, [workspaceId]);

  const loadProjects = async () => {
    try {
      const projs = await projectApi.list(workspaceId);
      const list = Array.isArray(projs) ? projs : projs?.data || [];
      setProjects(list);
      if (list.length > 0) {
        setSelectedProjectId(list[0].id);
        loadEnvironments(list[0].id);
      } else {
        loadEffectivePermissions(null, null);
      }
    } catch (err) {
      console.error('Failed to load projects:', err);
    }
  };

  const loadEnvironments = async (projId) => {
    try {
      const envs = await environmentApi.list(workspaceId, projId);
      const list = Array.isArray(envs) ? envs : envs?.data || [];
      setEnvironments(list);
      const envId = list.length > 0 ? list[0].id : null;
      setSelectedEnvId(envId || '');
      loadEffectivePermissions(projId, envId);
    } catch (err) {
      console.error('Failed to load environments:', err);
    }
  };

  const loadEffectivePermissions = async (pId, eId) => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const res = await accessApi.getEffectivePermissions(workspaceId, {
        projectId: pId || undefined,
        environmentId: eId || undefined
      });
      setExplanations(res.data?.data || res.data || []);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || 'Failed to evaluate effective permissions');
    } finally {
      setIsLoading(false);
    }
  };

  const handleProjectChange = (e) => {
    const pId = e.target.value;
    setSelectedProjectId(pId);
    loadEnvironments(pId);
  };

  const handleEnvChange = (e) => {
    const eId = e.target.value;
    setSelectedEnvId(eId);
    loadEffectivePermissions(selectedProjectId, eId);
  };

  return (
    <div className="space-y-6 font-body">
      {/* Target Selector */}
      <div className="p-4 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-[#3E0018] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D]">
            <Search className="w-4 h-4" />
          </div>
          <div>
            <h3 className="font-headline font-bold text-sm text-white">Target Scope Inspector</h3>
            <p className="text-xs text-[#F4B5C8]/60">Evaluate your current effective permissions and lineage attribution</p>
          </div>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          <select
            value={selectedProjectId}
            onChange={handleProjectChange}
            className="h-9 px-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] transition-colors"
          >
            <option value="">Workspace-Wide Scope</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>

          {selectedProjectId && (
            <select
              value={selectedEnvId}
              onChange={handleEnvChange}
              className="h-9 px-3 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-xs text-white focus:outline-none focus:border-[#FF2D6D] transition-colors"
            >
              <option value="">All Environments</option>
              {environments.map((e) => (
                <option key={e.id} value={e.id}>{e.name} ({e.envType})</option>
              ))}
            </select>
          )}

          <button
            onClick={() => loadEffectivePermissions(selectedProjectId, selectedEnvId)}
            className="p-2 rounded-xl bg-[#2C0012] border border-[#FFB4C8]/20 text-[#F4B5C8] hover:text-white transition-colors"
            title="Refresh permissions"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Permissions Matrix */}
      {isLoading ? (
        <div className="p-12 flex flex-col items-center justify-center gap-3 text-[#F4B5C8]/60">
          <Loader2 className="w-6 h-6 animate-spin text-[#FF2D6D]" />
          <span className="text-xs font-mono">Evaluating 11-step permission pipeline...</span>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {explanations.map((exp) => (
            <div
              key={exp.permissionCode}
              className={`p-4 rounded-xl border flex items-start justify-between gap-3 transition-all ${
                exp.granted
                  ? 'bg-[#1C000A] border-[#00C853]/30 shadow-md shadow-[#00C853]/5'
                  : 'bg-[#150007] border-[#FFB4C8]/10 opacity-75'
              }`}
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="font-mono font-bold text-xs text-white">{exp.permissionCode}</span>
                  {exp.granted ? (
                    <span className="px-2 py-0.5 rounded-full text-[9px] font-mono font-bold bg-[#00C853]/20 text-[#00E676] border border-[#00C853]/30">
                      AUTHORIZED
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full text-[9px] font-mono font-medium bg-[#FF2D6D]/15 text-[#FF4D82] border border-[#FF2D6D]/20">
                      DENIED
                    </span>
                  )}
                </div>

                <p className="text-[11px] text-[#F4B5C8]/70 font-mono">
                  {exp.granted ? exp.reason : exp.deniedReason}
                </p>

                {exp.granted && exp.sourceType && (
                  <div className="pt-1 flex items-center gap-2 text-[10px] text-[#F4B5C8]/50 font-mono">
                    <span>Source: <strong className="text-[#FF85A2]">{exp.sourceType}</strong></span>
                    <span>•</span>
                    <span>Scope: <strong className="text-white">{exp.scope}</strong></span>
                  </div>
                )}
              </div>

              <div className="shrink-0 mt-0.5">
                {exp.granted ? (
                  <CheckCircle2 className="w-4 h-4 text-[#00E676]" />
                ) : (
                  <XCircle className="w-4 h-4 text-[#FF4D82]" />
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

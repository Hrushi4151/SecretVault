import React from 'react';
import {
  ShieldCheck,
  ShieldAlert,
  Key,
  Clock,
  UserCheck,
  Layers,
  Info,
  CheckCircle2,
  XCircle,
  Calendar
} from 'lucide-react';

/**
 * Enterprise WhyAccess Lineage Component (Phase 5.5).
 * Explains why a user has or is denied a specific permission on a resource target.
 */
export const WhyAccess = ({ explanation }) => {
  if (!explanation) {
    return (
      <div className="p-4 rounded-xl bg-[#1C000A] border border-[#FFB4C8]/10 text-xs text-[#F4B5C8]/60 flex items-center gap-2">
        <Info className="w-4 h-4 text-[#FF85A2]" />
        <span>No lineage metadata available for this target.</span>
      </div>
    );
  }

  const isAllowed = explanation.allowed;

  const getSourceIcon = (sourceType) => {
    switch (sourceType) {
      case 'JIT_GRANT':
        return <Clock className="w-4 h-4 text-[#FFD600]" />;
      case 'GRANULAR_GRANT':
        return <Key className="w-4 h-4 text-[#00E676]" />;
      case 'ENVIRONMENT_ACCESS':
      case 'PROJECT_ACCESS':
        return <Layers className="w-4 h-4 text-[#00B0FF]" />;
      case 'WORKSPACE_ROLE':
      case 'ROLE':
        return <UserCheck className="w-4 h-4 text-[#E040FB]" />;
      default:
        return <ShieldCheck className="w-4 h-4 text-[#FF85A2]" />;
    }
  };

  return (
    <div className="p-5 rounded-2xl bg-[#1C000A] border border-[#FFB4C8]/15 space-y-4 font-body shadow-xl">
      {/* Header Decision Banner */}
      <div className="flex items-center justify-between gap-3 pb-3 border-b border-[#FFB4C8]/10">
        <div className="flex items-center gap-2.5">
          {isAllowed ? (
            <div className="p-2 rounded-xl bg-[#00C853]/15 border border-[#00C853]/30 text-[#00E676]">
              <CheckCircle2 className="w-5 h-5" />
            </div>
          ) : (
            <div className="p-2 rounded-xl bg-[#D50000]/15 border border-[#D50000]/30 text-[#FF5252]">
              <XCircle className="w-5 h-5" />
            </div>
          )}
          <div>
            <span className="text-xs font-mono uppercase tracking-wider text-[#F4B5C8]/50 block">Authorization Decision</span>
            <span className={`text-sm font-bold font-headline ${isAllowed ? 'text-[#00E676]' : 'text-[#FF5252]'}`}>
              {isAllowed ? 'ACCESS ALLOWED' : 'ACCESS DENIED'}
            </span>
          </div>
        </div>

        {explanation.permission && (
          <span className="px-2.5 py-1 rounded-lg text-xs font-mono font-medium bg-[#2C0012] text-[#FF85A2] border border-[#FFB4C8]/20">
            {explanation.permission}
          </span>
        )}
      </div>

      {/* Metadata Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
        {explanation.scope && (
          <div className="p-3 rounded-xl bg-[#26000F] border border-[#FFB4C8]/10 space-y-1">
            <span className="text-[10px] font-mono text-[#F4B5C8]/50 uppercase block">Scope Level</span>
            <span className="font-semibold text-white">{explanation.scope}</span>
          </div>
        )}

        {explanation.sourceType && (
          <div className="p-3 rounded-xl bg-[#26000F] border border-[#FFB4C8]/10 space-y-1">
            <span className="text-[10px] font-mono text-[#F4B5C8]/50 uppercase block">Primary Source</span>
            <div className="flex items-center gap-1.5 font-semibold text-white">
              {getSourceIcon(explanation.sourceType)}
              <span>{explanation.sourceType}</span>
            </div>
          </div>
        )}

        {explanation.sourceReferenceId && (
          <div className="p-3 rounded-xl bg-[#26000F] border border-[#FFB4C8]/10 space-y-1 sm:col-span-2">
            <span className="text-[10px] font-mono text-[#F4B5C8]/50 uppercase block">Source Reference ID</span>
            <span className="font-mono text-[11px] text-[#F4B5C8]/80 break-all">{explanation.sourceReferenceId}</span>
          </div>
        )}
      </div>

      {/* Rationale Explanation */}
      {explanation.explanation && (
        <div className="p-3.5 rounded-xl bg-[#2C0012]/60 border border-[#FFB4C8]/15 text-xs text-[#F4B5C8]/90 leading-relaxed">
          <span className="text-[10px] font-mono text-[#F4B5C8]/50 uppercase block mb-1">Audit Rationale</span>
          {explanation.explanation}
        </div>
      )}
    </div>
  );
};

import React from 'react';
import { AlertCircle, CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';
import { clsx } from 'clsx';

export const Alert = ({
  variant = 'danger',
  title,
  message,
  requestId,
  onDismiss,
  className = '',
}) => {
  const variantStyles = {
    danger: {
      bg: 'bg-[#F87171]/15 border-[#F87171]/35 text-[#F87171]',
      icon: <AlertCircle className="w-4 h-4 shrink-0 text-[#F87171] mt-0.5" />,
    },
    warning: {
      bg: 'bg-[#FBBF24]/15 border-[#FBBF24]/35 text-[#FBBF24]',
      icon: <AlertTriangle className="w-4 h-4 shrink-0 text-[#FBBF24] mt-0.5" />,
    },
    success: {
      bg: 'bg-[#34D399]/15 border-[#34D399]/35 text-[#34D399]',
      icon: <CheckCircle2 className="w-4 h-4 shrink-0 text-[#34D399] mt-0.5" />,
    },
    info: {
      bg: 'bg-[#818CF8]/15 border-[#818CF8]/35 text-[#818CF8]',
      icon: <Info className="w-4 h-4 shrink-0 text-[#818CF8] mt-0.5" />,
    },
  };

  const current = variantStyles[variant] || variantStyles.danger;

  return (
    <div
      className={clsx(
        'w-full flex items-start gap-3 p-3.5 rounded-xl border text-xs leading-relaxed transition-all duration-150 font-body',
        current.bg,
        className
      )}
    >
      {current.icon}
      <div className="flex-1 flex flex-col gap-0.5">
        {title && <span className="font-semibold text-white">{title}</span>}
        <span className="text-white/90">{message}</span>
        {requestId && (
          <span className="text-[10px] font-mono text-[#A26377] mt-1">
            Request ID: {requestId}
          </span>
        )}
      </div>
      {onDismiss && (
        <button
          onClick={onDismiss}
          className="p-1 rounded hover:bg-black/20 text-[#A26377] hover:text-white transition-colors"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
};

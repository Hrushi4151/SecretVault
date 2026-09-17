import React from 'react';
import { AlertCircle, CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';
import { clsx } from 'clsx';

export const Alert = ({
  variant = 'danger',
  title,
  message,
  requestId,
  onDismiss,
  className,
}) => {
  const variantStyles = {
    danger: {
      bg: 'bg-vault-danger/10 border-vault-danger/30 text-vault-danger',
      icon: <AlertCircle className="w-4 h-4 shrink-0 text-vault-danger mt-0.5" />,
    },
    warning: {
      bg: 'bg-vault-warning/10 border-vault-warning/30 text-vault-warning',
      icon: <AlertTriangle className="w-4 h-4 shrink-0 text-vault-warning mt-0.5" />,
    },
    success: {
      bg: 'bg-vault-success/10 border-vault-success/30 text-vault-success',
      icon: <CheckCircle2 className="w-4 h-4 shrink-0 text-vault-success mt-0.5" />,
    },
    info: {
      bg: 'bg-vault-info/10 border-vault-info/30 text-vault-info',
      icon: <Info className="w-4 h-4 shrink-0 text-vault-info mt-0.5" />,
    },
  };

  const current = variantStyles[variant] || variantStyles.danger;

  return (
    <div
      className={clsx(
        'w-full flex items-start gap-3 p-3.5 rounded-xl border text-xs leading-relaxed transition-all duration-150',
        current.bg,
        className
      )}
    >
      {current.icon}
      <div className="flex-1 flex flex-col gap-0.5">
        {title && <span className="font-semibold">{title}</span>}
        <span className="text-vault-text/90">{message}</span>
        {requestId && (
          <span className="text-[10px] font-mono text-vault-text-muted mt-1">
            Request ID: {requestId}
          </span>
        )}
      </div>
      {onDismiss && (
        <button
          onClick={onDismiss}
          className="p-1 rounded hover:bg-white/[0.10] text-vault-text-muted hover:text-vault-text transition-colors"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
};

import React from 'react';
import { clsx } from 'clsx';

export const Badge = ({
  children,
  variant = 'neutral',
  size = 'sm',
  pulse = false,
}) => {
  const variantStyles = {
    primary: 'bg-vault-primary-subtle text-vault-primary-light border-vault-primary/30',
    success: 'bg-vault-success-subtle text-vault-success border-vault-success-border',
    warning: 'bg-vault-warning-subtle text-vault-warning border-vault-warning-border',
    danger: 'bg-vault-danger-subtle text-vault-danger border-vault-danger-border',
    info: 'bg-vault-info-subtle text-vault-info border-vault-info/30',
    neutral: 'bg-white/[0.06] text-vault-text-secondary border-white/[0.10]',
  };

  const pulseColors = {
    primary: 'bg-vault-primary',
    success: 'bg-vault-success',
    warning: 'bg-vault-warning',
    danger: 'bg-vault-danger',
    info: 'bg-vault-info',
    neutral: 'bg-vault-text-muted',
  };

  const sizeStyles = {
    sm: 'text-[10px] font-mono px-2 py-0.5',
    md: 'text-xs font-mono px-2.5 py-1',
  };

  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 font-medium rounded-full border uppercase tracking-wider',
        variantStyles[variant],
        sizeStyles[size]
      )}
    >
      {pulse && <span className={clsx('w-1.5 h-1.5 rounded-full animate-pulse', pulseColors[variant])} />}
      {children}
    </span>
  );
};

export const RoleBadge = ({ role }) => {
  switch (role) {
    case 'OWNER':
      return <Badge variant="primary">OWNER</Badge>;
    case 'ADMIN':
      return <Badge variant="info">ADMIN</Badge>;
    case 'DEVELOPER':
      return <Badge variant="success">DEVELOPER</Badge>;
    case 'VIEWER':
      return <Badge variant="neutral">VIEWER</Badge>;
    default:
      return <Badge variant="neutral">{role}</Badge>;
  }
};

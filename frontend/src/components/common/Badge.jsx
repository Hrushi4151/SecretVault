import React from 'react';
import { clsx } from 'clsx';

export const Badge = ({
  children,
  variant = 'neutral',
  size = 'sm',
  pulse = false,
  className = '',
}) => {
  const variantStyles = {
    primary: 'bg-[#FF2D6D]/15 text-[#FF2D6D] border-[#FF2D6D]/35',
    success: 'bg-[#34D399]/12 text-[#34D399] border-[#34D399]/24',
    optimal: 'bg-[#34D399]/12 text-[#34D399] border-[#34D399]/24',
    warning: 'bg-[#FBBF24]/12 text-[#FBBF24] border-[#FBBF24]/24',
    danger: 'bg-[#F87171]/15 text-[#F87171] border-[#F87171]/32',
    critical: 'bg-[#F87171]/15 text-[#F87171] border-[#F87171]/32',
    info: 'bg-[#818CF8]/12 text-[#818CF8] border-[#818CF8]/22',
    neutral: 'bg-[#3F0016] text-[#F4B5C8] border-[#FFB4C8]/20',
  };

  const pulseColors = {
    primary: 'bg-[#FF2D6D]',
    success: 'bg-[#34D399]',
    optimal: 'bg-[#34D399]',
    warning: 'bg-[#FBBF24]',
    danger: 'bg-[#F87171]',
    critical: 'bg-[#F87171]',
    info: 'bg-[#818CF8]',
    neutral: 'bg-[#F4B5C8]',
  };

  const sizeStyles = {
    sm: 'text-[10px] font-mono px-2 py-0.5',
    md: 'text-xs font-mono px-2.5 py-1',
  };

  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 font-medium rounded-full border uppercase tracking-wider',
        variantStyles[variant] || variantStyles.neutral,
        sizeStyles[size] || sizeStyles.sm,
        className
      )}
    >
      {pulse && <span className={clsx('w-1.5 h-1.5 rounded-full animate-pulse shrink-0', pulseColors[variant] || 'bg-[#FF2D6D]')} />}
      {children}
    </span>
  );
};

export const RoleBadge = ({ role }) => {
  switch (role) {
    case 'OWNER':
      return <Badge variant="primary" pulse>OWNER</Badge>;
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

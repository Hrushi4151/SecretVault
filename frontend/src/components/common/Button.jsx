import React from 'react';
import { Loader2 } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export const Button = ({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  leftIcon,
  rightIcon,
  className,
  disabled,
  ...props
}) => {
  const baseStyles =
    'inline-flex items-center justify-center font-medium rounded-lg transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-vault-primary/40 disabled:opacity-50 disabled:cursor-not-allowed select-none active:scale-[0.98]';

  const variantStyles = {
    primary:
      'bg-vault-primary hover:bg-[#6c48ff] text-white shadow-[0_0_20px_rgba(124,92,255,0.3)] hover:shadow-[0_0_25px_rgba(124,92,255,0.45)] border border-vault-primary/50',
    secondary:
      'bg-white/[0.06] hover:bg-white/[0.10] text-vault-text border border-white/[0.10] hover:border-white/[0.18] backdrop-blur-md',
    danger:
      'bg-vault-danger/20 hover:bg-vault-danger/30 text-vault-danger border border-vault-danger/30',
    outline:
      'bg-transparent hover:bg-white/[0.05] text-vault-text border border-white/[0.15]',
    ghost:
      'bg-transparent hover:bg-white/[0.06] text-vault-text-secondary hover:text-vault-text',
  };

  const sizeStyles = {
    sm: 'text-xs px-2.5 py-1.5 gap-1.5',
    md: 'text-sm px-4 py-2 gap-2',
    lg: 'text-base px-6 py-2.5 gap-2.5',
  };

  return (
    <button
      disabled={disabled || isLoading}
      className={twMerge(clsx(baseStyles, variantStyles[variant], sizeStyles[size], className))}
      {...props}
    >
      {isLoading ? (
        <Loader2 className="w-4 h-4 animate-spin shrink-0" />
      ) : (
        leftIcon && <span className="shrink-0">{leftIcon}</span>
      )}
      <span>{children}</span>
      {!isLoading && rightIcon && <span className="shrink-0">{rightIcon}</span>}
    </button>
  );
};

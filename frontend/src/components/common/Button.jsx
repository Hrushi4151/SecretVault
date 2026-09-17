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
  className = '',
  disabled,
  ...props
}) => {
  const baseStyles =
    'inline-flex items-center justify-center font-medium rounded-xl transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-[#FF2D6D]/40 disabled:opacity-50 disabled:cursor-not-allowed select-none active:scale-[0.98] font-body';

  const variantStyles = {
    primary:
      'bg-[#FF2D6D] hover:bg-[#FF4D85] active:bg-[#B8003E] text-white font-bold shadow-lg shadow-[#FF2D6D]/25 border border-[#FF2D6D]',
    secondary:
      'bg-[#3F0016] hover:bg-[#4A001C] active:bg-[#580023] text-white border border-[#FFB4C8]/20 hover:border-[#FFB4C8]/40 shadow-sm',
    danger:
      'bg-[#F87171]/15 text-[#F87171] border border-[#F87171]/30 hover:bg-[#D30018] hover:text-white font-semibold',
    outline:
      'bg-transparent hover:bg-[#3F0016] text-[#F4B5C8] hover:text-white border border-[#FFB4C8]/25 hover:border-[#FFB4C8]/50',
    ghost:
      'bg-transparent hover:bg-[#4A001C] text-[#F4B5C8] hover:text-white',
  };

  const sizeStyles = {
    sm: 'text-xs px-3 py-1.5 gap-1.5',
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

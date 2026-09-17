import React, { forwardRef } from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export const Input = forwardRef(
  ({ label, error, helperText, leftIcon, rightIcon, className, id, ...props }, ref) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="w-full flex flex-col gap-1.5">
        {label && (
          <label htmlFor={inputId} className="text-xs font-medium text-vault-text-secondary">
            {label}
          </label>
        )}
        <div className="relative flex items-center">
          {leftIcon && (
            <div className="absolute left-3 text-vault-text-muted pointer-events-none flex items-center justify-center">
              {leftIcon}
            </div>
          )}
          <input
            id={inputId}
            ref={ref}
            className={twMerge(
              clsx(
                'w-full text-sm rounded-lg bg-white/[0.04] text-vault-text placeholder:text-vault-text-muted/60 border border-white/[0.10] px-3 py-2 transition-all duration-150',
                'focus:outline-none focus:border-vault-primary/70 focus:ring-2 focus:ring-vault-primary/20 focus:bg-white/[0.06]',
                leftIcon && 'pl-9',
                rightIcon && 'pr-9',
                error && 'border-vault-danger/60 focus:border-vault-danger focus:ring-vault-danger/20',
                className
              )
            )}
            {...props}
          />
          {rightIcon && (
            <div className="absolute right-3 flex items-center justify-center text-vault-text-muted">
              {rightIcon}
            </div>
          )}
        </div>
        {error && <span className="text-xs text-vault-danger font-medium">{error}</span>}
        {!error && helperText && (
          <span className="text-xs text-vault-text-muted">{helperText}</span>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

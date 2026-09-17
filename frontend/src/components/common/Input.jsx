import React, { forwardRef } from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export const Input = forwardRef(
  ({ label, error, helperText, leftIcon, rightIcon, className = '', id, ...props }, ref) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="w-full flex flex-col gap-1.5 font-body">
        {label && (
          <label htmlFor={inputId} className="text-xs font-semibold text-[#F4B5C8]">
            {label}
          </label>
        )}
        <div className="relative flex items-center">
          {leftIcon && (
            <div className="absolute left-3 text-[#A26377] pointer-events-none flex items-center justify-center">
              {leftIcon}
            </div>
          )}
          <input
            id={inputId}
            ref={ref}
            className={twMerge(
              clsx(
                'w-full text-xs sm:text-sm rounded-xl bg-[#3F0016] text-white placeholder:text-[#A26377] border border-[#FFB4C8]/20 px-3.5 py-2.5 transition-all duration-150',
                'focus:outline-none focus:border-[#FF2D6D] focus:ring-2 focus:ring-[#FF2D6D]/25 focus:bg-[#4A001C]',
                leftIcon && 'pl-9',
                rightIcon && 'pr-9',
                error && 'border-[#F87171] focus:border-[#F87171] focus:ring-[#F87171]/25',
                className
              )
            )}
            {...props}
          />
          {rightIcon && (
            <div className="absolute right-3 flex items-center justify-center text-[#A26377] hover:text-white">
              {rightIcon}
            </div>
          )}
        </div>
        {error && <span className="text-xs text-[#F87171] font-medium">{error}</span>}
        {!error && helperText && (
          <span className="text-xs text-[#A26377]">{helperText}</span>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

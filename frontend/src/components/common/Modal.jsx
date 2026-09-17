import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import { clsx } from 'clsx';

export const Modal = ({
  isOpen,
  onClose,
  title,
  description,
  children,
  maxWidth = 'md',
}) => {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    if (isOpen) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);
    } else {
      document.body.style.overflow = 'unset';
    }

    return () => {
      document.body.style.overflow = 'unset';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const maxWidthStyles = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-xl',
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-fade-in">
      <div
        className="fixed inset-0"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className={clsx(
          'relative w-full rounded-2xl bg-[#0D1117]/95 border border-white/[0.12] p-6 text-vault-text shadow-2xl shadow-black/80 animate-scale-in z-10 flex flex-col gap-4',
          maxWidthStyles[maxWidth] || 'max-w-md'
        )}
      >
        <div className="flex items-start justify-between">
          <div className="flex flex-col gap-1">
            <h3 className="text-lg font-semibold tracking-tight text-vault-text font-sans">
              {title}
            </h3>
            {description && (
              <p className="text-xs text-vault-text-secondary leading-relaxed">
                {description}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-vault-text-muted hover:text-vault-text hover:bg-white/[0.08] transition-colors"
            title="Close modal"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="mt-1">{children}</div>
      </div>
    </div>
  );
};

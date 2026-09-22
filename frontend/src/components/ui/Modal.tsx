import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import { Button } from './Button';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl';
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  footer,
  maxWidth = 'md',
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const maxWidthClass = {
    sm: 'max-w-md',
    md: 'max-w-lg',
    lg: 'max-w-2xl',
    xl: 'max-w-4xl',
  }[maxWidth];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Dim flat backdrop */}
      <div
        className="fixed inset-0 bg-relay-bg/80 backdrop-blur-[2px] transition-opacity"
        onClick={onClose}
      />

      {/* Ledger Modal Box */}
      <div
        role="dialog"
        aria-modal="true"
        className={`relative w-full ${maxWidthClass} bg-relay-surface border border-relay-border-solid rounded-[4px] shadow-2xl flex flex-col max-h-[90vh] overflow-hidden z-10`}
      >
        {/* Header */}
        <div className="px-5 py-3.5 border-b border-relay-border bg-relay-sidebar flex items-center justify-between">
          <div>
            <h2 className="font-typewriter text-sm tracking-wider uppercase text-relay-text">
              {title}
            </h2>
            {subtitle && (
              <p className="font-mono text-2xs text-relay-muted mt-0.5">{subtitle}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="text-relay-muted hover:text-relay-text p-1 rounded-[4px] hover:bg-relay-surface transition-colors cursor-pointer"
            aria-label="Close dialog"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content */}
        <div className="px-5 py-4 overflow-y-auto font-sans text-xs text-relay-text flex-1">
          {children}
        </div>

        {/* Footer */}
        {footer && (
          <div className="px-5 py-3 border-t border-relay-border bg-relay-sidebar/60 flex items-center justify-end space-x-2.5">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

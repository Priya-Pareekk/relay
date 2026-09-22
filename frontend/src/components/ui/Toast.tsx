import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  timestamp: string;
}

interface ToastContextValue {
  showToast: (type: ToastType, title: string, message?: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
};

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((type: ToastType, title: string, message?: string) => {
    const id = Math.random().toString(36).substring(2, 9);
    const now = new Date().toLocaleTimeString('en-US', { hour12: false });
    const newToast: Toast = { id, type, title, message, timestamp: now };

    setToasts((prev) => [...prev, newToast]);

    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  }, []);

  const removeToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col space-y-2 max-w-sm w-full pointer-events-none">
        {toasts.map((toast) => {
          const borderStyle = {
            success: 'border-relay-sage bg-relay-surface text-relay-text',
            error: 'border-relay-red bg-relay-surface text-relay-text',
            warning: 'border-relay-amber bg-relay-surface text-relay-text',
            info: 'border-relay-brass bg-relay-surface text-relay-text',
          }[toast.type];

          const Icon = {
            success: <CheckCircle2 className="w-4 h-4 text-relay-sage shrink-0 mt-0.5" />,
            error: <AlertCircle className="w-4 h-4 text-relay-red shrink-0 mt-0.5" />,
            warning: <AlertCircle className="w-4 h-4 text-relay-amber shrink-0 mt-0.5" />,
            info: <Info className="w-4 h-4 text-relay-brass shrink-0 mt-0.5" />,
          }[toast.type];

          return (
            <div
              key={toast.id}
              role="status"
              className={`pointer-events-auto p-3 border rounded-[4px] shadow-lg flex items-start space-x-2.5 transition-all ${borderStyle}`}
            >
              {Icon}
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-typewriter text-xs uppercase tracking-wider text-relay-text">
                    {toast.title}
                  </span>
                  <span className="font-mono text-3xs text-relay-muted">{toast.timestamp}</span>
                </div>
                {toast.message && (
                  <p className="font-sans text-xs text-relay-muted mt-0.5 break-words">
                    {toast.message}
                  </p>
                )}
              </div>
              <button
                onClick={() => removeToast(toast.id)}
                className="text-relay-muted hover:text-relay-text p-0.5 rounded-[2px]"
                aria-label="Dismiss toast"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};

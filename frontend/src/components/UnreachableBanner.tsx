import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface UnreachableBannerProps {
  error: Error | null;
  onRetry: () => void;
}

export const UnreachableBanner: React.FC<UnreachableBannerProps> = ({ error, onRetry }) => {
  return (
    <div className="p-4 rounded border border-rose-900/60 bg-rose-950/40 flex items-center justify-between gap-4 my-3 text-rose-200">
      <div className="flex items-center gap-3">
        <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0" />
        <div>
          <div className="text-xs font-semibold text-rose-300">API Connection Unavailable</div>
          <div className="text-2xs text-rose-400 font-mono mt-0.5">
            {error?.message || 'Failed to communicate with Relay backend on http://localhost:8080.'}
          </div>
        </div>
      </div>
      <button
        onClick={onRetry}
        className="px-3 py-1.5 bg-rose-900/80 hover:bg-rose-800 text-rose-100 text-xs font-medium rounded inline-flex items-center gap-1.5 transition-colors focus:ring-2 focus:ring-rose-500 focus:outline-none"
      >
        <RefreshCw className="w-3.5 h-3.5" />
        Retry Connection
      </button>
    </div>
  );
};

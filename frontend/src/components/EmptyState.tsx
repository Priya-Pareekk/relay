import React from 'react';
import { Inbox, CheckCircle2 } from 'lucide-react';

interface EmptyStateProps {
  type?: 'general' | 'no-dead-letters' | 'no-filter-match';
  title?: string;
  message?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  type = 'general',
  title,
  message,
}) => {
  if (type === 'no-dead-letters') {
    return (
      <div className="py-12 px-4 flex flex-col items-center justify-center text-center border border-border-subtle rounded bg-bg-surface">
        <div className="w-9 h-9 rounded-full bg-emerald-950/60 border border-emerald-800/40 flex items-center justify-center mb-3">
          <CheckCircle2 className="w-5 h-5 text-emerald-400" />
        </div>
        <div className="text-xs font-semibold text-slate-200">No Dead Letters in Queue</div>
        <div className="text-2xs text-slate-400 max-w-sm mt-1">
          All jobs are completing successfully or within active retry windows. The dead-letter queue is empty.
        </div>
      </div>
    );
  }

  return (
    <div className="py-12 px-4 flex flex-col items-center justify-center text-center border border-border-subtle rounded bg-bg-surface">
      <div className="w-9 h-9 rounded-full bg-slate-900 border border-border-subtle flex items-center justify-center mb-3 text-slate-500">
        <Inbox className="w-5 h-5" />
      </div>
      <div className="text-xs font-semibold text-slate-300">
        {title || (type === 'no-filter-match' ? 'No Matching Jobs Found' : 'No Jobs in Queue')}
      </div>
      <div className="text-2xs text-slate-400 max-w-sm mt-1">
        {message || (type === 'no-filter-match' ? 'Try adjusting your status or job type filters.' : 'Submit a new job to start background processing.')}
      </div>
    </div>
  );
};

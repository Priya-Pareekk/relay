import React from 'react';
import { STATUS_COLORS, JobStatusKey } from '@/styles/tokens';

interface StatusDotProps {
  status: string;
  showText?: boolean;
}

export const StatusDot: React.FC<StatusDotProps> = ({ status, showText = true }) => {
  const token = STATUS_COLORS[status as JobStatusKey] || STATUS_COLORS.PENDING;

  return (
    <div className="inline-flex items-center gap-1.5 font-medium select-none">
      <span className={`w-2 h-2 rounded-full ${token.dot} shrink-0 ring-2 ring-black/40`} />
      {showText && <span className={`text-xs ${token.text}`}>{token.label}</span>}
    </div>
  );
};

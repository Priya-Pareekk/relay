import React from 'react';

export const Skeleton: React.FC<{ className?: string }> = ({ className = '' }) => {
  return (
    <div
      className={`animate-pulse bg-relay-surface-hover/60 rounded-[2px] ${className}`}
    />
  );
};

export const TableSkeleton: React.FC<{ rows?: number; cols?: number }> = ({
  rows = 5,
  cols = 6,
}) => {
  return (
    <div className="w-full border border-relay-border rounded-[4px] bg-relay-surface overflow-hidden">
      <div className="bg-relay-sidebar/80 border-b border-relay-border px-5 py-3 flex items-center space-x-6">
        {Array.from({ length: cols }).map((_, i) => (
          <Skeleton key={i} className="h-3.5 w-20" />
        ))}
      </div>
      <div className="divide-y divide-relay-border/60">
        {Array.from({ length: rows }).map((_, r) => (
          <div key={r} className="px-5 py-4 flex items-center space-x-6">
            {Array.from({ length: cols }).map((_, c) => (
              <Skeleton
                key={c}
                className={`h-3.5 ${
                  c === 0 ? 'w-28' : c === 1 ? 'w-36' : c === 2 ? 'w-20' : 'w-16'
                }`}
              />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
};

export const MetricCardSkeleton: React.FC = () => {
  return (
    <div className="p-4 bg-relay-surface border border-relay-border rounded-[4px]">
      <Skeleton className="h-3 w-24 mb-3" />
      <Skeleton className="h-7 w-20 mb-2" />
      <Skeleton className="h-2.5 w-32" />
    </div>
  );
};

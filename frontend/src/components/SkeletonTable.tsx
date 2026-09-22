import React from 'react';

interface SkeletonTableProps {
  rows?: number;
  cols?: number;
}

export const SkeletonTable: React.FC<SkeletonTableProps> = ({ rows = 8, cols = 6 }) => {
  return (
    <div className="w-full border border-border-subtle rounded divide-y divide-border-subtle bg-bg-surface overflow-hidden">
      {/* Header Skeleton */}
      <div className="h-8 bg-bg-card flex items-center px-4 gap-4">
        {Array.from({ length: cols }).map((_, i) => (
          <div key={i} className="h-3 bg-slate-800 rounded animate-pulse" style={{ width: `${60 + (i % 3) * 30}px` }} />
        ))}
      </div>
      {/* Rows */}
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="h-9 flex items-center px-4 gap-4 bg-bg-surface">
          {Array.from({ length: cols }).map((_, c) => (
            <div
              key={c}
              className="h-2.5 bg-slate-800/80 rounded animate-pulse"
              style={{ width: `${40 + ((r + c) % 4) * 35}px` }}
            />
          ))}
        </div>
      ))}
    </div>
  );
};

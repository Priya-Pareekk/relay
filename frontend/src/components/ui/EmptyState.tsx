import React from 'react';
import { Inbox } from 'lucide-react';
import { Button } from './Button';

interface EmptyStateProps {
  message?: string;
  actionLabel?: string;
  onAction?: () => void;
  icon?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  message = 'No records logged in this ledger period.',
  actionLabel,
  onAction,
  icon,
}) => {
  return (
    <div className="py-12 px-4 text-center border border-dashed border-relay-border rounded-[4px] bg-relay-surface/40 flex flex-col items-center justify-center">
      <div className="text-relay-muted/60 mb-2">
        {icon || <Inbox className="w-6 h-6 stroke-[1.5]" />}
      </div>
      <p className="font-sans text-xs text-relay-muted max-w-md mb-3">{message}</p>
      {actionLabel && onAction && (
        <Button variant="outline" size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
};

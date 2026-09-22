import React from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Button } from './Button';

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'Signal Interrupted',
  message = 'Failed to fetch ledger data from the relay server.',
  onRetry,
  className = '',
}) => {
  return (
    <div
      className={`p-4 border border-relay-red/50 bg-relay-red/10 rounded-[4px] flex items-center justify-between space-x-3 ${className}`}
    >
      <div className="flex items-start space-x-3">
        <AlertTriangle className="w-4 h-4 text-relay-red shrink-0 mt-0.5" />
        <div>
          <h4 className="font-typewriter text-xs uppercase tracking-wider text-relay-red">
            {title}
          </h4>
          <p className="font-sans text-xs text-relay-text/80 mt-0.5">{message}</p>
        </div>
      </div>
      {onRetry && (
        <Button
          variant="danger"
          size="xs"
          onClick={onRetry}
          leftIcon={<RotateCcw className="w-3 h-3" />}
        >
          RETRY SIGNAL
        </Button>
      )}
    </div>
  );
};

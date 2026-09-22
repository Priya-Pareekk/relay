import React from 'react';
import { JobStatus } from '@/api/types';

interface BadgeProps {
  status: JobStatus | 'SUCCESS' | 'FAILURE' | 'RUNNING' | 'CRON_ACTIVE' | 'CRON_PAUSED' | 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  label?: string;
  className?: string;
  size?: 'sm' | 'md';
  noTilt?: boolean;
}

export const StatusBadge: React.FC<BadgeProps> = ({
  status,
  label,
  className = '',
  size = 'md',
  noTilt = false,
}) => {
  const getStyles = () => {
    switch (status) {
      case 'COMPLETED':
      case 'SUCCESS':
      case 'CRON_ACTIVE':
      case 'CLOSED':
        return {
          bg: 'bg-relay-sage/10',
          border: 'border-relay-sage',
          text: 'text-relay-sage',
          defaultLabel: status === 'COMPLETED' ? 'COMPLETED' : status === 'CRON_ACTIVE' ? 'ACTIVE' : 'SUCCESS',
        };
      case 'RETRYING':
      case 'HALF_OPEN':
        return {
          bg: 'bg-relay-amber/10',
          border: 'border-relay-amber',
          text: 'text-relay-amber',
          defaultLabel: 'RETRYING',
        };
      case 'DEAD_LETTER':
      case 'FAILURE':
      case 'OPEN':
        return {
          bg: 'bg-relay-red/10',
          border: 'border-relay-red',
          text: 'text-relay-red',
          defaultLabel: status === 'DEAD_LETTER' ? 'DEAD LETTER' : 'FAILED',
        };
      case 'PROCESSING':
      case 'RUNNING':
        return {
          bg: 'bg-relay-brass/10',
          border: 'border-relay-brass',
          text: 'text-relay-brass',
          defaultLabel: 'PROCESSING',
        };
      case 'PENDING':
      case 'CRON_PAUSED':
      case 'CANCELLED':
      default:
        return {
          bg: 'bg-relay-surface',
          border: 'border-relay-border',
          text: 'text-relay-muted',
          defaultLabel: status === 'CANCELLED' ? 'CANCELLED' : status === 'CRON_PAUSED' ? 'PAUSED' : 'PENDING',
        };
    }
  };

  const style = getStyles();
  const textToShow = label || style.defaultLabel;
  const sizeClasses = size === 'sm' ? 'px-1.5 py-0.5 text-[0.65rem] tracking-wider' : 'px-2.5 py-1 text-xs tracking-wider';

  return (
    <span
      className={`inline-flex items-center justify-center font-mono font-medium uppercase border rounded-[4px] select-none transition-transform duration-100 ${
        style.bg
      } ${style.border} ${style.text} ${sizeClasses} ${
        noTilt ? '' : '-rotate-1 hover:rotate-0'
      } ${className}`}
    >
      {textToShow}
    </span>
  );
};

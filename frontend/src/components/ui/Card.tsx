import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  className?: string;
  headerTitle?: string;
  headerAction?: React.ReactNode;
  badge?: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({
  children,
  className = '',
  headerTitle,
  headerAction,
  badge,
  ...props
}) => {
  return (
    <div
      className={`bg-relay-surface border border-relay-border rounded-[4px] overflow-hidden ${className}`}
      {...props}
    >
      {(headerTitle || headerAction || badge) && (
        <div className="px-4 py-3 border-b border-relay-border flex items-center justify-between bg-relay-sidebar/40">
          <div className="flex items-center space-x-2.5">
            {headerTitle && (
              <h3 className="font-typewriter text-xs uppercase tracking-wider text-relay-text">
                {headerTitle}
              </h3>
            )}
            {badge}
          </div>
          {headerAction && <div>{headerAction}</div>}
        </div>
      )}
      <div>{children}</div>
    </div>
  );
};

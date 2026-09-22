import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'brass' | 'outline' | 'danger' | 'ghost' | 'sage';
  size?: 'xs' | 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'outline',
  size = 'md',
  isLoading = false,
  leftIcon,
  rightIcon,
  className = '',
  disabled,
  ...props
}) => {
  const getVariantStyles = () => {
    switch (variant) {
      case 'brass':
        return 'bg-relay-brass text-relay-bg border border-relay-brass hover:bg-relay-brass-dim font-medium';
      case 'sage':
        return 'bg-relay-sage text-relay-bg border border-relay-sage hover:bg-relay-sage-dim font-medium';
      case 'danger':
        return 'bg-relay-red/20 text-relay-red border border-relay-red/80 hover:bg-relay-red/30 hover:border-relay-red';
      case 'ghost':
        return 'bg-transparent text-relay-muted hover:text-relay-text hover:bg-relay-surface border border-transparent';
      case 'outline':
      default:
        return 'bg-relay-surface text-relay-text border border-relay-border hover:bg-relay-surface-hover hover:border-relay-muted/60';
    }
  };

  const getSizeStyles = () => {
    switch (size) {
      case 'xs':
        return 'px-2 py-1 text-2xs';
      case 'sm':
        return 'px-2.5 py-1.5 text-xs';
      case 'lg':
        return 'px-4 py-2.5 text-sm';
      case 'md':
      default:
        return 'px-3 py-2 text-xs';
    }
  };

  return (
    <button
      className={`inline-flex items-center justify-center font-sans rounded-[4px] select-none transition-colors duration-100 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer ${getVariantStyles()} ${getSizeStyles()} ${className}`}
      disabled={disabled || isLoading}
      {...props}
    >
      {isLoading ? (
        <span className="inline-flex items-center space-x-1.5 font-mono text-2xs">
          <svg className="animate-spin h-3.5 w-3.5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
          </svg>
          <span>PROCESSING...</span>
        </span>
      ) : (
        <>
          {leftIcon && <span className="mr-1.5">{leftIcon}</span>}
          {children}
          {rightIcon && <span className="ml-1.5">{rightIcon}</span>}
        </>
      )}
    </button>
  );
};

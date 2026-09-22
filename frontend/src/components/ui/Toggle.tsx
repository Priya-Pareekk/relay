import React from 'react';

interface ToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  label?: string;
  className?: string;
}

export const Toggle: React.FC<ToggleProps> = ({
  checked,
  onChange,
  disabled = false,
  label,
  className = '',
}) => {
  return (
    <label
      className={`inline-flex items-center space-x-2.5 select-none cursor-pointer ${
        disabled ? 'opacity-40 cursor-not-allowed' : ''
      } ${className}`}
    >
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => !disabled && onChange(!checked)}
        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-[4px] border transition-colors duration-150 focus-visible:outline-none ${
          checked
            ? 'bg-relay-sage/20 border-relay-sage'
            : 'bg-relay-surface border-relay-border'
        }`}
      >
        <span
          className={`pointer-events-none inline-block h-3.5 w-3.5 rounded-[2px] transform transition-transform duration-150 mt-[2px] ${
            checked
              ? 'translate-x-4 bg-relay-sage'
              : 'translate-x-0.5 bg-relay-muted/70'
          }`}
        />
      </button>
      {label && <span className="font-sans text-xs text-relay-text">{label}</span>}
    </label>
  );
};

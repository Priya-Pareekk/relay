import React from 'react';

export const TableContainer: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = '',
}) => {
  return (
    <div className={`w-full overflow-x-auto border border-relay-border rounded-[4px] bg-relay-surface ${className}`}>
      <table className="w-full text-left border-collapse">{children}</table>
    </div>
  );
};

export const TableHead: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = '',
}) => {
  return (
    <thead className={`bg-relay-sidebar/80 border-b border-relay-border ${className}`}>
      {children}
    </thead>
  );
};

export const TableHeaderCell: React.FC<{
  children: React.ReactNode;
  className?: string;
  align?: 'left' | 'center' | 'right';
}> = ({ children, className = '', align = 'left' }) => {
  const alignClass = align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left';
  return (
    <th
      className={`px-5 py-3 font-typewriter text-2xs uppercase tracking-wider text-relay-muted font-normal select-none ${alignClass} ${className}`}
    >
      {children}
    </th>
  );
};

export const TableBody: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = '',
}) => {
  return <tbody className={`divide-y divide-relay-border/60 ${className}`}>{children}</tbody>;
};

export const TableRow: React.FC<{
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  isClickable?: boolean;
}> = ({ children, className = '', onClick, isClickable = false }) => {
  return (
    <tr
      onClick={onClick}
      className={`transition-colors duration-75 ${
        isClickable ? 'cursor-pointer hover:bg-relay-surface-hover/80' : 'hover:bg-relay-surface-hover/40'
      } ${className}`}
    >
      {children}
    </tr>
  );
};

export const TableCell: React.FC<{
  children: React.ReactNode;
  className?: string;
  align?: 'left' | 'center' | 'right';
  mono?: boolean;
  onClick?: (e: React.MouseEvent<HTMLTableCellElement>) => void;
}> = ({ children, className = '', align = 'left', mono = false, onClick }) => {
  const alignClass = align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left';
  return (
    <td
      onClick={onClick}
      className={`px-5 py-3.5 text-xs text-relay-text ${
        mono ? 'font-mono' : 'font-sans'
      } ${alignClass} ${className}`}
    >
      {children}
    </td>
  );
};

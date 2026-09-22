import React, { useState } from 'react';
import { ChevronDown, ChevronRight, Copy, Check } from 'lucide-react';

interface JsonTreeProps {
  data: any;
  initialExpanded?: boolean;
}

export const JsonTree: React.FC<JsonTreeProps> = ({ data, initialExpanded = true }) => {
  const [expanded, setExpanded] = useState<Record<string, boolean>>({ root: initialExpanded });
  const [copied, setCopied] = useState(false);

  const toggle = (key: string) => {
    setExpanded((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(JSON.stringify(data, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const renderValue = (val: any, path: string): React.ReactNode => {
    if (val === null) return <span className="text-slate-500 font-mono">null</span>;
    if (typeof val === 'undefined') return <span className="text-slate-500 font-mono">undefined</span>;
    if (typeof val === 'boolean') return <span className="text-purple-400 font-mono">{String(val)}</span>;
    if (typeof val === 'number') return <span className="text-amber-400 font-mono">{val}</span>;
    if (typeof val === 'string') return <span className="text-emerald-400 font-mono">"{val}"</span>;

    if (Array.isArray(val)) {
      const isExp = expanded[path] ?? true;
      if (val.length === 0) return <span className="text-slate-400 font-mono">[]</span>;
      return (
        <div className="inline-block">
          <button
            onClick={() => toggle(path)}
            className="inline-flex items-center text-slate-400 hover:text-slate-200 text-2xs font-mono"
          >
            {isExp ? <ChevronDown className="w-3 h-3 inline" /> : <ChevronRight className="w-3 h-3 inline" />}
            Array({val.length})
          </button>
          {isExp && (
            <div className="pl-4 border-l border-border-subtle my-0.5 space-y-0.5">
              {val.map((item, idx) => (
                <div key={idx} className="flex gap-2">
                  <span className="text-slate-500 font-mono text-2xs">{idx}:</span>
                  {renderValue(item, `${path}.${idx}`)}
                </div>
              ))}
            </div>
          )}
        </div>
      );
    }

    if (typeof val === 'object') {
      const isExp = expanded[path] ?? true;
      const keys = Object.keys(val);
      if (keys.length === 0) return <span className="text-slate-400 font-mono">{'{}'}</span>;
      return (
        <div className="inline-block w-full">
          <button
            onClick={() => toggle(path)}
            className="inline-flex items-center text-slate-400 hover:text-slate-200 text-2xs font-mono"
          >
            {isExp ? <ChevronDown className="w-3 h-3 inline" /> : <ChevronRight className="w-3 h-3 inline" />}
            Object({keys.length})
          </button>
          {isExp && (
            <div className="pl-4 border-l border-border-subtle my-0.5 space-y-0.5">
              {keys.map((k) => (
                <div key={k} className="flex items-start gap-1 text-2xs">
                  <span className="text-blue-300 font-mono font-medium">{k}:</span>
                  <div className="flex-1">{renderValue(val[k], `${path}.${k}`)}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      );
    }

    return String(val);
  };

  return (
    <div className="relative rounded bg-bg-base border border-border-subtle p-3 text-xs font-mono overflow-auto max-h-96">
      <div className="absolute top-2 right-2 flex items-center gap-1">
        <button
          onClick={handleCopy}
          className="p-1 rounded bg-bg-card hover:bg-slate-800 border border-border-subtle text-slate-400 hover:text-slate-200 transition-colors"
          title="Copy raw JSON"
        >
          {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
        </button>
      </div>
      {renderValue(data, 'root')}
    </div>
  );
};

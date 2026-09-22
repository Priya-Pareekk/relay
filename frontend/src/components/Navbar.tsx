import React from 'react';
import { NavLink } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { Activity, Layers, Clock, BarChart3, Plus, Sparkles } from 'lucide-react';

interface NavbarProps {
  onOpenSubmitModal: () => void;
  isFetching?: boolean;
}

export const Navbar: React.FC<NavbarProps> = ({ onOpenSubmitModal, isFetching = false }) => {
  const queryClient = useQueryClient();

  const seedMutation = useMutation({
    mutationFn: () => api.seedDemoData(50),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['job-definitions'] });
      queryClient.invalidateQueries({ queryKey: ['metrics'] });
    },
  });

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-1.5 text-xs font-medium rounded flex items-center gap-1.5 transition-colors focus:ring-2 focus:ring-blue-500 focus:outline-none ${
      isActive
        ? 'bg-blue-950/60 text-blue-400 border border-blue-800/40'
        : 'text-slate-400 hover:text-slate-200 hover:bg-bg-hover'
    }`;

  return (
    <header className="h-12 border-b border-border-subtle bg-bg-surface flex items-center justify-between px-4 sticky top-0 z-20 select-none">
      <div className="flex items-center gap-6">
        {/* Brand */}
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded bg-blue-600 flex items-center justify-center text-white font-mono font-bold text-xs shadow-sm">
            R
          </div>
          <span className="font-mono font-semibold text-xs text-slate-100 tracking-wider">
            RELAY
          </span>
          <span className="text-2xs text-slate-400 font-mono px-1.5 py-0.5 rounded bg-bg-card border border-border-subtle">
            OPS v1.0
          </span>
        </div>

        {/* Navigation */}
        <nav className="flex items-center gap-1">
          <NavLink to="/jobs" className={linkClass}>
            <Layers className="w-3.5 h-3.5" />
            <span>Job Queue</span>
          </NavLink>
          <NavLink to="/definitions" className={linkClass}>
            <Clock className="w-3.5 h-3.5" />
            <span>Cron Definitions</span>
          </NavLink>
          <NavLink to="/metrics" className={linkClass}>
            <BarChart3 className="w-3.5 h-3.5" />
            <span>Metrics & Health</span>
          </NavLink>
        </nav>
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-2.5">
        {/* Live sync badge */}
        <div className="flex items-center gap-1.5 text-2xs text-slate-400 px-2 py-1 rounded bg-bg-base border border-border-subtle">
          <span className={`w-1.5 h-1.5 rounded-full ${isFetching ? 'bg-blue-400 animate-ping' : 'bg-emerald-400'}`} />
          <span className="font-mono text-2xs">LIVE (3s POLL)</span>
        </div>

        {/* Seed Demo Data Button */}
        <button
          onClick={() => seedMutation.mutate()}
          disabled={seedMutation.isPending}
          className="px-2.5 py-1.5 bg-bg-card hover:bg-slate-800 active:bg-slate-700 disabled:opacity-50 border border-border-subtle text-amber-300 text-xs font-medium rounded flex items-center gap-1.5 transition-colors focus:ring-2 focus:ring-amber-500 focus:outline-none"
          title="Seed 50 mixed synthetic demo jobs (healthy, retry, and dead letter)"
        >
          <Sparkles className="w-3.5 h-3.5 text-amber-400" />
          <span>{seedMutation.isPending ? 'Seeding 50 Jobs...' : 'Seed Demo Data'}</span>
        </button>

        {/* Submit Job Button */}
        <button
          onClick={onOpenSubmitModal}
          className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-xs font-medium rounded flex items-center gap-1.5 transition-colors focus:ring-2 focus:ring-blue-400 focus:outline-none shadow-sm"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>Submit Job</span>
        </button>
      </div>
    </header>
  );
};


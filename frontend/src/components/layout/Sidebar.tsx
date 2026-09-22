import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Layers,
  Clock,
  AlertOctagon,
  Radio,
  PlusCircle,
  DatabaseZap,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

interface SidebarProps {
  onOpenSubmitModal: () => void;
  onSeedData: () => void;
  isSeeding?: boolean;
}

export const Sidebar: React.FC<SidebarProps> = ({
  onOpenSubmitModal,
  onSeedData,
  isSeeding = false,
}) => {
  const { data: metrics } = useQuery({
    queryKey: ['metrics-summary'],
    queryFn: () => api.getMetrics(),
    refetchInterval: 3000,
  });

  const deadLettersCount = metrics?.deadLetterJobs ?? 0;
  const retryingCount = metrics?.retryingJobs ?? 0;

  const navItems = [
    {
      to: '/dashboard',
      label: 'Dashboard',
      icon: LayoutDashboard,
      badge: null,
    },
    {
      to: '/jobs',
      label: 'Jobs Ledger',
      icon: Layers,
      badge: metrics?.totalJobs ? `${metrics.totalJobs}` : null,
      badgeColor: 'text-relay-muted bg-relay-surface border-relay-border',
    },
    {
      to: '/definitions',
      label: 'Job Definitions',
      icon: Clock,
      badge: metrics?.enabledDefinitions !== undefined ? `${metrics.enabledDefinitions} Active` : null,
      badgeColor: 'text-relay-sage bg-relay-sage/10 border-relay-sage/40',
    },
    {
      to: '/dead-letters',
      label: 'Dead Letters',
      icon: AlertOctagon,
      badge: deadLettersCount > 0 ? `${deadLettersCount}` : '0',
      badgeColor: deadLettersCount > 0
        ? 'text-relay-red bg-relay-red/15 border-relay-red animate-pulse'
        : 'text-relay-muted bg-relay-surface border-relay-border',
    },
  ];

  return (
    <aside className="w-16 lg:w-60 shrink-0 bg-relay-sidebar border-r border-relay-border flex flex-col justify-between select-none min-h-screen">
      {/* Brand Header */}
      <div>
        <div className="h-14 px-4 flex items-center border-b border-relay-border space-x-3 bg-relay-sidebar">
          <div className="w-7 h-7 rounded-[4px] border border-relay-brass bg-relay-brass/10 flex items-center justify-center text-relay-brass shrink-0">
            <Radio className="w-4 h-4" />
          </div>
          <div className="hidden lg:block min-w-0">
            <div className="flex items-center space-x-2">
              <span className="font-typewriter text-sm tracking-wider uppercase text-relay-brass font-bold">
                RELAY
              </span>
              <span className="font-mono text-3xs px-1 py-0.2 border border-relay-border rounded-[2px] text-relay-muted">
                v2.4
              </span>
            </div>
            <p className="font-mono text-3xs text-relay-muted truncate uppercase tracking-tight">
              Night Dispatch Desk
            </p>
          </div>
        </div>

        {/* Action Button: Submit Job */}
        <div className="p-3 border-b border-relay-border">
          <button
            onClick={onOpenSubmitModal}
            className="w-full flex items-center justify-center lg:justify-start space-x-2 px-3 py-2 bg-relay-brass text-relay-bg border border-relay-brass rounded-[4px] hover:bg-relay-brass-dim transition-colors text-xs font-sans font-medium cursor-pointer"
            title="Dispatch New Job"
          >
            <PlusCircle className="w-4 h-4 shrink-0" />
            <span className="hidden lg:inline font-typewriter uppercase tracking-wider text-xs">
              Dispatch Job
            </span>
          </button>
        </div>

        {/* Navigation links */}
        <nav className="p-2 space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `flex items-center justify-between px-3 py-2 rounded-[4px] border transition-all text-xs ${
                    isActive
                      ? 'bg-relay-surface text-relay-brass border-relay-brass/50 font-medium'
                      : 'text-relay-muted hover:text-relay-text hover:bg-relay-surface/50 border-transparent'
                  }`
                }
                title={item.label}
              >
                <div className="flex items-center space-x-3 min-w-0">
                  <Icon className="w-4 h-4 shrink-0" />
                  <span className="hidden lg:inline font-typewriter tracking-wide uppercase truncate">
                    {item.label}
                  </span>
                </div>
                {item.badge && (
                  <span
                    className={`hidden lg:inline-block px-1.5 py-0.2 text-3xs font-mono border rounded-[2px] ${item.badgeColor}`}
                  >
                    {item.badge}
                  </span>
                )}
              </NavLink>
            );
          })}
        </nav>
      </div>

      {/* Footer Dispatch Status / System Status */}
      <div className="p-3 border-t border-relay-border space-y-2.5 bg-relay-sidebar/80">
        {/* Retrying Notice if active */}
        {retryingCount > 0 && (
          <div className="hidden lg:flex items-center justify-between px-2.5 py-1.5 border border-relay-amber/40 bg-relay-amber/10 rounded-[4px] text-2xs text-relay-amber font-mono">
            <span>RETRYING NOW:</span>
            <span className="font-bold">{retryingCount}</span>
          </div>
        )}

        {/* Demo Data Seeder */}
        <button
          onClick={onSeedData}
          disabled={isSeeding}
          className="w-full flex items-center justify-center lg:justify-start space-x-2 px-2.5 py-1.5 bg-relay-surface border border-relay-border rounded-[4px] hover:bg-relay-surface-hover text-relay-muted hover:text-relay-text transition-colors text-2xs font-mono disabled:opacity-50 cursor-pointer"
          title="Seed Demo Dispatch Data"
        >
          <DatabaseZap className="w-3.5 h-3.5 shrink-0 text-relay-brass" />
          <span className="hidden lg:inline">
            {isSeeding ? 'SEEDING JOBS...' : 'SEED DEMO LEDGER'}
          </span>
        </button>

        {/* Station Live indicator */}
        <div className="hidden lg:flex items-center justify-between pt-1 border-t border-relay-border/40 font-mono text-3xs text-relay-muted">
          <span className="flex items-center space-x-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-relay-sage inline-block animate-pulse" />
            <span>DISPATCH LIVE</span>
          </span>
          <span>POLL: 3.0s</span>
        </div>
      </div>
    </aside>
  );
};

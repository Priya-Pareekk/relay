import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { CIRCUIT_STATE_COLORS } from '@/styles/tokens';
import { UnreachableBanner } from '@/components/UnreachableBanner';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts';
import { Activity, ShieldAlert, Cpu, CheckCircle, RefreshCw, AlertTriangle } from 'lucide-react';

export const MetricsView: React.FC = () => {
  const {
    data: metrics,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['metrics'],
    queryFn: api.getMetrics,
    refetchInterval: 3000,
  });

  if (isLoading) {
    return (
      <div className="p-4 max-w-7xl mx-auto space-y-4 animate-pulse">
        <div className="h-6 w-48 bg-slate-800 rounded" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="h-40 bg-bg-surface border border-border-subtle rounded" />
          <div className="h-40 bg-bg-surface border border-border-subtle rounded" />
          <div className="h-40 bg-bg-surface border border-border-subtle rounded" />
        </div>
      </div>
    );
  }

  if (error || !metrics) {
    return (
      <div className="p-4 max-w-7xl mx-auto space-y-4">
        <UnreachableBanner error={error as Error} onRetry={() => refetch()} />
      </div>
    );
  }

  // Chart data formatting
  const statusChartData = [
    { name: 'Completed', count: metrics.completedJobs, color: '#10b981' },
    { name: 'Processing', count: metrics.processingJobs, color: '#3b82f6' },
    { name: 'Retrying', count: metrics.retryingJobs, color: '#f59e0b' },
    { name: 'Pending', count: metrics.pendingJobs, color: '#64748b' },
    { name: 'Dead Letter', count: metrics.deadLetterJobs, color: '#ef4444' },
    { name: 'Cancelled', count: metrics.cancelledJobs, color: '#8b5cf6' },
  ].filter((d) => d.count > 0);

  const typeChartData = Object.entries(metrics.jobsByType || {}).map(([type, count]) => ({
    type,
    count,
  }));

  const circuitBreakers = metrics.circuitBreakers || {
    EMAIL_NOTIFICATION: 'CLOSED',
    REPORT_GENERATION: 'CLOSED',
  };

  return (
    <div className="p-4 space-y-4 max-w-7xl mx-auto">
      {/* Header */}
      <div>
        <h1 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
          <Activity className="w-4 h-4 text-blue-400" />
          System Telemetry, Observability & Circuit Breakers
        </h1>
        <p className="text-2xs text-slate-400 mt-0.5">
          Real-time metrics, executor health status, and Resilience4j circuit breaker states.
        </p>
      </div>

      {/* Circuit Breakers Health Strip */}
      <div className="p-4 rounded bg-bg-surface border border-border-subtle space-y-3">
        <div className="flex items-center justify-between border-b border-border-subtle pb-2">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-4 h-4 text-slate-300" />
            <h2 className="text-xs font-semibold text-slate-200">
              Resilience4j Circuit Breakers (Per-Executor)
            </h2>
          </div>
          <span className="text-2xs text-slate-400 font-mono">
            Automatic backoff postponement without burning retries
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {Object.entries(circuitBreakers).map(([jobType, state]) => {
            const token =
              CIRCUIT_STATE_COLORS[state as keyof typeof CIRCUIT_STATE_COLORS] ||
              CIRCUIT_STATE_COLORS.CLOSED;

            return (
              <div
                key={jobType}
                className={`p-3 rounded border ${token.border} ${token.bg} flex items-center justify-between`}
              >
                <div>
                  <div className="font-mono text-xs font-semibold text-slate-200">{jobType}</div>
                  <div className="text-2xs text-slate-400 font-mono mt-0.5">
                    Target topic: {jobType === 'EMAIL_NOTIFICATION' ? 'job-queue (p0)' : 'job-queue (p1)'}
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${token.dot} ring-2 ring-black/40`} />
                  <span className={`text-xs font-mono font-medium ${token.text}`}>{token.label}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
        <div className="p-3 rounded bg-bg-surface border border-border-subtle">
          <div className="text-2xs font-semibold text-slate-400 uppercase tracking-wider">
            24H SUCCESS RATE
          </div>
          <div className="text-2xl font-mono font-bold text-emerald-400 mt-1">
            {metrics.successRatePercentLast24h}%
          </div>
          <div className="text-2xs text-slate-400 mt-1 font-mono">
            {metrics.completedLast24h} success / {metrics.totalLast24h} jobs in 24h
          </div>
        </div>

        <div className="p-3 rounded bg-bg-surface border border-border-subtle">
          <div className="text-2xs font-semibold text-slate-400 uppercase tracking-wider">
            DEAD LETTER RATE
          </div>
          <div className="text-2xl font-mono font-bold text-rose-400 mt-1">
            {metrics.deadLetterJobs.toLocaleString()}
          </div>
          <div className="text-2xs text-slate-400 mt-1 font-mono">
            {metrics.deadLetterLast24h} dead-lettered in past 24h
          </div>
        </div>

        <div className="p-3 rounded bg-bg-surface border border-border-subtle">
          <div className="text-2xs font-semibold text-slate-400 uppercase tracking-wider">
            ACTIVE EXECUTIONS
          </div>
          <div className="text-2xl font-mono font-bold text-blue-400 mt-1">
            {metrics.processingJobs.toLocaleString()}
          </div>
          <div className="text-2xs text-slate-400 mt-1 font-mono">
            {metrics.pendingJobs} queued in Kafka partition
          </div>
        </div>

        <div className="p-3 rounded bg-bg-surface border border-border-subtle">
          <div className="text-2xs font-semibold text-slate-400 uppercase tracking-wider">
            CRON RUNNERS
          </div>
          <div className="text-2xl font-mono font-bold text-purple-400 mt-1">
            {metrics.enabledDefinitions} / {metrics.totalDefinitions}
          </div>
          <div className="text-2xs text-slate-400 mt-1 font-mono">active recurring schedules</div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Status Distribution */}
        <div className="p-4 rounded bg-bg-surface border border-border-subtle">
          <h3 className="text-xs font-semibold text-slate-200 mb-3 flex items-center gap-2">
            <Cpu className="w-3.5 h-3.5 text-blue-400" />
            Job Queue Status Breakdown
          </h3>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={statusChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <XAxis dataKey="name" stroke="#64748b" fontSize={11} tickLine={false} />
                <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0f172a',
                    border: '1px solid #334155',
                    borderRadius: '4px',
                    fontSize: '11px',
                    fontFamily: 'monospace',
                  }}
                />
                <Bar dataKey="count" radius={[2, 2, 0, 0]}>
                  {statusChartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Distribution by JobType */}
        <div className="p-4 rounded bg-bg-surface border border-border-subtle">
          <h3 className="text-xs font-semibold text-slate-200 mb-3 flex items-center gap-2">
            <Activity className="w-3.5 h-3.5 text-emerald-400" />
            Throughput by Job Type
          </h3>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={typeChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <XAxis dataKey="type" stroke="#64748b" fontSize={10} tickLine={false} />
                <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0f172a',
                    border: '1px solid #334155',
                    borderRadius: '4px',
                    fontSize: '11px',
                    fontFamily: 'monospace',
                  }}
                />
                <Bar dataKey="count" fill="#3b82f6" radius={[2, 2, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  );
};

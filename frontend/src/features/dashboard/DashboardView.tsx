import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { JobResponse } from '@/api/types';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/Badge';
import {
  TableContainer,
  TableHead,
  TableHeaderCell,
  TableBody,
  TableRow,
  TableCell,
} from '@/components/ui/Table';
import { MetricCardSkeleton, TableSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { useToast } from '@/components/ui/Toast';
import {
  Activity,
  CheckCircle,
  RotateCw,
  AlertOctagon,
  ArrowRight,
  RefreshCw,
  RotateCcw,
  Ban,
  Radio,
  Clock,
  ShieldCheck,
  ShieldAlert,
} from 'lucide-react';

export const DashboardView: React.FC<{ onOpenSubmitModal: () => void }> = ({
  onOpenSubmitModal,
}) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [isManualRefreshing, setIsManualRefreshing] = useState(false);

  // Fetch summary metrics (polls every 3s)
  const {
    data: metrics,
    isLoading: isMetricsLoading,
    error: metricsError,
    refetch: refetchMetrics,
  } = useQuery({
    queryKey: ['metrics-summary'],
    queryFn: () => api.getMetrics(),
    refetchInterval: 3000,
  });

  // Fetch recent jobs (last 20, polls every 3s)
  const {
    data: recentJobsData,
    isLoading: isJobsLoading,
    error: jobsError,
    refetch: refetchJobs,
  } = useQuery({
    queryKey: ['recent-jobs'],
    queryFn: () => api.getJobs({ page: 0, size: 20, sort: 'updatedAt,desc' }),
    refetchInterval: 3000,
  });

  // Replay Mutation
  const replayMutation = useMutation({
    mutationFn: (id: string) => api.replayJob(id),
    onSuccess: (job) => {
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('success', 'JOB REPLAY DISPATCHED', `Job #${job.id.substring(0, 8)} reset to PENDING`);
    },
    onError: (err: any) => {
      showToast('error', 'REPLAY REJECTED', err.message);
    },
  });

  // Cancel Mutation
  const cancelMutation = useMutation({
    mutationFn: (id: string) => api.cancelJob(id),
    onSuccess: (job) => {
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('info', 'JOB CANCELLED', `Job #${job.id.substring(0, 8)} marked as CANCELLED`);
    },
    onError: (err: any) => {
      showToast('error', 'CANCEL REJECTED', err.message);
    },
  });

  const handleManualRefresh = async () => {
    setIsManualRefreshing(true);
    await Promise.all([refetchMetrics(), refetchJobs()]);
    setTimeout(() => setIsManualRefreshing(false), 500);
  };

  const recentJobs = recentJobsData?.content || [];

  return (
    <div className="p-4 lg:p-6 space-y-6 max-w-7xl mx-auto">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-relay-border">
        <div>
          <div className="flex items-center space-x-2.5">
            <h1 className="font-typewriter text-base lg:text-lg uppercase tracking-wider text-relay-text">
              DISPATCH DESK LEDGER
            </h1>
            <span className="font-mono text-3xs px-2 py-0.5 rounded-[2px] bg-relay-surface border border-relay-border text-relay-muted">
              STATION #01
            </span>
          </div>
          <p className="font-sans text-xs text-relay-muted mt-1">
            Real-time telemetry and task transmission logs for backend operations.
          </p>
        </div>

        <div className="flex items-center space-x-2.5">
          <Button
            variant="outline"
            size="sm"
            onClick={handleManualRefresh}
            isLoading={isManualRefreshing}
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isManualRefreshing ? 'animate-spin' : ''}`} />}
          >
            REFRESH LEDGER
          </Button>
          <Button
            variant="brass"
            size="sm"
            onClick={onOpenSubmitModal}
            leftIcon={<Radio className="w-3.5 h-3.5" />}
          >
            DISPATCH JOB
          </Button>
        </div>
      </div>

      {/* Error state if backend unreachable */}
      {(metricsError || jobsError) && (
        <ErrorState
          title="Relay Signal Degraded"
          message={
            (metricsError as Error)?.message ||
            (jobsError as Error)?.message ||
            'Unable to communicate with the relay dispatcher daemon.'
          }
          onRetry={handleManualRefresh}
        />
      )}

      {/* 4 Metric Ledger Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {isMetricsLoading ? (
          <>
            <MetricCardSkeleton />
            <MetricCardSkeleton />
            <MetricCardSkeleton />
            <MetricCardSkeleton />
          </>
        ) : (
          <>
            {/* 1. Jobs Today / 24h */}
            <div className="p-4 bg-relay-surface border border-relay-border rounded-[4px] relative overflow-hidden flex flex-col justify-between">
              <div className="flex items-center justify-between text-relay-muted">
                <span className="font-typewriter text-2xs uppercase tracking-wider">
                  JOBS LOGGED (24H)
                </span>
                <Activity className="w-4 h-4 text-relay-brass/70" />
              </div>
              <div className="my-2">
                <div className="font-mono text-2xl lg:text-3xl font-medium text-relay-text">
                  {metrics?.totalLast24h ?? metrics?.totalJobs ?? 0}
                </div>
              </div>
              <div className="font-mono text-3xs text-relay-muted flex items-center justify-between pt-2 border-t border-relay-border/50">
                <span>ACTIVE TOTAL:</span>
                <span className="text-relay-text font-semibold">{metrics?.totalJobs ?? 0}</span>
              </div>
            </div>

            {/* 2. Success Rate */}
            <div className="p-4 bg-relay-surface border border-relay-border rounded-[4px] relative overflow-hidden flex flex-col justify-between">
              <div className="flex items-center justify-between text-relay-muted">
                <span className="font-typewriter text-2xs uppercase tracking-wider">
                  SUCCESS RATE
                </span>
                <CheckCircle className="w-4 h-4 text-relay-sage/80" />
              </div>
              <div className="my-2">
                <div className="font-mono text-2xl lg:text-3xl font-medium text-relay-sage">
                  {metrics?.successRatePercentLast24h !== undefined
                    ? `${metrics.successRatePercentLast24h.toFixed(1)}%`
                    : '100.0%'}
                </div>
              </div>
              <div className="font-mono text-3xs text-relay-muted flex items-center justify-between pt-2 border-t border-relay-border/50">
                <span>COMPLETED (24H):</span>
                <span className="text-relay-sage font-medium">{metrics?.completedLast24h ?? metrics?.completedJobs ?? 0}</span>
              </div>
            </div>

            {/* 3. Retrying Now */}
            <div className="p-4 bg-relay-surface border border-relay-border rounded-[4px] relative overflow-hidden flex flex-col justify-between">
              <div className="flex items-center justify-between text-relay-muted">
                <span className="font-typewriter text-2xs uppercase tracking-wider">
                  RETRYING NOW
                </span>
                <RotateCw className={`w-4 h-4 ${(metrics?.retryingJobs ?? 0) > 0 ? 'text-relay-amber animate-spin' : 'text-relay-muted'}`} />
              </div>
              <div className="my-2">
                <div
                  className={`font-mono text-2xl lg:text-3xl font-medium ${
                    (metrics?.retryingJobs ?? 0) > 0 ? 'text-relay-amber' : 'text-relay-text'
                  }`}
                >
                  {metrics?.retryingJobs ?? 0}
                </div>
              </div>
              <div className="font-mono text-3xs text-relay-muted flex items-center justify-between pt-2 border-t border-relay-border/50">
                <span>IN FLIGHT:</span>
                <span className="text-relay-brass font-medium">{metrics?.processingJobs ?? 0}</span>
              </div>
            </div>

            {/* 4. Dead Letters */}
            <div
              className={`p-4 bg-relay-surface border rounded-[4px] relative overflow-hidden flex flex-col justify-between ${
                (metrics?.deadLetterJobs ?? 0) > 0
                  ? 'border-relay-red/60 bg-relay-red/5'
                  : 'border-relay-border'
              }`}
            >
              <div className="flex items-center justify-between text-relay-muted">
                <span className="font-typewriter text-2xs uppercase tracking-wider text-relay-red">
                  DEAD LETTERS
                </span>
                <AlertOctagon className="w-4 h-4 text-relay-red" />
              </div>
              <div className="my-2">
                <div
                  className={`font-mono text-2xl lg:text-3xl font-medium ${
                    (metrics?.deadLetterJobs ?? 0) > 0 ? 'text-relay-red' : 'text-relay-text'
                  }`}
                >
                  {metrics?.deadLetterJobs ?? 0}
                </div>
              </div>
              <div className="font-mono text-3xs text-relay-muted flex items-center justify-between pt-2 border-t border-relay-border/50">
                <span>REQUIRES REPLAY:</span>
                <button
                  onClick={() => navigate('/dead-letters')}
                  className="text-relay-brass hover:underline uppercase font-mono"
                >
                  INSPECT &rarr;
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Circuit Breakers & Dispatch Status Bar */}
      {metrics?.circuitBreakers && Object.keys(metrics.circuitBreakers).length > 0 && (
        <Card headerTitle="CIRCUIT BREAKER RELAY MONITORS">
          <div className="p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {Object.entries(metrics.circuitBreakers).map(([name, status]) => (
              <div
                key={name}
                className="p-3 border border-relay-border rounded-[4px] bg-relay-sidebar flex items-center justify-between"
              >
                <div className="flex items-center space-x-2">
                  {status === 'CLOSED' ? (
                    <ShieldCheck className="w-4 h-4 text-relay-sage" />
                  ) : (
                    <ShieldAlert className="w-4 h-4 text-relay-red animate-pulse" />
                  )}
                  <span className="font-mono text-xs text-relay-text uppercase">{name}</span>
                </div>
                <StatusBadge
                  status={status}
                  label={status}
                  size="sm"
                />
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Recent Jobs Ledger Table */}
      <Card
        headerTitle="RECENT DISPATCH LOG (LAST 20)"
        headerAction={
          <Button
            variant="ghost"
            size="xs"
            onClick={() => navigate('/jobs')}
            rightIcon={<ArrowRight className="w-3.5 h-3.5" />}
          >
            VIEW FULL LEDGER
          </Button>
        }
      >
        {isJobsLoading ? (
          <TableSkeleton rows={8} cols={6} />
        ) : recentJobs.length === 0 ? (
          <div className="p-6">
            <EmptyState
              message="No jobs recorded on the dispatch ledger yet."
              actionLabel="DISPATCH FIRST JOB"
              onAction={onOpenSubmitModal}
            />
          </div>
        ) : (
          <TableContainer className="border-0 rounded-none">
            <TableHead>
              <tr>
                <TableHeaderCell>JOB ID / REF</TableHeaderCell>
                <TableHeaderCell>TYPE / ROUTE</TableHeaderCell>
                <TableHeaderCell>STATUS</TableHeaderCell>
                <TableHeaderCell>ATTEMPTS</TableHeaderCell>
                <TableHeaderCell>TIMESTAMP</TableHeaderCell>
                <TableHeaderCell align="right">ACTIONS</TableHeaderCell>
              </tr>
            </TableHead>
            <TableBody>
              {recentJobs.map((job: JobResponse) => {
                const isPendingOrProcessing =
                  job.status === 'PENDING' || job.status === 'PROCESSING';
                const isFailedOrDeadLetter =
                  job.status === 'DEAD_LETTER' || job.status === 'CANCELLED';

                return (
                  <TableRow
                    key={job.id}
                    isClickable
                    onClick={() => navigate(`/jobs/${job.id}`)}
                  >
                    {/* Job ID */}
                    <TableCell mono className="font-semibold text-relay-brass">
                      <div className="flex items-center space-x-1.5">
                        <span className="font-mono">{job.id.substring(0, 8)}...</span>
                        {job.idempotencyKey && (
                          <span
                            className="font-mono text-3xs text-relay-muted px-1 border border-relay-border rounded-[2px]"
                            title={`Idempotency Key: ${job.idempotencyKey}`}
                          >
                            IK
                          </span>
                        )}
                      </div>
                    </TableCell>

                    {/* Job Type */}
                    <TableCell mono className="text-relay-text/90">
                      {job.jobType}
                    </TableCell>

                    {/* Status Badge */}
                    <TableCell>
                      <StatusBadge status={job.status} />
                    </TableCell>

                    {/* Attempts */}
                    <TableCell mono className="text-relay-muted">
                      <span className={job.attemptCount > 1 ? 'text-relay-amber font-bold' : ''}>
                        {job.attemptCount}
                      </span>
                      <span> / {job.maxAttempts}</span>
                    </TableCell>

                    {/* Timestamp */}
                    <TableCell mono className="text-relay-muted text-2xs">
                      {new Date(job.updatedAt || job.createdAt).toLocaleString('en-US', {
                        month: 'short',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit',
                        hour12: false,
                      })}
                    </TableCell>

                    {/* Actions */}
                    <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end space-x-1.5">
                        {isFailedOrDeadLetter && (
                          <Button
                            variant="outline"
                            size="xs"
                            onClick={() => replayMutation.mutate(job.id)}
                            isLoading={replayMutation.isPending}
                            leftIcon={<RotateCcw className="w-3 h-3" />}
                            title="Replay Job"
                          >
                            REPLAY
                          </Button>
                        )}
                        {isPendingOrProcessing && (
                          <Button
                            variant="danger"
                            size="xs"
                            onClick={() => cancelMutation.mutate(job.id)}
                            isLoading={cancelMutation.isPending}
                            leftIcon={<Ban className="w-3 h-3" />}
                            title="Cancel In-Flight Job"
                          >
                            CANCEL
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="xs"
                          onClick={() => navigate(`/jobs/${job.id}`)}
                          title="Inspect Log Entries"
                        >
                          LOGS &rarr;
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </TableContainer>
        )}
      </Card>
    </div>
  );
};

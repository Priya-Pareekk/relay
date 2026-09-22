import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { JobResponse, JobStatus, JobType } from '@/api/types';
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
import { TableSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { useToast } from '@/components/ui/Toast';
import {
  Search,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  Ban,
  ArrowRight,
  Filter,
  RefreshCw,
} from 'lucide-react';

export const MainQueueView: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [statusFilter, setStatusFilter] = useState<JobStatus | ''>('');
  const [typeFilter, setTypeFilter] = useState<JobType | ''>('');
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(0);
  const pageSize = 15;

  // Poll jobs list every 3.5s
  const {
    data: jobsData,
    isLoading: isJobsLoading,
    error: jobsError,
    refetch: refetchJobs,
  } = useQuery({
    queryKey: ['jobs', statusFilter, typeFilter, page],
    queryFn: () =>
      api.getJobs({
        status: statusFilter || undefined,
        jobType: typeFilter || undefined,
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      }),
    refetchInterval: 3500,
  });

  // Replay mutation
  const replayMutation = useMutation({
    mutationFn: (id: string) => api.replayJob(id),
    onSuccess: (job) => {
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('success', 'JOB REPLAY DISPATCHED', `Job #${job.id.substring(0, 8)} reset to PENDING`);
    },
    onError: (err: any) => {
      showToast('error', 'REPLAY FAILED', err.message);
    },
  });

  // Cancel mutation
  const cancelMutation = useMutation({
    mutationFn: (id: string) => api.cancelJob(id),
    onSuccess: (job) => {
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('info', 'JOB CANCELLED', `Job #${job.id.substring(0, 8)} marked as CANCELLED`);
    },
    onError: (err: any) => {
      showToast('error', 'CANCEL FAILED', err.message);
    },
  });

  const filteredJobs = jobsData?.content.filter((job) => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    return (
      job.id.toLowerCase().includes(term) ||
      (job.idempotencyKey && job.idempotencyKey.toLowerCase().includes(term))
    );
  });

  const statuses: { label: string; value: JobStatus | '' }[] = [
    { label: 'ALL STATUSES', value: '' },
    { label: 'PENDING', value: 'PENDING' },
    { label: 'PROCESSING', value: 'PROCESSING' },
    { label: 'RETRYING', value: 'RETRYING' },
    { label: 'DEAD LETTER', value: 'DEAD_LETTER' },
    { label: 'COMPLETED', value: 'COMPLETED' },
    { label: 'CANCELLED', value: 'CANCELLED' },
  ];

  return (
    <div className="p-4 lg:p-6 space-y-5 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-relay-border">
        <div>
          <div className="flex items-center space-x-2.5">
            <h1 className="font-typewriter text-base lg:text-lg uppercase tracking-wider text-relay-text">
              MASTER JOBS LEDGER
            </h1>
            <span className="font-mono text-3xs px-2 py-0.5 rounded-[2px] bg-relay-surface border border-relay-border text-relay-muted">
              {jobsData?.totalElements !== undefined ? `${jobsData.totalElements} RECORDS` : 'LOADING...'}
            </span>
          </div>
          <p className="font-sans text-xs text-relay-muted mt-1">
            Historical index of all signals dispatched through the Relay engine.
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => refetchJobs()}
          leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
        >
          REFRESH
        </Button>
      </div>

      {/* Error state */}
      {jobsError && (
        <ErrorState
          title="Ledger Communication Failure"
          message={(jobsError as Error)?.message || 'Failed to retrieve jobs from relay backend.'}
          onRetry={() => refetchJobs()}
        />
      )}

      {/* Filter Ledger Bar */}
      <Card className="p-3 bg-relay-surface/90">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          {/* Status Filter Tabs */}
          <div className="flex items-center space-x-1 overflow-x-auto pb-1 lg:pb-0">
            {statuses.map((tab) => (
              <button
                key={tab.value}
                onClick={() => {
                  setStatusFilter(tab.value);
                  setPage(0);
                }}
                className={`px-2.5 py-1 text-2xs font-typewriter uppercase tracking-wider rounded-[3px] border transition-colors whitespace-nowrap cursor-pointer ${
                  statusFilter === tab.value
                    ? 'bg-relay-surface-hover text-relay-brass border-relay-brass/60 font-semibold'
                    : 'text-relay-muted hover:text-relay-text border-transparent hover:bg-relay-surface'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Type & Search */}
          <div className="flex items-center space-x-2.5">
            <div className="flex items-center space-x-1.5 bg-relay-sidebar border border-relay-border rounded-[4px] px-2.5 py-1">
              <Filter className="w-3.5 h-3.5 text-relay-muted" />
              <select
                value={typeFilter}
                onChange={(e) => {
                  setTypeFilter(e.target.value as JobType);
                  setPage(0);
                }}
                className="bg-transparent text-2xs font-mono text-relay-text focus:outline-none cursor-pointer"
              >
                <option value="" className="bg-relay-surface">ALL TYPES</option>
                <option value="EMAIL_NOTIFICATION" className="bg-relay-surface">EMAIL_NOTIFICATION</option>
                <option value="REPORT_GENERATION" className="bg-relay-surface">REPORT_GENERATION</option>
              </select>
            </div>

            <div className="relative">
              <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-relay-muted" />
              <input
                type="text"
                placeholder="Search ID / Idempotency..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="bg-relay-sidebar border border-relay-border rounded-[4px] pl-8 pr-3 py-1 text-2xs font-mono text-relay-text placeholder:text-relay-muted/50 w-52 sm:w-64 focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none"
              />
            </div>
          </div>
        </div>
      </Card>

      {/* Main Jobs Ledger Table */}
      {isJobsLoading ? (
        <TableSkeleton rows={10} cols={6} />
      ) : !filteredJobs || filteredJobs.length === 0 ? (
        <Card className="p-8">
          <EmptyState
            message={
              searchTerm || statusFilter || typeFilter
                ? 'No ledger records matched the active filter criteria.'
                : 'No jobs have been transmitted to the relay queue.'
            }
            actionLabel={searchTerm || statusFilter || typeFilter ? 'CLEAR FILTERS' : undefined}
            onAction={() => {
              setSearchTerm('');
              setStatusFilter('');
              setTypeFilter('');
            }}
          />
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <TableContainer className="border-0 rounded-none">
            <TableHead>
              <tr>
                <TableHeaderCell>SIGNAL ID</TableHeaderCell>
                <TableHeaderCell>TYPE / ROUTE</TableHeaderCell>
                <TableHeaderCell>STATUS</TableHeaderCell>
                <TableHeaderCell>ATTEMPTS</TableHeaderCell>
                <TableHeaderCell>CREATED / NEXT RETRY</TableHeaderCell>
                <TableHeaderCell>PAYLOAD / ERROR PREVIEW</TableHeaderCell>
                <TableHeaderCell align="right">ACTIONS</TableHeaderCell>
              </tr>
            </TableHead>
            <TableBody>
              {filteredJobs.map((job: JobResponse) => {
                const isPendingOrProcessing =
                  job.status === 'PENDING' || job.status === 'PROCESSING' || job.status === 'RETRYING';
                const isDeadOrFailed =
                  job.status === 'DEAD_LETTER' || job.status === 'CANCELLED';

                return (
                  <TableRow
                    key={job.id}
                    isClickable
                    onClick={() => navigate(`/jobs/${job.id}`)}
                  >
                    {/* ID */}
                    <TableCell mono className="text-relay-brass font-medium">
                      <div className="flex items-center space-x-1.5">
                        <span>{job.id.substring(0, 8)}...</span>
                        {job.idempotencyKey && (
                          <span
                            className="font-mono text-3xs text-relay-muted px-1 border border-relay-border rounded-[2px] bg-relay-sidebar"
                            title={`Idempotency Key: ${job.idempotencyKey}`}
                          >
                            IK
                          </span>
                        )}
                      </div>
                    </TableCell>

                    {/* Type */}
                    <TableCell mono className="text-relay-text/90">
                      {job.jobType}
                    </TableCell>

                    {/* Status */}
                    <TableCell>
                      <StatusBadge status={job.status} />
                    </TableCell>

                    {/* Attempts */}
                    <TableCell mono className="text-relay-muted">
                      <span className={job.attemptCount >= job.maxAttempts ? 'text-relay-red font-bold' : ''}>
                        {job.attemptCount}
                      </span>
                      <span> / {job.maxAttempts}</span>
                    </TableCell>

                    {/* Timestamps */}
                    <TableCell mono className="text-2xs text-relay-muted">
                      <div>
                        {new Date(job.createdAt).toLocaleTimeString('en-US', {
                          hour12: false,
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                        })}
                      </div>
                      {job.status === 'RETRYING' && job.nextRetryAt && (
                        <div className="text-relay-amber text-3xs mt-0.5">
                          RETRY: {new Date(job.nextRetryAt).toLocaleTimeString('en-US', { hour12: false })}
                        </div>
                      )}
                    </TableCell>

                    {/* Preview / Error */}
                    <TableCell mono className="text-2xs text-relay-muted max-w-xs truncate">
                      {job.lastError ? (
                        <span className="text-relay-red truncate block" title={job.lastError}>
                          ERR: {job.lastError}
                        </span>
                      ) : (
                        <span className="text-relay-muted/80 truncate block">
                          {JSON.stringify(job.payload)}
                        </span>
                      )}
                    </TableCell>

                    {/* Actions */}
                    <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end space-x-1.5">
                        {isDeadOrFailed && (
                          <Button
                            variant="outline"
                            size="xs"
                            onClick={() => replayMutation.mutate(job.id)}
                            isLoading={replayMutation.isPending}
                            leftIcon={<RotateCcw className="w-3 h-3" />}
                            title="Replay from start"
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
                            title="Cancel Job"
                          >
                            CANCEL
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="xs"
                          onClick={() => navigate(`/jobs/${job.id}`)}
                          rightIcon={<ArrowRight className="w-3 h-3" />}
                        >
                          OPEN
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </TableContainer>

          {/* Pagination Footer */}
          {jobsData && jobsData.totalPages > 1 && (
            <div className="px-5 py-3 border-t border-relay-border bg-relay-sidebar/70 flex items-center justify-between font-mono text-2xs text-relay-muted">
              <span>
                PAGE {jobsData.number + 1} OF {jobsData.totalPages} ({jobsData.totalElements} TOTAL SIGNALS)
              </span>
              <div className="flex items-center space-x-1.5">
                <Button
                  variant="outline"
                  size="xs"
                  disabled={jobsData.first}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  leftIcon={<ChevronLeft className="w-3 h-3" />}
                >
                  PREV
                </Button>
                <Button
                  variant="outline"
                  size="xs"
                  disabled={jobsData.last}
                  onClick={() => setPage((p) => p + 1)}
                  rightIcon={<ChevronRight className="w-3 h-3" />}
                >
                  NEXT
                </Button>
              </div>
            </div>
          )}
        </Card>
      )}
    </div>
  );
};

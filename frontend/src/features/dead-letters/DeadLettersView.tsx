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
import { TableSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import {
  AlertOctagon,
  RotateCcw,
  RefreshCw,
  ArrowRight,
  ShieldCheck,
  Eye,
  AlertTriangle,
} from 'lucide-react';

export const DeadLettersView: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [page, setPage] = useState(0);
  const pageSize = 20;
  const [jobToReplay, setJobToReplay] = useState<JobResponse | null>(null);
  const [inspectJob, setInspectJob] = useState<JobResponse | null>(null);
  const [isBatchReplayConfirmOpen, setIsBatchReplayConfirmOpen] = useState(false);

  // Query only DEAD_LETTER status jobs
  const {
    data: deadLettersData,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['dead-letters', page],
    queryFn: () =>
      api.getJobs({
        status: 'DEAD_LETTER',
        page,
        size: pageSize,
        sort: 'updatedAt,desc',
      }),
    refetchInterval: 3000,
  });

  // Replay single job
  const replayMutation = useMutation({
    mutationFn: (id: string) => api.replayJob(id),
    onSuccess: (job) => {
      queryClient.invalidateQueries({ queryKey: ['dead-letters'] });
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('success', 'DEAD LETTER REPLAYED', `Job #${job.id.substring(0, 8)} reset to PENDING`);
      setJobToReplay(null);
    },
    onError: (err: any) => {
      showToast('error', 'REPLAY REJECTED', err.message);
    },
  });

  // Replay all currently visible dead letters
  const [isBatchReplaying, setIsBatchReplaying] = useState(false);
  const handleBatchReplay = async () => {
    if (!deadLettersData?.content || deadLettersData.content.length === 0) return;
    setIsBatchReplaying(true);
    let successCount = 0;
    for (const job of deadLettersData.content) {
      try {
        await api.replayJob(job.id);
        successCount++;
      } catch (e) {
        console.error('Batch replay error', e);
      }
    }
    queryClient.invalidateQueries({ queryKey: ['dead-letters'] });
    queryClient.invalidateQueries({ queryKey: ['jobs'] });
    queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
    showToast(
      'success',
      'BATCH REPLAY COMPLETE',
      `Replayed ${successCount} dead letter signals back to active queue.`
    );
    setIsBatchReplaying(false);
    setIsBatchReplayConfirmOpen(false);
  };

  const deadLetters = deadLettersData?.content || [];

  return (
    <div className="p-4 lg:p-6 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-relay-border">
        <div>
          <div className="flex items-center space-x-2.5">
            <h1 className="font-typewriter text-base lg:text-lg uppercase tracking-wider text-relay-red">
              DEAD LETTER QUARANTINE LEDGER
            </h1>
            <span className="font-mono text-3xs px-2 py-0.5 rounded-[2px] bg-relay-red/10 border border-relay-red/40 text-relay-red">
              {deadLettersData?.totalElements !== undefined
                ? `${deadLettersData.totalElements} QUARANTINED`
                : '...'}
            </span>
          </div>
          <p className="font-sans text-xs text-relay-muted mt-1">
            Exhausted signals quarantined after reaching maximum retry thresholds. Manual replay required.
          </p>
        </div>

        <div className="flex items-center space-x-2.5">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
          >
            REFRESH
          </Button>
          {deadLetters.length > 0 && (
            <Button
              variant="danger"
              size="sm"
              onClick={() => setIsBatchReplayConfirmOpen(true)}
              leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
            >
              REPLAY ALL ({deadLetters.length})
            </Button>
          )}
        </div>
      </div>

      {/* Error state */}
      {error && (
        <ErrorState
          title="Quarantine Partition Degraded"
          message={(error as Error)?.message || 'Failed to fetch dead letters partition.'}
          onRetry={() => refetch()}
        />
      )}

      {/* Dead Letters Table */}
      {isLoading ? (
        <TableSkeleton rows={6} cols={6} />
      ) : deadLetters.length === 0 ? (
        <Card className="p-12 text-center">
          <div className="flex flex-col items-center justify-center space-y-3">
            <div className="w-10 h-10 rounded-[4px] bg-relay-sage/10 border border-relay-sage/40 flex items-center justify-center text-relay-sage">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-typewriter text-sm uppercase tracking-wider text-relay-sage">
                QUARANTINE LEDGER CLEAR
              </h3>
              <p className="font-sans text-xs text-relay-muted mt-1">
                Zero dead letter signals exist in the quarantine partition. All dispatch pipelines nominal.
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/jobs')}
            >
              VIEW MASTER LEDGER
            </Button>
          </div>
        </Card>
      ) : (
        <Card headerTitle="QUARANTINED DEAD LETTERS">
          <TableContainer className="border-0 rounded-none">
            <TableHead>
              <tr>
                <TableHeaderCell>SIGNAL ID</TableHeaderCell>
                <TableHeaderCell>TYPE / ROUTE</TableHeaderCell>
                <TableHeaderCell>ATTEMPTS</TableHeaderCell>
                <TableHeaderCell>LAST TERMINATION ERROR</TableHeaderCell>
                <TableHeaderCell>QUARANTINED AT</TableHeaderCell>
                <TableHeaderCell align="right">ACTIONS</TableHeaderCell>
              </tr>
            </TableHead>
            <TableBody>
              {deadLetters.map((job: JobResponse) => (
                <TableRow
                  key={job.id}
                  isClickable
                  onClick={() => navigate(`/jobs/${job.id}`)}
                >
                  {/* Job ID */}
                  <TableCell mono className="text-relay-red font-semibold">
                    <div className="flex items-center space-x-1.5">
                      <span>{job.id.substring(0, 8)}...</span>
                      {job.idempotencyKey && (
                        <span className="font-mono text-3xs text-relay-muted px-1 border border-relay-border rounded-[2px] bg-relay-sidebar">
                          IK
                        </span>
                      )}
                    </div>
                  </TableCell>

                  {/* Type */}
                  <TableCell mono className="text-relay-text/90">
                    {job.jobType}
                  </TableCell>

                  {/* Attempts */}
                  <TableCell mono className="text-relay-red font-bold">
                    {job.attemptCount} / {job.maxAttempts} (EXHAUSTED)
                  </TableCell>

                  {/* Last Error */}
                  <TableCell mono className="text-2xs text-relay-red/90 max-w-sm truncate">
                    {job.lastError ? (
                      <span title={job.lastError}>{job.lastError}</span>
                    ) : (
                      <span className="italic text-relay-muted">RETRY LIMIT REACHED</span>
                    )}
                  </TableCell>

                  {/* Quarantined Timestamp */}
                  <TableCell mono className="text-2xs text-relay-muted">
                    {new Date(job.updatedAt).toLocaleString('en-US', {
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
                      <Button
                        variant="ghost"
                        size="xs"
                        onClick={() => setInspectJob(job)}
                        title="Inspect Payload & Error"
                        leftIcon={<Eye className="w-3 h-3" />}
                      >
                        INSPECT
                      </Button>
                      <Button
                        variant="danger"
                        size="xs"
                        onClick={() => setJobToReplay(job)}
                        leftIcon={<RotateCcw className="w-3 h-3" />}
                        title="Replay with state reset"
                      >
                        REPLAY
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </TableContainer>
        </Card>
      )}

      {/* Confirmation Modal for Single Replay */}
      {jobToReplay && (
        <Modal
          isOpen={Boolean(jobToReplay)}
          onClose={() => setJobToReplay(null)}
          title="CONFIRM DEAD LETTER REPLAY"
          subtitle="RESET ATTEMPT COUNTER TO ZERO & RE-ENQUEUE"
          footer={
            <>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setJobToReplay(null)}
              >
                ABORT
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => replayMutation.mutate(jobToReplay.id)}
                isLoading={replayMutation.isPending}
                leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
              >
                CONFIRM REPLAY
              </Button>
            </>
          }
        >
          <div className="space-y-3 font-sans text-xs text-relay-text">
            <p>
              Replaying signal <code className="font-mono text-relay-brass">{jobToReplay.id}</code> will reset its attempt state to <span className="font-mono font-bold text-relay-sage">0 / {jobToReplay.maxAttempts}</span> and re-transmit it to the active dispatch worker pool.
            </p>
            {jobToReplay.lastError && (
              <div className="p-3 bg-relay-red/10 border border-relay-red/40 rounded-[4px] font-mono text-2xs text-relay-red">
                <span className="font-typewriter block mb-1 uppercase">LAST RECORDED FAULT:</span>
                {jobToReplay.lastError}
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* Confirmation Modal for Batch Replay */}
      <Modal
        isOpen={isBatchReplayConfirmOpen}
        onClose={() => setIsBatchReplayConfirmOpen(false)}
        title="CONFIRM BATCH DEAD LETTER REPLAY"
        subtitle={`RESET & RESUBMIT ${deadLetters.length} QUARANTINED SIGNALS`}
        footer={
          <>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setIsBatchReplayConfirmOpen(false)}
            >
              ABORT
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={handleBatchReplay}
              isLoading={isBatchReplaying}
              leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
            >
              REPLAY ALL ({deadLetters.length})
            </Button>
          </>
        }
      >
        <p className="font-sans text-xs text-relay-text">
          Are you sure you wish to replay all <strong className="text-relay-red font-mono">{deadLetters.length}</strong> dead letter signals currently in this quarantine view? Each job's attempt state will be reset to 0.
        </p>
      </Modal>

      {/* Inspect Modal */}
      {inspectJob && (
        <Modal
          isOpen={Boolean(inspectJob)}
          onClose={() => setInspectJob(null)}
          title={`INSPECT DEAD LETTER // ${inspectJob.id.substring(0, 8)}`}
          subtitle={`ROUTE: ${inspectJob.jobType} · ATTEMPTS: ${inspectJob.attemptCount}/${inspectJob.maxAttempts}`}
          footer={
            <div className="flex items-center justify-between w-full">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => navigate(`/jobs/${inspectJob.id}`)}
                rightIcon={<ArrowRight className="w-3.5 h-3.5" />}
              >
                OPEN FULL DISPATCH BOOK
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => {
                  const target = inspectJob;
                  setInspectJob(null);
                  setJobToReplay(target);
                }}
                leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
              >
                REPLAY THIS JOB
              </Button>
            </div>
          }
        >
          <div className="space-y-4 font-mono text-xs">
            {inspectJob.lastError && (
              <div>
                <span className="font-typewriter text-2xs uppercase tracking-wider text-relay-red block mb-1">
                  TERMINATION ERROR:
                </span>
                <pre className="p-3 bg-relay-red/10 border border-relay-red/40 rounded-[4px] text-relay-red font-mono text-2xs whitespace-pre-wrap break-all">
                  {inspectJob.lastError}
                </pre>
              </div>
            )}

            <div>
              <span className="font-typewriter text-2xs uppercase tracking-wider text-relay-muted block mb-1">
                JSON PAYLOAD:
              </span>
              <pre className="p-3 bg-relay-sidebar border border-relay-border rounded-[4px] text-relay-text font-mono text-2xs overflow-x-auto leading-relaxed">
                {JSON.stringify(inspectJob.payload, null, 2)}
              </pre>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};

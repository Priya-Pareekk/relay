import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/Badge';
import { Modal } from '@/components/ui/Modal';
import { ErrorState } from '@/components/ui/ErrorState';
import { useToast } from '@/components/ui/Toast';
import {
  ArrowLeft,
  RotateCcw,
  Ban,
  Clock,
  AlertTriangle,
  FileCode,
  CheckCircle2,
  XCircle,
  Hourglass,
  Terminal,
} from 'lucide-react';

export const JobDetailView: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [isReplayConfirmOpen, setIsReplayConfirmOpen] = useState(false);

  const {
    data: job,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['job', id],
    queryFn: () => api.getJobById(id!),
    enabled: Boolean(id),
    refetchInterval: 3000,
  });

  const replayMutation = useMutation({
    mutationFn: (jobId: string) => api.replayJob(jobId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['job', id] });
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('success', 'JOB RESET TO PENDING', `Job #${id?.substring(0, 8)} ready for execution`);
      setIsReplayConfirmOpen(false);
    },
    onError: (err: any) => {
      showToast('error', 'REPLAY REJECTED', err.message);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (jobId: string) => api.cancelJob(jobId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['job', id] });
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('info', 'JOB CANCELLED', `Job #${id?.substring(0, 8)} has been cancelled`);
    },
    onError: (err: any) => {
      showToast('error', 'CANCEL REJECTED', err.message);
    },
  });

  if (isLoading) {
    return (
      <div className="p-4 lg:p-6 space-y-6 max-w-7xl mx-auto animate-pulse">
        <div className="h-6 w-36 bg-relay-surface rounded-[2px]" />
        <div className="h-28 bg-relay-surface border border-relay-border rounded-[4px]" />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="h-96 bg-relay-surface border border-relay-border rounded-[4px]" />
          <div className="h-96 bg-relay-surface border border-relay-border rounded-[4px]" />
        </div>
      </div>
    );
  }

  if (error || !job) {
    return (
      <div className="p-4 lg:p-6 space-y-4 max-w-7xl mx-auto">
        <Button
          variant="outline"
          size="sm"
          onClick={() => navigate('/jobs')}
          leftIcon={<ArrowLeft className="w-3.5 h-3.5" />}
        >
          BACK TO MASTER LEDGER
        </Button>
        <ErrorState
          title="Signal Not Found in Ledger"
          message={(error as Error)?.message || `No job matching ID "${id}" was found in this relay partition.`}
          onRetry={() => refetch()}
        />
      </div>
    );
  }

  const isDeadLetter = job.status === 'DEAD_LETTER';
  const isPendingOrProcessing =
    job.status === 'PENDING' || job.status === 'PROCESSING' || job.status === 'RETRYING';

  return (
    <div className="p-4 lg:p-6 space-y-6 max-w-7xl mx-auto">
      {/* Navigation & Action Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-relay-border">
        <div className="flex items-center space-x-3">
          <Button
            variant="outline"
            size="xs"
            onClick={() => navigate(-1)}
            leftIcon={<ArrowLeft className="w-3.5 h-3.5" />}
          >
            RETURN
          </Button>
          <span className="text-relay-muted/40 font-mono">/</span>
          <span className="font-typewriter text-xs uppercase tracking-wider text-relay-muted">
            DISPATCH RECORD
          </span>
          <span className="font-mono text-xs text-relay-brass font-bold">{job.id}</span>
        </div>

        <div className="flex items-center space-x-2.5">
          {isDeadLetter && (
            <Button
              variant="danger"
              size="sm"
              onClick={() => setIsReplayConfirmOpen(true)}
              leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
            >
              REPLAY DEAD LETTER
            </Button>
          )}

          {isPendingOrProcessing && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => cancelMutation.mutate(job.id)}
              isLoading={cancelMutation.isPending}
              leftIcon={<Ban className="w-3.5 h-3.5" />}
            >
              CANCEL IN-FLIGHT SIGNAL
            </Button>
          )}
        </div>
      </div>

      {/* Metadata Ledger Header */}
      <Card className="p-4 lg:p-5">
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-4 font-mono text-xs">
          <div>
            <span className="font-typewriter text-3xs uppercase tracking-wider text-relay-muted block mb-1">
              CURRENT STATUS
            </span>
            <StatusBadge status={job.status} />
          </div>

          <div>
            <span className="font-typewriter text-3xs uppercase tracking-wider text-relay-muted block mb-1">
              JOB TYPE
            </span>
            <span className="text-relay-text font-medium">{job.jobType}</span>
          </div>

          <div>
            <span className="font-typewriter text-3xs uppercase tracking-wider text-relay-muted block mb-1">
              ATTEMPTS EXECUTED
            </span>
            <span className={job.attemptCount >= job.maxAttempts ? 'text-relay-red font-bold' : 'text-relay-text'}>
              {job.attemptCount} / {job.maxAttempts}
            </span>
          </div>

          <div>
            <span className="font-typewriter text-3xs uppercase tracking-wider text-relay-muted block mb-1">
              CREATED AT
            </span>
            <span className="text-relay-muted">
              {new Date(job.createdAt).toLocaleTimeString('en-US', { hour12: false })}
            </span>
          </div>

          <div>
            <span className="font-typewriter text-3xs uppercase tracking-wider text-relay-muted block mb-1">
              LAST UPDATE
            </span>
            <span className="text-relay-muted">
              {new Date(job.updatedAt).toLocaleTimeString('en-US', { hour12: false })}
            </span>
          </div>

          <div>
            <span className="font-typewriter text-3xs uppercase tracking-wider text-relay-muted block mb-1">
              IDEMPOTENCY KEY
            </span>
            <span className="text-relay-text truncate block">
              {job.idempotencyKey || 'NONE SPECIFIED'}
            </span>
          </div>
        </div>
      </Card>

      {/* Main Grid: Vertical Dispatch Attempts Timeline (Left) & JSON Ledger (Right) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left 7 cols: Vertical Dispatch Log Timeline */}
        <div className="lg:col-span-7 space-y-4">
          <Card
            headerTitle="DISPATCH BOOK // ATTEMPT LOG & BACKOFF TIMELINE"
            badge={
              <span className="font-mono text-3xs text-relay-muted">
                {job.attempts?.length || 0} ATTEMPTS RECORDED
              </span>
            }
          >
            <div className="p-5 space-y-6">
              {/* Latest error banner if present */}
              {job.lastError && (
                <div className="p-3 bg-relay-red/10 border border-relay-red/50 rounded-[4px]">
                  <div className="flex items-center space-x-2 text-relay-red font-typewriter text-2xs uppercase tracking-wider mb-1">
                    <AlertTriangle className="w-3.5 h-3.5" />
                    <span>LATEST TERMINATION ERROR</span>
                  </div>
                  <pre className="font-mono text-2xs text-relay-red whitespace-pre-wrap break-all leading-relaxed">
                    {job.lastError}
                  </pre>
                </div>
              )}

              {/* Vertical Dispatch Book Timeline */}
              <div className="relative pl-6 space-y-0 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-[1px] before:bg-relay-border">
                {/* Entry 0: Signal Enqueued */}
                <div className="relative pb-6">
                  <div className="absolute -left-6 top-0.5 w-4 h-4 rounded-[2px] bg-relay-sidebar border border-relay-border flex items-center justify-center">
                    <Clock className="w-2.5 h-2.5 text-relay-muted" />
                  </div>
                  <div className="bg-relay-sidebar/60 border border-relay-border/60 rounded-[4px] p-3">
                    <div className="flex items-center justify-between font-mono text-2xs">
                      <span className="font-typewriter text-relay-text uppercase tracking-wider">
                        SIGNAL ENQUEUED INTO OUTBOX
                      </span>
                      <span className="text-relay-muted">
                        {new Date(job.createdAt).toLocaleString('en-US', { hour12: false })}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Attempts list */}
                {job.attempts && job.attempts.length > 0 ? (
                  job.attempts.map((att, idx) => {
                    const isSuccess = att.status === 'SUCCESS';
                    const duration = att.finishedAt
                      ? `${new Date(att.finishedAt).getTime() - new Date(att.startedAt).getTime()}ms`
                      : 'IN-PROGRESS';

                    // Compute backoff connecting wait if there's a subsequent attempt
                    const nextAttempt = job.attempts![idx + 1];
                    let backoffWaitMs = 0;
                    if (att.finishedAt && nextAttempt) {
                      backoffWaitMs =
                        new Date(nextAttempt.startedAt).getTime() - new Date(att.finishedAt).getTime();
                    }

                    return (
                      <React.Fragment key={att.id}>
                        {/* Attempt Entry */}
                        <div className="relative pb-6">
                          {/* Timeline dot */}
                          <div
                            className={`absolute -left-6 top-1 w-4 h-4 rounded-[2px] border flex items-center justify-center ${
                              isSuccess
                                ? 'bg-relay-sage/20 border-relay-sage text-relay-sage'
                                : 'bg-relay-red/20 border-relay-red text-relay-red'
                            }`}
                          >
                            {isSuccess ? (
                              <CheckCircle2 className="w-2.5 h-2.5" />
                            ) : (
                              <XCircle className="w-2.5 h-2.5" />
                            )}
                          </div>

                          {/* Entry card */}
                          <div
                            className={`border rounded-[4px] p-3.5 space-y-2 ${
                              isSuccess
                                ? 'bg-relay-surface border-relay-sage/40'
                                : 'bg-relay-surface border-relay-red/40'
                            }`}
                          >
                            {/* Attempt header */}
                            <div className="flex items-center justify-between">
                              <div className="flex items-center space-x-2">
                                <span className="font-typewriter text-xs uppercase tracking-wider text-relay-text">
                                  ATTEMPT #{att.attemptNumber}
                                </span>
                                <StatusBadge
                                  status={isSuccess ? 'SUCCESS' : 'FAILURE'}
                                  size="sm"
                                />
                              </div>
                              <span className="font-mono text-2xs text-relay-muted">
                                DURATION: <strong className="text-relay-text">{duration}</strong>
                              </span>
                            </div>

                            {/* Timestamp details */}
                            <div className="font-mono text-3xs text-relay-muted flex items-center space-x-4 border-t border-relay-border/40 pt-1.5">
                              <span>
                                STARTED: {new Date(att.startedAt).toLocaleTimeString('en-US', { hour12: false })}
                              </span>
                              {att.finishedAt && (
                                <span>
                                  FINISHED: {new Date(att.finishedAt).toLocaleTimeString('en-US', { hour12: false })}
                                </span>
                              )}
                            </div>

                            {/* Error text if failed */}
                            {att.errorMessage && (
                              <div className="pt-2 border-t border-relay-border/50">
                                <span className="font-typewriter text-3xs uppercase tracking-wider text-relay-red block mb-1">
                                  EXCEPTION LOGGED:
                                </span>
                                <pre className="font-mono text-2xs text-relay-red/90 bg-relay-sidebar p-2 border border-relay-red/30 rounded-[2px] whitespace-pre-wrap break-all">
                                  {att.errorMessage}
                                </pre>
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Backoff connecting line illustration between attempts */}
                        {backoffWaitMs > 0 && (
                          <div className="relative pb-6 -mt-2">
                            <div className="absolute -left-6 top-1 w-4 h-4 rounded-[2px] bg-relay-sidebar border border-relay-amber/60 flex items-center justify-center">
                              <Hourglass className="w-2.5 h-2.5 text-relay-amber" />
                            </div>
                            <div className="px-3 py-1.5 border border-dashed border-relay-amber/50 bg-relay-amber/5 rounded-[4px] flex items-center justify-between font-mono text-3xs text-relay-amber">
                              <span className="font-typewriter uppercase tracking-wider">
                                BACKOFF INTERVAL (WAIT):
                              </span>
                              <span>{(backoffWaitMs / 1000).toFixed(2)}s DELAY APPLIED</span>
                            </div>
                          </div>
                        )}
                      </React.Fragment>
                    );
                  })
                ) : (
                  <div className="font-mono text-2xs text-relay-muted italic py-4">
                    NO EXECUTION ATTEMPTS RECORDED YET. SIGNAL AWAITING WORKER DISPATCH.
                  </div>
                )}

                {/* Scheduled Next Retry state */}
                {job.status === 'RETRYING' && job.nextRetryAt && (
                  <div className="relative pb-2">
                    <div className="absolute -left-6 top-1 w-4 h-4 rounded-[2px] bg-relay-amber/20 border border-relay-amber flex items-center justify-center animate-pulse">
                      <Hourglass className="w-2.5 h-2.5 text-relay-amber" />
                    </div>
                    <div className="p-3 bg-relay-amber/10 border border-relay-amber/50 rounded-[4px] font-mono text-2xs text-relay-amber flex items-center justify-between">
                      <div>
                        <div className="font-typewriter uppercase tracking-wider font-bold">
                          NEXT RETRY SCHEDULED
                        </div>
                        <div className="text-3xs text-relay-muted mt-0.5">
                          Target dispatch time: {new Date(job.nextRetryAt).toLocaleTimeString('en-US', { hour12: false })}
                        </div>
                      </div>
                      <span className="px-2 py-0.5 border border-relay-amber/60 rounded-[2px] text-3xs">
                        BACKING OFF
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </Card>
        </div>

        {/* Right 5 cols: Payload Ledger & System Diagnostics */}
        <div className="lg:col-span-5 space-y-6">
          {/* Payload Card */}
          <Card
            headerTitle="TRANSMITTED JSON PAYLOAD"
            headerAction={
              <Button
                variant="ghost"
                size="xs"
                onClick={() => {
                  navigator.clipboard.writeText(JSON.stringify(job.payload, null, 2));
                  showToast('info', 'COPIED TO CLIPBOARD', 'JSON payload copied');
                }}
              >
                COPY JSON
              </Button>
            }
          >
            <div className="p-4 bg-relay-sidebar">
              <pre className="font-mono text-2xs text-relay-text/90 leading-relaxed overflow-x-auto p-3 bg-relay-bg/80 border border-relay-border rounded-[4px]">
                {JSON.stringify(job.payload, null, 2)}
              </pre>
            </div>
          </Card>

          {/* Ledger Audit Details */}
          <Card headerTitle="DISPATCH STATION AUDIT">
            <div className="p-4 divide-y divide-relay-border/50 font-mono text-2xs">
              <div className="py-2 flex justify-between">
                <span className="text-relay-muted">JOB ID (FULL)</span>
                <span className="text-relay-text select-all font-semibold">{job.id}</span>
              </div>
              <div className="py-2 flex justify-between">
                <span className="text-relay-muted">CREATED TIMESTAMP</span>
                <span className="text-relay-text">{new Date(job.createdAt).toISOString()}</span>
              </div>
              <div className="py-2 flex justify-between">
                <span className="text-relay-muted">UPDATED TIMESTAMP</span>
                <span className="text-relay-text">{new Date(job.updatedAt).toISOString()}</span>
              </div>
              <div className="py-2 flex justify-between">
                <span className="text-relay-muted">IDEMPOTENCY KEY</span>
                <span className="text-relay-text">{job.idempotencyKey || '—'}</span>
              </div>
              <div className="py-2 flex justify-between">
                <span className="text-relay-muted">MAX CONFIGURED ATTEMPTS</span>
                <span className="text-relay-text">{job.maxAttempts}</span>
              </div>
              <div className="py-2 flex justify-between">
                <span className="text-relay-muted">TOTAL ATTEMPTS LOGGED</span>
                <span className="text-relay-text">{job.attemptCount}</span>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Confirmation Modal for Replay */}
      <Modal
        isOpen={isReplayConfirmOpen}
        onClose={() => setIsReplayConfirmOpen(false)}
        title="CONFIRM DEAD LETTER REPLAY"
        subtitle="RESET ATTEMPT STATE & RESUBMIT TO DISPATCH ENGINE"
        footer={
          <>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setIsReplayConfirmOpen(false)}
            >
              ABORT
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={() => replayMutation.mutate(job.id)}
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
            Replaying job <code className="font-mono text-relay-brass">{job.id}</code> will reset its attempt counter to 0, clear any failure flags, and re-enqueue the job payload to the active dispatch pipeline.
          </p>
          <div className="p-3 border border-relay-amber/50 bg-relay-amber/10 rounded-[4px] text-relay-amber font-mono text-2xs">
            CAUTION: Ensure any underlying external service faults or network outages have been rectified before replaying.
          </div>
        </div>
      </Modal>
    </div>
  );
};

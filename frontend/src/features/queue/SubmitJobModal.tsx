import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { JobType, JobSubmissionRequest } from '@/api/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import { Send, AlertTriangle } from 'lucide-react';

interface SubmitJobModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const SubmitJobModal: React.FC<SubmitJobModalProps> = ({ isOpen, onClose }) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [jobType, setJobType] = useState<JobType>('EMAIL_NOTIFICATION');
  const [payloadText, setPayloadText] = useState(
    JSON.stringify({ to: 'dispatch@relay.internal', subject: 'Night Shift Handover Memo', priority: 'HIGH' }, null, 2)
  );
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [maxAttempts, setMaxAttempts] = useState<number>(3);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (req: JobSubmissionRequest) => api.submitJob(req),
    onSuccess: (job) => {
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['recent-jobs'] });
      queryClient.invalidateQueries({ queryKey: ['recent-jobs-ticker'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('success', 'JOB DISPATCHED', `Signal #${job.id.substring(0, 8)} enqueued successfully`);
      onClose();
      setError(null);
    },
    onError: (err: any) => {
      setError(err.message || 'Failed to transmit signal to queue');
      showToast('error', 'DISPATCH FAILED', err.message);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    let parsedPayload: Record<string, any>;
    try {
      parsedPayload = JSON.parse(payloadText);
    } catch {
      setError('Invalid JSON payload syntax');
      return;
    }

    if (maxAttempts < 1) {
      setError('Max attempts must be at least 1');
      return;
    }

    mutation.mutate({
      jobType,
      payload: parsedPayload,
      idempotencyKey: idempotencyKey.trim() || undefined,
      maxAttempts: Number(maxAttempts),
    });
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="DISPATCH NEW SIGNAL"
      subtitle="ENQUEUE ASYNC JOB TO RELAY LEDGER"
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={onClose}>
            CANCEL
          </Button>
          <Button
            variant="brass"
            size="sm"
            onClick={handleSubmit}
            isLoading={mutation.isPending}
            leftIcon={<Send className="w-3.5 h-3.5" />}
          >
            TRANSMIT TO QUEUE
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="p-3 bg-relay-red/10 border border-relay-red/50 rounded-[4px] flex items-center space-x-2.5 text-relay-red font-mono text-2xs">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div>
          <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
            JOB TYPE / ROUTE
          </label>
          <select
            value={jobType}
            onChange={(e) => {
              const type = e.target.value as JobType;
              setJobType(type);
              if (type === 'EMAIL_NOTIFICATION') {
                setPayloadText(
                  JSON.stringify({ to: 'ops@relay.internal', subject: 'Night Shift Alert', priority: 'HIGH' }, null, 2)
                );
              } else {
                setPayloadText(
                  JSON.stringify({ reportType: 'SYSTEM_AUDIT_LOG', period: '2026-Q3', format: 'PARQUET' }, null, 2)
                );
              }
            }}
            className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] px-3 py-2 text-xs font-mono text-relay-text focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none"
          >
            <option value="EMAIL_NOTIFICATION">EMAIL_NOTIFICATION</option>
            <option value="REPORT_GENERATION">REPORT_GENERATION</option>
          </select>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
              IDEMPOTENCY KEY (OPTIONAL)
            </label>
            <input
              type="text"
              placeholder="e.g. wire-sig-8841"
              value={idempotencyKey}
              onChange={(e) => setIdempotencyKey(e.target.value)}
              className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] px-3 py-2 text-xs font-mono text-relay-text placeholder:text-relay-muted/40 focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none"
            />
          </div>
          <div>
            <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
              MAX RETRY ATTEMPTS
            </label>
            <input
              type="number"
              min={1}
              max={20}
              value={maxAttempts}
              onChange={(e) => setMaxAttempts(parseInt(e.target.value) || 1)}
              className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] px-3 py-2 text-xs font-mono text-relay-text focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
            PAYLOAD LEDGER (JSON)
          </label>
          <textarea
            rows={5}
            value={payloadText}
            onChange={(e) => setPayloadText(e.target.value)}
            className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] p-3 text-xs font-mono text-relay-text focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none resize-none leading-relaxed"
          />
        </div>
      </form>
    </Modal>
  );
};

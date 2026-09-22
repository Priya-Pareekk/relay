import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { JobType } from '@/api/types';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import { PlusCircle, AlertTriangle } from 'lucide-react';

interface CreateDefinitionModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CreateDefinitionModal: React.FC<CreateDefinitionModalProps> = ({
  isOpen,
  onClose,
}) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [name, setName] = useState('');
  const [jobType, setJobType] = useState<JobType>('REPORT_GENERATION');
  const [cronExpression, setCronExpression] = useState('0 0 2 * * *');
  const [payloadText, setPayloadText] = useState(
    JSON.stringify({ reportType: 'NIGHT_SHIFT_LOG_AUDIT', format: 'JSON' }, null, 2)
  );
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: api.createJobDefinition,
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['job-definitions'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('success', 'CRON DEFINITION REGISTERED', `Schedule "${created.name}" established`);
      onClose();
      setName('');
      setError(null);
    },
    onError: (err: any) => {
      setError(err.message || 'Failed to establish Job Definition');
      showToast('error', 'CREATION REJECTED', err.message);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError('Definition name is required');
      return;
    }

    let parsedPayload: Record<string, any>;
    try {
      parsedPayload = JSON.parse(payloadText);
    } catch {
      setError('Invalid JSON payload syntax');
      return;
    }

    mutation.mutate({
      name: name.trim(),
      jobType,
      cronExpression: cronExpression.trim(),
      payloadTemplate: parsedPayload,
      enabled: true,
    });
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="REGISTER CRON JOB DEFINITION"
      subtitle="SCHEDULE RECURRING TIMETABLE IN DISPATCH DESK"
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
            leftIcon={<PlusCircle className="w-3.5 h-3.5" />}
          >
            ESTABLISH SCHEDULE
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4 font-sans text-xs">
        {error && (
          <div className="p-3 bg-relay-red/10 border border-relay-red/50 rounded-[4px] flex items-center space-x-2.5 text-relay-red font-mono text-2xs">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div>
          <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
            DEFINITION IDENTIFIER / NAME
          </label>
          <input
            type="text"
            placeholder="e.g. Nightly Audit Rollup"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] px-3 py-2 text-xs font-mono text-relay-text placeholder:text-relay-muted/40 focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
              JOB TYPE
            </label>
            <select
              value={jobType}
              onChange={(e) => setJobType(e.target.value as JobType)}
              className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] px-3 py-2 text-xs font-mono text-relay-text focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none"
            >
              <option value="REPORT_GENERATION">REPORT_GENERATION</option>
              <option value="EMAIL_NOTIFICATION">EMAIL_NOTIFICATION</option>
            </select>
          </div>

          <div>
            <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
              CRON TIMETABLE (6 FIELDS)
            </label>
            <input
              type="text"
              placeholder="0 0 2 * * *"
              value={cronExpression}
              onChange={(e) => setCronExpression(e.target.value)}
              className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] px-3 py-2 text-xs font-mono text-relay-text focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label className="block font-typewriter text-2xs uppercase tracking-wider text-relay-muted mb-1.5">
            PAYLOAD TEMPLATE (JSON)
          </label>
          <textarea
            rows={4}
            value={payloadText}
            onChange={(e) => setPayloadText(e.target.value)}
            className="w-full bg-relay-sidebar border border-relay-border rounded-[4px] p-3 text-xs font-mono text-relay-text focus:border-relay-brass focus:ring-1 focus:ring-relay-brass focus:outline-none resize-none leading-relaxed"
          />
        </div>
      </form>
    </Modal>
  );
};

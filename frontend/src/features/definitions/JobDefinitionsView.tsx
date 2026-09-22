import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { JobDefinitionResponse } from '@/api/types';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Toggle } from '@/components/ui/Toggle';
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
import { CreateDefinitionModal } from './CreateDefinitionModal';
import {
  Clock,
  Plus,
  Trash2,
  Calendar,
  RefreshCw,
  Eye,
} from 'lucide-react';

function translateCron(expr: string): string {
  const parts = expr.trim().split(/\s+/);
  if (parts.length === 6) {
    const [sec, min, hour, day, month, dayOfWeek] = parts;
    if (sec === '0' && min === '0' && hour === '2' && day === '*' && month === '*' && dayOfWeek === '*') {
      return 'Every night at 02:00:00 UTC';
    }
    if (sec === '0' && min === '*/5' && hour === '*' && day === '*' && month === '*' && dayOfWeek === '*') {
      return 'Every 5 minutes on the interval';
    }
    if (sec === '*/10' && min === '*' && hour === '*' && day === '*' && month === '*' && dayOfWeek === '*') {
      return 'Every 10 seconds continuous pulse';
    }
    if (sec === '*/30' && min === '*' && hour === '*' && day === '*' && month === '*' && dayOfWeek === '*') {
      return 'Every 30 seconds periodic check';
    }
    if (sec === '0' && min === '0' && hour === '*' && day === '*' && month === '*' && dayOfWeek === '*') {
      return 'Hourly at minute 0';
    }
    if (sec === '0' && min === '0' && hour === '0' && day === '*' && month === '*' && dayOfWeek === '*') {
      return 'Daily at midnight (00:00 UTC)';
    }
  }
  return `Custom timetable: [${expr}]`;
}

export const JobDefinitionsView: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [definitionToDelete, setDefinitionToDelete] = useState<JobDefinitionResponse | null>(null);
  const [inspectPayloadDef, setInspectPayloadDef] = useState<JobDefinitionResponse | null>(null);

  const {
    data: definitions,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['job-definitions'],
    queryFn: api.getJobDefinitions,
    refetchInterval: 3500,
  });

  // Toggle Mutation with optimistic updates
  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      api.toggleJobDefinition(id, enabled),
    onMutate: async ({ id, enabled }) => {
      await queryClient.cancelQueries({ queryKey: ['job-definitions'] });
      const previous = queryClient.getQueryData<JobDefinitionResponse[]>(['job-definitions']);
      if (previous) {
        queryClient.setQueryData<JobDefinitionResponse[]>(
          ['job-definitions'],
          previous.map((d) => (d.id === id ? { ...d, enabled } : d))
        );
      }
      return { previous };
    },
    onError: (err: any, _, context) => {
      if (context?.previous) {
        queryClient.setQueryData(['job-definitions'], context.previous);
      }
      showToast('error', 'SCHEDULE TOGGLE FAILED', err.message);
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['job-definitions'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast(
        'info',
        updated.enabled ? 'CRON SCHEDULE ENGAGED' : 'CRON SCHEDULE SUSPENDED',
        `"${updated.name}" is now ${updated.enabled ? 'active' : 'paused'}`
      );
    },
  });

  // Delete Mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteJobDefinition(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['job-definitions'] });
      queryClient.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast('info', 'DEFINITION REMOVED', 'Cron schedule expunged from ledger');
      setDefinitionToDelete(null);
    },
    onError: (err: any) => {
      showToast('error', 'DELETION FAILED', err.message);
    },
  });

  return (
    <div className="p-4 lg:p-6 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-relay-border">
        <div>
          <div className="flex items-center space-x-2.5">
            <h1 className="font-typewriter text-base lg:text-lg uppercase tracking-wider text-relay-text">
              JOB DEFINITIONS // RECURRING CRON SCHEDULES
            </h1>
            <span className="font-mono text-3xs px-2 py-0.5 rounded-[2px] bg-relay-surface border border-relay-border text-relay-sage">
              {definitions ? `${definitions.filter((d) => d.enabled).length} ACTIVE` : '...'}
            </span>
          </div>
          <p className="font-sans text-xs text-relay-muted mt-1">
            Automated recurring job transmissions managed by the Relay background scheduler daemon.
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
          <Button
            variant="brass"
            size="sm"
            onClick={() => setIsCreateModalOpen(true)}
            leftIcon={<Plus className="w-3.5 h-3.5" />}
          >
            REGISTER DEFINITION
          </Button>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <ErrorState
          title="Scheduler Daemon Unreachable"
          message={(error as Error)?.message || 'Failed to communicate with cron definition registry.'}
          onRetry={() => refetch()}
        />
      )}

      {/* Definitions Table */}
      {isLoading ? (
        <TableSkeleton rows={5} cols={6} />
      ) : !definitions || definitions.length === 0 ? (
        <Card className="p-8">
          <EmptyState
            message="No recurring job definitions are registered on this relay station."
            actionLabel="REGISTER FIRST SCHEDULE"
            onAction={() => setIsCreateModalOpen(true)}
          />
        </Card>
      ) : (
        <Card headerTitle="REGISTERED CRON TIMETABLES">
          <TableContainer className="border-0 rounded-none">
            <TableHead>
              <tr>
                <TableHeaderCell>DEFINITION / NAME</TableHeaderCell>
                <TableHeaderCell>ROUTE TYPE</TableHeaderCell>
                <TableHeaderCell>CRON EXPRESSION</TableHeaderCell>
                <TableHeaderCell>HUMAN SCHEDULE</TableHeaderCell>
                <TableHeaderCell>LAST TRIGGERED</TableHeaderCell>
                <TableHeaderCell>STATE</TableHeaderCell>
                <TableHeaderCell align="right">ACTIONS</TableHeaderCell>
              </tr>
            </TableHead>
            <TableBody>
              {definitions.map((def: JobDefinitionResponse) => (
                <TableRow key={def.id}>
                  {/* Name & ID */}
                  <TableCell>
                    <div className="font-sans font-medium text-relay-text text-xs">
                      {def.name}
                    </div>
                    <div className="font-mono text-3xs text-relay-muted mt-0.5">
                      REF: {def.id.substring(0, 8)}
                    </div>
                  </TableCell>

                  {/* Route Type */}
                  <TableCell mono className="text-2xs text-relay-text/90">
                    {def.jobType}
                  </TableCell>

                  {/* Cron Expression */}
                  <TableCell mono className="text-2xs text-relay-brass">
                    <span className="px-2 py-0.5 rounded-[2px] bg-relay-sidebar border border-relay-border">
                      {def.cronExpression}
                    </span>
                  </TableCell>

                  {/* Human Translation */}
                  <TableCell className="text-xs text-relay-muted font-sans">
                    {translateCron(def.cronExpression)}
                  </TableCell>

                  {/* Last Triggered */}
                  <TableCell mono className="text-2xs text-relay-muted">
                    {def.lastTriggeredAt ? (
                      <div>
                        {new Date(def.lastTriggeredAt).toLocaleString('en-US', {
                          month: 'short',
                          day: '2-digit',
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                          hour12: false,
                        })}
                      </div>
                    ) : (
                      <span className="italic text-relay-muted/50">NEVER FIRED</span>
                    )}
                  </TableCell>

                  {/* State Toggle */}
                  <TableCell>
                    <Toggle
                      checked={def.enabled}
                      onChange={(checked) =>
                        toggleMutation.mutate({ id: def.id, enabled: checked })
                      }
                      label={def.enabled ? 'ACTIVE' : 'PAUSED'}
                    />
                  </TableCell>

                  {/* Actions */}
                  <TableCell align="right">
                    <div className="flex items-center justify-end space-x-1.5">
                      <Button
                        variant="ghost"
                        size="xs"
                        onClick={() => setInspectPayloadDef(def)}
                        title="View Payload Template"
                        leftIcon={<Eye className="w-3 h-3" />}
                      >
                        TEMPLATE
                      </Button>
                      <Button
                        variant="danger"
                        size="xs"
                        onClick={() => setDefinitionToDelete(def)}
                        title="Delete Definition"
                        leftIcon={<Trash2 className="w-3 h-3" />}
                      >
                        DELETE
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </TableContainer>
        </Card>
      )}

      {/* Create Definition Modal */}
      <CreateDefinitionModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
      />

      {/* Inspect Template Modal */}
      {inspectPayloadDef && (
        <Modal
          isOpen={Boolean(inspectPayloadDef)}
          onClose={() => setInspectPayloadDef(null)}
          title={`PAYLOAD TEMPLATE // ${inspectPayloadDef.name}`}
          subtitle={`ROUTE: ${inspectPayloadDef.jobType} · CRON: ${inspectPayloadDef.cronExpression}`}
          footer={
            <Button variant="outline" size="sm" onClick={() => setInspectPayloadDef(null)}>
              DISMISS
            </Button>
          }
        >
          <pre className="font-mono text-xs text-relay-text/90 p-4 bg-relay-sidebar border border-relay-border rounded-[4px] leading-relaxed overflow-x-auto">
            {JSON.stringify(inspectPayloadDef.payloadTemplate, null, 2)}
          </pre>
        </Modal>
      )}

      {/* Delete Confirmation Modal */}
      {definitionToDelete && (
        <Modal
          isOpen={Boolean(definitionToDelete)}
          onClose={() => setDefinitionToDelete(null)}
          title="EXPUNGE CRON DEFINITION"
          subtitle="CONFIRM PERMANENT REMOVAL FROM DISPATCH ENGINE"
          footer={
            <>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setDefinitionToDelete(null)}
              >
                ABORT
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => deleteMutation.mutate(definitionToDelete.id)}
                isLoading={deleteMutation.isPending}
                leftIcon={<Trash2 className="w-3.5 h-3.5" />}
              >
                CONFIRM EXPUNGE
              </Button>
            </>
          }
        >
          <p className="font-sans text-xs text-relay-text">
            Are you sure you wish to delete the recurring cron schedule{' '}
            <strong className="text-relay-brass">"{definitionToDelete.name}"</strong>? This will cease all future automated dispatches.
          </p>
        </Modal>
      )}
    </div>
  );
};

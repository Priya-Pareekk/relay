import React, { useMemo, useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { JobResponse } from '@/api/types';

interface TickerEvent {
  id: string;
  shortId: string;
  type: 'DISPATCHED' | 'RETRYING' | 'DEAD_LETTER' | 'COMPLETED' | 'PROCESSING';
  message: string;
  colorClass: string;
  timestamp: string;
}

export const DispatchTicker: React.FC = () => {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    setPrefersReducedMotion(mediaQuery.matches);
    const handler = (e: MediaQueryListEvent) => setPrefersReducedMotion(e.matches);
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);

  const { data: jobsData } = useQuery({
    queryKey: ['recent-jobs-ticker'],
    queryFn: () => api.getJobs({ page: 0, size: 20, sort: 'updatedAt,desc' }),
    refetchInterval: 4000,
  });

  const tickerEvents: TickerEvent[] = useMemo(() => {
    const jobs = jobsData?.content || [];
    if (jobs.length === 0) {
      return [
        {
          id: 'init-1',
          shortId: 'relay-01',
          type: 'DISPATCHED',
          message: 'RELAY DISPATCH DESK ONLINE // LISTENING ON PORT 8080',
          colorClass: 'text-relay-sage',
          timestamp: 'NOW',
        },
        {
          id: 'init-2',
          shortId: 'sync-02',
          type: 'PROCESSING',
          message: 'CIRCUIT MONITOR OPERATIONAL · ZERO DEAD LETTERS LOGGED',
          colorClass: 'text-relay-brass',
          timestamp: 'NOW',
        },
      ];
    }

    return jobs.map((job: JobResponse) => {
      const shortId = job.id.substring(0, 6);
      let type: TickerEvent['type'] = 'PROCESSING';
      let message = `JOB #${shortId} ENQUEUED`;
      let colorClass = 'text-relay-muted';

      if (job.status === 'COMPLETED') {
        type = 'COMPLETED';
        message = `JOB #${shortId} DISPATCHED & COMPLETED`;
        colorClass = 'text-relay-sage';
      } else if (job.status === 'RETRYING') {
        type = 'RETRYING';
        message = `JOB #${shortId} RETRYING (ATTEMPT ${job.attemptCount}/${job.maxAttempts})`;
        colorClass = 'text-relay-amber';
      } else if (job.status === 'DEAD_LETTER') {
        type = 'DEAD_LETTER';
        message = `JOB #${shortId} DEAD LETTER · ${job.lastError ? job.lastError.substring(0, 30) : 'EXHAUSTED RETRIES'}`;
        colorClass = 'text-relay-red';
      } else if (job.status === 'PROCESSING') {
        type = 'PROCESSING';
        message = `JOB #${shortId} IN FLIGHT [${job.jobType}]`;
        colorClass = 'text-relay-brass';
      }

      return {
        id: job.id,
        shortId,
        type,
        message,
        colorClass,
        timestamp: new Date(job.updatedAt).toLocaleTimeString('en-US', {
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        }),
      };
    });
  }, [jobsData]);

  const latestEvent = tickerEvents[0];

  return (
    <div className="w-full bg-relay-sidebar/95 telegraph-tape-border py-1.5 px-3 select-none overflow-hidden relative flex items-center z-20">
      {/* Telegraph Station Stamp Label */}
      <div className="flex items-center shrink-0 pr-3 mr-3 border-r border-relay-border z-10 bg-relay-sidebar">
        <span className="inline-block w-1.5 h-1.5 rounded-[1px] bg-relay-brass mr-2 animate-pulse" />
        <span className="font-typewriter text-2xs uppercase tracking-widest text-relay-brass">
          DISPATCH WIRE
        </span>
      </div>

      {prefersReducedMotion ? (
        /* Reduced motion: Static latest event */
        <div className="font-typewriter text-2xs tracking-widest uppercase flex items-center space-x-3 text-relay-text overflow-hidden text-ellipsis whitespace-nowrap">
          <span className="text-relay-muted font-mono">[{latestEvent?.timestamp}]</span>
          <span className={latestEvent?.colorClass}>{latestEvent?.message}</span>
        </div>
      ) : (
        /* Animated Telegraph Tape Strip */
        <div className="overflow-hidden flex-1 relative flex items-center">
          <div className="animate-ticker flex items-center space-x-6 whitespace-nowrap font-typewriter text-2xs uppercase tracking-widest">
            {/* Duplicated set to ensure seamless infinite right-to-left scroll */}
            {[...tickerEvents, ...tickerEvents].map((event, idx) => (
              <React.Fragment key={`${event.id}-${idx}`}>
                <span className="inline-flex items-center space-x-2">
                  <span className="font-mono text-relay-muted/70 text-3xs">
                    {event.timestamp}
                  </span>
                  <span className={`${event.colorClass} font-medium`}>{event.message}</span>
                </span>
                <span className="text-relay-muted/40 font-mono">·</span>
              </React.Fragment>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

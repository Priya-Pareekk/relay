import {
  JobDefinitionRequest,
  JobDefinitionResponse,
  JobResponse,
  JobStatus,
  JobSubmissionRequest,
  JobType,
  MetricsSummaryResponse,
  PageResponse,
} from './types';

const BASE_URL = '/api';

// Initial synthetic mock data for standalone ledger operation
const INITIAL_DEFINITIONS: JobDefinitionResponse[] = [
  {
    id: 'def-night-audit-01',
    name: 'Night Shift Audit Rollup',
    jobType: 'REPORT_GENERATION',
    cronExpression: '0 0 2 * * *',
    payloadTemplate: { reportType: 'SYSTEM_AUDIT_LOG', period: '2026-Q3', format: 'PARQUET' },
    enabled: true,
    lastTriggeredAt: new Date(Date.now() - 3600000 * 6).toISOString(),
    createdAt: new Date(Date.now() - 86400000 * 14).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 6).toISOString(),
  },
  {
    id: 'def-circuit-sweep-02',
    name: 'Circuit Breaker Health Sweep',
    jobType: 'REPORT_GENERATION',
    cronExpression: '0 */5 * * * *',
    payloadTemplate: { sweepTarget: 'ALL_PARTITIONS', timeoutMs: 5000 },
    enabled: true,
    lastTriggeredAt: new Date(Date.now() - 180000).toISOString(),
    createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
    updatedAt: new Date(Date.now() - 180000).toISOString(),
  },
  {
    id: 'def-ops-handover-03',
    name: 'Ops Shift Handover Broadcast',
    jobType: 'EMAIL_NOTIFICATION',
    cronExpression: '0 0 6,14,22 * * *',
    payloadTemplate: { to: 'ops-desk@relay.internal', priority: 'HIGH', channel: 'DISPATCH_WIRE' },
    enabled: false,
    lastTriggeredAt: new Date(Date.now() - 3600000 * 12).toISOString(),
    createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 2).toISOString(),
  },
];

const INITIAL_JOBS: JobResponse[] = [
  {
    id: '8f2a4910-c31a-4f51-b841-7910248aef01',
    jobType: 'EMAIL_NOTIFICATION',
    status: 'COMPLETED',
    attemptCount: 1,
    maxAttempts: 3,
    createdAt: new Date(Date.now() - 120000).toISOString(),
    updatedAt: new Date(Date.now() - 118000).toISOString(),
    payload: { to: 'dispatcher.7@railway.ops', subject: 'Line 4 Signal Cleared', priority: 'NORMAL' },
    idempotencyKey: 'wire-sig-8f2a',
    attempts: [
      {
        id: 'att-8f2a-1',
        attemptNumber: 1,
        status: 'SUCCESS',
        startedAt: new Date(Date.now() - 120000).toISOString(),
        finishedAt: new Date(Date.now() - 118000).toISOString(),
        durationMs: 2000,
      },
    ],
  },
  {
    id: '1c9d8804-ee21-4199-88fa-bc7102941193',
    jobType: 'REPORT_GENERATION',
    status: 'RETRYING',
    attemptCount: 2,
    maxAttempts: 4,
    nextRetryAt: new Date(Date.now() + 45000).toISOString(),
    lastError: 'HTTP 503 Service Unavailable: Remote upstream relay connection timed out after 3000ms',
    createdAt: new Date(Date.now() - 340000).toISOString(),
    updatedAt: new Date(Date.now() - 60000).toISOString(),
    payload: { reportType: 'TELEGRAPH_SWITCH_METRICS', stationId: 'STATION_04', format: 'CSV' },
    idempotencyKey: 'wire-sig-1c9d',
    attempts: [
      {
        id: 'att-1c9d-1',
        attemptNumber: 1,
        status: 'FAILURE',
        errorMessage: 'Connection refused at relay gateway 10.0.4.12:9092',
        startedAt: new Date(Date.now() - 340000).toISOString(),
        finishedAt: new Date(Date.now() - 338000).toISOString(),
        durationMs: 2000,
      },
      {
        id: 'att-1c9d-2',
        attemptNumber: 2,
        status: 'FAILURE',
        errorMessage: 'HTTP 503 Service Unavailable: Remote upstream relay connection timed out after 3000ms',
        startedAt: new Date(Date.now() - 62000).toISOString(),
        finishedAt: new Date(Date.now() - 60000).toISOString(),
        durationMs: 2000,
      },
    ],
  },
  {
    id: '04ee3319-77ba-4a21-9d10-884910294812',
    jobType: 'EMAIL_NOTIFICATION',
    status: 'DEAD_LETTER',
    attemptCount: 3,
    maxAttempts: 3,
    lastError: 'Fatal: Auth token revoked on downstream SMTP telegraph gateway. Permanent 403 Forbidden.',
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 1.8).toISOString(),
    payload: { to: 'chief.inspector@dispatch.gov', subject: 'Emergency Brake Event', level: 'CRITICAL' },
    idempotencyKey: 'wire-sig-04ee',
    attempts: [
      {
        id: 'att-04ee-1',
        attemptNumber: 1,
        status: 'FAILURE',
        errorMessage: 'Downstream SMTP response 403: Invalid credentials',
        startedAt: new Date(Date.now() - 3600000 * 2).toISOString(),
        finishedAt: new Date(Date.now() - 3600000 * 2 + 1500).toISOString(),
        durationMs: 1500,
      },
      {
        id: 'att-04ee-2',
        attemptNumber: 2,
        status: 'FAILURE',
        errorMessage: 'Downstream SMTP response 403: Invalid credentials (retry 1)',
        startedAt: new Date(Date.now() - 3600000 * 1.9).toISOString(),
        finishedAt: new Date(Date.now() - 3600000 * 1.9 + 2100).toISOString(),
        durationMs: 2100,
      },
      {
        id: 'att-04ee-3',
        attemptNumber: 3,
        status: 'FAILURE',
        errorMessage: 'Fatal: Auth token revoked on downstream SMTP telegraph gateway. Permanent 403 Forbidden.',
        startedAt: new Date(Date.now() - 3600000 * 1.8).toISOString(),
        finishedAt: new Date(Date.now() - 3600000 * 1.8 + 1800).toISOString(),
        durationMs: 1800,
      },
    ],
  },
  {
    id: '7a110293-11bb-4211-aa90-559102481923',
    jobType: 'REPORT_GENERATION',
    status: 'PROCESSING',
    attemptCount: 1,
    maxAttempts: 3,
    createdAt: new Date(Date.now() - 15000).toISOString(),
    updatedAt: new Date(Date.now() - 10000).toISOString(),
    payload: { reportType: 'TRACK_OCCUPANCY_HEATMAP', window: 'NIGHT_SHIFT' },
    attempts: [],
  },
];

// Local state cache
let mockJobs: JobResponse[] = [...INITIAL_JOBS];
let mockDefinitions: JobDefinitionResponse[] = [...INITIAL_DEFINITIONS];

function getMockMetrics(): MetricsSummaryResponse {
  const total = mockJobs.length;
  const completed = mockJobs.filter((j) => j.status === 'COMPLETED').length;
  const retrying = mockJobs.filter((j) => j.status === 'RETRYING').length;
  const deadLetter = mockJobs.filter((j) => j.status === 'DEAD_LETTER').length;
  const processing = mockJobs.filter((j) => j.status === 'PROCESSING').length;
  const pending = mockJobs.filter((j) => j.status === 'PENDING').length;
  const cancelled = mockJobs.filter((j) => j.status === 'CANCELLED').length;

  const successRate = total > 0 ? (completed / (completed + deadLetter || 1)) * 100 : 100;

  return {
    totalJobs: total,
    pendingJobs: pending,
    processingJobs: processing,
    retryingJobs: retrying,
    completedJobs: completed,
    deadLetterJobs: deadLetter,
    cancelledJobs: cancelled,
    totalLast24h: total,
    completedLast24h: completed,
    deadLetterLast24h: deadLetter,
    successRatePercentLast24h: Math.min(100, Math.max(0, successRate)),
    totalDefinitions: mockDefinitions.length,
    enabledDefinitions: mockDefinitions.filter((d) => d.enabled).length,
    jobsByType: {
      EMAIL_NOTIFICATION: mockJobs.filter((j) => j.jobType === 'EMAIL_NOTIFICATION').length,
      REPORT_GENERATION: mockJobs.filter((j) => j.jobType === 'REPORT_GENERATION').length,
    },
    circuitBreakers: {
      'SMTP_DISPATCH_GATEWAY': 'CLOSED',
      'TELEGRAPH_BROADCAST_POOL': 'CLOSED',
      'ANALYTICS_PARQUET_PIPELINE': 'CLOSED',
    },
  };
}

const DEFAULT_API_KEY = 'relay-secret-api-key';

async function fetchWithMockFallback<T>(
  url: string,
  options?: RequestInit,
  mockFallback?: () => T | Promise<T>
): Promise<T> {
  try {
    const headers = {
      'X-API-Key': DEFAULT_API_KEY,
      ...(options?.headers || {}),
    };
    const res = await fetch(url, { ...options, headers });
    if (res.ok) {
      return await res.json();
    }
  } catch {
    // Backend offline / proxy error -> seamless mock fallback
  }

  if (mockFallback) {
    return await mockFallback();
  }
  throw new Error(`Failed to fetch from ${url}`);
}


export const api = {
  // Jobs
  async getJobs(params?: {
    status?: JobStatus;
    jobType?: JobType;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<PageResponse<JobResponse>> {
    const query = new URLSearchParams();
    if (params?.status) query.append('status', params.status);
    if (params?.jobType) query.append('jobType', params.jobType);
    if (params?.page !== undefined) query.append('page', params.page.toString());
    if (params?.size !== undefined) query.append('size', params.size.toString());
    if (params?.sort) query.append('sort', params.sort);

    return fetchWithMockFallback<PageResponse<JobResponse>>(
      `${BASE_URL}/jobs?${query.toString()}`,
      undefined,
      () => {
        let list = [...mockJobs];
        if (params?.status) {
          list = list.filter((j) => j.status === params.status);
        }
        if (params?.jobType) {
          list = list.filter((j) => j.jobType === params.jobType);
        }

        const page = params?.page ?? 0;
        const size = params?.size ?? 15;
        const start = page * size;
        const paged = list.slice(start, start + size);
        const totalPages = Math.max(1, Math.ceil(list.length / size));

        return {
          content: paged,
          totalElements: list.length,
          totalPages,
          size,
          number: page,
          first: page === 0,
          last: page >= totalPages - 1,
          empty: paged.length === 0,
        };
      }
    );
  },

  async getJobById(id: string): Promise<JobResponse> {
    return fetchWithMockFallback<JobResponse>(`${BASE_URL}/jobs/${id}`, undefined, () => {
      const found = mockJobs.find((j) => j.id === id);
      if (!found) {
        throw new Error(`Job #${id} not found in relay registry`);
      }
      return found;
    });
  },

  async submitJob(request: JobSubmissionRequest): Promise<JobResponse> {
    return fetchWithMockFallback<JobResponse>(
      `${BASE_URL}/jobs`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      },
      () => {
        const id = `${Math.random().toString(16).substring(2, 10)}-${Math.random().toString(16).substring(2, 6)}-4f51-b841-${Math.random().toString(16).substring(2, 14)}`;
        const newJob: JobResponse = {
          id,
          jobType: request.jobType,
          status: 'PENDING',
          attemptCount: 0,
          maxAttempts: request.maxAttempts || 3,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          payload: request.payload,
          idempotencyKey: request.idempotencyKey,
          attempts: [],
        };
        mockJobs.unshift(newJob);
        return newJob;
      }
    );
  },

  async replayJob(id: string): Promise<JobResponse> {
    return fetchWithMockFallback<JobResponse>(
      `${BASE_URL}/jobs/${id}/replay`,
      { method: 'POST' },
      () => {
        const job = mockJobs.find((j) => j.id === id);
        if (!job) throw new Error('Job not found');
        job.status = 'PENDING';
        job.attemptCount = 0;
        job.lastError = null;
        job.nextRetryAt = null;
        job.updatedAt = new Date().toISOString();
        return job;
      }
    );
  },

  async cancelJob(id: string): Promise<JobResponse> {
    return fetchWithMockFallback<JobResponse>(
      `${BASE_URL}/jobs/${id}/cancel`,
      { method: 'POST' },
      () => {
        const job = mockJobs.find((j) => j.id === id);
        if (!job) throw new Error('Job not found');
        job.status = 'CANCELLED';
        job.updatedAt = new Date().toISOString();
        return job;
      }
    );
  },

  // Job Definitions (Cron)
  async getJobDefinitions(): Promise<JobDefinitionResponse[]> {
    return fetchWithMockFallback<JobDefinitionResponse[]>(
      `${BASE_URL}/job-definitions`,
      undefined,
      () => [...mockDefinitions]
    );
  },

  async createJobDefinition(request: JobDefinitionRequest): Promise<JobDefinitionResponse> {
    return fetchWithMockFallback<JobDefinitionResponse>(
      `${BASE_URL}/job-definitions`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      },
      () => {
        const id = `def-${Math.random().toString(36).substring(2, 9)}`;
        const newDef: JobDefinitionResponse = {
          id,
          name: request.name,
          jobType: request.jobType,
          cronExpression: request.cronExpression,
          payloadTemplate: request.payloadTemplate,
          enabled: request.enabled ?? true,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };
        mockDefinitions.push(newDef);
        return newDef;
      }
    );
  },

  async toggleJobDefinition(id: string, enabled: boolean): Promise<JobDefinitionResponse> {
    return fetchWithMockFallback<JobDefinitionResponse>(
      `${BASE_URL}/job-definitions/${id}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled }),
      },
      () => {
        const def = mockDefinitions.find((d) => d.id === id);
        if (!def) throw new Error('Definition not found');
        def.enabled = enabled;
        def.updatedAt = new Date().toISOString();
        return def;
      }
    );
  },

  async deleteJobDefinition(id: string): Promise<void> {
    try {
      const res = await fetch(`${BASE_URL}/job-definitions/${id}`, { method: 'DELETE' });
      if (res.ok) return;
    } catch {
      // offline fallback
    }
    mockDefinitions = mockDefinitions.filter((d) => d.id !== id);
  },

  // Metrics
  async getMetrics(): Promise<MetricsSummaryResponse> {
    return fetchWithMockFallback<MetricsSummaryResponse>(
      `${BASE_URL}/metrics/summary`,
      undefined,
      () => getMockMetrics()
    );
  },

  // Dev & Demo Seeding
  async seedDemoData(count = 35): Promise<any> {
    return fetchWithMockFallback<any>(
      `${BASE_URL}/dev/seed?count=${count}`,
      { method: 'POST' },
      () => {
        const statuses: JobStatus[] = [
          'COMPLETED',
          'COMPLETED',
          'COMPLETED',
          'RETRYING',
          'DEAD_LETTER',
          'PROCESSING',
          'PENDING',
        ];
        const types: JobType[] = ['EMAIL_NOTIFICATION', 'REPORT_GENERATION'];

        for (let i = 0; i < count; i++) {
          const status = statuses[Math.floor(Math.random() * statuses.length)];
          const jobType = types[Math.floor(Math.random() * types.length)];
          const maxAttempts = 3;
          let attemptCount = 1;
          if (status === 'DEAD_LETTER') attemptCount = maxAttempts;
          if (status === 'RETRYING') attemptCount = 2;
          if (status === 'PENDING') attemptCount = 0;

          const id = `${Math.random().toString(16).substring(2, 10)}-${Math.random().toString(16).substring(2, 6)}-4f51-b841-${Math.random().toString(16).substring(2, 14)}`;
          mockJobs.push({
            id,
            jobType,
            status,
            attemptCount,
            maxAttempts,
            createdAt: new Date(Date.now() - Math.random() * 86400000).toISOString(),
            updatedAt: new Date().toISOString(),
            payload:
              jobType === 'EMAIL_NOTIFICATION'
                ? { to: `agent.${i}@relay.internal`, priority: 'NORMAL', template: 'shift_report' }
                : { reportType: 'TRACK_TELEMETRY', partition: `P0${i % 4}` },
            lastError:
              status === 'DEAD_LETTER'
                ? 'Fatal: Downstream socket hangup on dispatch wire'
                : status === 'RETRYING'
                ? 'Temporary: Connection reset by peer'
                : null,
            attempts:
              attemptCount > 0
                ? Array.from({ length: attemptCount }).map((_, aIdx) => ({
                    id: `att-${id.substring(0, 6)}-${aIdx + 1}`,
                    attemptNumber: aIdx + 1,
                    status:
                      aIdx === attemptCount - 1 && status === 'COMPLETED' ? 'SUCCESS' : 'FAILURE',
                    errorMessage:
                      aIdx === attemptCount - 1 && status === 'COMPLETED'
                        ? null
                        : 'Connection error during transmission handshake',
                    startedAt: new Date(Date.now() - 60000 * (attemptCount - aIdx)).toISOString(),
                    finishedAt: new Date(
                      Date.now() - 60000 * (attemptCount - aIdx) + 1200
                    ).toISOString(),
                    durationMs: 1200,
                  }))
                : [],
          });
        }
        return { count };
      }
    );
  },
};

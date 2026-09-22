export type JobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'RETRYING' | 'DEAD_LETTER' | 'CANCELLED';

export type JobType = 'EMAIL_NOTIFICATION' | 'REPORT_GENERATION';

export interface JobAttempt {
  id: string;
  attemptNumber: number;
  status: 'SUCCESS' | 'FAILURE';
  errorMessage?: string | null;
  startedAt: string;
  finishedAt?: string | null;
  durationMs?: number | null;
}

export interface JobResponse {
  id: string;
  jobType: JobType;
  status: JobStatus;
  attemptCount: number;
  maxAttempts: number;
  nextRetryAt?: string | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt: string;
  payload: Record<string, any>;
  idempotencyKey?: string | null;
  attempts?: JobAttempt[];
}

export interface JobSubmissionRequest {
  jobType: JobType;
  payload: Record<string, any>;
  idempotencyKey?: string;
  maxAttempts?: number;
}

export interface JobDefinitionResponse {
  id: string;
  name: string;
  jobType: JobType;
  cronExpression: string;
  payloadTemplate: Record<string, any>;
  enabled: boolean;
  lastTriggeredAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface JobDefinitionRequest {
  name: string;
  jobType: JobType;
  cronExpression: string;
  payloadTemplate: Record<string, any>;
  enabled?: boolean;
}

export interface MetricsSummaryResponse {
  totalJobs: number;
  pendingJobs: number;
  processingJobs: number;
  retryingJobs: number;
  completedJobs: number;
  deadLetterJobs: number;
  cancelledJobs: number;
  totalLast24h: number;
  completedLast24h: number;
  deadLetterLast24h: number;
  successRatePercentLast24h: number;
  totalDefinitions: number;
  enabledDefinitions: number;
  jobsByType: Record<string, number>;
  circuitBreakers?: Record<string, 'CLOSED' | 'OPEN' | 'HALF_OPEN'>;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

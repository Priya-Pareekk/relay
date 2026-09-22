# ⚡ Relay — Distributed Job & Task Queue System

Relay is a high-performance distributed task and job queue system built with **Java 17/21**, **Spring Boot 3**, **Apache Kafka** (KRaft mode), **PostgreSQL 16**, and **Flyway**. It delivers asynchronous execution, exponential backoff retries with database state tracking, dead-letter queuing with replay capabilities, recurring cron-based job definitions, and full observability.

---

## 🌟 Key Features

- **Transactional Outbox Pattern**: Eliminates dual-write inconsistencies between PostgreSQL and Kafka. State changes (submissions, retry re-queues, DLQ transitions) and their corresponding outbox events (`outbox_events` table) are committed in the **same ACID database transaction**. A dedicated `@Scheduled` `OutboxPublisherService` asynchronously publishes events to Kafka and marks `published_at` upon broker ACK.
- **Circuit Breaker per Executor (Resilience4j)**: Each executor (`JobType`) is protected by a Resilience4j circuit breaker. When an downstream service fails and crosses the rolling error threshold (50% failure rate over 5 calls), the circuit opens. Subsequent jobs are immediately deferred to `RETRYING` with an extended backoff delay (`60s`) without calling the executor and **without burning retry attempts on a known-down dependency**.
- **Cron Catch-up on Restart & Error Isolation**: `CronJobSchedulerService` isolates each `JobDefinition`'s evaluation inside an isolated transactional boundary so one broken definition cannot block others. When recovering from an outage where jobs were due in the past, it executes **once immediately as a catch-up run** and advances the baseline to `now`, avoiding runaway cascades.
- **Graceful Shutdown**: `JobConsumer` tracks in-flight executions and coordinates with Spring Kafka's `shutdownTimeout(30s)` using a `@PreDestroy` bounded await hook, allowing in-flight tasks to complete before the container stops on `SIGTERM`.
- **Query Performance & Indexing**: Includes composite indexing on `(status, job_type, created_at DESC)` (Flyway migration `V4`) to ensure sub-millisecond paginated and filtered dashboard queries.
- **Defensive Request Validation**: Rejects `maxAttempts < 1` with HTTP 400 Bad Request at submission time, preventing silent dead-letter transitions.
- **Idempotency Key & DEAD_LETTER Resubmission Policy**: If a client submits a new job with an `idempotencyKey` matching an existing `DEAD_LETTER` job, Relay returns the dead-letter job as-is (`duplicate: true`) with a warning log rather than silently auto-replaying. Retrying a dead-letter job is an explicit, intentional operational decision performed via `POST /api/jobs/{id}/replay`.
- **Idempotent Consumption**: Before execution, `JobConsumer` / `JobProcessingService` checks job status and attempt freshness. If a redelivered/duplicate Kafka message arrives for a job that is already `COMPLETED`, `CANCELLED`, `DEAD_LETTER`, or has already progressed to a higher attempt, it safely logs and acknowledges the message without duplicate execution.
- **Poison Pill Protection**: A `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` wraps the listener container. Malformed or un-deserializable messages (`DeserializationException`, `MessageConversionException`) are immediately routed to a dedicated `job-queue-dlt` Dead Letter Topic (distinct from the business-logic `job-dlq`). This prevents a single bad message from wedging the consumer loop in an endless crash-restart cycle. Transient listener errors get 2 retries with 1s backoff before being sent to the DLT.
- **Ops Dashboard (React + TypeScript + Vite + Tailwind)**:
  - Strict dark-mode ops aesthetics with single design tokens ([tokens.ts](file:///e:/E/Projects/Relay/frontend/src/styles/tokens.ts)), dense ~36px focusable rows with visible rings.
  - **Main Queue View**: Metric strip, status filters, search, dense table, Replay for `DEAD_LETTER`, Cancel for `PENDING`/`RETRYING`.
  - **Job Detail View**: Vertical state/attempt transition timeline on left, collapsible monospace JSON tree on right.
  - **Job Definitions (Cron) View**: Raw cron with human translation, next-run countdown, and live enabled toggle.
  - **Metrics & Observability View**: 24h success rate KPIs, throughput charts (Recharts), and live Resilience4j Circuit Breaker indicators per executor (`CLOSED` / `OPEN` / `HALF_OPEN`).
  - **Live Auto-Polling**: 3-second `refetchInterval` with active background synchronization.
- **Synthetic Seed / Demo Data Generator (`POST /api/dev/seed`)**:
  - Gated by `@Profile({"dev", "demo", "default", "local"})` in [DevSeedController](file:///e:/E/Projects/Relay/src/main/java/com/relay/controller/DevSeedController.java).
  - Populates ~50 mixed-state jobs (60% healthy completions, 20% retrying, 20% dead-lettered) with unique timestamped idempotency keys and creates 3 live recurring cron schedules.
- **Explicit State Machine**: Status transitions are governed by [JobStatusTransition](file:///e:/E/Projects/Relay/src/main/java/com/relay/domain/statemachine/JobStatusTransition.java). All status mutations route through `job.transitionTo(newStatus)`, throwing `IllegalJobStateTransitionException` (HTTP 409 Conflict) on invalid transitions (e.g. replaying COMPLETED, cancelling PROCESSING).
- **Horizontal Scaling & Concurrency Safe**:
  - **Pessimistic `FOR UPDATE SKIP LOCKED`**: Both `RetryPollerService` and `OutboxPublisherService` use `SELECT ... FOR UPDATE SKIP LOCKED`. When multiple application instances scale horizontally, poller nodes lock and process mutually exclusive batches without deadlocks, blocking, or duplicate processing.
  - **Optimistic Locking (`@Version`)**: Entity versioning on `Job` provides defense-in-depth against race conditions across concurrent state transitions (e.g. concurrent cancellation vs retry execution).
- **Asynchronous Execution**: Submissions to REST API are published to Kafka partitions (`job-queue`), processed asynchronously by consumer worker threads.
- **Pluggable Job Executors**: Extensible `JobExecutor` interface with dynamic O(1) registry dispatch (`EMAIL_NOTIFICATION`, `REPORT_GENERATION`).
- **Exponential Backoff & State Tracking**: Persistent state in PostgreSQL. When an executor fails, attempt count is incremented, and `nextRetryAt = now + (baseDelay * 2^(attempts-1))` is calculated.
- **Automated Retry Poller**: `@Scheduled` poller queries retryable jobs and creates outbox events to re-enqueue them into Kafka.
- **Dead-Letter Queue (DLQ) & Replay**: When max attempts are exhausted, an outbox event is recorded to publish to `job-dlq` and the job is marked `DEAD_LETTER`. Dead-letter jobs can be replayed with `POST /api/jobs/{id}/replay`.
- **Idempotency Deduplication**: Native deduplication using `idempotencyKey` prevents duplicate job creation and duplicate processing.
- **Recurring Cron Schedules**: Automatic scheduling of recurring tasks from `JobDefinition` templates using Spring `CronExpression`.
- **Structured Logging (MDC)**: Every log message during job processing contains `[jobId=..., attempt=...]` for tracing.
- **Interactive Live Dashboard**: Built-in dark mode web dashboard for monitoring jobs, inspecting audit logs, triggering submissions, and managing cron definitions.

---

## 🏗️ Architecture & Flow (Transactional Outbox)

```mermaid
flowchart TD
    Client["Client / API Caller"] -->|POST /api/jobs| API["REST Controller & JobService"]
    
    subgraph ACID_Tx ["Single PostgreSQL ACID Transaction"]
        API -->|Insert Job (PENDING)| DBJobs[("jobs table")]
        API -->|Insert OutboxEvent (JOB_SUBMITTED)| DBOutbox[("outbox_events table")]
    end
    
    OutboxPub["OutboxPublisherService (@Scheduled)"] -->|Poll published_at IS NULL| DBOutbox
    OutboxPub -->|Kafka send with ACK| KQueue["Kafka Topic: job-queue"]
    OutboxPub -->|Update published_at = NOW()| DBOutbox
    
    KQueue -->|Consume| Consumer["JobConsumer (MDC Tracing)"]
    Consumer --> Registry["JobExecutorRegistry"]
    Registry --> Exec["JobExecutor (Email / Report)"]
    
    Exec -->|Success| Success["Mark COMPLETED\nRecord JobAttempt (SUCCESS)"]
    Success --> DBJobs
    
    Exec -->|Failure & Attempts < Max| Retry["Calculate Backoff\nMark RETRYING\nRecord JobAttempt (FAILURE)"]
    Retry --> DBJobs
    
    Poller["RetryPollerService (@Scheduled)"] -->|Poll RETRYING & nextRetryAt <= now| DBJobs
    Poller -->|Tx: Mark PENDING + Insert Outbox RETRY_REQUEUE| DBOutbox
    
    Exec -->|Failure & Attempts >= Max| DLQTx["Tx: Mark DEAD_LETTER + Insert Outbox DEAD_LETTER"]
    DLQTx --> DBJobs
    DLQTx --> DBOutbox
    OutboxPub -->|Publish DLQ Event| KDLQ["Kafka Topic: job-dlq"]
    
    ReplayAPI["POST /api/jobs/{id}/replay"] -->|Tx: Reset attempts + Insert Outbox JOB_SUBMITTED| DBOutbox
    
    CronPoller["CronJobSchedulerService"] -->|Evaluate Cron Definitions| DBJobs
    CronPoller -->|Spawn Job| API
```

---

## 🚀 Quickstart with Docker Compose

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development)

### 1. Launch All Infrastructure & Services
```bash
docker compose up --build -d
```

This starts:
- **Postgres 16**: Port `5432` (`relay_db`)
- **Apache Kafka (KRaft mode)**: Port `9092` / `9094`
- **Kafdrop UI**: Port `9000` (Browse topics and messages at `http://localhost:9000`)
- **Relay Application**: Port `8080` (Dashboard and REST API at `http://localhost:8080`)

---

## 📡 REST API Reference

### 1. Jobs API

#### Submit a Job
`POST /api/jobs`
```json
{
  "jobType": "EMAIL_NOTIFICATION",
  "payload": {
    "to": "alex@example.com",
    "subject": "System Alert",
    "body": "Daily report is ready."
  },
  "idempotencyKey": "email-alert-4491",
  "maxAttempts": 5
}
```
*Returns `201 Created` for new jobs, or `200 OK` if the `idempotencyKey` already exists (deduplicated).*

#### Get Job Details (with Attempt Audit Trail)
`GET /api/jobs/{id}`

#### List & Filter Jobs (Paginated)
`GET /api/jobs?status=RETRYING&jobType=EMAIL_NOTIFICATION&page=0&size=20`

#### Replay Dead-Letter Job
`POST /api/jobs/{id}/replay`
*Resets attempt count to 0, sets status to PENDING, and republishes to `job-queue`.*

#### Cancel Pending Job
`POST /api/jobs/{id}/cancel`

---

### 2. Job Definitions (Recurring Cron) API

#### Create Recurring Schedule
`POST /api/job-definitions`
```json
{
  "name": "Nightly Financial Report",
  "jobType": "REPORT_GENERATION",
  "cronExpression": "0 0 2 * * *",
  "payloadTemplate": {
    "reportName": "Daily Financials",
    "format": "PDF"
  },
  "enabled": true
}
```

#### List Schedules
`GET /api/job-definitions`

#### Toggle or Update Schedule
`PATCH /api/job-definitions/{id}`
```json
{
  "enabled": false
}
```

---

### 3. Observability & Metrics API

#### Queue Summary Metrics
`GET /api/metrics/summary`
```json
{
  "totalJobs": 1420,
  "pendingJobs": 3,
  "processingJobs": 2,
  "retryingJobs": 5,
  "completedJobs": 1390,
  "deadLetterJobs": 20,
  "cancelledJobs": 0,
  "totalLast24h": 1420,
  "completedLast24h": 1390,
  "deadLetterLast24h": 20,
  "successRatePercentLast24h": 98.58,
  "totalDefinitions": 4,
  "enabledDefinitions": 4,
  "jobsByType": {
    "EMAIL_NOTIFICATION": 850,
    "REPORT_GENERATION": 570
  }
}
```

---

## 🧪 Testing

### Running Unit & Core Logic Tests
```bash
./mvnw test -Dtest="*Test,!*IntegrationTest"
```

### Running Testcontainers Integration Tests (Requires Docker)
```bash
./mvnw test
```

---

## 💻 Tech Stack Summary

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.3.5, Java 17/21 |
| Messaging | Spring Kafka, Apache Kafka 3.7 (KRaft) |
| Persistence | Spring Data JPA, PostgreSQL 16 (JSONB), Flyway |
| Testing | JUnit 5, Mockito, AssertJ, Awaitility, Testcontainers |
| Frontend | Vanilla JS, Modern Dark Theme CSS, Responsive Grid |

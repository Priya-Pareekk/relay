package com.relay.service;

import com.relay.domain.entity.Job;
import com.relay.domain.entity.JobAttempt;
import com.relay.domain.enums.AttemptStatus;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.OutboxEventType;
import com.relay.dto.DeadLetterJobMessage;
import com.relay.dto.KafkaJobMessage;
import com.relay.executor.JobExecutor;
import com.relay.executor.JobExecutorRegistry;
import com.relay.repository.JobAttemptRepository;
import com.relay.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobProcessingService {

    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final JobExecutorRegistry executorRegistry;
    private final OutboxService outboxService;
    private final io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;
    private final com.relay.policy.RetryPolicy retryPolicy;

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Value("${relay.topics.job-dlq:job-dlq}")
    private String dlqTopic;

    @Value("${relay.retry.circuit-breaker-delay-seconds:60}")
    private long circuitBreakerDelaySeconds;

    @Transactional
    public void processJob(UUID jobId, int incomingAttemptNumber) {
        Optional<Job> optionalJob = jobRepository.findById(jobId);
        if (optionalJob.isEmpty()) {
            log.warn("Job not found for processing: jobId={}. Acknowledging message without action.", jobId);
            return;
        }

        Job job = optionalJob.get();

        // Idempotency Check: Verify job status is still eligible for execution
        if (job.getStatus() == JobStatus.COMPLETED) {
            log.info("Idempotent check: Job {} is already COMPLETED. Acknowledging duplicate/redelivered message without re-executing.", jobId);
            return;
        }

        if (job.getStatus() == JobStatus.CANCELLED) {
            log.info("Idempotent check: Job {} is CANCELLED. Acknowledging message and skipping execution.", jobId);
            return;
        }

        if (job.getStatus() == JobStatus.DEAD_LETTER) {
            log.info("Idempotent check: Job {} is in DEAD_LETTER state. Acknowledging message without execution (replay required).", jobId);
            return;
        }

        // Check if an older/stale attempt message was redelivered while job already progressed
        if (job.getStatus() == JobStatus.PROCESSING && incomingAttemptNumber <= job.getAttemptCount()) {
            log.info("Idempotent check: Stale message for jobId={} (incoming attempt {} <= current attempt {}). Skipping duplicate processing.",
                    jobId, incomingAttemptNumber, job.getAttemptCount());
            return;
        }

        // Job must be in PENDING or RETRYING state to transition to PROCESSING
        if (job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.RETRYING) {
            log.warn("Job {} has ineligible status {} for processing. Skipping execution.", jobId, job.getStatus());
            return;
        }

        // Circuit Breaker Check per Executor
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker(job.getJobType().name());

        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("Circuit breaker for executor {} is OPEN. Postponing jobId={} with {}s delay without burning attempts.",
                    job.getJobType(), jobId, circuitBreakerDelaySeconds);

            job.transitionTo(JobStatus.RETRYING);
            job.setNextRetryAt(Instant.now().plusSeconds(circuitBreakerDelaySeconds));
            job.setLastError("Circuit breaker is OPEN for executor: " + job.getJobType());
            jobRepository.save(job);

            KafkaJobMessage retryMessage = KafkaJobMessage.builder()
                    .jobId(job.getId())
                    .jobType(job.getJobType())
                    .attemptNumber(job.getAttemptCount() + 1)
                    .payload(job.getPayload())
                    .idempotencyKey(job.getIdempotencyKey())
                    .build();

            String partitionKey = job.getIdempotencyKey() != null
                    ? job.getIdempotencyKey()
                    : job.getJobType().name();

            outboxService.saveEvent(
                    "JOB",
                    job.getId(),
                    OutboxEventType.JOB_RETRY,
                    jobTopic,
                    partitionKey,
                    retryMessage
            );
            return;
        }

        int currentAttemptNumber = job.getAttemptCount() + 1;
        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();

        // Transition to PROCESSING through the explicit state machine
        job.transitionTo(JobStatus.PROCESSING);
        job.setAttemptCount(currentAttemptNumber);
        jobRepository.saveAndFlush(job);

        log.info("Starting processing for jobId={}, type={}, attempt={}/{}",
                job.getId(), job.getJobType(), currentAttemptNumber, job.getMaxAttempts());

        JobAttempt attempt = JobAttempt.builder()
                .job(job)
                .attemptNumber(currentAttemptNumber)
                .startedAt(startedAt)
                .build();

        try {
            JobExecutor executor = executorRegistry.getExecutor(job.getJobType());
            executor.execute(job);

            long durationNanos = System.nanoTime() - startNanos;
            circuitBreaker.onSuccess(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

            // Handle success
            Instant finishedAt = Instant.now();
            attempt.setFinishedAt(finishedAt);
            attempt.setStatus(AttemptStatus.SUCCESS);
            attempt.setErrorMessage(null);
            jobAttemptRepository.save(attempt);

            job.transitionTo(JobStatus.COMPLETED);
            job.setNextRetryAt(null);
            job.setLastError(null);
            jobRepository.save(job);

            // Save COMPLETED OutboxEvent in the same transaction
            outboxService.saveEvent(
                    "JOB",
                    job.getId(),
                    OutboxEventType.JOB_COMPLETED,
                    jobTopic,
                    job.getJobType().name(),
                    Map.of(
                            "jobId", job.getId().toString(),
                            "jobType", job.getJobType().name(),
                            "status", "COMPLETED",
                            "attemptNumber", currentAttemptNumber,
                            "completedAt", finishedAt.toString()
                    )
            );

            log.info("Job {} successfully completed on attempt {} in {} ms",
                    jobId, currentAttemptNumber, finishedAt.toEpochMilli() - startedAt.toEpochMilli());

        } catch (Exception ex) {
            long durationNanos = System.nanoTime() - startNanos;
            circuitBreaker.onError(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS, ex);

            Instant finishedAt = Instant.now();
            String errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

            log.warn("Execution failed for jobId={} on attempt {}: {}", jobId, currentAttemptNumber, errorMessage);

            attempt.setFinishedAt(finishedAt);
            attempt.setStatus(AttemptStatus.FAILURE);
            attempt.setErrorMessage(errorMessage);
            jobAttemptRepository.save(attempt);

            job.setLastError(errorMessage);

            if (currentAttemptNumber >= job.getMaxAttempts()) {
                // Max attempts exhausted -> Move to DLQ
                job.transitionTo(JobStatus.DEAD_LETTER);
                job.setNextRetryAt(null);
                jobRepository.save(job);

                log.error("Job {} exceeded max attempts ({}/{}). Moving to DEAD_LETTER.",
                        jobId, currentAttemptNumber, job.getMaxAttempts());

                DeadLetterJobMessage dlqMessage = DeadLetterJobMessage.builder()
                        .jobId(job.getId())
                        .jobType(job.getJobType())
                        .attemptNumber(currentAttemptNumber)
                        .payload(job.getPayload())
                        .deadLetterReason("Exceeded maximum retry attempts (" + job.getMaxAttempts() + ")")
                        .lastError(errorMessage)
                        .deadLetteredAt(Instant.now())
                        .build();

                // Save DEAD_LETTER OutboxEvent in the same transaction
                outboxService.saveEvent(
                        "JOB",
                        job.getId(),
                        OutboxEventType.DEAD_LETTER,
                        dlqTopic,
                        job.getJobType().name(),
                        dlqMessage
                );
            } else {
                // Calculate backoff using injected RetryPolicy strategy
                long delaySeconds = retryPolicy.calculateDelaySeconds(currentAttemptNumber);
                Instant nextRetryAt = Instant.now().plusSeconds(delaySeconds);

                job.transitionTo(JobStatus.RETRYING);
                job.setNextRetryAt(nextRetryAt);
                jobRepository.save(job);

                KafkaJobMessage retryMessage = KafkaJobMessage.builder()
                        .jobId(job.getId())
                        .jobType(job.getJobType())
                        .attemptNumber(currentAttemptNumber + 1)
                        .payload(job.getPayload())
                        .idempotencyKey(job.getIdempotencyKey())
                        .build();

                String partitionKey = job.getIdempotencyKey() != null
                        ? job.getIdempotencyKey()
                        : job.getJobType().name();

                // Save RETRYING OutboxEvent in the same transaction
                outboxService.saveEvent(
                        "JOB",
                        job.getId(),
                        OutboxEventType.JOB_RETRY,
                        jobTopic,
                        partitionKey,
                        retryMessage
                );

                log.info("Job {} scheduled for retry (attempt {}/{}) at {} (delay {}s)",
                        jobId, currentAttemptNumber + 1, job.getMaxAttempts(), nextRetryAt, delaySeconds);
            }
        }
    }
}


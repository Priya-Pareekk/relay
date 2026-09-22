package com.relay.service;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.AttemptStatus;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.domain.enums.OutboxEventType;
import com.relay.exception.JobExecutionException;
import com.relay.executor.EmailNotificationExecutor;
import com.relay.executor.JobExecutorRegistry;
import com.relay.repository.JobAttemptRepository;
import com.relay.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobProcessingServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobAttemptRepository jobAttemptRepository;

    @Mock
    private JobExecutorRegistry executorRegistry;

    @Mock
    private OutboxService outboxService;

    @Mock
    private EmailNotificationExecutor emailExecutor;

    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    private JobProcessingService processingService;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults();
        com.relay.policy.RetryPolicy retryPolicy = new com.relay.policy.ExponentialBackoffRetryPolicy(5L, 900L);
        processingService = new JobProcessingService(
                jobRepository,
                jobAttemptRepository,
                executorRegistry,
                outboxService,
                circuitBreakerRegistry,
                retryPolicy
        );
        ReflectionTestUtils.setField(processingService, "dlqTopic", "job-dlq");
        ReflectionTestUtils.setField(processingService, "circuitBreakerDelaySeconds", 60L);
    }

    @Test
    @DisplayName("Should successfully process job, record SUCCESS attempt, and mark job COMPLETED")
    void testProcessJobSuccess() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com"))
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(5)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(executorRegistry.getExecutor(JobType.EMAIL_NOTIFICATION)).thenReturn(emailExecutor);

        processingService.processJob(jobId, 1);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastError()).isNull();

        verify(jobAttemptRepository).save(argThat(attempt ->
                attempt.getStatus() == AttemptStatus.SUCCESS && attempt.getAttemptNumber() == 1
        ));
        verify(jobRepository, atLeastOnce()).save(job);
        verify(outboxService).saveEvent(
                eq("JOB"),
                eq(jobId),
                eq(OutboxEventType.JOB_COMPLETED),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should schedule retry with exponential backoff on failure when attempts < maxAttempts and save outbox event")
    void testProcessJobFailureTriggersRetry() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "fail@error.com"))
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(executorRegistry.getExecutor(JobType.EMAIL_NOTIFICATION)).thenReturn(emailExecutor);
        doThrow(new JobExecutionException("Connection refused")).when(emailExecutor).execute(any());

        processingService.processJob(jobId, 1);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastError()).isEqualTo("Connection refused");
        assertThat(job.getNextRetryAt()).isNotNull();

        verify(jobAttemptRepository).save(argThat(attempt ->
                attempt.getStatus() == AttemptStatus.FAILURE && attempt.getErrorMessage().contains("Connection refused")
        ));
        verify(outboxService).saveEvent(
                eq("JOB"),
                eq(jobId),
                eq(OutboxEventType.JOB_RETRY),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should move job to DEAD_LETTER and save DLQ event to Transactional Outbox when attempts reach maxAttempts")
    void testProcessJobExhaustedRetriesMovesToDeadLetter() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "fail@error.com"))
                .status(JobStatus.RETRYING)
                .attemptCount(2) // already had 2 attempts, this will be 3rd
                .maxAttempts(3)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(executorRegistry.getExecutor(JobType.EMAIL_NOTIFICATION)).thenReturn(emailExecutor);
        doThrow(new JobExecutionException("Persistent failure")).when(emailExecutor).execute(any());

        processingService.processJob(jobId, 3);

        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
        assertThat(job.getAttemptCount()).isEqualTo(3);
        assertThat(job.getNextRetryAt()).isNull();

        verify(outboxService).saveEvent(
                eq("JOB"),
                eq(jobId),
                eq(OutboxEventType.DEAD_LETTER),
                eq("job-dlq"),
                eq(JobType.EMAIL_NOTIFICATION.name()),
                any()
        );
    }

    @Test
    @DisplayName("Idempotent check: Redelivered message for COMPLETED job must be skipped without re-execution")
    void testProcessJobSkipsWhenAlreadyCompleted() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com"))
                .status(JobStatus.COMPLETED)
                .attemptCount(1)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        processingService.processJob(jobId, 1);

        verify(executorRegistry, never()).getExecutor(any());
        verify(jobAttemptRepository, never()).save(any());
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("Idempotent check: Message for CANCELLED job must be skipped without execution")
    void testProcessJobSkipsWhenCancelled() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com"))
                .status(JobStatus.CANCELLED)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        processingService.processJob(jobId, 1);

        verify(executorRegistry, never()).getExecutor(any());
        verify(jobAttemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotent check: Message for DEAD_LETTER job must be skipped without execution")
    void testProcessJobSkipsWhenDeadLetter() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com"))
                .status(JobStatus.DEAD_LETTER)
                .attemptCount(5)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        processingService.processJob(jobId, 5);

        verify(executorRegistry, never()).getExecutor(any());
        verify(jobAttemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("Circuit Breaker check: When circuit is OPEN, job is moved to RETRYING with circuit delay without burning attempts")
    void testProcessJobWhenCircuitBreakerIsOpenDefersJobWithoutBurningAttempts() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com"))
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(5)
                .build();

        // Force circuit breaker into OPEN state
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
                circuitBreakerRegistry.circuitBreaker(JobType.EMAIL_NOTIFICATION.name());
        cb.transitionToOpenState();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        processingService.processJob(jobId, 1);

        // Verify executor is never called and attempts table is untouched
        verify(executorRegistry, never()).getExecutor(any());
        verify(jobAttemptRepository, never()).save(any());

        // Verify job state is RETRYING with nextRetryAt set and attemptCount unchanged (0)
        assertThat(job.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(job.getAttemptCount()).isEqualTo(0);
        assertThat(job.getNextRetryAt()).isNotNull();
        assertThat(job.getLastError()).contains("Circuit breaker is OPEN for executor: EMAIL_NOTIFICATION");

        verify(jobRepository).save(job);
    }
}

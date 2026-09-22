package com.relay.service;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.domain.enums.OutboxEventType;
import com.relay.dto.JobResponse;
import com.relay.dto.JobSubmissionRequest;
import com.relay.exception.InvalidJobStateException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private OutboxService outboxService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, outboxService);
        ReflectionTestUtils.setField(jobService, "jobTopic", "job-queue");
    }

    @Test
    @DisplayName("Should create new job and save event to Transactional Outbox for new submission")
    void testSubmitJobNew() {
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "user@test.com"))
                .idempotencyKey("test-key-1")
                .maxAttempts(4)
                .build();

        when(jobRepository.findByIdempotencyKey("test-key-1")).thenReturn(Optional.empty());
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job j = invocation.getArgument(0);
            j.setId(UUID.randomUUID());
            return j;
        });

        JobService.JobSubmissionResult result = jobService.submitJob(request);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getJobResponse().getId()).isNotNull();
        assertThat(result.getJobResponse().getMaxAttempts()).isEqualTo(4);

        verify(outboxService).saveEvent(
                eq(result.getJobResponse().getId()),
                eq(OutboxEventType.JOB_SUBMITTED),
                eq("job-queue"),
                eq("test-key-1"),
                any()
        );
    }

    @Test
    @DisplayName("Should return existing job without creating duplicate when idempotencyKey exists")
    void testSubmitJobDuplicateIdempotencyKey() {
        UUID existingId = UUID.randomUUID();
        Job existingJob = Job.builder()
                .id(existingId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "user@test.com"))
                .status(JobStatus.COMPLETED)
                .idempotencyKey("duplicate-key")
                .build();

        when(jobRepository.findByIdempotencyKey("duplicate-key")).thenReturn(Optional.of(existingJob));

        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "user@test.com"))
                .idempotencyKey("duplicate-key")
                .build();

        JobService.JobSubmissionResult result = jobService.submitJob(request);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getJobResponse().getId()).isEqualTo(existingId);
        verify(jobRepository, never()).save(any());
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should replay DEAD_LETTER job by resetting attemptCount to 0, status to PENDING, and saving outbox event")
    void testReplayDeadLetterJob() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.REPORT_GENERATION)
                .payload(Map.of("reportName", "Finance"))
                .status(JobStatus.DEAD_LETTER)
                .attemptCount(5)
                .lastError("Previous error")
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = jobService.replayJob(jobId);

        assertThat(response.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(response.getAttemptCount()).isEqualTo(0);
        assertThat(response.getLastError()).isNull();
        verify(outboxService).saveEvent(
                eq(jobId),
                eq(OutboxEventType.JOB_SUBMITTED),
                eq("job-queue"),
                eq(JobType.REPORT_GENERATION.name()),
                any()
        );
    }

    @Test
    @DisplayName("Should throw IllegalJobStateTransitionException when attempting to replay a non-DEAD_LETTER job")
    void testReplayNonDeadLetterJobThrows() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .status(JobStatus.COMPLETED)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.replayJob(jobId))
                .isInstanceOf(com.relay.exception.IllegalJobStateTransitionException.class)
                .hasMessageContaining("Illegal job status transition from COMPLETED to PENDING");
    }

    @Test
    @DisplayName("Should throw IllegalJobStateTransitionException when attempting to cancel a COMPLETED job")
    void testCancelCompletedJobThrows() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .status(JobStatus.COMPLETED)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancelJob(jobId))
                .isInstanceOf(com.relay.exception.IllegalJobStateTransitionException.class)
                .hasMessageContaining("Illegal job status transition from COMPLETED to CANCELLED");
    }

    @Test
    @DisplayName("Should cancel PENDING job")
    void testCancelPendingJob() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .status(JobStatus.PENDING)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = jobService.cancelJob(jobId);
        assertThat(response.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should return existing DEAD_LETTER job as-is without auto-replaying when idempotencyKey matches")
    void testSubmitJobWithDeadLetterIdempotencyKeyReturnsDeadLetterAsIs() {
        UUID jobId = UUID.randomUUID();
        Job existingDeadLetterJob = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "user@domain.com"))
                .status(JobStatus.DEAD_LETTER)
                .attemptCount(3)
                .maxAttempts(3)
                .idempotencyKey("dead-key-123")
                .lastError("Persistent connection failure")
                .build();

        when(jobRepository.findByIdempotencyKey("dead-key-123")).thenReturn(Optional.of(existingDeadLetterJob));

        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "user@domain.com"))
                .idempotencyKey("dead-key-123")
                .build();

        JobService.JobSubmissionResult result = jobService.submitJob(request);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getJobResponse().getId()).isEqualTo(jobId);
        assertThat(result.getJobResponse().getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
        assertThat(result.getJobResponse().getAttemptCount()).isEqualTo(3);

        // Assert no new job saved and no outbox publish was triggered
        verify(jobRepository, never()).save(any());
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject job submission with maxAttempts < 1")
    void testSubmitJobWithInvalidMaxAttemptsThrows() {
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "user@domain.com"))
                .maxAttempts(0)
                .build();

        assertThatThrownBy(() -> jobService.submitJob(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts must be at least 1");
    }
}

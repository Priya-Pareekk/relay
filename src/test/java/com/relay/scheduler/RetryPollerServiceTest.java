package com.relay.scheduler;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.domain.enums.OutboxEventType;
import com.relay.repository.JobRepository;
import com.relay.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryPollerServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private OutboxService outboxService;

    private RetryPollerService retryPollerService;

    @BeforeEach
    void setUp() {
        retryPollerService = new RetryPollerService(jobRepository, outboxService);
        ReflectionTestUtils.setField(retryPollerService, "jobTopic", "job-queue");
        ReflectionTestUtils.setField(retryPollerService, "batchSize", 50);
    }

    @Test
    @DisplayName("Should fetch retryable jobs with SKIP LOCKED, transition to PENDING, and save RETRY_REQUEUE to outbox")
    void testPollAndRequeueRetries() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "retry@domain.com"))
                .status(JobStatus.RETRYING)
                .attemptCount(1)
                .maxAttempts(3)
                .nextRetryAt(Instant.now().minusSeconds(10))
                .build();

        when(jobRepository.findRetryableJobs(eq(JobStatus.RETRYING), any(), any()))
                .thenReturn(List.of(job));

        List<UUID> processed = retryPollerService.pollAndRequeueRetries();

        assertThat(processed).containsExactly(jobId);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);

        verify(jobRepository).save(job);
        verify(outboxService).saveEvent(
                eq(jobId),
                eq(OutboxEventType.RETRY_REQUEUE),
                eq("job-queue"),
                eq(JobType.EMAIL_NOTIFICATION.name()),
                any()
        );
    }

    @Test
    @DisplayName("Should return empty list when no retryable jobs are available")
    void testPollAndRequeueRetriesEmpty() {
        when(jobRepository.findRetryableJobs(eq(JobStatus.RETRYING), any(), any()))
                .thenReturn(List.of());

        List<UUID> processed = retryPollerService.pollAndRequeueRetries();

        assertThat(processed).isEmpty();
        verify(jobRepository, never()).save(any());
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
    }
}

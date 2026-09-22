package com.relay;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobSubmissionRequest;
import com.relay.repository.JobRepository;
import com.relay.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class JobDeadLetterQueueIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("Should transition to DEAD_LETTER after exhausting max attempts and publish to DLQ")
    void testJobExhaustsRetriesToDeadLetter() {
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.REPORT_GENERATION)
                .payload(Map.of(
                        "reportName", "Failing Report",
                        "reportType", "INVALID",
                        "fail", true
                ))
                .maxAttempts(2) // Low max attempts to quickly exhaust retries
                .build();

        JobService.JobSubmissionResult result = jobService.submitJob(request);

        // Allow retry poller and consumer to exhaust 2 attempts
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByIdWithAttempts(result.getJobResponse().getId()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
                    assertThat(job.getAttemptCount()).isGreaterThanOrEqualTo(2);
                    assertThat(job.getNextRetryAt()).isNull();
                    assertThat(job.getLastError()).contains("Simulated generator failure");
                    assertThat(job.getAttempts().size()).isGreaterThanOrEqualTo(2);
                });
    }
}

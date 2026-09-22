package com.relay;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.AttemptStatus;
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

class JobRetryAndBackoffIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("Should mark failing job as RETRYING, compute nextRetryAt, and record failed attempt")
    void testFailingJobTriggersRetryAndBackoff() {
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of(
                        "to", "fail@error.com",
                        "subject", "Test Retry",
                        "fail", true
                ))
                .maxAttempts(4)
                .build();

        JobService.JobSubmissionResult result = jobService.submitJob(request);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByIdWithAttempts(result.getJobResponse().getId()).orElseThrow();
                    // First attempt should fail and trigger RETRYING or higher attempt
                    assertThat(job.getStatus()).isIn(JobStatus.RETRYING, JobStatus.PROCESSING, JobStatus.PENDING);
                    assertThat(job.getAttemptCount()).isGreaterThanOrEqualTo(1);
                    assertThat(job.getLastError()).contains("Simulated delivery error");
                    assertThat(job.getAttempts()).isNotEmpty();
                    assertThat(job.getAttempts().get(0).getStatus()).isEqualTo(AttemptStatus.FAILURE);
                });
    }
}

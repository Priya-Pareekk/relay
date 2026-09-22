package com.relay;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.AttemptStatus;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobResponse;
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

class JobSubmissionAndExecutionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("Should successfully submit and asynchronously execute email job to COMPLETED status")
    void testSuccessfulJobSubmissionAndExecution() {
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of(
                        "to", "user@example.com",
                        "subject", "Welcome to Relay",
                        "body", "Your task queue is live!"
                ))
                .build();

        JobService.JobSubmissionResult result = jobService.submitJob(request);
        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getJobResponse().getId()).isNotNull();

        // Await asynchronous processing via Kafka consumer
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByIdWithAttempts(result.getJobResponse().getId()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
                    assertThat(job.getAttemptCount()).isEqualTo(1);
                    assertThat(job.getAttempts()).hasSize(1);
                    assertThat(job.getAttempts().get(0).getStatus()).isEqualTo(AttemptStatus.SUCCESS);
                    assertThat(job.getAttempts().get(0).getErrorMessage()).isNull();
                });
    }

    @Test
    @DisplayName("Should successfully process report generation job")
    void testSuccessfulReportGenerationExecution() {
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .jobType(JobType.REPORT_GENERATION)
                .payload(Map.of(
                        "reportName", "Monthly Activity Summary",
                        "format", "PDF",
                        "month", "August"
                ))
                .build();

        JobService.JobSubmissionResult result = jobService.submitJob(request);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByIdWithAttempts(result.getJobResponse().getId()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
                    assertThat(job.getAttemptCount()).isEqualTo(1);
                    assertThat(job.getAttempts()).isNotEmpty();
                });
    }
}

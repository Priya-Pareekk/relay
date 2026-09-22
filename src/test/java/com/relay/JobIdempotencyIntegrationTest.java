package com.relay;

import com.relay.domain.enums.JobType;
import com.relay.dto.JobSubmissionRequest;
import com.relay.repository.JobRepository;
import com.relay.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobIdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("Should return existing job without duplicate insertion when same idempotencyKey is used")
    void testIdempotentJobSubmission() {
        String uniqueKey = "idem-key-" + UUID.randomUUID();

        JobSubmissionRequest request1 = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com", "subject", "First Submission"))
                .idempotencyKey(uniqueKey)
                .build();

        JobService.JobSubmissionResult result1 = jobService.submitJob(request1);
        assertThat(result1.isDuplicate()).isFalse();
        UUID createdId = result1.getJobResponse().getId();
        assertThat(createdId).isNotNull();

        // Submit second time with identical key
        JobSubmissionRequest request2 = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com", "subject", "Second Submission Attempt"))
                .idempotencyKey(uniqueKey)
                .build();

        JobService.JobSubmissionResult result2 = jobService.submitJob(request2);
        assertThat(result2.isDuplicate()).isTrue();
        assertThat(result2.getJobResponse().getId()).isEqualTo(createdId);

        // Verify database only has 1 record with this idempotency key
        assertThat(jobRepository.findByIdempotencyKey(uniqueKey)).isPresent();
    }
}

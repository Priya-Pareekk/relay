package com.relay;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobResponse;
import com.relay.repository.JobRepository;
import com.relay.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class JobReplayIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("Should successfully replay a DEAD_LETTER job and execute it to COMPLETED")
    void testReplayDeadLetterJob() {
        // Manually seed a DEAD_LETTER job that has payload capable of succeeding
        Job deadLetterJob = Job.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "replay-success@example.com", "subject", "Replayed Email"))
                .status(JobStatus.DEAD_LETTER)
                .attemptCount(5)
                .maxAttempts(5)
                .lastError("Previous simulated failure")
                .build();

        Job saved = jobRepository.saveAndFlush(deadLetterJob);

        // Replay job
        JobResponse replayed = jobService.replayJob(saved.getId());
        assertThat(replayed.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(replayed.getAttemptCount()).isEqualTo(0);

        // Consumer should pick it up and process to COMPLETED
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByIdWithAttempts(saved.getId()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
                    assertThat(job.getAttemptCount()).isEqualTo(1);
                    assertThat(job.getLastError()).isNull();
                });
    }
}

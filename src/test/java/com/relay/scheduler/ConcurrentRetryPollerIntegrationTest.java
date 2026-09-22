package com.relay.scheduler;

import com.relay.BaseIntegrationTest;
import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.domain.enums.OutboxEventType;
import com.relay.repository.JobRepository;
import com.relay.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "relay.retry.poller-interval-ms=600000"
})
class ConcurrentRetryPollerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RetryPollerService retryPollerService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("Should use SELECT ... FOR UPDATE SKIP LOCKED so concurrent poller instances never grab the same RETRYING rows")
    void testConcurrentRetryPollersDoNotDuplicateJobs() throws Exception {
        // 1. Seed 10 jobs in RETRYING state with nextRetryAt in the past
        int totalJobs = 10;
        List<UUID> seededJobIds = new ArrayList<>();
        Instant past = Instant.now().minusSeconds(30);

        for (int i = 0; i < totalJobs; i++) {
            Job job = Job.builder()
                    .jobType(JobType.EMAIL_NOTIFICATION)
                    .payload(Map.of("to", "user" + i + "@example.com", "index", i))
                    .status(JobStatus.RETRYING)
                    .attemptCount(1)
                    .maxAttempts(3)
                    .nextRetryAt(past)
                    .build();
            Job saved = jobRepository.save(job);
            seededJobIds.add(saved.getId());
        }

        // 2. Prepare two concurrent poller executions started at the exact same instant
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<List<UUID>> poller1Future = executor.submit(() -> {
            startLatch.await();
            return retryPollerService.pollAndRequeueRetries();
        });

        Future<List<UUID>> poller2Future = executor.submit(() -> {
            startLatch.await();
            return retryPollerService.pollAndRequeueRetries();
        });

        // Trigger both pollers simultaneously
        startLatch.countDown();

        List<UUID> poller1Processed = poller1Future.get(10, TimeUnit.SECONDS);
        List<UUID> poller2Processed = poller2Future.get(10, TimeUnit.SECONDS);

        executor.shutdown();

        // 3. Assertions:
        // A) Disjoint check: No job was picked up by both poller instances (FOR UPDATE SKIP LOCKED guarantees mutual exclusion)
        Set<UUID> intersection = new HashSet<>(poller1Processed);
        intersection.retainAll(poller2Processed);
        assertThat(intersection)
                .as("Poller 1 and Poller 2 must not process any overlapping jobs")
                .isEmpty();

        // B) Completeness check: The combined set of processed jobs covers all seeded jobs
        Set<UUID> union = new HashSet<>();
        union.addAll(poller1Processed);
        union.addAll(poller2Processed);
        assertThat(union)
                .as("All seeded retryable jobs must be processed between the concurrent pollers")
                .containsAll(seededJobIds);

        // C) Database verification: Every seeded job is now PENDING (none remained in RETRYING)
        for (UUID jobId : seededJobIds) {
            Job job = jobRepository.findById(jobId).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        }

        // D) Outbox verification: Exactly 1 RETRY_REQUEUE outbox event per seeded job
        long outboxEventCount = outboxEventRepository.findAll().stream()
                .filter(e -> seededJobIds.contains(e.getAggregateId()) && e.getEventType() == OutboxEventType.RETRY_REQUEUE)
                .count();

        assertThat(outboxEventCount)
                .as("Exactly one OutboxEvent must be written per retryable job")
                .isEqualTo(totalJobs);
    }
}

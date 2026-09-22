package com.relay.scheduler;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.OutboxEventType;
import com.relay.dto.KafkaJobMessage;
import com.relay.repository.JobRepository;
import com.relay.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryPollerService {

    private final JobRepository jobRepository;
    private final OutboxService outboxService;

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Value("${relay.retry.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${relay.retry.poller-interval-ms:5000}")
    @Transactional
    public List<UUID> pollAndRequeueRetries() {
        Instant now = Instant.now();
        List<Job> retryableJobs = jobRepository.findRetryableJobs(
                JobStatus.RETRYING,
                now,
                PageRequest.of(0, batchSize)
        );

        if (retryableJobs.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Found {} retryable job(s) due for re-queuing with FOR UPDATE SKIP LOCKED", retryableJobs.size());
        List<UUID> requeuedJobIds = new ArrayList<>();

        for (Job job : retryableJobs) {
            String correlationId = UUID.randomUUID().toString();
            try {
                org.slf4j.MDC.put("correlationId", correlationId);
                org.slf4j.MDC.put("jobId", job.getId().toString());

                // Transition to PENDING to avoid duplicate polling before consume
                job.transitionTo(JobStatus.PENDING);
                jobRepository.save(job);

                KafkaJobMessage message = KafkaJobMessage.builder()
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
                        job.getId(),
                        OutboxEventType.RETRY_REQUEUE,
                        jobTopic,
                        partitionKey,
                        message
                );

                requeuedJobIds.add(job.getId());
                log.info("Re-queued retryable jobId={} for attempt {} via Transactional Outbox (correlationId={})",
                        job.getId(), job.getAttemptCount() + 1, correlationId);
            } catch (Exception e) {
                log.error("Failed to re-queue retryable jobId={}: {}", job.getId(), e.getMessage(), e);
            } finally {
                org.slf4j.MDC.clear();
            }
        }
        return requeuedJobIds;

    }
}



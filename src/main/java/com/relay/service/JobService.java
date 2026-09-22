package com.relay.service;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobResponse;
import com.relay.dto.JobSubmissionRequest;
import com.relay.dto.KafkaJobMessage;
import com.relay.exception.InvalidJobStateException;
import com.relay.exception.ResourceNotFoundException;
import com.relay.mapper.JobMapper;
import com.relay.producer.JobProducer;
import com.relay.repository.JobRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final OutboxService outboxService;

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Getter
    @RequiredArgsConstructor
    public static class JobSubmissionResult {
        private final JobResponse jobResponse;
        private final boolean duplicate;
    }

    @Transactional
    public JobSubmissionResult submitJob(JobSubmissionRequest request) {
        // Check idempotency key deduplication
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Job> existingJob = jobRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingJob.isPresent()) {
                Job job = existingJob.get();
                if (job.getStatus() == JobStatus.DEAD_LETTER) {
                    // DESIGN DECISION: A new submission matching a DEAD_LETTER idempotency key returns
                    // the existing dead-letter job as-is (duplicate=true). It does NOT auto-replay.
                    // The caller must inspect status==DEAD_LETTER and explicitly call
                    // POST /api/jobs/{id}/replay to retry. This avoids silent side-effects and
                    // preserves the requirement that replay is always an intentional user action.
                    log.warn("Idempotent submission matched a DEAD_LETTER job: id={}, key={}. " +
                                    "Returning dead-letter job as-is. Use POST /api/jobs/{}/replay to retry.",
                            job.getId(), request.getIdempotencyKey(), job.getId());
                } else {
                    log.info("Idempotent request detected. Returning existing job: id={}, key={}",
                            job.getId(), request.getIdempotencyKey());
                }
                return new JobSubmissionResult(JobMapper.toJobResponse(job), true);
            }
        }

        if (request.getMaxAttempts() != null && request.getMaxAttempts() < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        int maxAttempts = request.getMaxAttempts() != null ? request.getMaxAttempts() : 5;

        Job job = Job.builder()
                .jobType(request.getJobType())
                .payload(request.getPayload())
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Job savedJob = jobRepository.save(job);
        log.info("Created new job: id={}, type={}, idempotencyKey={}",
                savedJob.getId(), savedJob.getJobType(), savedJob.getIdempotencyKey());

        // Prepare message and save to Transactional Outbox
        KafkaJobMessage message = KafkaJobMessage.builder()
                .jobId(savedJob.getId())
                .jobType(savedJob.getJobType())
                .attemptNumber(1)
                .payload(savedJob.getPayload())
                .idempotencyKey(savedJob.getIdempotencyKey())
                .build();

        String partitionKey = savedJob.getIdempotencyKey() != null
                ? savedJob.getIdempotencyKey()
                : savedJob.getJobType().name();

        outboxService.saveEvent(
                savedJob.getId(),
                com.relay.domain.enums.OutboxEventType.JOB_SUBMITTED,
                jobTopic,
                partitionKey,
                message
        );

        return new JobSubmissionResult(JobMapper.toJobResponse(savedJob), false);
    }

    @Transactional(readOnly = true)
    public JobResponse getJobById(UUID id) {
        Job job = jobRepository.findByIdWithAttempts(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));
        return JobMapper.toJobResponse(job);
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> getJobs(JobStatus status, JobType jobType, Pageable pageable) {
        Specification<Job> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (jobType != null) {
                predicates.add(cb.equal(root.get("jobType"), jobType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return jobRepository.findAll(spec, pageable).map(JobMapper::toJobResponse);
    }

    @Transactional
    public JobResponse replayJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));

        // State machine validates transition from current status to PENDING
        job.transitionTo(JobStatus.PENDING);
        job.setAttemptCount(0);
        job.setNextRetryAt(null);
        job.setLastError(null);
        Job savedJob = jobRepository.save(job);

        log.info("Replaying dead-letter job: id={}", id);

        KafkaJobMessage message = KafkaJobMessage.builder()
                .jobId(savedJob.getId())
                .jobType(savedJob.getJobType())
                .attemptNumber(1)
                .payload(savedJob.getPayload())
                .idempotencyKey(savedJob.getIdempotencyKey())
                .build();

        String partitionKey = savedJob.getIdempotencyKey() != null
                ? savedJob.getIdempotencyKey()
                : savedJob.getJobType().name();

        outboxService.saveEvent(
                savedJob.getId(),
                com.relay.domain.enums.OutboxEventType.JOB_SUBMITTED,
                jobTopic,
                partitionKey,
                message
        );

        return JobMapper.toJobResponse(savedJob);
    }

    @Transactional
    public JobResponse cancelJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));

        // State machine validates transition from current status to CANCELLED
        job.transitionTo(JobStatus.CANCELLED);
        job.setNextRetryAt(null);
        Job savedJob = jobRepository.save(job);

        log.info("Cancelled job: id={}, newStatus={}", id, job.getStatus());
        return JobMapper.toJobResponse(savedJob);
    }
}

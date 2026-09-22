package com.relay.scheduler;

import com.relay.domain.entity.Job;
import com.relay.domain.entity.JobArchive;
import com.relay.domain.entity.JobAttempt;
import com.relay.domain.entity.JobAttemptArchive;
import com.relay.domain.enums.JobStatus;
import com.relay.repository.JobArchiveRepository;
import com.relay.repository.JobRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobArchiverService {

    private final JobRepository jobRepository;
    private final JobArchiveRepository jobArchiveRepository;

    @Value("${relay.archive.enabled:true}")
    private boolean archiveEnabled;

    @Value("${relay.archive.retention-days:30}")
    private int retentionDays;

    @Value("${relay.archive.batch-size:500}")
    private int batchSize;

    @Value("${relay.archive.max-batches-per-run:50}")
    private int maxBatchesPerRun;

    @Data
    @Builder
    public static class ArchiveResult {
        private int totalJobsArchived;
        private int totalAttemptsArchived;
        private int batchesProcessed;
        private Duration duration;
    }

    @Scheduled(cron = "${relay.archive.cron:0 0 3 * * *}")
    public ArchiveResult runArchiving() {
        if (!archiveEnabled) {
            log.info("Job archiver is disabled via configuration.");
            return ArchiveResult.builder().build();
        }

        String correlationId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        Instant cutoff = startTime.minus(Duration.ofDays(retentionDays));

        int totalJobs = 0;
        int totalAttempts = 0;
        int batchCount = 0;

        try {
            MDC.put("correlationId", correlationId);
            log.info("Starting scheduled job archiving: retentionDays={}, cutoff={}, batchSize={}, maxBatches={}",
                    retentionDays, cutoff, batchSize, maxBatchesPerRun);

            while (batchCount < maxBatchesPerRun) {
                BatchResult result = archiveNextChunk(cutoff, batchSize);
                if (result.getJobsArchived() == 0) {
                    break;
                }
                totalJobs += result.getJobsArchived();
                totalAttempts += result.getAttemptsArchived();
                batchCount++;
                log.info("Archived batch #{}: {} jobs, {} attempts (cumulative jobs: {})",
                        batchCount, result.getJobsArchived(), result.getAttemptsArchived(), totalJobs);
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            log.info("Completed job archiving run: {} jobs and {} attempts moved to archive tables across {} batches in {} ms",
                    totalJobs, totalAttempts, batchCount, elapsed.toMillis());

            return ArchiveResult.builder()
                    .totalJobsArchived(totalJobs)
                    .totalAttemptsArchived(totalAttempts)
                    .batchesProcessed(batchCount)
                    .duration(elapsed)
                    .build();

        } catch (Exception e) {
            log.error("Unhandled error during job archiving run: {}", e.getMessage(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    @Data
    @Builder
    public static class BatchResult {
        private int jobsArchived;
        private int attemptsArchived;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult archiveNextChunk(Instant cutoff, int chunkSize) {
        List<Job> completedJobs = jobRepository.findCompletedJobsOlderThan(
                JobStatus.COMPLETED,
                cutoff,
                PageRequest.of(0, chunkSize)
        );

        if (completedJobs.isEmpty()) {
            return BatchResult.builder().jobsArchived(0).attemptsArchived(0).build();
        }

        List<JobArchive> archives = new ArrayList<>(completedJobs.size());
        int attemptCount = 0;

        for (Job job : completedJobs) {
            JobArchive archive = JobArchive.builder()
                    .id(job.getId())
                    .jobType(job.getJobType())
                    .payload(job.getPayload())
                    .status(job.getStatus())
                    .attemptCount(job.getAttemptCount())
                    .maxAttempts(job.getMaxAttempts())
                    .nextRetryAt(job.getNextRetryAt())
                    .idempotencyKey(job.getIdempotencyKey())
                    .lastError(job.getLastError())
                    .createdAt(job.getCreatedAt())
                    .updatedAt(job.getUpdatedAt())
                    .version(job.getVersion())
                    .archivedAt(Instant.now())
                    .build();

            if (job.getAttempts() != null && !job.getAttempts().isEmpty()) {
                List<JobAttemptArchive> attemptArchives = new ArrayList<>(job.getAttempts().size());
                for (JobAttempt attempt : job.getAttempts()) {
                    JobAttemptArchive attArchive = JobAttemptArchive.builder()
                            .id(attempt.getId())
                            .jobArchive(archive)
                            .attemptNumber(attempt.getAttemptNumber())
                            .startedAt(attempt.getStartedAt())
                            .finishedAt(attempt.getFinishedAt())
                            .status(attempt.getStatus())
                            .errorMessage(attempt.getErrorMessage())
                            .archivedAt(Instant.now())
                            .build();
                    attemptArchives.add(attArchive);
                    attemptCount++;
                }
                archive.setAttempts(attemptArchives);
            }

            archives.add(archive);
        }

        // Save archives in bulk (cascades to job_attempt_archive)
        jobArchiveRepository.saveAll(archives);

        // Delete from active tables (cascades to job_attempts via FK or JPA orphanRemoval)
        jobRepository.deleteAll(completedJobs);

        return BatchResult.builder()
                .jobsArchived(completedJobs.size())
                .attemptsArchived(attemptCount)
                .build();
    }
}

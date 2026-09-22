package com.relay.scheduler;

import com.relay.domain.entity.Job;
import com.relay.domain.entity.JobAttempt;
import com.relay.domain.enums.AttemptStatus;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.repository.JobArchiveRepository;
import com.relay.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobArchiverServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobArchiveRepository jobArchiveRepository;

    @InjectMocks
    private JobArchiverService jobArchiverService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobArchiverService, "archiveEnabled", true);
        ReflectionTestUtils.setField(jobArchiverService, "retentionDays", 30);
        ReflectionTestUtils.setField(jobArchiverService, "batchSize", 500);
        ReflectionTestUtils.setField(jobArchiverService, "maxBatchesPerRun", 10);
    }

    @Test
    @DisplayName("Should archive completed jobs older than 30 days in batched chunks")
    void testArchivingMovesCompletedOldJobsAndAttempts() {
        UUID jobId = UUID.randomUUID();
        Instant oldTimestamp = Instant.now().minus(35, ChronoUnit.DAYS);

        Job job = Job.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "old@archive.org"))
                .status(JobStatus.COMPLETED)
                .attemptCount(1)
                .maxAttempts(3)
                .createdAt(oldTimestamp)
                .updatedAt(oldTimestamp)
                .attempts(new ArrayList<>())
                .build();

        JobAttempt attempt = JobAttempt.builder()
                .id(UUID.randomUUID())
                .job(job)
                .attemptNumber(1)
                .startedAt(oldTimestamp)
                .finishedAt(oldTimestamp.plusSeconds(2))
                .status(AttemptStatus.SUCCESS)
                .build();
        job.getAttempts().add(attempt);

        when(jobRepository.findCompletedJobsOlderThan(eq(JobStatus.COMPLETED), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(job))
                .thenReturn(Collections.emptyList());

        JobArchiverService.ArchiveResult result = jobArchiverService.runArchiving();

        assertThat(result.getTotalJobsArchived()).isEqualTo(1);
        assertThat(result.getTotalAttemptsArchived()).isEqualTo(1);
        assertThat(result.getBatchesProcessed()).isEqualTo(1);

        verify(jobArchiveRepository, times(1)).saveAll(anyList());
        verify(jobRepository, times(1)).deleteAll(List.of(job));
    }

    @Test
    @DisplayName("Should bypass archiving when disabled via configuration")
    void testArchivingDisabledBypassesExecution() {
        ReflectionTestUtils.setField(jobArchiverService, "archiveEnabled", false);

        JobArchiverService.ArchiveResult result = jobArchiverService.runArchiving();

        assertThat(result.getTotalJobsArchived()).isEqualTo(0);
        verifyNoInteractions(jobRepository);
        verifyNoInteractions(jobArchiveRepository);
    }

    @Test
    @DisplayName("Should process multiple chunks until no more eligible rows remain")
    void testArchivingProcessesMultipleChunks() {
        Instant oldTimestamp = Instant.now().minus(40, ChronoUnit.DAYS);

        Job job1 = Job.builder()
                .id(UUID.randomUUID())
                .jobType(JobType.REPORT_GENERATION)
                .payload(Map.of("report", "1"))
                .status(JobStatus.COMPLETED)
                .updatedAt(oldTimestamp)
                .attempts(new ArrayList<>())
                .build();

        Job job2 = Job.builder()
                .id(UUID.randomUUID())
                .jobType(JobType.REPORT_GENERATION)
                .payload(Map.of("report", "2"))
                .status(JobStatus.COMPLETED)
                .updatedAt(oldTimestamp)
                .attempts(new ArrayList<>())
                .build();

        // First batch returns job1, second returns job2, third returns empty
        when(jobRepository.findCompletedJobsOlderThan(eq(JobStatus.COMPLETED), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(job1))
                .thenReturn(List.of(job2))
                .thenReturn(Collections.emptyList());

        JobArchiverService.ArchiveResult result = jobArchiverService.runArchiving();

        assertThat(result.getTotalJobsArchived()).isEqualTo(2);
        assertThat(result.getBatchesProcessed()).isEqualTo(2);

        verify(jobArchiveRepository, times(2)).saveAll(anyList());
        verify(jobRepository, times(2)).deleteAll(anyList());
    }
}

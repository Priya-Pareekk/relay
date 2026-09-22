package com.relay.scheduler;

import com.relay.domain.entity.JobDefinition;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobSubmissionRequest;
import com.relay.repository.JobDefinitionRepository;
import com.relay.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CronJobSchedulerServiceTest {

    @Mock
    private JobDefinitionRepository jobDefinitionRepository;

    @Mock
    private JobService jobService;

    private CronJobSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new CronJobSchedulerService(jobDefinitionRepository, jobService);
        ReflectionTestUtils.setField(schedulerService, "cronEnabled", true);
    }

    @Test
    @DisplayName("Should isolate errors so a malformed/failing definition does not stop other definitions")
    void testPerDefinitionIsolationInCronScheduler() {
        // Definition 1 has invalid cron syntax
        JobDefinition badDef = JobDefinition.builder()
                .id(UUID.randomUUID())
                .name("Broken Cron Def")
                .jobType(JobType.EMAIL_NOTIFICATION)
                .cronExpression("INVALID_CRON_SYNTAX")
                .enabled(true)
                .createdAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();

        // Definition 2 is valid and due (every second: * * * * * *)
        JobDefinition goodDef = JobDefinition.builder()
                .id(UUID.randomUUID())
                .name("Healthy Cron Def")
                .jobType(JobType.REPORT_GENERATION)
                .cronExpression("*/5 * * * * *")
                .enabled(true)
                .payloadTemplate(Map.of("reportType", "FINANCIAL"))
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .lastTriggeredAt(Instant.now().minus(30, ChronoUnit.SECONDS))
                .build();

        when(jobDefinitionRepository.findByEnabledTrue()).thenReturn(List.of(badDef, goodDef));

        schedulerService.evaluateCronJobs();

        // Good definition must be submitted despite badDef throwing an exception
        ArgumentCaptor<JobSubmissionRequest> captor = ArgumentCaptor.forClass(JobSubmissionRequest.class);
        verify(jobService, times(1)).submitJob(captor.capture());

        assertThat(captor.getValue().getJobType()).isEqualTo(JobType.REPORT_GENERATION);
        assertThat(captor.getValue().getIdempotencyKey()).startsWith("cron-" + goodDef.getId());
        verify(jobDefinitionRepository).save(goodDef);
    }

    @Test
    @DisplayName("Catch-up on restart: When app was down and definition was due, fire once and advance lastTriggeredAt")
    void testCatchUpExecutionOnRestart() {
        // Was due 3 hours ago during an app outage
        Instant threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS);
        JobDefinition overdueDef = JobDefinition.builder()
                .id(UUID.randomUUID())
                .name("Nightly Catch-up Def")
                .jobType(JobType.REPORT_GENERATION)
                .cronExpression("0 0 * * * *") // hourly
                .enabled(true)
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .lastTriggeredAt(threeHoursAgo)
                .build();

        when(jobDefinitionRepository.findByEnabledTrue()).thenReturn(List.of(overdueDef));

        schedulerService.evaluateCronJobs();

        // Must trigger exactly ONCE as a catch-up run, not 3 separate cascading runs
        verify(jobService, times(1)).submitJob(any(JobSubmissionRequest.class));

        // lastTriggeredAt must be advanced to current time (within last few seconds)
        assertThat(overdueDef.getLastTriggeredAt()).isAfterOrEqualTo(Instant.now().minusSeconds(5));
        verify(jobDefinitionRepository).save(overdueDef);
    }
}

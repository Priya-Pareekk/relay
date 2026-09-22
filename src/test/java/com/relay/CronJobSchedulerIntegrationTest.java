package com.relay;

import com.relay.domain.entity.Job;
import com.relay.domain.entity.JobDefinition;
import com.relay.domain.enums.JobType;
import com.relay.repository.JobDefinitionRepository;
import com.relay.repository.JobRepository;
import com.relay.scheduler.CronJobSchedulerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CronJobSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobDefinitionRepository jobDefinitionRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CronJobSchedulerService cronJobSchedulerService;

    @Test
    @DisplayName("Should evaluate active JobDefinition and trigger automatic job submission")
    void testCronJobEvaluationAndTrigger() {
        // Create an enabled JobDefinition with an instant-due cron (every second)
        JobDefinition definition = JobDefinition.builder()
                .name("Hourly Metrics Aggregator")
                .jobType(JobType.REPORT_GENERATION)
                .cronExpression("* * * * * *") // every second
                .payloadTemplate(Map.of("reportName", "Metrics Dump", "format", "JSON"))
                .enabled(true)
                .createdAt(Instant.now().minusSeconds(10))
                .build();

        JobDefinition savedDef = jobDefinitionRepository.saveAndFlush(definition);

        // Manually invoke evaluation or let scheduler run
        cronJobSchedulerService.evaluateCronJobs();

        // Verify a new job was generated and completed
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    long count = jobRepository.count();
                    assertThat(count).isGreaterThanOrEqualTo(1);

                    JobDefinition updatedDef = jobDefinitionRepository.findById(savedDef.getId()).orElseThrow();
                    assertThat(updatedDef.getLastTriggeredAt()).isNotNull();
                });
    }
}

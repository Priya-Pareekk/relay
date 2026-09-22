package com.relay.service;

import com.relay.domain.entity.JobDefinition;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobDefinitionRequest;
import com.relay.dto.JobDefinitionResponse;
import com.relay.repository.JobDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDefinitionServiceTest {

    @Mock
    private JobDefinitionRepository jobDefinitionRepository;

    private JobDefinitionService definitionService;

    @BeforeEach
    void setUp() {
        definitionService = new JobDefinitionService(jobDefinitionRepository);
    }

    @Test
    @DisplayName("Should parse both 5-part and 6-part cron expressions correctly")
    void testParseCronExpression() {
        CronExpression expr5 = JobDefinitionService.parseCronExpression("0 9 * * *");
        assertThat(expr5).isNotNull();

        CronExpression expr6 = JobDefinitionService.parseCronExpression("0 0 9 * * *");
        assertThat(expr6).isNotNull();
    }

    @Test
    @DisplayName("Should create JobDefinition and return valid response with next execution time")
    void testCreateJobDefinition() {
        JobDefinitionRequest request = JobDefinitionRequest.builder()
                .name("Nightly Report")
                .jobType(JobType.REPORT_GENERATION)
                .cronExpression("0 0 2 * * *")
                .payloadTemplate(Map.of("type", "FINANCIAL"))
                .enabled(true)
                .build();

        JobDefinition saved = JobDefinition.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .jobType(request.getJobType())
                .cronExpression(request.getCronExpression())
                .payloadTemplate(request.getPayloadTemplate())
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        when(jobDefinitionRepository.save(any(JobDefinition.class))).thenReturn(saved);

        JobDefinitionResponse response = definitionService.createJobDefinition(request);
        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Nightly Report");
        assertThat(response.getNextExecutionTime()).isNotNull();
    }
}

package com.relay.controller;

import com.relay.domain.enums.JobType;
import com.relay.dto.JobResponse;
import com.relay.dto.JobSubmissionRequest;
import com.relay.service.JobDefinitionService;
import com.relay.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevSeedControllerTest {

    @Mock
    private JobService jobService;

    @Mock
    private JobDefinitionService jobDefinitionService;

    @InjectMocks
    private DevSeedController devSeedController;

    @Test
    @DisplayName("Should submit synthetic jobs and create sample cron definitions")
    void testSeedDemoData() {
        when(jobDefinitionService.getAllJobDefinitions()).thenReturn(Collections.emptyList());
        when(jobService.submitJob(any(JobSubmissionRequest.class))).thenReturn(
                new JobService.JobSubmissionResult(
                        JobResponse.builder().id(UUID.randomUUID()).build(),
                        false
                )
        );

        ResponseEntity<Map<String, Object>> response = devSeedController.seedDemoData(20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
        assertThat(response.getBody().get("jobsSubmitted")).isEqualTo(20);

        verify(jobService, times(20)).submitJob(any(JobSubmissionRequest.class));
        verify(jobDefinitionService, times(3)).createJobDefinition(any());
    }
}

package com.relay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobDefinitionRequest;
import com.relay.dto.JobDefinitionResponse;
import com.relay.dto.JobDefinitionUpdateRequest;
import com.relay.exception.GlobalExceptionHandler;
import com.relay.security.ApiKeyAuthenticationFilter;
import com.relay.security.SecurityConfig;
import com.relay.service.JobDefinitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {JobDefinitionController.class})
@Import({SecurityConfig.class, ApiKeyAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "relay.security.api-key=test-secret-api-key",
        "relay.security.api-key-header=X-API-Key"
})
class JobDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobDefinitionService jobDefinitionService;

    private static final String API_KEY = "test-secret-api-key";

    @Test
    @DisplayName("Should create JobDefinition using JobDefinitionRequest and return JobDefinitionResponse DTO")
    void testCreateJobDefinition() throws Exception {
        UUID id = UUID.randomUUID();
        JobDefinitionRequest requestDto = JobDefinitionRequest.builder()
                .name("Hourly Report")
                .jobType(JobType.REPORT_GENERATION)
                .cronExpression("0 0 * * * *")
                .payloadTemplate(Map.of("type", "HOURLY"))
                .enabled(true)
                .build();

        JobDefinitionResponse responseDto = JobDefinitionResponse.builder()
                .id(id)
                .name(requestDto.getName())
                .jobType(requestDto.getJobType())
                .cronExpression(requestDto.getCronExpression())
                .payloadTemplate(requestDto.getPayloadTemplate())
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(jobDefinitionService.createJobDefinition(any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/job-definitions")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Hourly Report"))
                .andExpect(jsonPath("$.jobType").value("REPORT_GENERATION"))
                .andExpect(jsonPath("$.cronExpression").value("0 0 * * * *"));
    }

    @Test
    @DisplayName("Should return list of JobDefinitionResponse DTOs")
    void testGetAllJobDefinitions() throws Exception {
        UUID id = UUID.randomUUID();
        JobDefinitionResponse responseDto = JobDefinitionResponse.builder()
                .id(id)
                .name("Daily Sync")
                .jobType(JobType.EMAIL_NOTIFICATION)
                .cronExpression("0 0 2 * * *")
                .payloadTemplate(Map.of("action", "SYNC"))
                .enabled(true)
                .build();

        when(jobDefinitionService.getAllJobDefinitions()).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/job-definitions")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Daily Sync"));
    }

    @Test
    @DisplayName("Should update JobDefinition using JobDefinitionUpdateRequest DTO")
    void testUpdateJobDefinition() throws Exception {
        UUID id = UUID.randomUUID();
        JobDefinitionUpdateRequest updateDto = JobDefinitionUpdateRequest.builder()
                .name("Updated Name")
                .enabled(false)
                .build();

        JobDefinitionResponse responseDto = JobDefinitionResponse.builder()
                .id(id)
                .name("Updated Name")
                .jobType(JobType.EMAIL_NOTIFICATION)
                .cronExpression("0 0 2 * * *")
                .enabled(false)
                .build();

        when(jobDefinitionService.updateJobDefinition(eq(id), any())).thenReturn(responseDto);

        mockMvc.perform(patch("/api/job-definitions/" + id)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("Should delete JobDefinition and return 204 No Content")
    void testDeleteJobDefinition() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/job-definitions/" + id)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        verify(jobDefinitionService).deleteJobDefinition(id);
    }
}

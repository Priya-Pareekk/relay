package com.relay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.dto.JobResponse;
import com.relay.dto.JobSubmissionRequest;
import com.relay.exception.GlobalExceptionHandler;
import com.relay.exception.InvalidJobStateException;
import com.relay.exception.ResourceNotFoundException;
import com.relay.security.ApiKeyAuthenticationFilter;
import com.relay.security.SecurityConfig;
import com.relay.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {JobController.class})
@Import({SecurityConfig.class, ApiKeyAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "relay.security.api-key=test-secret-api-key",
        "relay.security.api-key-header=X-API-Key"
})
class JobControllerSecurityAndValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    private static final String API_KEY = "test-secret-api-key";

    @Test
    @DisplayName("Should return 401 Unauthorized when X-API-Key header is missing for /api/jobs")
    void testMissingApiKeyReturns401WithJsonErrorResponse() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing X-API-Key header"))
                .andExpect(jsonPath("$.path").value("/api/jobs"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when X-API-Key header is invalid")
    void testInvalidApiKeyReturns401WithJsonErrorResponse() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .header("X-API-Key", "wrong-invalid-key")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid X-API-Key header"))
                .andExpect(jsonPath("$.path").value("/api/jobs"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 200 OK and JobResponse DTO when valid X-API-Key header is provided")
    void testValidApiKeyReturnsJobResponseDto() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobResponse responseDto = JobResponse.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "test@domain.com"))
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .createdAt(Instant.now())
                .attempts(List.of())
                .build();

        when(jobService.getJobById(jobId)).thenReturn(responseDto);

        mockMvc.perform(get("/api/jobs/" + jobId)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.jobType").value("EMAIL_NOTIFICATION"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should return 201 Created and JobResponse DTO on valid job submission")
    void testSubmitJobReturns201WithResponseDto() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobSubmissionRequest requestDto = JobSubmissionRequest.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "hello@example.com"))
                .maxAttempts(5)
                .build();

        JobResponse responseDto = JobResponse.builder()
                .id(jobId)
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(requestDto.getPayload())
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(5)
                .createdAt(Instant.now())
                .attempts(List.of())
                .build();

        when(jobService.submitJob(any())).thenReturn(new JobService.JobSubmissionResult(responseDto, false));

        mockMvc.perform(post("/api/jobs")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.jobType").value("EMAIL_NOTIFICATION"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request with field validation errors when request DTO is invalid")
    void testValidationFailureReturns400WithFieldErrors() throws Exception {
        // Missing required jobType and payload
        String invalidRequestBody = "{}";

        mockMvc.perform(post("/api/jobs")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/jobs"))
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 404 Not Found with consistent JSON ErrorResponse when resource does not exist")
    void testResourceNotFoundReturns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        when(jobService.getJobById(randomId))
                .thenThrow(new ResourceNotFoundException("Job not found with id: " + randomId));

        mockMvc.perform(get("/api/jobs/" + randomId)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Job not found with id: " + randomId))
                .andExpect(jsonPath("$.path").value("/api/jobs/" + randomId))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 409 Conflict with consistent JSON ErrorResponse when invalid job state transition occurs")
    void testInvalidJobStateReturns409Conflict() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobService.replayJob(jobId))
                .thenThrow(new InvalidJobStateException("Cannot replay job in status: COMPLETED"));

        mockMvc.perform(post("/api/jobs/" + jobId + "/replay")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot replay job in status: COMPLETED"))
                .andExpect(jsonPath("$.path").value("/api/jobs/" + jobId + "/replay"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

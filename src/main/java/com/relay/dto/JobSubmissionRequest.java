package com.relay.dto;

import com.relay.domain.enums.JobType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSubmissionRequest {

    @NotNull(message = "jobType is required")
    private JobType jobType;

    @NotNull(message = "payload is required")
    private Map<String, Object> payload;

    private String idempotencyKey;

    @Builder.Default
    @jakarta.validation.constraints.Min(value = 1, message = "maxAttempts must be at least 1")
    private Integer maxAttempts = 5;
}

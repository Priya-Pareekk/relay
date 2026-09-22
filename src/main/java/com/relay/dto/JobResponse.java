package com.relay.dto;

import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {
    private UUID id;
    private JobType jobType;
    private Map<String, Object> payload;
    private JobStatus status;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextRetryAt;
    private String idempotencyKey;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private List<JobAttemptResponse> attempts;
}

package com.relay.mapper;

import com.relay.domain.entity.Job;
import com.relay.domain.entity.JobAttempt;
import com.relay.dto.JobAttemptResponse;
import com.relay.dto.JobResponse;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JobMapper {

    public static JobResponse toJobResponse(Job job) {
        if (job == null) {
            return null;
        }

        List<JobAttemptResponse> attemptResponses = job.getAttempts() != null
                ? job.getAttempts().stream()
                .map(JobMapper::toJobAttemptResponse)
                .collect(Collectors.toList())
                : Collections.emptyList();

        return JobResponse.builder()
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
                .attempts(attemptResponses)
                .build();
    }

    public static JobAttemptResponse toJobAttemptResponse(JobAttempt attempt) {
        if (attempt == null) {
            return null;
        }

        return JobAttemptResponse.builder()
                .id(attempt.getId())
                .attemptNumber(attempt.getAttemptNumber())
                .startedAt(attempt.getStartedAt())
                .finishedAt(attempt.getFinishedAt())
                .status(attempt.getStatus())
                .errorMessage(attempt.getErrorMessage())
                .build();
    }
}

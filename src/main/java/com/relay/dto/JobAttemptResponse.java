package com.relay.dto;

import com.relay.domain.enums.AttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAttemptResponse {
    private UUID id;
    private int attemptNumber;
    private Instant startedAt;
    private Instant finishedAt;
    private AttemptStatus status;
    private String errorMessage;
}

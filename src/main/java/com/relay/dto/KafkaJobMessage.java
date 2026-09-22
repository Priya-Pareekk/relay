package com.relay.dto;

import com.relay.domain.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaJobMessage {
    private UUID jobId;
    private JobType jobType;
    private int attemptNumber;
    private Map<String, Object> payload;
    private String idempotencyKey;
    @Builder.Default
    private Instant sentAt = Instant.now();
}

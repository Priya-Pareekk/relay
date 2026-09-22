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
public class JobDefinitionResponse {
    private UUID id;
    private String name;
    private JobType jobType;
    private String cronExpression;
    private Map<String, Object> payloadTemplate;
    private boolean enabled;
    private Instant lastTriggeredAt;
    private Instant nextExecutionTime;
    private Instant createdAt;
    private Instant updatedAt;
}

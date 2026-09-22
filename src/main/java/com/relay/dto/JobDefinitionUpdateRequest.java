package com.relay.dto;

import com.relay.domain.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDefinitionUpdateRequest {
    private String name;
    private JobType jobType;
    private String cronExpression;
    private Map<String, Object> payloadTemplate;
    private Boolean enabled;
}

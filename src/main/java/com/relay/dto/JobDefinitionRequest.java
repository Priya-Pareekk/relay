package com.relay.dto;

import com.relay.domain.enums.JobType;
import jakarta.validation.constraints.NotBlank;
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
public class JobDefinitionRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "jobType is required")
    private JobType jobType;

    @NotBlank(message = "cronExpression is required")
    private String cronExpression;

    @NotNull(message = "payloadTemplate is required")
    private Map<String, Object> payloadTemplate;

    @Builder.Default
    private Boolean enabled = true;
}

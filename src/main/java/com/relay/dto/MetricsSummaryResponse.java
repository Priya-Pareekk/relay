package com.relay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSummaryResponse {
    private long totalJobs;
    private long pendingJobs;
    private long processingJobs;
    private long retryingJobs;
    private long completedJobs;
    private long deadLetterJobs;
    private long cancelledJobs;

    private long totalLast24h;
    private long completedLast24h;
    private long deadLetterLast24h;
    private double successRatePercentLast24h;

    private long totalDefinitions;
    private long enabledDefinitions;

    private Map<String, Long> jobsByType;
    private Map<String, String> circuitBreakers;
}

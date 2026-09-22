package com.relay.service;

import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.dto.MetricsSummaryResponse;
import com.relay.repository.JobDefinitionRepository;
import com.relay.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final JobRepository jobRepository;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @Transactional(readOnly = true)
    public MetricsSummaryResponse getSummary() {
        long totalJobs = jobRepository.count();
        long pending = jobRepository.countByStatus(JobStatus.PENDING);
        long processing = jobRepository.countByStatus(JobStatus.PROCESSING);
        long retrying = jobRepository.countByStatus(JobStatus.RETRYING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long deadLetter = jobRepository.countByStatus(JobStatus.DEAD_LETTER);
        long cancelled = jobRepository.countByStatus(JobStatus.CANCELLED);

        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        long totalLast24h = jobRepository.countTotalSince(twentyFourHoursAgo);
        long completedLast24h = jobRepository.countByStatusSince(JobStatus.COMPLETED, twentyFourHoursAgo);
        long deadLetterLast24h = jobRepository.countByStatusSince(JobStatus.DEAD_LETTER, twentyFourHoursAgo);

        double successRate = 0.0;
        long processedLast24h = completedLast24h + deadLetterLast24h;
        if (processedLast24h > 0) {
            successRate = Math.round(((double) completedLast24h / processedLast24h) * 10000.0) / 100.0;
        }

        long totalDefinitions = jobDefinitionRepository.count();
        long enabledDefinitions = jobDefinitionRepository.findByEnabledTrue().size();

        Map<String, Long> jobsByType = new HashMap<>();
        Map<String, String> circuitBreakers = new HashMap<>();
        for (JobType type : JobType.values()) {
            jobsByType.put(type.name(), (long) jobRepository.findByJobType(type, org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
            io.github.resilience4j.circuitbreaker.CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(type.name());
            circuitBreakers.put(type.name(), cb.getState().name());
        }

        return MetricsSummaryResponse.builder()
                .totalJobs(totalJobs)
                .pendingJobs(pending)
                .processingJobs(processing)
                .retryingJobs(retrying)
                .completedJobs(completed)
                .deadLetterJobs(deadLetter)
                .cancelledJobs(cancelled)
                .totalLast24h(totalLast24h)
                .completedLast24h(completedLast24h)
                .deadLetterLast24h(deadLetterLast24h)
                .successRatePercentLast24h(successRate)
                .totalDefinitions(totalDefinitions)
                .enabledDefinitions(enabledDefinitions)
                .jobsByType(jobsByType)
                .circuitBreakers(circuitBreakers)
                .build();
    }
}

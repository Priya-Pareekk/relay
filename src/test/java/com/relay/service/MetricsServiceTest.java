package com.relay.service;

import com.relay.domain.enums.JobStatus;
import com.relay.dto.MetricsSummaryResponse;
import com.relay.repository.JobDefinitionRepository;
import com.relay.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobDefinitionRepository jobDefinitionRepository;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService(
                jobRepository,
                jobDefinitionRepository,
                io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults()
        );
    }

    @Test
    @DisplayName("Should aggregate metric counts and calculate 24h success rate correctly")
    void testGetSummary() {
        when(jobRepository.count()).thenReturn(100L);
        when(jobRepository.countByStatus(JobStatus.PENDING)).thenReturn(10L);
        when(jobRepository.countByStatus(JobStatus.PROCESSING)).thenReturn(5L);
        when(jobRepository.countByStatus(JobStatus.RETRYING)).thenReturn(5L);
        when(jobRepository.countByStatus(JobStatus.COMPLETED)).thenReturn(70L);
        when(jobRepository.countByStatus(JobStatus.DEAD_LETTER)).thenReturn(10L);
        when(jobRepository.countByStatus(JobStatus.CANCELLED)).thenReturn(0L);

        when(jobRepository.countTotalSince(any(Instant.class))).thenReturn(80L);
        when(jobRepository.countByStatusSince(eq(JobStatus.COMPLETED), any(Instant.class))).thenReturn(70L);
        when(jobRepository.countByStatusSince(eq(JobStatus.DEAD_LETTER), any(Instant.class))).thenReturn(10L);

        when(jobDefinitionRepository.count()).thenReturn(3L);
        when(jobDefinitionRepository.findByEnabledTrue()).thenReturn(Collections.emptyList());
        when(jobRepository.findByJobType(any(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));

        MetricsSummaryResponse summary = metricsService.getSummary();

        assertThat(summary.getTotalJobs()).isEqualTo(100L);
        assertThat(summary.getCompletedJobs()).isEqualTo(70L);
        assertThat(summary.getDeadLetterJobs()).isEqualTo(10L);
        // 70 / (70 + 10) = 87.5%
        assertThat(summary.getSuccessRatePercentLast24h()).isEqualTo(87.5);
    }
}

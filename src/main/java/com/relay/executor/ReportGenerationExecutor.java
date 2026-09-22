package com.relay.executor;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobType;
import com.relay.exception.JobExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ReportGenerationExecutor implements JobExecutor {

    @Override
    public JobType getJobType() {
        return JobType.REPORT_GENERATION;
    }

    @Override
    public void execute(Job job) throws JobExecutionException {
        Map<String, Object> payload = job.getPayload();
        log.info("Executing ReportGenerationExecutor for jobId={}: payload={}", job.getId(), payload);

        if (payload == null) {
            throw new JobExecutionException("Payload is missing for REPORT_GENERATION job");
        }

        Object failFlag = payload.get("fail");
        Object shouldFail = payload.get("shouldFail");
        String reportType = String.valueOf(payload.getOrDefault("reportType", ""));

        if (Boolean.TRUE.equals(failFlag) || Boolean.TRUE.equals(shouldFail) || "INVALID".equalsIgnoreCase(reportType)) {
            log.warn("Simulated failure triggered for REPORT_GENERATION jobId={}", job.getId());
            throw new JobExecutionException("Failed to generate report with type: " + reportType + " (Simulated generator failure)");
        }

        // Simulate calculation / document rendering latency
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobExecutionException("Execution interrupted", e);
        }

        log.info("Successfully generated report '{}' with format '{}'",
                payload.getOrDefault("reportName", "Default Report"),
                payload.getOrDefault("format", "PDF"));
    }
}

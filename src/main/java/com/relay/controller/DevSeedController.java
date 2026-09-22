package com.relay.controller;

import com.relay.domain.enums.JobType;
import com.relay.dto.JobDefinitionRequest;
import com.relay.dto.JobSubmissionRequest;
import com.relay.service.JobDefinitionService;
import com.relay.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile({"dev", "demo", "default", "test", "local"})
@Tag(name = "Development & Demo Utilities", description = "Endpoints for generating synthetic test load and demo state")
public class DevSeedController {

    private final JobService jobService;
    private final JobDefinitionService jobDefinitionService;

    @PostMapping("/seed")
    @Operation(summary = "Seed synthetic demo jobs and cron definitions")
    public ResponseEntity<Map<String, Object>> seedDemoData(
            @RequestParam(defaultValue = "50") int count) {

        int totalJobs = Math.max(5, Math.min(count, 200));
        long timestamp = System.currentTimeMillis();
        int jobsSubmitted = 0;

        log.info("Seeding {} synthetic demo jobs with mixed state outcomes (runId={})...", totalJobs, timestamp);

        for (int i = 1; i <= totalJobs; i++) {
            JobType jobType = (i % 2 == 0) ? JobType.EMAIL_NOTIFICATION : JobType.REPORT_GENERATION;
            Map<String, Object> payload = new HashMap<>();
            int maxAttempts = 3;

            if (i % 5 == 0) {
                // 20% are engineered to fail and exhaust retries into DEAD_LETTER
                payload.put("shouldFail", true);
                payload.put("reason", "Simulated downstream outage");
                maxAttempts = 2;
                if (jobType == JobType.EMAIL_NOTIFICATION) {
                    payload.put("to", "fail@error.com");
                    payload.put("subject", "Urgent Invoice #" + i);
                } else {
                    payload.put("reportType", "INVALID");
                    payload.put("reportName", "Damaged Ledger #" + i);
                }
            } else if (i % 5 == 1) {
                // 20% are engineered for transient retries with larger maxAttempts
                payload.put("shouldFail", true);
                payload.put("reason", "Simulated timeout");
                maxAttempts = 5;
                if (jobType == JobType.EMAIL_NOTIFICATION) {
                    payload.put("to", "retry-" + i + "@company.org");
                    payload.put("subject", "Retryable Password Reset #" + i);
                } else {
                    payload.put("reportType", "INVALID");
                    payload.put("reportName", "Transient Report #" + i);
                }
            } else {
                // 60% are standard healthy jobs that will succeed on attempt 1
                if (jobType == JobType.EMAIL_NOTIFICATION) {
                    payload.put("to", "customer-" + i + "@example.com");
                    payload.put("subject", "Order Confirmation #" + (10000 + i));
                    payload.put("template", "order_confirmation");
                } else {
                    payload.put("reportType", "MONTHLY_FINANCIAL");
                    payload.put("reportName", "Financial Summary Q1-" + i);
                    payload.put("format", "PDF");
                }
            }

            String idempotencyKey = "seed-" + timestamp + "-" + i;

            jobService.submitJob(
                    JobSubmissionRequest.builder()
                            .jobType(jobType)
                            .payload(payload)
                            .idempotencyKey(idempotencyKey)
                            .maxAttempts(maxAttempts)
                            .build()
            );
            jobsSubmitted++;
        }

        // Ensure 3 sample recurring cron definitions exist
        int definitionsCreated = 0;
        try {
            if (jobDefinitionService.getAllJobDefinitions().isEmpty()) {
                jobDefinitionService.createJobDefinition(
                        JobDefinitionRequest.builder()
                                .name("Real-Time Customer Sync")
                                .jobType(JobType.EMAIL_NOTIFICATION)
                                .cronExpression("*/30 * * * * *")
                                .payloadTemplate(Map.of("to", "sync@relay.internal", "subject", "Live Sync Heartbeat"))
                                .enabled(true)
                                .build()
                );
                definitionsCreated++;

                jobDefinitionService.createJobDefinition(
                        JobDefinitionRequest.builder()
                                .name("Hourly Transaction Aggregator")
                                .jobType(JobType.REPORT_GENERATION)
                                .cronExpression("0 0 * * * *")
                                .payloadTemplate(Map.of("reportType", "TRANSACTION_SUMMARY", "format", "CSV"))
                                .enabled(true)
                                .build()
                );
                definitionsCreated++;

                jobDefinitionService.createJobDefinition(
                        JobDefinitionRequest.builder()
                                .name("Nightly Financial Audit")
                                .jobType(JobType.REPORT_GENERATION)
                                .cronExpression("0 0 2 * * *")
                                .payloadTemplate(Map.of("reportType", "DAILY_REVENUE", "format", "PDF"))
                                .enabled(true)
                                .build()
                );
                definitionsCreated++;
            }
        } catch (Exception e) {
            log.warn("Sample definitions already exist or could not be created: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("jobsSubmitted", jobsSubmitted);
        result.put("definitionsCreated", definitionsCreated);
        result.put("seedTimestamp", Instant.ofEpochMilli(timestamp).toString());
        result.put("message", "Successfully seeded demo dataset into Relay queue and outbox");

        return ResponseEntity.ok(result);
    }
}

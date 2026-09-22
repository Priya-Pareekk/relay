package com.relay.executor;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobType;
import com.relay.exception.JobExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class EmailNotificationExecutor implements JobExecutor {

    @Override
    public JobType getJobType() {
        return JobType.EMAIL_NOTIFICATION;
    }

    @Override
    public void execute(Job job) throws JobExecutionException {
        Map<String, Object> payload = job.getPayload();
        log.info("Executing EmailNotificationExecutor for jobId={}: payload={}", job.getId(), payload);

        if (payload == null) {
            throw new JobExecutionException("Payload is missing for EMAIL_NOTIFICATION job");
        }

        // Support intentional failure for testing retries and DLQ
        Object failFlag = payload.get("fail");
        Object shouldFail = payload.get("shouldFail");
        String to = String.valueOf(payload.getOrDefault("to", ""));

        if (Boolean.TRUE.equals(failFlag) || Boolean.TRUE.equals(shouldFail) || to.contains("fail@error.com") || to.equalsIgnoreCase("fail")) {
            log.warn("Simulated failure triggered for EMAIL_NOTIFICATION jobId={}", job.getId());
            throw new JobExecutionException("Failed to deliver email to: " + to + " (Simulated delivery error)");
        }

        // Simulate network / SMTP latency
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobExecutionException("Execution interrupted", e);
        }

        log.info("Successfully sent email notification to '{}' with subject '{}'",
                to, payload.getOrDefault("subject", "No Subject"));
    }
}

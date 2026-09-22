package com.relay.scheduler;

import com.relay.domain.entity.JobDefinition;
import com.relay.dto.JobSubmissionRequest;
import com.relay.repository.JobDefinitionRepository;
import com.relay.service.JobDefinitionService;
import com.relay.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class CronJobSchedulerService {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobService jobService;

    @Value("${relay.cron.enabled:true}")
    private boolean cronEnabled;

    @Scheduled(fixedDelayString = "${relay.cron.eval-interval-ms:10000}")
    public void evaluateCronJobs() {
        if (!cronEnabled) {
            return;
        }

        List<JobDefinition> enabledDefinitions = jobDefinitionRepository.findByEnabledTrue();
        if (enabledDefinitions.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        ZonedDateTime nowZdt = now.atZone(ZoneOffset.UTC);

        // Process each definition with strict error isolation
        for (JobDefinition def : enabledDefinitions) {
            try {
                evaluateAndTriggerDefinition(def, now, nowZdt);
            } catch (Exception e) {
                // Isolated catch ensures one broken definition never blocks others
                log.error("Failed to evaluate/trigger cron for JobDefinition id='{}', name='{}': {}",
                        def.getId(), def.getName(), e.getMessage(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateAndTriggerDefinition(JobDefinition def, Instant now, ZonedDateTime nowZdt) {
        String correlationId = UUID.randomUUID().toString();
        try {
            org.slf4j.MDC.put("correlationId", correlationId);
            org.slf4j.MDC.put("definitionId", def.getId().toString());

            CronExpression cron = JobDefinitionService.parseCronExpression(def.getCronExpression());

            // Base calculation off lastTriggeredAt or createdAt
            Instant referenceInstant = def.getLastTriggeredAt() != null
                    ? def.getLastTriggeredAt()
                    : def.getCreatedAt();

            ZonedDateTime refZdt = referenceInstant.atZone(ZoneOffset.UTC);
            ZonedDateTime nextScheduledZdt = cron.next(refZdt);

            if (nextScheduledZdt != null && !nextScheduledZdt.isAfter(nowZdt)) {
                // CRON CATCH-UP POLICY:
                // If the app was down when the job was due (nextScheduledZdt < now),
                // fire ONCE immediately as a catch-up execution, then set lastTriggeredAt = now.
                // This prevents an uncontrolled storm of past missed runs while ensuring
                // the missed recurring task is executed upon service recovery.
                boolean isCatchUp = nextScheduledZdt.isBefore(nowZdt.minusSeconds(60));
                if (isCatchUp) {
                    log.info("Catch-up execution for JobDefinition id={}, name='{}' (was due at {}, now={}). Firing once and advancing schedule (correlationId={}).",
                            def.getId(), def.getName(), nextScheduledZdt.toInstant(), now, correlationId);
                } else {
                    log.info("Triggering scheduled recurring job for JobDefinition: id={}, name='{}', scheduledTime={}, correlationId={}",
                            def.getId(), def.getName(), nextScheduledZdt.toInstant(), correlationId);
                }

                String idempotencyKey = "cron-" + def.getId() + "-" + nextScheduledZdt.toInstant().getEpochSecond();

                JobSubmissionRequest submission = JobSubmissionRequest.builder()
                        .jobType(def.getJobType())
                        .payload(def.getPayloadTemplate())
                        .idempotencyKey(idempotencyKey)
                        .build();

                jobService.submitJob(submission);

                // Advancing lastTriggeredAt to now resets the schedule baseline past all missed intervals
                def.setLastTriggeredAt(now);
                jobDefinitionRepository.save(def);
            }
        } finally {
            org.slf4j.MDC.clear();
        }
    }
}

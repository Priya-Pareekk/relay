package com.relay.consumer;

import com.relay.dto.KafkaJobMessage;
import com.relay.service.JobProcessingService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Component
@RequiredArgsConstructor
public class JobConsumer {

    private final JobProcessingService jobProcessingService;
    private final AtomicInteger inFlightCount = new AtomicInteger(0);
    private volatile boolean shuttingDown = false;

    @KafkaListener(
            topics = "${relay.topics.job-queue:job-queue}",
            groupId = "${spring.kafka.consumer.group-id:relay-job-consumer-group}"
    )
    public void consumeJob(KafkaJobMessage message) {
        if (message == null || message.getJobId() == null) {
            log.warn("Received empty or invalid job message: {}", message);
            return;
        }

        if (shuttingDown) {
            log.warn("JobConsumer is shutting down. Message for jobId={} will be re-delivered upon restart.", message.getJobId());
            throw new IllegalStateException("Consumer is shutting down");
        }

        inFlightCount.incrementAndGet();

        try {
            // Generate correlation ID (UUID) per job-processing cycle and set MDC for structured logging
            String correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
            MDC.put("jobId", message.getJobId().toString());
            MDC.put("attemptNumber", String.valueOf(message.getAttemptNumber()));
            if (message.getJobType() != null) {
                MDC.put("jobType", message.getJobType().name());
            }

            log.info("Consumer received job: correlationId={}, jobId={}, type={}, attempt={}",
                    correlationId, message.getJobId(), message.getJobType(), message.getAttemptNumber());

            jobProcessingService.processJob(message.getJobId(), message.getAttemptNumber());

        } catch (Exception e) {
            log.error("Unhandled error in JobConsumer for jobId={}: {}", message.getJobId(), e.getMessage(), e);
        } finally {
            inFlightCount.decrementAndGet();
            MDC.clear();
        }

    }

    public int getInFlightCount() {
        return inFlightCount.get();
    }

    @PreDestroy
    public void gracefulShutdown() {
        shuttingDown = true;
        int active = inFlightCount.get();
        if (active > 0) {
            log.info("Graceful shutdown: Waiting for {} in-flight jobs to complete (bounded up to 30s)...", active);
            long deadline = System.currentTimeMillis() + 30000;
            while (inFlightCount.get() > 0 && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for in-flight jobs during shutdown.");
                    break;
                }
            }
            if (inFlightCount.get() == 0) {
                log.info("Graceful shutdown: All in-flight jobs completed cleanly.");
            } else {
                log.warn("Graceful shutdown: Timeout reached with {} in-flight jobs still executing.", inFlightCount.get());
            }
        } else {
            log.info("Graceful shutdown: No in-flight jobs to wait for.");
        }
    }
}

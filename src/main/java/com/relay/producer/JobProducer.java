package com.relay.producer;

import com.relay.dto.DeadLetterJobMessage;
import com.relay.dto.KafkaJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Value("${relay.topics.job-dlq:job-dlq}")
    private String dlqTopic;

    public CompletableFuture<SendResult<String, Object>> sendJob(KafkaJobMessage message) {
        String key = message.getIdempotencyKey() != null
                ? message.getIdempotencyKey()
                : message.getJobType().name();

        log.info("Publishing job to topic '{}' with key '{}': jobId={}, attemptNumber={}",
                jobTopic, key, message.getJobId(), message.getAttemptNumber());

        return kafkaTemplate.send(jobTopic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish job message for jobId={}: {}", message.getJobId(), ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully published job message for jobId={} at offset={}",
                                message.getJobId(), result.getRecordMetadata().offset());
                    }
                });
    }

    public CompletableFuture<SendResult<String, Object>> sendToDlq(DeadLetterJobMessage message) {
        String key = message.getJobType().name();

        log.warn("Publishing job to DLQ topic '{}': jobId={}, reason='{}'",
                dlqTopic, message.getJobId(), message.getDeadLetterReason());

        return kafkaTemplate.send(dlqTopic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish job to DLQ for jobId={}: {}", message.getJobId(), ex.getMessage(), ex);
                    } else {
                        log.info("Successfully published job to DLQ for jobId={} at offset={}",
                                message.getJobId(), result.getRecordMetadata().offset());
                    }
                });
    }
}

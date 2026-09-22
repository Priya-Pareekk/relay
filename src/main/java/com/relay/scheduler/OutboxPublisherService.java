package com.relay.scheduler;

import com.relay.domain.entity.OutboxEvent;
import com.relay.domain.enums.OutboxEventType;
import com.relay.domain.enums.OutboxStatus;
import com.relay.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Value("${relay.topics.job-dlq:job-dlq}")
    private String dlqTopic;

    @Value("${relay.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${relay.outbox.poller-interval-ms:2000}")
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findUnpublishedEvents(
                PageRequest.of(0, batchSize)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    @Transactional
    public void publishSingleEvent(OutboxEvent event) {
        String targetTopic = determineTopic(event);
        String partitionKey = event.getPartitionKey() != null
                ? event.getPartitionKey()
                : (event.getAggregateId() != null ? event.getAggregateId().toString() : null);

        String correlationId = UUID.randomUUID().toString();
        try {
            org.slf4j.MDC.put("correlationId", correlationId);
            if (event.getAggregateId() != null) {
                org.slf4j.MDC.put("jobId", event.getAggregateId().toString());
            }
            if (event.getEventType() != null) {
                org.slf4j.MDC.put("eventType", event.getEventType().name());
            }

            log.debug("Publishing outbox event id={} to topic '{}' with key '{}', correlationId={}",
                    event.getId(), targetTopic, partitionKey, correlationId);

            // Synchronously wait for Kafka ACK to ensure delivery guarantee before marking as SENT
            kafkaTemplate.send(targetTopic, partitionKey, event.getPayload())
                    .get(5, TimeUnit.SECONDS);

            event.markSent(Instant.now());
            outboxEventRepository.save(event);

            log.info("Outbox event published successfully: id={}, aggregateId={}, type={}, topic={}, status=SENT",
                    event.getId(), event.getAggregateId(), event.getEventType(), targetTopic);

        } catch (Exception e) {
            log.error("Failed to publish outbox event id={} for aggregateId={}: {}",
                    event.getId(), event.getAggregateId(), e.getMessage(), e);
        } finally {
            org.slf4j.MDC.clear();
        }
    }


    private String determineTopic(OutboxEvent event) {
        if (event.getTopic() != null && !event.getTopic().isBlank()) {
            return event.getTopic();
        }
        if (event.getEventType() == OutboxEventType.DEAD_LETTER) {
            return dlqTopic;
        }
        return jobTopic;
    }
}


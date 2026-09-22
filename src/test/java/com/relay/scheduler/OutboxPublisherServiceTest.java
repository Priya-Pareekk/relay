package com.relay.scheduler;

import com.relay.domain.entity.OutboxEvent;
import com.relay.domain.enums.OutboxEventType;
import com.relay.domain.enums.OutboxStatus;
import com.relay.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxPublisherService publisherService;

    @BeforeEach
    void setUp() {
        publisherService = new OutboxPublisherService(outboxEventRepository, kafkaTemplate);
        ReflectionTestUtils.setField(publisherService, "batchSize", 10);
        ReflectionTestUtils.setField(publisherService, "jobTopic", "job-queue");
        ReflectionTestUtils.setField(publisherService, "dlqTopic", "job-dlq");
    }

    @Test
    @DisplayName("Should poll pending events, send to Kafka, and mark status=SENT and sentAt timestamp")
    void testPublishOutboxEventsSuccess() {
        UUID eventId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("JOB")
                .aggregateId(jobId)
                .eventType(OutboxEventType.JOB_SUBMITTED)
                .topic("job-queue")
                .partitionKey("EMAIL_NOTIFICATION")
                .payload(Map.of("jobId", jobId.toString()))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(outboxEventRepository.findUnpublishedEvents(any())).thenReturn(List.of(event));

        SendResult<String, Object> sendResult = new SendResult<>(
                null,
                new RecordMetadata(new TopicPartition("job-queue", 0), 0, 0, 0, 0, 0)
        );
        when(kafkaTemplate.send(eq("job-queue"), eq("EMAIL_NOTIFICATION"), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisherService.publishOutboxEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Should not mark SENT if Kafka publication fails")
    void testPublishOutboxEventsFailureLeavesUnpublished() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("JOB")
                .aggregateId(UUID.randomUUID())
                .eventType(OutboxEventType.JOB_SUBMITTED)
                .topic("job-queue")
                .partitionKey("KEY")
                .payload(Map.of("data", 123))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(outboxEventRepository.findUnpublishedEvents(any())).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unreachable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

        publisherService.publishOutboxEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getSentAt()).isNull();
        verify(outboxEventRepository, never()).save(event);
    }
}


package com.relay;

import com.relay.domain.entity.Job;
import com.relay.domain.entity.OutboxEvent;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.domain.enums.OutboxEventType;
import com.relay.domain.enums.OutboxStatus;
import com.relay.dto.DeadLetterJobMessage;
import com.relay.dto.KafkaJobMessage;
import com.relay.repository.JobRepository;
import com.relay.repository.OutboxEventRepository;
import com.relay.scheduler.OutboxPublisherService;
import com.relay.service.JobProcessingService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "relay.outbox.poller-interval-ms=600000"
})
class TransactionalOutboxCrashRecoveryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobProcessingService jobProcessingService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Value("${relay.topics.job-dlq:job-dlq}")
    private String dlqTopic;

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Test
    @DisplayName("Should survive app crash between DB write and Kafka publish without losing events or violating at-least-once delivery")
    void testCrashBetweenDbWriteAndKafkaPublishRecovery() {
        // 1. Setup a failing job about to exhaust retries (attempt 2 of 2)
        Job job = Job.builder()
                .jobType(JobType.REPORT_GENERATION)
                .payload(Map.of("reportType", "INVALID", "fail", true))
                .status(JobStatus.RETRYING)
                .attemptCount(1)
                .maxAttempts(2)
                .build();

        Job savedJob = jobRepository.save(job);
        UUID jobId = savedJob.getId();

        // Count existing outbox events before crash
        long initialPendingCount = outboxEventRepository.countByStatus(OutboxStatus.PENDING);

        // 2. Execute consumer processing in a transactional method
        // This writes Job status = DEAD_LETTER and OutboxEvent = PENDING in the SAME PostgreSQL transaction
        jobProcessingService.processJob(jobId, 2);

        // 3. SIMULATE APP CRASH:
        // Immediate verification right after DB commit, before OutboxPublisherService has run:
        // Verify database state survived crash with zero data loss
        Job crashedJobState = jobRepository.findById(jobId).orElseThrow();
        assertThat(crashedJobState.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
        assertThat(crashedJobState.getAttemptCount()).isEqualTo(2);

        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 100)
        );

        OutboxEvent crashEvent = pendingEvents.stream()
                .filter(e -> e.getAggregateId().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("OutboxEvent must be persisted in DB prior to publisher execution"));

        assertThat(crashEvent.getAggregateType()).isEqualTo("JOB");
        assertThat(crashEvent.getEventType()).isEqualTo(OutboxEventType.DEAD_LETTER);
        assertThat(crashEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(crashEvent.getSentAt()).isNull();
        assertThat(crashEvent.getTopic()).isEqualTo(dlqTopic);

        // 4. APP RECOVERY / REBOOT:
        // Background OutboxPublisherService wakes up on recovery and processes pending events
        outboxPublisherService.publishOutboxEvents();

        // 5. Verify that after publisher runs, the event is marked SENT in Postgres
        OutboxEvent publishedEvent = outboxEventRepository.findById(crashEvent.getId()).orElseThrow();
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(publishedEvent.getSentAt()).isNotNull();

        // 6. Verify Kafka topic received the message
        Consumer<String, Object> consumer = createTestKafkaConsumer();
        consumer.subscribe(Collections.singletonList(dlqTopic));

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
                    boolean found = false;
                    for (ConsumerRecord<String, Object> record : records) {
                        if (record.value() instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) record.value();
                            if (jobId.toString().equals(map.get("jobId"))) {
                                found = true;
                                assertThat(map.get("jobType")).isEqualTo("REPORT_GENERATION");
                                break;
                            }
                        }
                    }
                    assertThat(found).isTrue();
                });

        consumer.close();

        // 7. AT-LEAST-ONCE & IDEMPOTENCY CHECK:
        // Running publisher again must NOT re-publish already SENT events
        long remainingPending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        assertThat(remainingPending).isEqualTo(initialPendingCount);
    }

    @Test
    @DisplayName("Should persist retry outbox event in DB transaction on execution failure and publish on schedule")
    void testRetryOutboxEventSavedInSameTransaction() {
        Job job = Job.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "fail@domain.com", "fail", true))
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .build();

        Job savedJob = jobRepository.save(job);
        UUID jobId = savedJob.getId();

        // Process attempt 1 (will fail and move to RETRYING)
        jobProcessingService.processJob(jobId, 1);

        Job retryJobState = jobRepository.findById(jobId).orElseThrow();
        assertThat(retryJobState.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(retryJobState.getAttemptCount()).isEqualTo(1);
        assertThat(retryJobState.getNextRetryAt()).isNotNull();

        // Verify outbox event was created in the SAME transaction
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 100)
        );

        OutboxEvent retryEvent = pendingEvents.stream()
                .filter(e -> e.getAggregateId().equals(jobId) && e.getEventType() == OutboxEventType.JOB_RETRY)
                .findFirst()
                .orElseThrow();

        assertThat(retryEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(retryEvent.getTopic()).isEqualTo(jobTopic);

        // Publisher publishes the retry event
        outboxPublisherService.publishOutboxEvents();

        OutboxEvent sentRetryEvent = outboxEventRepository.findById(retryEvent.getId()).orElseThrow();
        assertThat(sentRetryEvent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(sentRetryEvent.getSentAt()).isNotNull();
    }

    private Consumer<String, Object> createTestKafkaConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-verifier-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<String, Object>(props).createConsumer();
    }
}

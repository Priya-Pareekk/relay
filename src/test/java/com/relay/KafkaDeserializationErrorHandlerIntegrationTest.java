package com.relay;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobStatus;
import com.relay.domain.enums.JobType;
import com.relay.dto.KafkaJobMessage;
import com.relay.repository.JobRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class KafkaDeserializationErrorHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Value("${relay.topics.job-dlq:job-dlq}")
    private String dlqTopic;

    @Test
    @DisplayName("Should route malformed message with raw bytes, error reason, and timestamp directly to job-dlq without crashing consumer")
    void testMalformedMessageRoutedToDlqAndConsumerRemainsOperational() {
        // 1. Create raw string Kafka producer to publish un-deserializable poison-pill
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        KafkaTemplate<String, String> rawStringKafkaTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps)
        );

        String malformedPayload = "{\"badField\": [unclosed_json_array_corrupt";
        String poisonKey = "corrupt-job-key-123";

        // 2. Publish malformed message to job-queue
        rawStringKafkaTemplate.send(jobTopic, poisonKey, malformedPayload);

        // 3. Set up consumer to listen to job-dlq for the forwarded poison-pill
        Consumer<String, Object> dlqConsumer = createDlqTestConsumer();
        dlqConsumer.subscribe(Collections.singletonList(dlqTopic));

        // 4. Assert that the malformed message lands in job-dlq containing rawPayload, errorReason, and timestamp
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    ConsumerRecords<String, Object> records = dlqConsumer.poll(Duration.ofMillis(500));
                    boolean foundDlqRecord = false;
                    for (ConsumerRecord<String, Object> record : records) {
                        if (record.value() instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) record.value();
                            if (map.containsKey("rawPayload") && map.get("rawPayload").toString().contains("unclosed_json_array_corrupt")) {
                                foundDlqRecord = true;
                                assertThat(map.get("errorReason")).isNotNull();
                                assertThat(map.get("timestamp")).isNotNull();
                                assertThat(map.get("sourceTopic")).isEqualTo(jobTopic);
                                break;
                            }
                        }
                    }
                    assertThat(foundDlqRecord).isTrue();
                });

        dlqConsumer.close();

        // 5. PROVE CONSUMER DID NOT CRASH OR BLOCK:
        // Publish a subsequent VALID job and verify the consumer successfully processes it to COMPLETED
        Job validJob = Job.builder()
                .jobType(JobType.EMAIL_NOTIFICATION)
                .payload(Map.of("to", "healthy@example.com"))
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .build();
        Job savedValidJob = jobRepository.save(validJob);

        KafkaJobMessage validMessage = KafkaJobMessage.builder()
                .jobId(savedValidJob.getId())
                .jobType(savedValidJob.getJobType())
                .attemptNumber(1)
                .payload(savedValidJob.getPayload())
                .build();

        Map<String, Object> jsonProducerProps = new HashMap<>();
        jsonProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        jsonProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        jsonProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        KafkaTemplate<String, Object> jsonKafkaTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(jsonProducerProps)
        );

        jsonKafkaTemplate.send(jobTopic, savedValidJob.getId().toString(), validMessage);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Job processedJob = jobRepository.findById(savedValidJob.getId()).orElseThrow();
                    assertThat(processedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
                    assertThat(processedJob.getAttemptCount()).isEqualTo(1);
                });
    }

    private Consumer<String, Object> createDlqTestConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-deserialization-verifier-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<String, Object>(props).createConsumer();
    }
}

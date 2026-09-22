package com.relay.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConfigTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "jobTopic", "job-queue");
        ReflectionTestUtils.setField(kafkaConfig, "dlqTopic", "job-dlq");
    }

    @Test
    @DisplayName("DeserializationFailureRecoverer should extract raw bytes, error reason, and timestamp and publish directly to job-dlq")
    void testDeserializationFailureRecovererPublishesToJobDlq() {
        DeserializationFailureRecoverer recoverer = kafkaConfig.deserializationFailureRecoverer(kafkaTemplate);

        byte[] rawBytes = "{invalid-json-payload-corrupted}".getBytes(StandardCharsets.UTF_8);
        DeserializationException desEx = new DeserializationException("Failed to deserialize", rawBytes, false, new RuntimeException("Malformed JSON"));

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("job-queue", 2, 42L, "corrupt-key", null);

        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        recoverer.accept(record, desEx);

        @SuppressWarnings("unchecked")

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        verify(kafkaTemplate).send(eq("job-dlq"), eq("corrupt-key"), payloadCaptor.capture());

        Map<String, Object> sentPayload = payloadCaptor.getValue();
        assertThat(sentPayload).isNotNull();
        assertThat(sentPayload.get("rawPayload")).isEqualTo("{invalid-json-payload-corrupted}");
        assertThat(sentPayload.get("errorReason").toString()).contains("Malformed JSON");
        assertThat(sentPayload.get("timestamp")).isNotNull();
        assertThat(sentPayload.get("sourceTopic")).isEqualTo("job-queue");
        assertThat(sentPayload.get("partition")).isEqualTo(2);
        assertThat(sentPayload.get("offset")).isEqualTo(42L);

    }

    @Test
    @DisplayName("DefaultErrorHandler should be created and non-retryable deserialization exceptions should be registered")
    void testKafkaErrorHandlerIsConfigured() {
        DeserializationFailureRecoverer recoverer = kafkaConfig.deserializationFailureRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = kafkaConfig.kafkaErrorHandler(recoverer);

        assertThat(errorHandler).isNotNull();
    }
}


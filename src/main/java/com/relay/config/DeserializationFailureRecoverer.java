package com.relay.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class DeserializationFailureRecoverer implements ConsumerRecordRecoverer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String dlqTopic;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        // Log at ERROR level with partition and offset as explicitly requested
        log.error("Deserialization failure on topic '{}', partition {}, offset {}: {}",
                record.topic(), record.partition(), record.offset(), exception.getMessage(), exception);


        String rawPayload = extractRawPayload(record, exception);
        String errorReason = extractErrorReason(exception);
        Instant timestamp = Instant.now();

        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("rawPayload", rawPayload);
        dlqMessage.put("rawBytes", rawPayload.getBytes(StandardCharsets.UTF_8));
        dlqMessage.put("errorReason", errorReason);
        dlqMessage.put("timestamp", timestamp.toString());
        dlqMessage.put("sourceTopic", record.topic());
        dlqMessage.put("partition", record.partition());
        dlqMessage.put("offset", record.offset());

        String key = record.key() != null ? record.key().toString() : "DESERIALIZATION_FAILURE";

        try {
            kafkaTemplate.send(dlqTopic, key, dlqMessage);
            log.info("Successfully published malformed message (topic={}, partition={}, offset={}) directly to DLQ '{}'",
                    record.topic(), record.partition(), record.offset(), dlqTopic);
        } catch (Exception e) {
            log.error("Failed to forward deserialization failure to DLQ topic '{}': {}", dlqTopic, e.getMessage(), e);
        }
    }

    private String extractRawPayload(ConsumerRecord<?, ?> record, Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof DeserializationException) {
                byte[] data = ((DeserializationException) cause).getData();
                if (data != null && data.length > 0) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            }
            cause = cause.getCause();
        }

        if (record.value() != null) {
            if (record.value() instanceof byte[]) {
                return new String((byte[]) record.value(), StandardCharsets.UTF_8);
            }
            return record.value().toString();
        }

        return "<MALFORMED_PAYLOAD>";
    }

    private String extractErrorReason(Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}

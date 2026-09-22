package com.relay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.domain.entity.OutboxEvent;
import com.relay.domain.enums.OutboxEventType;
import com.relay.domain.enums.OutboxStatus;
import com.relay.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent saveEvent(UUID aggregateId, OutboxEventType eventType, String topic, String partitionKey, Object messagePayload) {
        return saveEvent("JOB", aggregateId, eventType, topic, partitionKey, messagePayload);
    }

    @Transactional
    public OutboxEvent saveEvent(String aggregateType, UUID aggregateId, OutboxEventType eventType, String topic, String partitionKey, Object messagePayload) {
        Map<String, Object> payloadMap;
        if (messagePayload instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) messagePayload;
            payloadMap = map;
        } else {
            payloadMap = objectMapper.convertValue(messagePayload, new TypeReference<Map<String, Object>>() {});
        }

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType != null ? aggregateType : "JOB")
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .partitionKey(partitionKey)
                .payload(payloadMap)
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        OutboxEvent saved = outboxEventRepository.save(event);
        log.debug("Saved outbox event: id={}, aggregateId={}, type={}, topic={}, status=PENDING",
                saved.getId(), aggregateId, eventType, topic);
        return saved;
    }
}


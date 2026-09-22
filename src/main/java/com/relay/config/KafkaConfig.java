package com.relay.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${relay.topics.job-queue:job-queue}")
    private String jobTopic;

    @Value("${relay.topics.job-dlq:job-dlq}")
    private String dlqTopic;

    @Bean
    public NewTopic jobQueueTopic() {
        return TopicBuilder.name(jobTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobDlqTopic() {
        return TopicBuilder.name(dlqTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public DeserializationFailureRecoverer deserializationFailureRecoverer(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DeserializationFailureRecoverer(kafkaTemplate, dlqTopic);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeserializationFailureRecoverer recoverer) {
        // Retry 2 times with 1-second backoff for transient issues before moving to DLQ
        FixedBackOff backOff = new FixedBackOff(1000L, 2L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Immediately route poison pills (deserialization and conversion failures) to DLQ without retrying
        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,
                MessageConversionException.class,
                org.apache.kafka.common.errors.SerializationException.class,
                com.fasterxml.jackson.core.JsonParseException.class,
                com.fasterxml.jackson.databind.JsonMappingException.class,
                com.fasterxml.jackson.databind.exc.MismatchedInputException.class,
                IllegalArgumentException.class,
                ClassCastException.class
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setShutdownTimeout(30000L);
        return factory;
    }
}
